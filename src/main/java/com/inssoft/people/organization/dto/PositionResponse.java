package com.inssoft.people.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record PositionResponse(
        UUID uuid,
        UUID departmentUuid,
        String departmentName,
        UUID branchUuid,
        String code,
        String name,
        String description,
        String jobLevel,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
