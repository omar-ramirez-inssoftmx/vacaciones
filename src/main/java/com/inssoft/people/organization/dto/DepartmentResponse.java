package com.inssoft.people.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(
        UUID uuid,
        UUID branchUuid,
        String branchName,
        UUID companyUuid,
        UUID parentUuid,
        String parentName,
        String code,
        String name,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
