package com.inssoft.people.vacation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VacationPeriodResponse(
        UUID uuid,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate anniversaryDate,
        BigDecimal entitledDays,
        BigDecimal openingBalance,
        String status,
        String policyCode,
        String policyName
) {
}
