package com.inssoft.people.vacation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VacationLedgerResponse(
        UUID uuid,
        String movementType,
        BigDecimal days,
        BigDecimal signedDays,
        String referenceType,
        Long referenceId,
        String notes,
        Instant createdAt
) {
}
