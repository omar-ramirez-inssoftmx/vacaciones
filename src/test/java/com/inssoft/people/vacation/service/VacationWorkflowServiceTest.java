package com.inssoft.people.vacation.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.vacation.entity.VacationMovementType;
import com.inssoft.people.vacation.entity.VacationPeriod;
import com.inssoft.people.vacation.entity.VacationPeriodStatus;
import com.inssoft.people.vacation.entity.VacationPolicy;
import com.inssoft.people.vacation.entity.VacationStatus;
import com.inssoft.people.vacation.repository.VacationRequestEventRepository;
import com.inssoft.people.vacation.validation.VacationValidator;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.VacationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationWorkflowServiceTest {

    @Mock
    private VacationRepository vacationRepository;

    @Mock
    private VacationBalanceService balanceService;

    @Mock
    private VacationRequestEventRepository eventRepository;

    @Mock
    private VacationValidator validator;

    @Mock
    private AuditService auditService;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private VacationWorkflowService service;

    @Test
    void submitsRequestAndHoldsBalance() {
        Employee employee = employee();
        User employeeUser = employee.getUser();
        User actor = user(2L, "actor", Role.ROLE_COLABORADOR);
        VacationPeriod period = period(employee);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(validator.businessDays(any(), any())).thenReturn(5);
        when(vacationRepository.existsOverlappingByEmployee(eq(1L), any(), any(), any()))
                .thenReturn(false);
        when(balanceService.lockCurrentPeriod(employee)).thenReturn(period);
        when(balanceService.balanceForPeriod(period)).thenReturn(balance(period, "12"));
        when(vacationRepository.save(any(VacationRequest.class))).thenAnswer(invocation -> {
            VacationRequest request = invocation.getArgument(0);
            request.setId(200L);
            request.setUuid(UUID.randomUUID());
            request.setCreatedAt(LocalDateTime.now());
            return request;
        });

        VacationRequest saved = service.submit(
                employee,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(7),
                null,
                actor,
                null);

        assertThat(saved.getStatus()).isEqualTo(VacationStatus.PENDING_ADMIN.name());
        assertThat(saved.getDays()).isEqualTo(5);
        assertThat(saved.isLegacyBalance()).isFalse();
        assertThat(saved.getUser()).isSameAs(employeeUser);
        verify(balanceService).addMovement(
                eq(employee), eq(period), eq(VacationMovementType.HOLD),
                eq(BigDecimal.valueOf(5)), eq(BigDecimal.valueOf(-5)), any(), eq(200L), any(), eq(actor));
        verify(balanceService).syncLegacyBalance(employee);
        verify(eventRepository).save(any());
    }

    @Test
    void rejectsRequestWhenBalanceIsInsufficient() {
        Employee employee = employee();
        VacationPeriod period = period(employee);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(validator.businessDays(any(), any())).thenReturn(20);
        when(vacationRepository.existsOverlappingByEmployee(eq(1L), any(), any(), any()))
                .thenReturn(false);
        when(balanceService.lockCurrentPeriod(employee)).thenReturn(period);
        when(balanceService.balanceForPeriod(period)).thenReturn(balance(period, "5"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.submit(
                        employee, LocalDate.now().plusDays(1), LocalDate.now().plusDays(25), null,
                        user(2L, "actor", Role.ROLE_COLABORADOR), null))
                .isInstanceOf(com.inssoft.people.common.exception.BusinessException.class)
                .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    void leaderApprovesAndMovesToAdmin() {
        VacationRequest request = request(VacationStatus.PENDING_LEADER, user(3L, "leader", Role.ROLE_LIDER));
        when(vacationRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(request));
        when(vacationRepository.save(any(VacationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationRequest saved = service.approveByLeader(200L, request.getLeader());

        assertThat(saved.getStatus()).isEqualTo(VacationStatus.PENDING_ADMIN.name());
        assertThat(saved.getLeaderDecision()).isEqualTo("APPROVED");
        verify(eventRepository).save(any());
    }

    @Test
    void leaderRejectionReleasesHold() {
        User leader = user(3L, "leader", Role.ROLE_LIDER);
        VacationRequest request = request(VacationStatus.PENDING_LEADER, leader);
        when(vacationRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(request));
        when(vacationRepository.save(any(VacationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceService.hasMovement(200L, VacationMovementType.HOLD)).thenReturn(true);
        when(balanceService.hasMovement(200L, VacationMovementType.RELEASE)).thenReturn(false);

        VacationRequest saved = service.rejectByLeader(200L, leader, "No hay cobertura");

        assertThat(saved.getStatus()).isEqualTo(VacationStatus.REJECTED.name());
        verify(balanceService).addMovement(
                any(), any(), eq(VacationMovementType.RELEASE),
                eq(BigDecimal.valueOf(5)), eq(BigDecimal.valueOf(5)), any(), eq(200L), any(), eq(leader));
    }

    @Test
    void adminApprovalConsumesHold() {
        User admin = user(4L, "admin", Role.ROLE_ADMIN);
        VacationRequest request = request(VacationStatus.PENDING_ADMIN, null);
        when(vacationRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(request));
        when(vacationRepository.save(any(VacationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceService.hasMovement(200L, VacationMovementType.HOLD)).thenReturn(true);
        when(balanceService.hasMovement(200L, VacationMovementType.RELEASE)).thenReturn(false);
        when(balanceService.hasMovement(200L, VacationMovementType.USAGE)).thenReturn(false);

        VacationRequest saved = service.approveByAdmin(200L, admin);

        assertThat(saved.getStatus()).isEqualTo(VacationStatus.APPROVED.name());
        verify(balanceService, atLeast(2)).addMovement(
                any(), any(), any(), any(), any(), any(), eq(200L), any(), any());
    }

    @Test
    void pendingCancellationReleasesHold() {
        User owner = user(2L, "owner", Role.ROLE_COLABORADOR);
        VacationRequest request = request(VacationStatus.PENDING_ADMIN, null);
        request.setUser(owner);
        when(vacationRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(request));
        when(vacationRepository.save(any(VacationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceService.hasMovement(200L, VacationMovementType.HOLD)).thenReturn(true);
        when(balanceService.hasMovement(200L, VacationMovementType.RELEASE)).thenReturn(false);

        VacationRequest saved = service.cancel(200L, owner, "Cambio de planes", false);

        assertThat(saved.getStatus()).isEqualTo(VacationStatus.CANCELLED.name());
        assertThat(saved.getCancelReason()).isEqualTo("Cambio de planes");
    }

    @Test
    void approvedCancellationReversesUsage() {
        User owner = user(2L, "owner", Role.ROLE_COLABORADOR);
        VacationRequest request = request(VacationStatus.APPROVED, null);
        request.setUser(owner);
        request.setStartDate(LocalDate.now().plusDays(5));
        when(vacationRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(request));
        when(vacationRepository.save(any(VacationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceService.hasMovement(200L, VacationMovementType.USAGE)).thenReturn(true);
        when(balanceService.hasMovement(200L, VacationMovementType.USAGE_REVERSAL)).thenReturn(false);

        VacationRequest saved = service.cancel(200L, owner, "Cancelación", false);

        assertThat(saved.getStatus()).isEqualTo(VacationStatus.CANCELLED.name());
        verify(balanceService).addMovement(
                any(), any(), eq(VacationMovementType.USAGE_REVERSAL),
                eq(BigDecimal.valueOf(5)), eq(BigDecimal.valueOf(5)), any(), eq(200L), any(), eq(owner));
    }

    @Test
    void adminCannotApproveOwnRequest() {
        User admin = user(4L, "admin", Role.ROLE_ADMIN);
        VacationRequest request = request(VacationStatus.PENDING_ADMIN, null);
        request.setUser(admin);
        when(vacationRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(request));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.approveByAdmin(200L, admin))
                .isInstanceOf(com.inssoft.people.common.exception.BusinessException.class)
                .hasMessageContaining("propia");
    }

    @Test
    void legacyRejectionRestoresBalanceWithAdjustment() {
        User leader = user(3L, "leader", Role.ROLE_LIDER);
        VacationRequest request = request(VacationStatus.PENDING_LEADER, leader);
        request.setLegacyBalance(true);
        request.setPeriod(null);
        VacationPeriod currentPeriod = period(request.getEmployee());

        when(vacationRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(request));
        when(vacationRepository.save(any(VacationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceService.ensureCurrentPeriod(request.getEmployee())).thenReturn(currentPeriod);
        when(balanceService.hasMovement(200L, VacationMovementType.ADJUSTMENT)).thenReturn(false);

        VacationRequest saved = service.rejectByLeader(200L, leader, "Sin cobertura");

        assertThat(saved.getStatus()).isEqualTo(VacationStatus.REJECTED.name());
        verify(balanceService).addMovement(
                eq(request.getEmployee()), eq(currentPeriod), eq(VacationMovementType.ADJUSTMENT),
                eq(BigDecimal.valueOf(5)), eq(BigDecimal.valueOf(5)), any(), eq(200L), any(), eq(leader));
    }

    private Employee employee() {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("INSSOFT");
        company.setLegalName("INSSOFT");
        company.setEnabled(true);

        User user = user(2L, "employee", Role.ROLE_COLABORADOR);
        user.setEmail("employee@example.com");

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUuid(UUID.randomUUID());
        employee.setEmployeeNumber("EMP-001");
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");
        employee.setCompany(company);
        employee.setHireDate(LocalDate.now().minusYears(2));
        employee.setUser(user);
        employee.setEnabled(true);
        return employee;
    }

    private User user(long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUuid(UUID.randomUUID());
        user.setUsername(username);
        user.setName(username);
        user.setPassword("hash");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private VacationPolicy policy() {
        VacationPolicy policy = new VacationPolicy();
        policy.setId(10L);
        policy.setUuid(UUID.randomUUID());
        policy.setCode("DEFAULT");
        policy.setName("Política");
        policy.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        policy.setDefaultPolicy(true);
        policy.setEnabled(true);
        policy.setAllowNegative(false);
        return policy;
    }

    private VacationPeriod period(Employee employee) {
        LocalDate start = employee.getHireDate().plusYears(2);
        VacationPeriod period = new VacationPeriod();
        period.setId(100L);
        period.setUuid(UUID.randomUUID());
        period.setEmployee(employee);
        period.setPolicy(policy());
        period.setPeriodStart(start);
        period.setPeriodEnd(start.plusYears(1).minusDays(1));
        period.setAnniversaryDate(start);
        period.setEntitledDays(BigDecimal.valueOf(12));
        period.setOpeningBalance(BigDecimal.ZERO);
        period.setStatus(VacationPeriodStatus.OPEN);
        period.setEnabled(true);
        return period;
    }

    private VacationBalanceService.Balance balance(VacationPeriod period, String available) {
        return new VacationBalanceService.Balance(
                period,
                new BigDecimal(available),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                period.getEntitledDays(),
                BigDecimal.ZERO);
    }

    private VacationRequest request(VacationStatus status, User leader) {
        Employee employee = employee();
        VacationRequest request = new VacationRequest();
        request.setId(200L);
        request.setUuid(UUID.randomUUID());
        request.setEmployee(employee);
        request.setUser(employee.getUser());
        request.setUsername(employee.getUser().getUsername());
        request.setPeriod(period(employee));
        request.setStartDate(LocalDate.now().plusDays(5));
        request.setEndDate(LocalDate.now().plusDays(9));
        request.setDays(5);
        request.setStatus(status.name());
        request.setLeader(leader);
        request.setCreatedAt(LocalDateTime.now());
        request.setLegacyBalance(false);
        return request;
    }
}
