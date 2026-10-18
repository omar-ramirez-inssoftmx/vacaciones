package com.inssoft.people.documents.dto;

import java.util.UUID;

public record DocumentTypeResponse(
        UUID uuid,
        String code,
        String name,
        boolean requiresExpiry,
        boolean employeeVisible,
        int maxSizeMb,
        String allowedMime
) {
}
