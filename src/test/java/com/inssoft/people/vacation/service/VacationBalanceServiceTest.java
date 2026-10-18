package com.inssoft.people.vacation.service;

import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.inssoft.people.vacation.entity.VacationLedger;
import com.inssoft.people.vacation.entity.VacationMovementType;
import com.inssoft.people.vacation.entity.VacationPeriod;
import com.inssoft.people.vacation.entity.VacationPeriodStatus;
import com.inssoft.people.vacation.entity.VacationPolicy;
import com.inssoft.people.vacation.repository.VacationLedgerRepository;
import com.inssoft.people.vacation.repository.VacationPeriodRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
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
class VacationBalanceServiceTest {

    @Mock
    private VacationPolicyService policyService;

    @Mock
    private VacationPeriodRepository periodRepository;

    @Mock
    private VacationLedgerRepository ledgerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private VacationBalanceService service;

    @Test
    void returnsExistingCurrentPeriod() {
        Employee employee = employee();
        VacationPeriod period = period(employee);
        when(periodRepository.findByEmployee_IdAndPeriodStart(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(period));

        assertThat(service.ensureCurrentPeriod(employee)).isSameAs(period);
    }

    @Test
    void createsPeriodAndAccrualForNewPeriod() {
        Employee employee = employee();
        VacationPolicy policy = policy();
        LocalDate periodStart = employee.getHireDate().plusYears(2);

        when(periodRepository.findByEmployee_IdAndPeriodStart(1L, periodStart))
                .thenReturn(Optional.empty());
        when(policyService.resolvePolicy(employee.getCompany())).thenReturn(policy);
        when(policyService.entitledDays(policy, 2)).thenReturn(BigDecimal.valueOf(12));
        when(periodRepository.save(any(VacationPeriod.class))).thenAnswer(invocation -> {
            VacationPeriod p = invocation.getArgument(0);
            p.setId(100L);
            p.setUuid(UUID.randomUUID());
            p.setCreatedAt(Instant.now());
            return p;
        });
        when(ledgerRepository.save(any(VacationLedger.class))).thenAnswer(invocation -> {
            VacationLedger l = invocation.getArgument(0);
            l.setId(1L);
            l.setUuid(UUID.randomUUID());
            return l;
        });

        VacationPeriod created = service.ensureCurrentPeriod(employee);

        assertThat(created.getEntitledDays()).isEqualByComparingTo("12");
        ArgumentCaptor<VacationLedger> captor = ArgumentCaptor.forClass(VacationLedger.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getMovementType()).isEqualTo(VacationMovementType.ACCRUAL);
        assertThat(captor.getValue().getSignedDays()).isEqualByComparingTo("12");
    }

    @Test
    void usesLegacyOpeningBalanceForFirstYear() {
        Employee employee = employee();
        employee.setHireDate(LocalDate.now());
        User user = new User();
        user.setId(5L);
        user.setVacationDaysAvailable(8);
        employee.setUser(user);
        VacationPolicy policy = policy();

        when(periodRepository.findByEmployee_IdAndPeriodStart(1L, employee.getHireDate()))
                .thenReturn(Optional.empty());
        when(policyService.resolvePolicy(employee.getCompany())).thenReturn(policy);
        when(policyService.entitledDays(policy, 0)).thenReturn(BigDecimal.ZERO);
        when(periodRepository.save(any(VacationPeriod.class))).thenAnswer(invocation -> {
            VacationPeriod p = invocation.getArgument(0);
            p.setId(100L);
            p.setUuid(UUID.randomUUID());
            return p;
        });
        when(ledgerRepository.save(any(VacationLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureCurrentPeriod(employee);

        ArgumentCaptor<VacationLedger> captor = ArgumentCaptor.forClass(VacationLedger.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getMovementType()).isEqualTo(VacationMovementType.OPENING_BALANCE);
        assertThat(captor.getValue().getSignedDays()).isEqualByComparingTo("8");
    }

    @Test
    void rejectsFutureHireDate() {
        Employee employee = employee();
        employee.setHireDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> service.ensureCurrentPeriod(employee))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha de ingreso");
    }

    @Test
    void calculatesBalanceFromLedger() {
        VacationPeriod period = period(employee());
        when(ledgerRepository.sumSignedByPeriod(100L)).thenReturn(BigDecimal.valueOf(10));
        when(ledgerRepository.sumDaysByPeriodAndType(100L, VacationMovementType.USAGE))
                .thenReturn(BigDecimal.valueOf(4));
        when(ledgerRepository.sumDaysByPeriodAndType(100L, VacationMovementType.USAGE_REVERSAL))
                .thenReturn(BigDecimal.valueOf(1));
        when(ledgerRepository.sumDaysByPeriodAndType(100L, VacationMovementType.HOLD))
                .thenReturn(BigDecimal.valueOf(2));
        when(ledgerRepository.sumDaysByPeriodAndType(100L, VacationMovementType.RELEASE))
                .thenReturn(BigDecimal.valueOf(1));

        VacationBalanceService.Balance balance = service.balanceForPeriod(period);

        assertThat(balance.available()).isEqualByComparingTo("10");
        assertThat(balance.used()).isEqualByComparingTo("3");
        assertThat(balance.held()).isEqualByComparingTo("1");
    }

    @Test
    void adjustsAvailableBalance() {
        Employee employee = employee();
        User user = new User();
        user.setId(5L);
        user.setVacationDaysAvailable(5);
        employee.setUser(user);
        VacationPeriod period = period(employee);
        when(periodRepository.findByEmployee_IdAndPeriodStart(1L, period.getPeriodStart()))
                .thenReturn(Optional.of(period));
        when(periodRepository.findByEmployeeAndStartForUpdate(1L, period.getPeriodStart()))
                .thenReturn(Optional.of(period));
        when(ledgerRepository.sumSignedByPeriod(100L))
                .thenReturn(BigDecimal.valueOf(5), BigDecimal.valueOf(8), BigDecimal.valueOf(8));
        when(ledgerRepository.sumDaysByPeriodAndType(any(), any())).thenReturn(BigDecimal.ZERO);
        when(ledgerRepository.save(any(VacationLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VacationBalanceResponse response = service.adjust(
                employee, BigDecimal.valueOf(8), "Ajuste de prueba", null);

        assertThat(response.available()).isEqualByComparingTo("8");
        ArgumentCaptor<VacationLedger> captor = ArgumentCaptor.forClass(VacationLedger.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getMovementType()).isEqualTo(VacationMovementType.ADJUSTMENT);
        assertThat(captor.getValue().getSignedDays()).isEqualByComparingTo("3");
    }

    @Test
    void syncsLegacyUserBalance() {
        Employee employee = employee();
        User user = new User();
        user.setId(5L);
        user.setVacationDaysAvailable(0);
        employee.setUser(user);
        VacationPeriod period = period(employee);

        when(periodRepository.findByEmployee_IdAndPeriodStart(1L, period.getPeriodStart()))
                .thenReturn(Optional.of(period));
        when(ledgerRepository.sumSignedByPeriod(100L)).thenReturn(BigDecimal.valueOf(7));
        when(ledgerRepository.sumDaysByPeriodAndType(any(), any())).thenReturn(BigDecimal.ZERO);

        service.syncLegacyBalance(employee);

        assertThat(user.getVacationDaysAvailable()).isEqualTo(7);
        verify(userRepository).save(user);
    }

    private Employee employee() {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("INSSOFT");
        company.setLegalName("INSSOFT");
        company.setEnabled(true);

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUuid(UUID.randomUUID());
        employee.setCompany(company);
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");
        employee.setHireDate(LocalDate.now().minusYears(2));
        employee.setEnabled(true);
        return employee;
    }

    private VacationPolicy policy() {
        VacationPolicy policy = new VacationPolicy();
        policy.setId(10L);
        policy.setUuid(UUID.randomUUID());
        policy.setCompany(employee().getCompany());
        policy.setCode("DEFAULT");
        policy.setName("Política");
        policy.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        policy.setDefaultPolicy(true);
        policy.setEnabled(true);
        policy.setAllowNegative(false);
        return policy;
    }

    private VacationPeriod period(Employee employee) {
        VacationPolicy policy = policy();
        LocalDate start = employee.getHireDate().plusYears(2);
        VacationPeriod period = new VacationPeriod();
        period.setId(100L);
        period.setUuid(UUID.randomUUID());
        period.setEmployee(employee);
        period.setPolicy(policy);
        period.setPeriodStart(start);
        period.setPeriodEnd(start.plusYears(1).minusDays(1));
        period.setAnniversaryDate(start);
        period.setEntitledDays(BigDecimal.valueOf(12));
        period.setOpeningBalance(BigDecimal.ZERO);
        period.setStatus(VacationPeriodStatus.OPEN);
        period.setEnabled(true);
        return period;
    }
}
