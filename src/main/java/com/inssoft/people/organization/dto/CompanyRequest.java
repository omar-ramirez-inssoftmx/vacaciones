package com.inssoft.people.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 30, message = "El código no puede exceder 30 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "El código solo permite letras, números, guion y guion bajo")
        String code,

        @NotBlank(message = "La razón social es obligatoria")
        @Size(max = 200, message = "La razón social no puede exceder 200 caracteres")
        String legalName,

        @Size(max = 200, message = "El nombre comercial no puede exceder 200 caracteres")
        String tradeName,

        @Size(max = 13, message = "El RFC no puede exceder 13 caracteres")
        @Pattern(
                regexp = "^$|^[A-Za-z&Ññ]{3,4}[0-9]{6}[A-Za-z0-9]{3}$",
                message = "El RFC no tiene un formato válido")
        String rfc,

        @Size(max = 64, message = "La zona horaria no puede exceder 64 caracteres")
        String timezone,

        Boolean enabled
) {
}
