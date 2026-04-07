package com.mx.vacaciones.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mx.vacaciones.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Controlador encargado del cambio obligatorio de contraseña.
 *
 * <p>
 * Se utiliza cuando el usuario inicia sesión con una contraseña temporal.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class PasswordController {

    private final UserService userService;

    /**
     * Muestra la pantalla para cambiar contraseña.
     *
     * @param model modelo para la vista
     * @return vista change-password
     */
    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        return "change-password";
    }

    /**
     * Procesa el cambio de contraseña del usuario autenticado.
     *
     * @param authentication usuario autenticado
     * @param newPassword nueva contraseña
     * @param confirmPassword confirmación de la nueva contraseña
     * @param model modelo para la vista
     * @return vista o redirección según resultado
     */
    @PostMapping("/change-password")
    public String changePassword(
            Authentication authentication,
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

        String username = authentication.getName();
        userService.changeOwnPassword(username, newPassword);

        return "redirect:/home?ok=PASSWORD_CHANGED";
    }
}