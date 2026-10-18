package com.inssoft.people.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record BranchResponse(
        UUID uuid,
        UUID companyUuid,
        String companyName,
        String code,
        String name,
        String address,
        String phone,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
