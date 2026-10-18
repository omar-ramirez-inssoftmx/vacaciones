package com.inssoft.people.payroll.dto;

import java.time.Instant;
import java.util.UUID;

public record PayReceiptResponse(
        UUID uuid,
        UUID employeeUuid,
        String employeeName,
        UUID periodUuid,
        String periodLabel,
        String fileName,
        String mimeType,
        long sizeBytes,
        String uploadedBy,
        Instant createdAt
) {
}
