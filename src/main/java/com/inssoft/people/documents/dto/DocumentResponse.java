package com.inssoft.people.documents.dto;

import com.inssoft.people.documents.entity.DocumentVisibility;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentResponse(
        UUID uuid,
        UUID employeeUuid,
        String employeeName,
        String typeCode,
        String typeName,
        String name,
        String description,
        String fileName,
        String mimeType,
        long sizeBytes,
        String sha256,
        DocumentVisibility visibility,
        LocalDate expiresAt,
        String uploadedBy,
        Instant createdAt,
        boolean enabled
) {
}
