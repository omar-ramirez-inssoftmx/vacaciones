package com.inssoft.people.profile.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.profile.dto.ThemePreferenceRequest;
import com.inssoft.people.profile.dto.ThemePreferenceResponse;
import com.inssoft.people.profile.service.UserPreferenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de preferencias para el cliente web (sesión + CSRF).
 */
@RestController
@RequestMapping("/profile")
public class ProfilePreferenceController {

    private final UserPreferenceService preferenceService;

    public ProfilePreferenceController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/theme")
    public ApiResponse<ThemePreferenceResponse> getTheme(Authentication authentication) {
        return ApiResponse.ok("Preferencia obtenida",
                preferenceService.getTheme(authentication.getName()));
    }

    @PostMapping("/theme")
    public ApiResponse<ThemePreferenceResponse> updateTheme(
            Authentication authentication,
            @Valid @RequestBody ThemePreferenceRequest request) {
        return ApiResponse.ok("Preferencia actualizada",
                preferenceService.updateTheme(authentication.getName(), request.theme()));
    }
}
