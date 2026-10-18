package com.inssoft.people.vacation.dto;

import jakarta.validation.constraints.Size;

public record VacationCancelRequest(
        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
        String reason
) {
}
