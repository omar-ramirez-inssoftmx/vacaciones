package com.inssoft.people.vacation.job;

import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.service.VacationBalanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationAccrualJobTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private VacationBalanceService balanceService;

    @InjectMocks
    private VacationAccrualJob job;

    @Test
    void accruesActiveEmployeesAndSkipsFutureHires() {
        Employee active = employee(LocalDate.now().minusYears(2));
        Employee future = employee(LocalDate.now().plusDays(10));
        future.setId(2L);
        when(employeeRepository.findByDeletedAtIsNullAndEnabledTrue())
                .thenReturn(List.of(active, future));

        job.accrueCurrentPeriods();

        verify(balanceService).ensureCurrentPeriodById(active.getId());
        verify(balanceService, never()).ensureCurrentPeriodById(future.getId());
    }

    @Test
    void continuesWhenOneEmployeeFails() {
        Employee failing = employee(LocalDate.now().minusYears(1));
        Employee active = employee(LocalDate.now().minusYears(2));
        active.setId(2L);
        when(employeeRepository.findByDeletedAtIsNullAndEnabledTrue())
                .thenReturn(List.of(failing, active));
        doThrow(new RuntimeException("boom")).when(balanceService).ensureCurrentPeriodById(failing.getId());

        job.accrueCurrentPeriods();

        verify(balanceService).ensureCurrentPeriodById(active.getId());
    }

    private Employee employee(LocalDate hireDate) {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUuid(UUID.randomUUID());
        employee.setHireDate(hireDate);
        employee.setEnabled(true);
        return employee;
    }
}
