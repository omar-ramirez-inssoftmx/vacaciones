package com.inssoft.people.calendar.dto;

import com.inssoft.people.calendar.entity.HolidayType;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(
        UUID uuid,
        UUID companyUuid,
        String companyName,
        String name,
        LocalDate date,
        HolidayType type,
        int year,
        String description,
        boolean enabled
) {
}
