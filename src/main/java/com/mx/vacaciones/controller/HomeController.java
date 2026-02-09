package com.mx.vacaciones.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {

        String username = authentication.getName();

        String rol = authentication.getAuthorities()
                .stream()
                .findFirst()
                .get()
                .getAuthority();

        model.addAttribute("usuario", username);
        model.addAttribute("rol", rol);

        return "home";
    }
}