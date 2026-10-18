package com.inssoft.people.vacation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VacationBalanceResponse(
        UUID employeeUuid,
        String employeeName,
        UUID periodUuid,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate anniversaryDate,
        String policyCode,
        String policyName,
        BigDecimal entitledDays,
        BigDecimal openingBalance,
        BigDecimal available,
        BigDecimal used,
        BigDecimal held
) {
}
