package com.mx.vacaciones.controller;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.documents.dto.DocumentResponse;
import com.inssoft.people.documents.service.DocumentService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
@RequestMapping("/documents")
@PreAuthorize("@accessGuard.has('DOCUMENT_VIEW_SELF')")
public class DocumentWebController {

    private final DocumentService documentService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public DocumentWebController(
            DocumentService documentService,
            EmployeeRepository employeeRepository,
            UserRepository userRepository) {
        this.documentService = documentService;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String myDocuments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Employee employee = currentEmployee(authentication);
        PageResponse<DocumentResponse> documents =
                documentService.listForEmployee(employee, Math.max(page, 0), 20);
        model.addAttribute("documentos", documents);
        return "documents";
    }

    @GetMapping("/{uuid}/download")
    public ResponseEntity<byte[]> download(Authentication authentication, @PathVariable UUID uuid) {
        Employee employee = currentEmployee(authentication);
        DocumentService.Download download = documentService.download(
                uuid, currentUser(authentication), employee.getCompany(), false);
        return toResponse(download);
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

    static ResponseEntity<byte[]> toResponse(DocumentService.Download download) {
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
}
