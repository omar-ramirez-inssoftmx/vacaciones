package com.inssoft.people.common.security;

import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.entity.VacationStatus;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorizationServiceTest {

    @Mock
    private AccessGuard accessGuard;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthorizationService service;

    @Test
    void ownerCanViewOwnEmployeeButNotAnother() {
        Employee owner = employee(1L, "owner");
        Employee other = employee(2L, "other");
        User ownerUser = owner.getUser();

        assertThat(service.canViewEmployee(ownerUser, owner)).isTrue();
        assertThat(service.canViewEmployee(ownerUser, other)).isFalse();
    }

    @Test
    void leaderCanViewDirectTeamOnly() {
        Employee leaderEmployee = employee(10L, "leader");
        Employee teamMember = employee(20L, "member");
        Employee outsider = employee(30L, "outsider");
        teamMember.setManager(leaderEmployee);
        outsider.setManager(employee(40L, "other-leader"));

        when(accessGuard.has("EMPLOYEE_VIEW_TEAM")).thenReturn(true);
        when(employeeRepository.findByUser_IdAndDeletedAtIsNull(leaderEmployee.getUser().getId()))
                .thenReturn(Optional.of(leaderEmployee));

        assertThat(service.canViewEmployee(leaderEmployee.getUser(), teamMember)).isTrue();
        assertThat(service.canViewEmployee(leaderEmployee.getUser(), outsider)).isFalse();
    }

    @Test
    void leaderDoesNotSeeSensitiveDataByDefault() {
        Employee leader = employee(10L, "leader");
        Employee member = employee(20L, "member");
        member.setManager(leader);
        when(employeeRepository.findByUser_IdAndDeletedAtIsNull(leader.getUser().getId()))
                .thenReturn(Optional.of(leader));
        when(accessGuard.has("EMPLOYEE_VIEW_TEAM")).thenReturn(true);

        assertThat(service.canViewEmployee(leader.getUser(), member)).isTrue();
        assertThat(service.canViewSensitiveEmployee(leader.getUser(), member)).isFalse();
    }

    @Test
    void adminCanViewAllAndSensitive() {
        User admin = user(1L, "admin");
        Employee employee = employee(20L, "member");
        when(accessGuard.has("EMPLOYEE_VIEW_ALL")).thenReturn(true);

        assertThat(service.canViewEmployee(admin, employee)).isTrue();
        assertThat(service.canViewSensitiveEmployee(admin, employee)).isTrue();
    }

    @Test
    void assignedLeaderCanApproveOnlyTeamRequest() {
        User leader = user(10L, "leader");
        VacationRequest request = vacation(VacationStatus.PENDING_LEADER, leader);
        when(accessGuard.has("VACATION_APPROVE_TEAM")).thenReturn(true);

        assertThat(service.isRequester(leader, request)).isFalse();
        assertThat(service.isAssignedLeader(leader, request)).isTrue();
        assertThat(accessGuard.has("VACATION_APPROVE_TEAM")).isTrue();
        assertThat(service.canApproveVacation(leader, request)).isTrue();
    }

    @Test
    void requesterCannotApproveOwnRequest() {
        User admin = user(1L, "admin");
        VacationRequest request = vacation(VacationStatus.PENDING_ADMIN, null);
        request.setRequestedBy(admin);
        when(accessGuard.has("VACATION_MANAGE_ALL")).thenReturn(true);

        assertThat(service.canApproveVacation(admin, request)).isFalse();
    }

    @Test
    void payrollOwnerCanViewButOtherCannot() {
        Employee owner = employee(1L, "owner");
        Employee other = employee(2L, "other");
        User ownerUser = owner.getUser();

        assertThat(service.canViewPayroll(ownerUser, owner)).isTrue();
        assertThat(service.canViewPayroll(ownerUser, other)).isFalse();
    }

    @Test
    void teamPayrollRequiresExplicitTeamPermission() {
        Employee leader = employee(10L, "leader");
        Employee member = employee(20L, "member");
        member.setManager(leader);
        when(employeeRepository.findByUser_IdAndDeletedAtIsNull(leader.getUser().getId()))
                .thenReturn(Optional.of(leader));

        assertThat(service.canViewPayroll(leader.getUser(), member)).isFalse();
        when(accessGuard.has("PAYROLL_VIEW_TEAM")).thenReturn(true);
        assertThat(service.canViewPayroll(leader.getUser(), member)).isTrue();
    }

    private Employee employee(Long id, String username) {
        User user = user(id, username);
        Employee employee = new Employee();
        employee.setId(id);
        employee.setUuid(UUID.randomUUID());
        employee.setUser(user);
        employee.setFirstName(username);
        employee.setLastName("Test");
        return employee;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("hash");
        return user;
    }

    private VacationRequest vacation(VacationStatus status, User leader) {
        Employee employee = employee(20L, "member");
        VacationRequest request = new VacationRequest();
        request.setId(1L);
        request.setUuid(UUID.randomUUID());
        request.setEmployee(employee);
        request.setUser(employee.getUser());
        request.setLeader(leader);
        request.setStatus(status.name());
        return request;
    }
}
