package com.inssoft.people.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BranchRequest(
        @NotNull(message = "La empresa es obligatoria")
        UUID companyUuid,

        @NotBlank(message = "El código es obligatorio")
        @Size(max = 30, message = "El código no puede exceder 30 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "El código solo permite letras, números, guion y guion bajo")
        String code,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String name,

        @Size(max = 300, message = "La dirección no puede exceder 300 caracteres")
        String address,

        @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
        String phone,

        Boolean enabled
) {
}
