package com.inssoft.people.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ThemePreferenceRequest(
        @NotBlank(message = "El tema es obligatorio")
        @Pattern(regexp = "(?i)^(LIGHT|DARK)$", message = "El tema debe ser LIGHT o DARK")
        String theme
) {
}
