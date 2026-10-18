package com.mx.vacaciones.controller;

import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.dto.EmergencyContactRequest;
import com.inssoft.people.employee.dto.EmployeeResponse;
import com.inssoft.people.employee.dto.ProfileUpdateRequest;
import com.inssoft.people.employee.service.EmployeeService;
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

import java.util.UUID;

@Controller
@RequestMapping("/profile")
@PreAuthorize("@accessGuard.has('EMPLOYEE_VIEW_SELF')")
public class ProfileWebController {

    private final EmployeeService employeeService;

    public ProfileWebController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public String profile(Authentication authentication, Model model) {
        EmployeeResponse employee = employeeService.getMe(authentication.getName());
        model.addAttribute("empleado", employee);
        return "profile";
    }

    @PostMapping("/update")
    public String updateProfile(
            Authentication authentication,
            @RequestParam(required = false) String personalEmail,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String addressLine1,
            @RequestParam(required = false) String addressLine2,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String postalCode,
            @RequestParam(required = false) String country,
            RedirectAttributes redirect) {
        try {
            employeeService.updateMyProfile(authentication.getName(), new ProfileUpdateRequest(
                    personalEmail, phone, addressLine1, addressLine2,
                    city, state, postalCode, country));
            redirect.addFlashAttribute("ok", "Datos de contacto actualizados");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/contacts")
    public String addContact(
            Authentication authentication,
            @RequestParam String name,
            @RequestParam(required = false) String relationship,
            @RequestParam String phone,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "false") boolean primary,
            RedirectAttributes redirect) {
        try {
            employeeService.addMyContact(authentication.getName(),
                    new EmergencyContactRequest(name, relationship, phone, email, primary));
            redirect.addFlashAttribute("ok", "Contacto agregado correctamente");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/contacts/{contactUuid}/delete")
    public String deleteContact(
            Authentication authentication,
            @PathVariable UUID contactUuid,
            RedirectAttributes redirect) {
        try {
            employeeService.deleteMyContact(authentication.getName(), contactUuid);
            redirect.addFlashAttribute("ok", "Contacto eliminado");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/profile";
    }
}
