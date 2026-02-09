package com.mx.vacaciones.service;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.stereotype.Service;

@Service
public class VacationPolicyService {

  // Devuelve días anuales por años cumplidos (1..n)
  public int entitledDaysByYears(int years) {
    if (years < 1) return 0;
    if (years == 1) return 12;
    if (years == 2) return 14;
    if (years == 3) return 16;
    if (years == 4) return 18;
    if (years == 5) return 20;

    // 6-10: 22, 11-15: 24, 16-20: 26, etc.
    int extraBlocks = (years - 6) / 5;   // 0 para 6..10, 1 para 11..15...
    return 22 + (extraBlocks * 2);
  }

  public int yearsOfService(LocalDate hireDate, LocalDate today) {
    if (hireDate == null) return 0;
    if (hireDate.isAfter(today)) return 0;
    return Period.between(hireDate, today).getYears();
  }
}
