package com.mx.vacaciones.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.onboarding.service.OnboardingService;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final UserRepository userRepository;

    public OnboardingController(
            OnboardingService onboardingService,
            UserRepository userRepository) {
        this.onboardingService = onboardingService;
        this.userRepository = userRepository;
    }

    @PostMapping("/complete")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> complete(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Autenticación requerida"));
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("Usuario no encontrado"));
        }
        onboardingService.complete(user);
        return ResponseEntity.ok(ApiResponse.ok("Recorrido completado"));
    }
}
