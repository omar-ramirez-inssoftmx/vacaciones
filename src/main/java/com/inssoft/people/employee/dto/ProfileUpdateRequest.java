package com.inssoft.people.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Email(message = "El correo personal no es válido")
        @Size(max = 150, message = "El correo personal no puede exceder 150 caracteres")
        String personalEmail,

        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,20}$", message = "El teléfono no tiene un formato válido")
        String phone,

        @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
        String addressLine1,

        @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
        String addressLine2,

        @Size(max = 100, message = "La ciudad no puede exceder 100 caracteres")
        String city,

        @Size(max = 100, message = "El estado no puede exceder 100 caracteres")
        String state,

        @Pattern(regexp = "^$|^[0-9]{4,10}$", message = "El código postal no tiene un formato válido")
        String postalCode,

        @Size(max = 100, message = "El país no puede exceder 100 caracteres")
        String country
) {
}
