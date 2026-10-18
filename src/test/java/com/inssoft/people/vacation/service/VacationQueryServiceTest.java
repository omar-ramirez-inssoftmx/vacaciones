package com.inssoft.people.vacation.service;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.security.AuthorizationService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.inssoft.people.vacation.dto.VacationRequestResponse;
import com.inssoft.people.vacation.entity.VacationPeriod;
import com.inssoft.people.vacation.entity.VacationPolicy;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.VacationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationQueryServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private VacationRepository vacationRepository;

    @Mock
    private VacationBalanceService balanceService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private VacationQueryService service;

    @Test
    void returnsMyBalance() {
        Employee employee = employee();
        VacationBalanceResponse balance = balance();
        when(employeeRepository.findByUser_UsernameAndDeletedAtIsNull("omar"))
                .thenReturn(Optional.of(employee));
        when(balanceService.balanceResponse(employee)).thenReturn(balance);

        assertThat(service.myBalance("omar")).isSameAs(balance);
    }

    @Test
    void listsMyRequests() {
        Employee employee = employee();
        VacationRequest request = request(employee);
        when(employeeRepository.findByUser_UsernameAndDeletedAtIsNull("omar"))
                .thenReturn(Optional.of(employee));
        when(vacationRepository.findByEmployee_IdAndDeletedAtIsNullOrderByStartDateDesc(
                eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request)));

        PageResponse<VacationRequestResponse> response = service.myRequests("omar", 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).employeeNumber()).isEqualTo("EMP-001");
    }

    @Test
    void getsOwnRequest() {
        Employee employee = employee();
        VacationRequest request = request(employee);
        when(vacationRepository.findByUuidAndDeletedAtIsNull(request.getUuid()))
                .thenReturn(Optional.of(request));

        VacationRequestResponse response = service.get(request.getUuid(), "omar", false);

        assertThat(response.uuid()).isEqualTo(request.getUuid());
    }

    @Test
    void forbidsOtherUsersRequest() {
        Employee employee = employee();
        VacationRequest request = request(employee);
        when(vacationRepository.findByUuidAndDeletedAtIsNull(request.getUuid()))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.get(request.getUuid(), "otro", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no te pertenece");
    }

    @Test
    void returnsNotFound() {
        UUID uuid = UUID.randomUUID();
        when(vacationRepository.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(uuid, "omar", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchesRequests() {
        VacationRequest request = request(employee());
        when(vacationRepository.searchRequests(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request)));

        PageResponse<VacationRequestResponse> response =
                service.search(null, "PENDING_ADMIN", null, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    void listsPendingForLeader() {
        VacationRequest request = request(employee());
        when(vacationRepository.findByLeader_UsernameAndStatusOrderByStartDateAsc(
                "leader", "PENDING_LEADER")).thenReturn(List.of(request));

        assertThat(service.pendingForLeader("leader")).hasSize(1);
    }

    private Employee employee() {
        User user = new User();
        user.setId(2L);
        user.setUsername("omar");
        user.setName("Omar");
        user.setRole(com.mx.vacaciones.model.Role.ROLE_COLABORADOR);
        user.setEnabled(true);

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUuid(UUID.randomUUID());
        employee.setEmployeeNumber("EMP-001");
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");
        employee.setHireDate(LocalDate.now().minusYears(2));
        employee.setUser(user);
        employee.setEnabled(true);
        return employee;
    }

    private VacationRequest request(Employee employee) {
        VacationPolicy policy = new VacationPolicy();
        policy.setId(10L);
        policy.setUuid(UUID.randomUUID());
        policy.setCode("DEFAULT");
        policy.setName("Política");
        policy.setEnabled(true);

        VacationPeriod period = new VacationPeriod();
        period.setId(100L);
        period.setUuid(UUID.randomUUID());
        period.setEmployee(employee);
        period.setPolicy(policy);
        period.setPeriodStart(LocalDate.now().minusYears(1));
        period.setPeriodEnd(LocalDate.now());
        period.setAnniversaryDate(LocalDate.now().minusYears(1));
        period.setEntitledDays(BigDecimal.valueOf(12));
        period.setOpeningBalance(BigDecimal.ZERO);

        VacationRequest request = new VacationRequest();
        request.setId(200L);
        request.setUuid(UUID.randomUUID());
        request.setEmployee(employee);
        request.setUser(employee.getUser());
        request.setUsername(employee.getUser().getUsername());
        request.setPeriod(period);
        request.setStartDate(LocalDate.now().plusDays(5));
        request.setEndDate(LocalDate.now().plusDays(9));
        request.setDays(5);
        request.setStatus("PENDING_ADMIN");
        request.setCreatedAt(LocalDateTime.now());
        return request;
    }

    private VacationBalanceResponse balance() {
        return new VacationBalanceResponse(
                UUID.randomUUID(), "Omar Ramírez", UUID.randomUUID(),
                LocalDate.now(), LocalDate.now().plusYears(1), LocalDate.now(),
                "DEFAULT", "Política", BigDecimal.valueOf(12), BigDecimal.ZERO,
                BigDecimal.valueOf(10), BigDecimal.valueOf(2), BigDecimal.ZERO);
    }
}
