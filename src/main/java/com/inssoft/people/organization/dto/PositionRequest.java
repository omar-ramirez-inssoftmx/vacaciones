package com.inssoft.people.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PositionRequest(
        @NotNull(message = "El departamento es obligatorio")
        UUID departmentUuid,

        @NotBlank(message = "El código es obligatorio")
        @Size(max = 30, message = "El código no puede exceder 30 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "El código solo permite letras, números, guion y guion bajo")
        String code,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String name,

        @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
        String description,

        @Size(max = 50, message = "El nivel no puede exceder 50 caracteres")
        String jobLevel,

        Boolean enabled
) {
}
