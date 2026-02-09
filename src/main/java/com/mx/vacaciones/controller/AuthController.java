package com.mx.vacaciones.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }
    
    @GetMapping("/whoami")
    @ResponseBody
    public Object whoami(Authentication auth) {
        return auth.getAuthorities();
    }
}