package com.inssoft.people.vacation.service;

import java.time.LocalDate;

public interface WorkingDayCalculator {

    int businessDays(Long companyId, LocalDate start, LocalDate end);

    boolean isWorkingDay(Long companyId, LocalDate date);
}
