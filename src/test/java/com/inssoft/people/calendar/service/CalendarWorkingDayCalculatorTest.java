package com.inssoft.people.calendar.service;

import com.inssoft.people.vacation.service.WorkingDayCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarWorkingDayCalculatorTest {

    @Mock
    private CalendarService calendarService;

    @InjectMocks
    private CalendarWorkingDayCalculator calculator;

    @Test
    void delegatesToCalendarService() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        when(calendarService.isWorkingDay(1L, date)).thenReturn(true);
        when(calendarService.businessDays(1L, date, date.plusDays(4))).thenReturn(5);

        assertThat(calculator.isWorkingDay(1L, date)).isTrue();
        assertThat(calculator.businessDays(1L, date, date.plusDays(4))).isEqualTo(5);
    }
}
