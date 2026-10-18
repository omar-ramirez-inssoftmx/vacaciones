package com.inssoft.people.masterdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CatalogItemRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 50, message = "El código no puede exceder 50 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "El código solo permite letras, números, guion y guion bajo")
        String code,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String name,

        @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
        String description,

        @Min(value = 0, message = "El orden no puede ser negativo")
        @Max(value = 9999, message = "El orden es demasiado grande")
        Integer sortOrder,

        @Size(max = 4000, message = "Los metadatos no pueden exceder 4000 caracteres")
        String metadataJson,

        Boolean enabled
) {
}
