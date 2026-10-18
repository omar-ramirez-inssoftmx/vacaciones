package com.inssoft.people.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AnnouncementRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede exceder 200 caracteres")
        String title,

        @NotBlank(message = "El contenido es obligatorio")
        String body,

        @Pattern(regexp = "^(NORMAL|IMPORTANT|URGENT)$", message = "La prioridad no es válida")
        String priority,

        LocalDateTime expiresAt
) {
}
