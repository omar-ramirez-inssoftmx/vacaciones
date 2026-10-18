package com.inssoft.people.vacation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record VacationRequestCreateRequest(
        UUID employeeUuid,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate startDate,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate endDate,

        UUID leaderUuid,

        @Size(max = 500, message = "El comentario no puede exceder 500 caracteres")
        String comment
) {
}
