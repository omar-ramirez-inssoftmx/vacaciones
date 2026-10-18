package com.inssoft.people.calendar.dto;

import com.inssoft.people.calendar.entity.SpecialDayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record SpecialDayRequest(
        UUID companyUuid,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String name,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate startDate,

        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate endDate,

        @NotNull(message = "El tipo de día especial es obligatorio")
        SpecialDayType type,

        Boolean affectsWorkdays,

        @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
        String description,

        Boolean enabled
) {
}
