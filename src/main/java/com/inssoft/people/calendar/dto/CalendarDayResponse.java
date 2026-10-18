package com.inssoft.people.calendar.dto;

import java.time.LocalDate;

public record CalendarDayResponse(
        LocalDate date,
        boolean workingDay,
        String holidayName,
        String holidayType,
        String specialDayName,
        String specialDayType
) {
}
