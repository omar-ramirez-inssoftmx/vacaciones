package com.inssoft.people.vacation.dto;

import jakarta.validation.constraints.Size;

public record VacationDecisionRequest(
        @Size(max = 500, message = "El comentario no puede exceder 500 caracteres")
        String comment
) {
}
