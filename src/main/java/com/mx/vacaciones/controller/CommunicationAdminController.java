package com.mx.vacaciones.controller;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.communication.dto.AnnouncementRequest;
import com.inssoft.people.communication.dto.AnnouncementResponse;
import com.inssoft.people.communication.service.CommunicationService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

@Controller
@RequestMapping("/admin/communications")
@PreAuthorize("@accessGuard.has('COMMUNICATION_PUBLISH')")
public class CommunicationAdminController {

    private final CommunicationService communicationService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CommunicationAdminController(
            CommunicationService communicationService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository) {
        this.communicationService = communicationService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String communications(
            Authentication authentication,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        PageResponse<AnnouncementResponse> announcements = communicationService.search(
                resolveCompany(authentication), q, Math.max(page, 0), 20);
        model.addAttribute("comunicados", announcements);
        model.addAttribute("q", q);
        return "admin/communications";
    }

    @PostMapping
    public String create(
            Authentication authentication,
            @RequestParam String title,
            @RequestParam String body,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiresAt,
            RedirectAttributes redirect) {
        try {
            communicationService.create(resolveCompany(authentication),
                    new AnnouncementRequest(title, body, priority, expiresAt),
                    currentUser(authentication));
            redirect.addFlashAttribute("ok", "Comunicado publicado");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/admin/communications";
    }

    @PostMapping("/{uuid}/delete")
    public String delete(
            Authentication authentication,
            @PathVariable UUID uuid,
            RedirectAttributes redirect) {
        try {
            communicationService.delete(uuid, resolveCompany(authentication),
                    currentUser(authentication));
            redirect.addFlashAttribute("ok", "Comunicado eliminado");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/admin/communications";
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
