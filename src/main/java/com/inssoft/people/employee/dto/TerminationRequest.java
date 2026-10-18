package com.inssoft.people.employee.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TerminationRequest(
        LocalDate terminationDate,

        @Size(max = 500, message = "El motivo de baja no puede exceder 500 caracteres")
        String reason,

        String statusCode
) {
}
