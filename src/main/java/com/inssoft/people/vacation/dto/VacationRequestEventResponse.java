package com.inssoft.people.vacation.dto;

import java.time.Instant;
import java.util.UUID;

public record VacationRequestEventResponse(
        UUID uuid,
        String action,
        String fromStatus,
        String toStatus,
        String actorUsername,
        String comment,
        Instant createdAt
) {
}
