package com.inssoft.people.communication.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnnouncementResponse(
        UUID uuid,
        String title,
        String body,
        String priority,
        LocalDateTime publishedAt,
        LocalDateTime expiresAt,
        String createdBy,
        boolean active
) {
}
