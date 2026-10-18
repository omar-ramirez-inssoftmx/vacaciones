package com.inssoft.people.calendar.dto;

import com.inssoft.people.calendar.entity.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayRequest(
        UUID companyUuid,

        @NotBlank(message = "El nombre del festivo es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String name,

        @NotNull(message = "La fecha es obligatoria")
        LocalDate date,

        @NotNull(message = "El tipo de festivo es obligatorio")
        HolidayType type,

        @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
        String description,

        Boolean enabled
) {
}
