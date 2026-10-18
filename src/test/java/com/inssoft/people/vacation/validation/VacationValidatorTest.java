package com.inssoft.people.vacation.validation;

import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.vacation.entity.VacationStatus;
import com.inssoft.people.vacation.service.WorkingDayCalculator;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationValidatorTest {

    @Mock
    private WorkingDayCalculator workingDayCalculator;

    private VacationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new VacationValidator(workingDayCalculator);
    }

    @Test
    void acceptsFutureWorkingRange() {
        when(workingDayCalculator.isWorkingDay(any())).thenReturn(true);
        when(workingDayCalculator.businessDays(any(), any())).thenReturn(5);

        LocalDate start = LocalDate.now().plusDays(1);
        assertThatCode(() -> {
            validator.validateRequestDates(start, start.plusDays(6));
            validator.businessDays(start, start.plusDays(6));
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> validator.validateRequestDates(
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("menor");
    }

    @Test
    void rejectsPastStart() {
        assertThatThrownBy(() -> validator.validateRequestDates(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pasada");
    }

    @Test
    void rejectsNonWorkingStart() {
        when(workingDayCalculator.isWorkingDay(any())).thenReturn(false);

        assertThatThrownBy(() -> validator.validateRequestDates(
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("día hábil");
    }

    @Test
    void rejectsRangeWithoutWorkingDays() {
        when(workingDayCalculator.businessDays(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> validator.businessDays(
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("días hábiles");
    }

    @Test
    void validatesLeader() {
        Employee employee = new Employee();
        User owner = user(1L, Role.ROLE_COLABORADOR);
        employee.setUser(owner);

        User leader = user(2L, Role.ROLE_LIDER);
        assertThatCode(() -> validator.validateLeader(employee, leader)).doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateLeader(employee, owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mismo usuario");

        assertThatThrownBy(() -> validator.validateLeader(employee, user(3L, Role.ROLE_COLABORADOR)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rol de líder");
    }

    @Test
    void validatesLeaderDecision() {
        User leader = user(2L, Role.ROLE_LIDER);
        VacationRequest request = request(VacationStatus.PENDING_LEADER, leader);

        assertThatCode(() -> validator.validateLeaderDecision(request, leader))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateLeaderDecision(request, user(3L, Role.ROLE_LIDER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("asignada");

        request.setStatus(VacationStatus.PENDING_ADMIN.name());
        assertThatThrownBy(() -> validator.validateLeaderDecision(request, leader))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pendiente del líder");
    }

    @Test
    void validatesAdminDecision() {
        VacationRequest request = request(VacationStatus.PENDING_ADMIN, null);
        assertThatCode(() -> validator.validateAdminDecision(request)).doesNotThrowAnyException();

        request.setStatus(VacationStatus.PENDING_LEADER.name());
        request.setLeader(null);
        assertThatCode(() -> validator.validateAdminDecision(request)).doesNotThrowAnyException();

        request.setStatus(VacationStatus.APPROVED.name());
        assertThatThrownBy(() -> validator.validateAdminDecision(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validatesCancellation() {
        VacationRequest request = request(VacationStatus.PENDING_ADMIN, null);
        request.setStartDate(LocalDate.now().plusDays(10));
        assertThatCode(() -> validator.validateCancelable(request)).doesNotThrowAnyException();

        request.setStatus(VacationStatus.APPROVED.name());
        request.setStartDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> validator.validateCancelable(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("iniciada");

        request.setStatus(VacationStatus.CANCELLED.name());
        assertThatThrownBy(() -> validator.validateCancelable(request))
                .isInstanceOf(BusinessException.class);
    }

    private User user(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setName("User " + id);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private VacationRequest request(VacationStatus status, User leader) {
        VacationRequest request = new VacationRequest();
        request.setStatus(status.name());
        request.setLeader(leader);
        request.setStartDate(LocalDate.now().plusDays(5));
        return request;
    }
}
