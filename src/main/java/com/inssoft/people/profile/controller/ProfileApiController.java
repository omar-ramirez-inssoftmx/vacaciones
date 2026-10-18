package com.inssoft.people.profile.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.profile.dto.ThemePreferenceRequest;
import com.inssoft.people.profile.dto.ThemePreferenceResponse;
import com.inssoft.people.profile.service.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile", description = "Preferencias del usuario")
@SecurityRequirement(name = "bearerAuth")
public class ProfileApiController {

    private final UserPreferenceService preferenceService;

    public ProfileApiController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/theme")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtiene la preferencia de tema")
    public ApiResponse<ThemePreferenceResponse> getTheme(Authentication authentication) {
        return ApiResponse.ok("Preferencia obtenida",
                preferenceService.getTheme(authentication.getName()));
    }

    @PutMapping("/theme")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Actualiza la preferencia de tema")
    public ApiResponse<ThemePreferenceResponse> updateTheme(
            Authentication authentication,
            @Valid @RequestBody ThemePreferenceRequest request) {
        return ApiResponse.ok("Preferencia actualizada",
                preferenceService.updateTheme(authentication.getName(), request.theme()));
    }
}
