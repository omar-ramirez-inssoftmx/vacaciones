package com.inssoft.people.payroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PayPeriodRequest(
        @NotNull(message = "El año es obligatorio")
        @Min(value = 2000, message = "El año no es válido")
        @Max(value = 2100, message = "El año no es válido")
        Integer year,

        @NotNull(message = "El mes es obligatorio")
        @Min(value = 1, message = "El mes debe estar entre 1 y 12")
        @Max(value = 12, message = "El mes debe estar entre 1 y 12")
        Integer month,

        @NotBlank(message = "El tipo de periodo es obligatorio")
        String periodType,

        @NotNull(message = "La fecha inicial es obligatoria")
        LocalDate startDate,

        @NotNull(message = "La fecha final es obligatoria")
        LocalDate endDate
) {
}
