package com.mx.vacaciones.controller;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.payroll.dto.PayReceiptResponse;
import com.inssoft.people.payroll.service.PayrollService;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequestMapping("/payroll")
@PreAuthorize("@accessGuard.has('PAYROLL_VIEW_SELF')")
public class PayrollWebController {

    private final PayrollService payrollService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public PayrollWebController(
            PayrollService payrollService,
            EmployeeRepository employeeRepository,
            UserRepository userRepository) {
        this.payrollService = payrollService;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String myReceipts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        PageResponse<PayReceiptResponse> receipts =
                payrollService.listSelf(authentication.getName(), Math.max(page, 0), 20);
        model.addAttribute("recibos", receipts);
        return "payroll";
    }

    @GetMapping("/{uuid}/download")
    public ResponseEntity<byte[]> download(Authentication authentication, @PathVariable UUID uuid) {
        Employee employee = currentEmployee(authentication);
        PayrollService.Download download = payrollService.download(
                uuid, currentUser(authentication), employee.getCompany(), false);
        return toResponse(download);
    }

    static ResponseEntity<byte[]> toResponse(PayrollService.Download download) {
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .contentLength(download.sizeBytes())
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment()
                                .filename(download.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .body(download.content());
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
}
