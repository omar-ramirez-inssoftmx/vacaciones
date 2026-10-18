package com.mx.vacaciones.controller;

import com.inssoft.people.employee.dto.EmployeeSummaryResponse;
import com.inssoft.people.employee.service.EmployeeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/leader/team")
@PreAuthorize("@accessGuard.has('EMPLOYEE_VIEW')")
public class TeamWebController {

    private final EmployeeService employeeService;

    public TeamWebController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public String team(Authentication authentication, Model model) {
        List<EmployeeSummaryResponse> team = employeeService.listTeam(authentication.getName());
        model.addAttribute("equipo", team);
        return "leader/team";
    }
}
