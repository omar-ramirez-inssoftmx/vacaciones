package com.mx.vacaciones.controller;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.inssoft.people.payroll.dto.PayPeriodRequest;
import com.inssoft.people.payroll.dto.PayReceiptResponse;
import com.inssoft.people.payroll.service.PayrollService;
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
import java.util.UUID;

@Controller
@RequestMapping("/admin/payroll")
@PreAuthorize("@accessGuard.has('PAYROLL_VIEW')")
public class PayrollAdminController {

    private final PayrollService payrollService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public PayrollAdminController(
            PayrollService payrollService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository) {
        this.payrollService = payrollService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String payroll(
            Authentication authentication,
            @RequestParam(required = false) UUID employeeUuid,
            @RequestParam(required = false) UUID periodUuid,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Company company = resolveCompany(authentication);
        PageResponse<PayReceiptResponse> receipts = payrollService.search(
                company, employeeUuid, periodUuid, q, Math.max(page, 0), 20);

        model.addAttribute("recibos", receipts);
        model.addAttribute("periodos", payrollService.periods(company));
        model.addAttribute("empleados", employeeRepository.findByDeletedAtIsNullAndEnabledTrue());
        model.addAttribute("employeeUuid", employeeUuid);
        model.addAttribute("periodUuid", periodUuid);
        model.addAttribute("q", q);
        model.addAttribute("hoy", LocalDate.now());
        return "admin/payroll";
    }

    @PostMapping("/periods")
    @PreAuthorize("@accessGuard.has('PAYROLL_UPLOAD')")
    public String createPeriod(
            Authentication authentication,
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam String periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            RedirectAttributes redirect) {
        try {
            payrollService.createPeriod(resolveCompany(authentication),
                    new PayPeriodRequest(year, month, periodType, startDate, endDate));
            redirect.addFlashAttribute("ok", "Periodo de nómina creado");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/admin/payroll";
    }

    @PostMapping("/upload")
    @PreAuthorize("@accessGuard.has('PAYROLL_UPLOAD')")
    public String upload(
            Authentication authentication,
            @RequestParam UUID employeeUuid,
            @RequestParam UUID periodUuid,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirect) {
        try {
            Employee employee = employeeRepository.findByUuidAndDeletedAtIsNull(employeeUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Empleado", employeeUuid));
            payrollService.upload(employee, periodUuid, file, currentUser(authentication));
            redirect.addFlashAttribute("ok", "Recibo cargado correctamente");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/admin/payroll";
    }

    @PostMapping("/{uuid}/delete")
    @PreAuthorize("@accessGuard.has('PAYROLL_UPLOAD')")
    public String delete(
            Authentication authentication,
            @PathVariable UUID uuid,
            RedirectAttributes redirect) {
        try {
            payrollService.delete(uuid, currentUser(authentication), resolveCompany(authentication));
            redirect.addFlashAttribute("ok", "Recibo eliminado");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/admin/payroll";
    }

    @GetMapping("/{uuid}/download")
    public ResponseEntity<byte[]> download(Authentication authentication, @PathVariable UUID uuid) {
        PayrollService.Download download = payrollService.download(
                uuid, currentUser(authentication), resolveCompany(authentication), true);
        return PayrollWebController.toResponse(download);
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
