package com.inssoft.people.payroll.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.inssoft.people.payroll.dto.PayPeriodRequest;
import com.inssoft.people.payroll.dto.PayPeriodResponse;
import com.inssoft.people.payroll.dto.PayReceiptResponse;
import com.inssoft.people.payroll.service.PayrollService;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payroll")
@Validated
@Tag(name = "Payroll", description = "Periodos y recibos de nómina")
@SecurityRequirement(name = "bearerAuth")
public class PayrollApiController {

    private final PayrollService payrollService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public PayrollApiController(
            PayrollService payrollService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository,
            AccessGuard accessGuard) {
        this.payrollService = payrollService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/periods")
    @PreAuthorize("@accessGuard.hasAny('PAYROLL_VIEW', 'PAYROLL_UPLOAD')")
    @Operation(summary = "Lista los periodos de nómina de la empresa")
    public ApiResponse<List<PayPeriodResponse>> periods(
            Authentication authentication,
            @RequestParam(required = false) UUID companyUuid) {
        return ApiResponse.ok("Periodos obtenidos",
                payrollService.periods(resolveCompany(companyUuid, authentication)));
    }

    @PostMapping("/periods")
    @PreAuthorize("@accessGuard.has('PAYROLL_UPLOAD')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un periodo de nómina")
    public ApiResponse<PayPeriodResponse> createPeriod(
            Authentication authentication,
            @RequestParam(required = false) UUID companyUuid,
            @Valid @RequestBody PayPeriodRequest request) {
        return ApiResponse.ok("Periodo creado",
                payrollService.createPeriod(resolveCompany(companyUuid, authentication), request));
    }

    @GetMapping("/me")
    @PreAuthorize("@accessGuard.has('PAYROLL_VIEW_SELF')")
    @Operation(summary = "Recibos del usuario autenticado")
    public ApiResponse<PageResponse<PayReceiptResponse>> myReceipts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Recibos obtenidos",
                payrollService.listSelf(authentication.getName(), page, size));
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('PAYROLL_VIEW')")
    @Operation(summary = "Búsqueda administrativa de recibos")
    public ApiResponse<PageResponse<PayReceiptResponse>> search(
            Authentication authentication,
            @RequestParam(required = false) UUID companyUuid,
            @RequestParam(required = false) UUID employeeUuid,
            @RequestParam(required = false) UUID periodUuid,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Recibos obtenidos", payrollService.search(
                resolveCompany(companyUuid, authentication),
                employeeUuid, periodUuid, q, page, size));
    }

    @PostMapping(value = "/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@accessGuard.has('PAYROLL_UPLOAD')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Carga el recibo PDF de un empleado para un periodo")
    public ApiResponse<PayReceiptResponse> upload(
            Authentication authentication,
            @RequestParam UUID employeeUuid,
            @RequestParam UUID periodUuid,
            @RequestPart("file") MultipartFile file) {
        Employee employee = employeeRepository.findByUuidAndDeletedAtIsNull(employeeUuid)
                .orElseThrow(() -> new com.inssoft.people.common.exception.ResourceNotFoundException(
                        "Empleado", employeeUuid));
        return ApiResponse.ok("Recibo cargado", payrollService.upload(
                employee, periodUuid, file, currentUser(authentication)));
    }

    @GetMapping("/receipts/{uuid}/download")
    @PreAuthorize("@accessGuard.hasAny('PAYROLL_VIEW', 'PAYROLL_VIEW_SELF')")
    @Operation(summary = "Descarga autorizada de un recibo")
    public ResponseEntity<byte[]> download(
            Authentication authentication,
            @PathVariable UUID uuid,
            @RequestParam(required = false) UUID companyUuid) {
        PayrollService.Download download = payrollService.download(
                uuid, currentUser(authentication),
                resolveCompany(companyUuid, authentication),
                accessGuard.has("PAYROLL_VIEW"));

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    @DeleteMapping("/receipts/{uuid}")
    @PreAuthorize("@accessGuard.has('PAYROLL_UPLOAD')")
    @Operation(summary = "Elimina lógicamente un recibo")
    public ApiResponse<Void> delete(
            Authentication authentication,
            @PathVariable UUID uuid,
            @RequestParam(required = false) UUID companyUuid) {
        payrollService.delete(uuid, currentUser(authentication),
                resolveCompany(companyUuid, authentication));
        return ApiResponse.ok("Recibo eliminado");
    }

    private Employee currentEmployee(Authentication authentication) {
        return employeeRepository.findByUser_UsernameAndDeletedAtIsNull(authentication.getName())
                .orElseThrow(() -> new com.inssoft.people.common.exception.ResourceNotFoundException(
                        "No existe un empleado vinculado al usuario " + authentication.getName()));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new com.inssoft.people.common.exception.ResourceNotFoundException(
                        "Usuario", authentication.getName()));
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
            if (accessGuard.has("PAYROLL_VIEW")) {
                return companyRepository.findFirstByDeletedAtIsNullOrderByIdAsc()
                        .orElseThrow(() -> new com.inssoft.people.common.exception.ResourceNotFoundException(
                                "No existe una empresa activa"));
            }
            throw new com.inssoft.people.common.exception.ResourceNotFoundException(
                    "No existe una empresa asociada al usuario " + authentication.getName());
        }

        Company requested = companyRepository.findByUuidAndDeletedAtIsNull(companyUuid)
                .orElseThrow(() -> new com.inssoft.people.common.exception.ResourceNotFoundException(
                        "Empresa", companyUuid));
        if (ownCompany != null && ownCompany.getId().equals(requested.getId())) {
            return requested;
        }
        if (accessGuard.has("PAYROLL_VIEW")) {
            return requested;
        }
        throw new com.inssoft.people.common.exception.BusinessException(
                com.inssoft.people.common.api.ErrorCode.FORBIDDEN,
                "No puedes consultar nómina de otra empresa");
    }
}
