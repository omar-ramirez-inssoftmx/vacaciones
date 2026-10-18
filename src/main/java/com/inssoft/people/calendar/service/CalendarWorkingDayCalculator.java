package com.inssoft.people.calendar.service;

import com.inssoft.people.vacation.service.WorkingDayCalculator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Implementación de días hábiles respaldada por el calendario laboral de la
 * empresa (V7). Reemplaza el cálculo hardcodeado del sistema legado.
 */
@Service
public class CalendarWorkingDayCalculator implements WorkingDayCalculator {

    private final CalendarService calendarService;

    public CalendarWorkingDayCalculator(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Override
    public int businessDays(Long companyId, LocalDate start, LocalDate end) {
        return calendarService.businessDays(companyId, start, end);
    }

    @Override
    public boolean isWorkingDay(Long companyId, LocalDate date) {
        return calendarService.isWorkingDay(companyId, date);
    }
}
