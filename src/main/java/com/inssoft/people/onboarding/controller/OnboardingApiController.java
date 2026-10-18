package com.inssoft.people.onboarding.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.onboarding.dto.OnboardingResponse;
import com.inssoft.people.onboarding.service.OnboardingService;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "Recorrido inicial para usuarios nuevos")
@SecurityRequirement(name = "bearerAuth")
public class OnboardingApiController {

    private final OnboardingService onboardingService;
    private final UserRepository userRepository;

    public OnboardingApiController(
            OnboardingService onboardingService,
            UserRepository userRepository) {
        this.onboardingService = onboardingService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Estado y pasos del recorrido inicial")
    public ApiResponse<OnboardingResponse> status(Authentication authentication) {
        User user = currentUser(authentication);
        return ApiResponse.ok("Onboarding obtenido", onboardingService.statusFor(user));
    }

    @PostMapping("/complete")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marca el recorrido inicial como completado")
    public ApiResponse<Void> complete(Authentication authentication) {
        onboardingService.complete(currentUser(authentication));
        return ApiResponse.ok("Recorrido completado");
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new com.inssoft.people.common.exception.ResourceNotFoundException(
                        "Usuario", authentication.getName()));
    }
}
