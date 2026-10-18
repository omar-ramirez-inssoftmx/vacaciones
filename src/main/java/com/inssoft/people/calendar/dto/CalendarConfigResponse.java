package com.inssoft.people.calendar.dto;

import java.util.UUID;

public record CalendarConfigResponse(
        UUID uuid,
        UUID companyUuid,
        String companyName,
        String name,
        String timezone,
        String workweek,
        boolean enabled
) {
}
