package com.mx.vacaciones.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.inssoft.people.common.exception.BusinessException;
import com.mx.vacaciones.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Controlador encargado del cambio obligatorio de contraseña.
 */
@Controller
@RequiredArgsConstructor
public class PasswordController {

    private final UserService userService;

    @GetMapping("/change-password")
    public String showChangePasswordForm(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
            Authentication authentication,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model model) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            model.addAttribute("err", "La nueva contraseña es obligatoria");
            return "change-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("err", "Las contraseñas no coinciden");
            return "change-password";
        }

        try {
            userService.changeOwnPassword(authentication.getName(), currentPassword, newPassword);
        } catch (BusinessException ex) {
            model.addAttribute("err", ex.getMessage());
            return "change-password";
        }

        return "redirect:/home?ok=PASSWORD_CHANGED";
    }
}
