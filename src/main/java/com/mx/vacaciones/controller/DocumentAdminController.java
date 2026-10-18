package com.mx.vacaciones.controller;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin/documents")
@PreAuthorize("@accessGuard.has('DOCUMENT_VIEW')")
public class DocumentAdminController {

    private final DocumentService documentService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public DocumentAdminController(
            DocumentService documentService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository) {
        this.documentService = documentService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String documents(
            Authentication authentication,
            @RequestParam(required = false) UUID employeeUuid,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Company company = resolveCompany(authentication);
        PageResponse<DocumentResponse> documents = documentService.search(
                company, employeeUuid, typeCode, q, Math.max(page, 0), 20);

        model.addAttribute("documentos", documents);
        model.addAttribute("tipos", documentService.types());
        model.addAttribute("empleados", employeeRepository.findByDeletedAtIsNullAndEnabledTrue());
        model.addAttribute("employeeUuid", employeeUuid);
        model.addAttribute("typeCode", typeCode);
        model.addAttribute("q", q);
        return "admin/documents";
    }

    @PostMapping("/upload")
    @PreAuthorize("@accessGuard.has('DOCUMENT_UPLOAD')")
    public String upload(
            Authentication authentication,
            @RequestParam UUID employeeUuid,
            @RequestParam String typeCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) DocumentVisibility visibility,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirect) {
        try {
            Employee employee = employeeRepository.findByUuidAndDeletedAtIsNull(employeeUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Empleado", employeeUuid));
            documentService.upload(employee, typeCode, name, description, visibility, expiresAt, file,
                    currentUser(authentication));
            redirect.addFlashAttribute("ok", "Documento cargado correctamente");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/admin/documents";
    }

    @PostMapping("/{uuid}/delete")
    @PreAuthorize("@accessGuard.has('DOCUMENT_DELETE')")
    public String delete(
            Authentication authentication,
            @PathVariable UUID uuid,
            RedirectAttributes redirect) {
        try {
            documentService.delete(uuid, currentUser(authentication), resolveCompany(authentication));
            redirect.addFlashAttribute("ok", "Documento eliminado");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/admin/documents";
    }

    @GetMapping("/{uuid}/download")
    public ResponseEntity<byte[]> download(Authentication authentication, @PathVariable UUID uuid) {
        DocumentService.Download download = documentService.download(
                uuid, currentUser(authentication), resolveCompany(authentication), true);
        return DocumentWebController.toResponse(download);
    }

    private Company resolveCompany(Authentication authentication) {
        return employeeRepository.findByUser_UsernameAndDeletedAtIsNull(authentication.getName())
                .map(Employee::getCompany)
                .orElseGet(() -> companyRepository.findFirstByDeletedAtIsNullOrderByIdAsc()
                        .orElseThrow(() -> new ResourceNotFoundException("No existe una empresa activa")));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", authentication.getName()));
    }
}
