package com.inssoft.people.documents.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.documents.dto.DocumentResponse;
import com.inssoft.people.documents.dto.DocumentTypeResponse;
import com.inssoft.people.documents.entity.DocumentVisibility;
import com.inssoft.people.documents.service.DocumentService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Validated
@Tag(name = "Documents", description = "Documentos de empleados, descarga autorizada y tipos")
@SecurityRequirement(name = "bearerAuth")
public class DocumentApiController {

    private final DocumentService documentService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public DocumentApiController(
            DocumentService documentService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository,
            AccessGuard accessGuard) {
        this.documentService = documentService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/types")
    @PreAuthorize("@accessGuard.hasAny('DOCUMENT_VIEW', 'DOCUMENT_UPLOAD', 'DOCUMENT_VIEW_SELF')")
    @Operation(summary = "Lista los tipos de documento disponibles")
    public ApiResponse<List<DocumentTypeResponse>> types() {
        return ApiResponse.ok("Tipos obtenidos", documentService.types());
    }

    @GetMapping("/me")
    @PreAuthorize("@accessGuard.has('DOCUMENT_VIEW_SELF')")
    @Operation(summary = "Documentos visibles del usuario autenticado")
    public ApiResponse<PageResponse<DocumentResponse>> myDocuments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Employee employee = currentEmployee(authentication);
        return ApiResponse.ok("Documentos obtenidos",
                documentService.listForEmployee(employee, page, size));
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('DOCUMENT_VIEW')")
    @Operation(summary = "Búsqueda administrativa de documentos")
    public ApiResponse<PageResponse<DocumentResponse>> search(
            Authentication authentication,
            @RequestParam(required = false) UUID companyUuid,
            @RequestParam(required = false) UUID employeeUuid,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Company company = resolveCompany(companyUuid, authentication);
        return ApiResponse.ok("Documentos obtenidos",
                documentService.search(company, employeeUuid, typeCode, q, page, size));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("@accessGuard.hasAny('DOCUMENT_VIEW', 'DOCUMENT_VIEW_SELF')")
    @Operation(summary = "Metadatos de un documento")
    public ApiResponse<DocumentResponse> get(
            Authentication authentication,
            @PathVariable UUID uuid,
            @RequestParam(required = false) UUID companyUuid) {
        Company company = resolveCompany(companyUuid, authentication);
        boolean canViewAll = accessGuard.has("DOCUMENT_VIEW");
        return ApiResponse.ok("Documento obtenido",
                documentService.get(uuid, currentUser(authentication), company, canViewAll));
    }

    @GetMapping("/{uuid}/download")
    @PreAuthorize("@accessGuard.hasAny('DOCUMENT_VIEW', 'DOCUMENT_VIEW_SELF')")
    @Operation(summary = "Descarga autorizada de un documento")
    public ResponseEntity<byte[]> download(
            Authentication authentication,
            @PathVariable UUID uuid,
            @RequestParam(required = false) UUID companyUuid) {
        Company company = resolveCompany(companyUuid, authentication);
        boolean canViewAll = accessGuard.has("DOCUMENT_VIEW");
        DocumentService.Download download = documentService.download(
                uuid, currentUser(authentication), company, canViewAll);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.mimeType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@accessGuard.has('DOCUMENT_UPLOAD')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Carga un documento para un empleado")
    public ApiResponse<DocumentResponse> upload(
            Authentication authentication,
            @RequestParam UUID employeeUuid,
            @RequestParam String typeCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) DocumentVisibility visibility,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
            @RequestPart("file") MultipartFile file) {

        Employee employee = employeeRepository.findByUuidAndDeletedAtIsNull(employeeUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", employeeUuid));

        return ApiResponse.ok("Documento cargado", documentService.upload(
                employee, typeCode, name, description, visibility, expiresAt, file,
                currentUser(authentication)));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('DOCUMENT_DELETE')")
    @Operation(summary = "Deshabilita lógicamente un documento")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable UUID uuid,
            @RequestParam(required = false) UUID companyUuid) {
        Company company = resolveCompany(companyUuid, authentication);
        documentService.delete(uuid, currentUser(authentication), company);
        return ApiResponse.ok("Documento deshabilitado");
    }

    private Employee currentEmployee(Authentication authentication) {
        return employeeRepository.findByUser_UsernameAndDeletedAtIsNull(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe un empleado vinculado al usuario " + authentication.getName()));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", authentication.getName()));
    }

    private Company resolveCompany(UUID companyUuid, Authentication authentication) {
        Employee employee = employeeRepository
                .findByUser_UsernameAndDeletedAtIsNull(authentication.getName())
                .orElse(null);
        Company ownCompany = employee == null ? null : employee.getCompany();

        if (companyUuid == null) {
            if (ownCompany != null) {
                return ownCompany;
            }
            if (accessGuard.has("DOCUMENT_VIEW")) {
                return companyRepository.findFirstByDeletedAtIsNullOrderByIdAsc()
                        .orElseThrow(() -> new ResourceNotFoundException("No existe una empresa activa"));
            }
            throw new ResourceNotFoundException(
                    "No existe una empresa asociada al usuario " + authentication.getName());
        }

        Company requested = companyRepository.findByUuidAndDeletedAtIsNull(companyUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", companyUuid));
        if (ownCompany != null && ownCompany.getId().equals(requested.getId())) {
            return requested;
        }
        if (accessGuard.has("DOCUMENT_VIEW")) {
            return requested;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "No tienes permisos para consultar documentos de otra empresa");
    }
}
