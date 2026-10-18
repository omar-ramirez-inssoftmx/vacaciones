package com.inssoft.people.calendar.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CalendarEventResponse(
        String type,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        boolean affectsWorkdays,
        UUID referenceUuid,
        String employeeName
) {
}
