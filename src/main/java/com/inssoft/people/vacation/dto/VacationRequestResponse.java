package com.inssoft.people.vacation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record VacationRequestResponse(
        UUID uuid,
        UUID employeeUuid,
        String employeeName,
        String employeeNumber,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        String status,
        UUID requestedByUuid,
        String requestedByUsername,
        UUID leaderUuid,
        String leaderName,
        String leaderDecision,
        LocalDateTime leaderDecidedAt,
        String leaderComment,
        String decidedBy,
        LocalDateTime decidedAt,
        String adminComment,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        UUID periodUuid,
        boolean legacyBalance
) {
}
