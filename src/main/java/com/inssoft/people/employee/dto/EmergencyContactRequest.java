package com.inssoft.people.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequest(
        @NotBlank(message = "El nombre del contacto es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String name,

        @Size(max = 50, message = "El parentesco no puede exceder 50 caracteres")
        String relationship,

        @NotBlank(message = "El teléfono del contacto es obligatorio")
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "El teléfono no tiene un formato válido")
        String phone,

        @Email(message = "El correo del contacto no es válido")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        String email,

        Boolean primary
) {
}
