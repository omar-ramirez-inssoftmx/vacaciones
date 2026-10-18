package com.inssoft.people.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID uuid,
        String code,
        String legalName,
        String tradeName,
        String rfc,
        String timezone,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
