package com.inssoft.people.common.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID uuid,
        String actorUsername,
        String action,
        String entity,
        String entityId,
        String oldValue,
        String newValue,
        String ip,
        String userAgent,
        String correlationId,
        String result,
        Instant createdAt
) {
}
