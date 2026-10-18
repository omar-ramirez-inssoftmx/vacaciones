package com.inssoft.people.calendar.dto;

import com.inssoft.people.calendar.entity.SpecialDayType;

import java.time.LocalDate;
import java.util.UUID;

public record SpecialDayResponse(
        UUID uuid,
        UUID companyUuid,
        String companyName,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        SpecialDayType type,
        boolean affectsWorkdays,
        String description,
        boolean enabled
) {
}
