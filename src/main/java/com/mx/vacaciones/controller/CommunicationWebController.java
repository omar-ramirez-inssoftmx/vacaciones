package com.mx.vacaciones.controller;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.communication.dto.AnnouncementResponse;
import com.inssoft.people.communication.service.CommunicationService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/communications")
@PreAuthorize("@accessGuard.has('COMMUNICATION_VIEW')")
public class CommunicationWebController {

    private final CommunicationService communicationService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;

    public CommunicationWebController(
            CommunicationService communicationService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository) {
        this.communicationService = communicationService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
    }

    @GetMapping
    public String communications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        PageResponse<AnnouncementResponse> announcements =
                communicationService.active(resolveCompany(authentication), Math.max(page, 0), 20);
        model.addAttribute("comunicados", announcements);
        return "communications";
    }

    private Company resolveCompany(Authentication authentication) {
        return employeeRepository.findByUser_UsernameAndDeletedAtIsNull(authentication.getName())
                .map(Employee::getCompany)
                .orElseGet(() -> companyRepository.findFirstByDeletedAtIsNullOrderByIdAsc()
                        .orElseThrow(() -> new ResourceNotFoundException("No existe una empresa activa")));
    }
}
