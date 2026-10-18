package com.mx.vacaciones.web;

import com.mx.vacaciones.security.RoleResolver;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Datos comunes para las vistas Thymeleaf: usuario y rol funcional.
 * Las páginas nuevas usan estos atributos para construir el menú por rol.
 */
@ControllerAdvice
public class WebModelAdvice {

    @ModelAttribute
    public void addUserContext(Model model, Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        model.addAttribute("usuario", authentication.getName());
        model.addAttribute("rol", RoleResolver.resolve(authentication));
    }
}
