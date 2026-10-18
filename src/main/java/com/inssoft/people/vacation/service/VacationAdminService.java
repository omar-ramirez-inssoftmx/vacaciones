package com.inssoft.people.vacation.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.inssoft.people.vacation.mapper.VacationMapper;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VacationAdminService {

    private final EmployeeRepository employeeRepository;
    private final VacationBalanceService balanceService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public VacationAdminService(
            EmployeeRepository employeeRepository,
            VacationBalanceService balanceService,
            UserRepository userRepository,
            AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.balanceService = balanceService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public VacationBalanceResponse balance(UUID employeeUuid) {
        return balanceService.balanceResponse(findEmployee(employeeUuid));
    }

    @Transactional
    public VacationBalanceResponse adjust(
            UUID employeeUuid,
            BigDecimal available,
            String reason,
            User actor) {

        Employee employee = findEmployee(employeeUuid);
        VacationBalanceResponse before = balanceService.balanceResponse(employee);
        VacationBalanceResponse response = balanceService.adjust(employee, available, reason, actor);

        Map<String, Object> oldValue = new LinkedHashMap<>();
        oldValue.put("available", before.available());
        oldValue.put("used", before.used());
        oldValue.put("held", before.held());
        oldValue.put("employeeUuid", employeeUuid);
        oldValue.put("reason", reason);
        oldValue.put("actor", actor == null ? null : actor.getUsername());

        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("available", response.available());
        newValue.put("used", response.used());
        newValue.put("held", response.held());
        newValue.put("employeeUuid", employeeUuid);
        newValue.put("reason", reason);
        newValue.put("actor", actor == null ? null : actor.getUsername());

        auditService.record("VACATION_ADJUST", "Employee", employeeUuid.toString(), oldValue, newValue);
        return response;
    }

    @Transactional
    public VacationBalanceResponse adjustForUser(
            Long userId,
            BigDecimal available,
            String reason,
            String actorUsername) {

        Employee employee = employeeRepository.findByUser_IdAndDeletedAtIsNull(userId).orElse(null);
        if (employee == null) {
            return null;
        }

        User actor = actorUsername == null
                ? null
                : userRepository.findByUsername(actorUsername).orElse(null);

        VacationBalanceResponse response = balanceService.adjust(employee, available, reason, actor);
        auditService.record("VACATION_ADJUST", "Employee", employee.getUuid().toString(), response);
        return response;
    }

    private Employee findEmployee(UUID employeeUuid) {
        return employeeRepository.findByUuidAndDeletedAtIsNull(employeeUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", employeeUuid));
    }
}
