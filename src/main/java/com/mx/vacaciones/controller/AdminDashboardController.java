package com.mx.vacaciones.controller;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.mx.vacaciones.repository.VacationRepository;


@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

  private final VacationRepository vacationRepository;

  public AdminDashboardController(VacationRepository vacationRepository) {
    this.vacationRepository = vacationRepository;
  }

  @GetMapping("/dashboard")
  public String dashboard(Model model) {

    LocalDate today = LocalDate.now();
    LocalDate startOfMonth = today.withDayOfMonth(1);
    LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

    var outToday = vacationRepository.findApprovedCoveringDay(today);
    long pendingCount = vacationRepository.countPending();
    long approvedDaysThisMonth = vacationRepository.sumApprovedDaysOverlappingMonth(startOfMonth, endOfMonth);

    var top = vacationRepository.topUsersApprovedDaysOverlappingMonth(startOfMonth, endOfMonth)
        .stream()
        .limit(5)
        .map(r -> new TopUserRow((String) r[0], ((Number) r[1]).longValue()))
        .toList();

    model.addAttribute("today", today);
    model.addAttribute("outTodayCount", outToday.size());
    model.addAttribute("outToday", outToday);

    model.addAttribute("pendingCount", pendingCount);
    model.addAttribute("approvedDaysThisMonth", approvedDaysThisMonth);

    model.addAttribute("topUsers", top);

    return "admin/dashboard";
  }

  public record TopUserRow(String username, long days) {}
}
