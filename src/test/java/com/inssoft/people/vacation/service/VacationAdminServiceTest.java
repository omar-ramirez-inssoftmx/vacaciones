package com.inssoft.people.vacation.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationAdminServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private VacationBalanceService balanceService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private VacationAdminService service;

    @Test
    void returnsEmployeeBalance() {
        Employee employee = employee();
        VacationBalanceResponse balance = balance();
        when(employeeRepository.findByUuidAndDeletedAtIsNull(employee.getUuid()))
                .thenReturn(Optional.of(employee));
        when(balanceService.balanceResponse(employee)).thenReturn(balance);

        assertThat(service.balance(employee.getUuid())).isSameAs(balance);
    }

    @Test
    void adjustsEmployeeBalanceAndAudits() {
        Employee employee = employee();
        VacationBalanceResponse balance = balance();
        when(employeeRepository.findByUuidAndDeletedAtIsNull(employee.getUuid()))
                .thenReturn(Optional.of(employee));
        when(balanceService.balanceResponse(employee)).thenReturn(balance);
        when(balanceService.adjust(eq(employee), any(), any(), any())).thenReturn(balance);

        assertThat(service.adjust(employee.getUuid(), BigDecimal.TEN, "Ajuste", null))
                .isSameAs(balance);
        verify(auditService).record(eq("VACATION_ADJUST"), eq("Employee"),
                eq(employee.getUuid().toString()), any(), any());
    }

    @Test
    void adjustsLegacyUserThroughEmployee() {
        Employee employee = employee();
        User actor = new User();
        actor.setUsername("admin");
        VacationBalanceResponse balance = balance();

        when(employeeRepository.findByUser_IdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(employee));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));
        when(balanceService.adjust(eq(employee), any(), any(), eq(actor))).thenReturn(balance);

        assertThat(service.adjustForUser(2L, BigDecimal.TEN, "Ajuste", "admin"))
                .isSameAs(balance);
    }

    @Test
    void ignoresAdjustmentWhenUserHasNoEmployee() {
        when(employeeRepository.findByUser_IdAndDeletedAtIsNull(2L)).thenReturn(Optional.empty());

        assertThat(service.adjustForUser(2L, BigDecimal.TEN, "Ajuste", "admin")).isNull();
    }

    @Test
    void employeeNotFound() {
        UUID uuid = UUID.randomUUID();
        when(employeeRepository.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.balance(uuid))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUuid(UUID.randomUUID());
        employee.setEmployeeNumber("EMP-001");
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");
        employee.setHireDate(LocalDate.now().minusYears(2));
        employee.setEnabled(true);
        return employee;
    }

    private VacationBalanceResponse balance() {
        return new VacationBalanceResponse(
                UUID.randomUUID(), "Omar Ramírez", UUID.randomUUID(),
                LocalDate.now(), LocalDate.now().plusYears(1), LocalDate.now(),
                "DEFAULT", "Política", BigDecimal.valueOf(12), BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
