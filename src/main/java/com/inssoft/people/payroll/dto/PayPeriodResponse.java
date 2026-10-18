package com.inssoft.people.payroll.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PayPeriodResponse(
        UUID uuid,
        int year,
        int month,
        String periodType,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String label
) {
}
