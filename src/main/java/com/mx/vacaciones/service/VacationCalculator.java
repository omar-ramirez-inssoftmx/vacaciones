package com.mx.vacaciones.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

@Service
public class VacationCalculator {

  public int businessDaysInclusive(LocalDate start, LocalDate end) {
    int count = 0;
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY ||
                        d.getDayOfWeek() == DayOfWeek.SUNDAY;
      boolean holiday = isMexHoliday(d);
      if (!weekend && !holiday) count++;
    }
    return count;
  }

  private boolean isMexHoliday(LocalDate d) {
    int y = d.getYear();

    // fijos
    if (d.equals(LocalDate.of(y, 1, 1))) return true;
    if (d.equals(LocalDate.of(y, 5, 1))) return true;
    if (d.equals(LocalDate.of(y, 9, 16))) return true;
    if (d.equals(LocalDate.of(y, 12, 25))) return true;

    // "lunes" oficiales:
    if (d.equals(nthMonday(y, 2, 1))) return true;  // 1er lunes feb (Constitución)
    if (d.equals(nthMonday(y, 3, 3))) return true;  // 3er lunes mar (Benito Juárez)
    if (d.equals(nthMonday(y, 11, 3))) return true; // 3er lunes nov (Revolución)

    return false;
  }

  private LocalDate nthMonday(int year, int month, int nth) {
    LocalDate first = LocalDate.of(year, month, 1);
    int shift = (DayOfWeek.MONDAY.getValue() - first.getDayOfWeek().getValue() + 7) % 7;
    LocalDate firstMonday = first.plusDays(shift);
    return firstMonday.plusWeeks(nth - 1);
  }
}
