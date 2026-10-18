package com.inssoft.people.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 150, message = "El usuario no puede exceder 150 caracteres")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 128, message = "La contraseña no puede exceder 128 caracteres")
        String password
) {
}
