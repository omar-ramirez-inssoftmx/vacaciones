package com.inssoft.people.vacation.service;

import java.time.LocalDate;

public interface WorkingDayCalculator {

    int businessDays(LocalDate start, LocalDate end);

    boolean isWorkingDay(LocalDate date);
}
