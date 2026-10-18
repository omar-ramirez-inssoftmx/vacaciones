package com.inssoft.people.vacation.service;

import com.mx.vacaciones.service.VacationCalculator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Adaptador temporal al cálculo legado de días hábiles (fines de semana +
 * festivos oficiales de México). En la Fase 6 (Calendar) se reemplaza por
 * el calendario laboral configurable por empresa.
 */
@Service
public class LegacyWorkingDayCalculator implements WorkingDayCalculator {

    private final VacationCalculator vacationCalculator;

    public LegacyWorkingDayCalculator(VacationCalculator vacationCalculator) {
        this.vacationCalculator = vacationCalculator;
    }

    @Override
    public int businessDays(LocalDate start, LocalDate end) {
        return vacationCalculator.businessDaysInclusive(start, end);
    }

    @Override
    public boolean isWorkingDay(LocalDate date) {
        return !vacationCalculator.isBlockedDate(date);
    }
}
