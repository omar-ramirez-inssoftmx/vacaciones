package com.inssoft.people.vacation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VacationAdjustmentRequest(
        @NotNull(message = "El nuevo saldo disponible es obligatorio")
        @DecimalMin(value = "0.0", message = "El saldo disponible no puede ser negativo")
        @Digits(integer = 3, fraction = 2, message = "El saldo disponible tiene un formato inválido")
        BigDecimal available,

        @Size(max = 500, message = "El motivo no puede exceder 500 caracteres")
        String reason
) {
}
