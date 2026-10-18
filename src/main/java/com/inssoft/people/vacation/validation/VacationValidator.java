package com.inssoft.people.vacation.validation;

import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.vacation.entity.VacationStatus;
import com.inssoft.people.vacation.service.WorkingDayCalculator;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class VacationValidator {

    private final WorkingDayCalculator workingDayCalculator;

    public VacationValidator(WorkingDayCalculator workingDayCalculator) {
        this.workingDayCalculator = workingDayCalculator;
    }

    public void validateRequestDates(Long companyId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("Las fechas de inicio y fin son obligatorias");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("La fecha fin no puede ser menor que la fecha inicio");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BusinessException("No se pueden solicitar vacaciones con fecha de inicio pasada");
        }
        if (!workingDayCalculator.isWorkingDay(companyId, startDate)) {
            throw new BusinessException("La fecha de inicio debe ser un día hábil");
        }
        if (!workingDayCalculator.isWorkingDay(companyId, endDate)) {
            throw new BusinessException("La fecha de fin debe ser un día hábil");
        }
    }

    public int businessDays(Long companyId, LocalDate startDate, LocalDate endDate) {
        int days = workingDayCalculator.businessDays(companyId, startDate, endDate);
        if (days <= 0) {
            throw new BusinessException("El rango no contiene días hábiles");
        }
        return days;
    }

    public void validateLeader(Employee employee, User leader) {
        if (leader == null) {
            return;
        }

        if (employee.getUser() != null
                && employee.getUser().getId().equals(leader.getId())) {
            throw new BusinessException("El líder no puede ser el mismo usuario que solicita");
        }

        Role role = leader.getRole();
        if (role != Role.ROLE_LIDER && role != Role.ROLE_ADMIN) {
            throw new BusinessException("El autorizador debe tener rol de líder");
        }
    }

    public void validateLeaderDecision(VacationRequest request, User leader) {
        if (!VacationStatus.PENDING_LEADER.name().equals(request.getStatus())) {
            throw new BusinessException("La solicitud no está pendiente del líder");
        }
        if (request.getLeader() == null
                || leader == null
                || !request.getLeader().getId().equals(leader.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "No puedes decidir una solicitud que no te fue asignada");
        }
    }

    public void validateAdminDecision(VacationRequest request) {
        boolean actionable = VacationStatus.PENDING_ADMIN.name().equals(request.getStatus())
                || (VacationStatus.PENDING_LEADER.name().equals(request.getStatus())
                    && request.getLeader() == null);
        if (!actionable) {
            throw new BusinessException("La solicitud ya fue decidida o no está disponible para administración");
        }
    }

    public void validateCancelable(VacationRequest request) {
        if (VacationStatus.REJECTED.name().equals(request.getStatus())
                || VacationStatus.CANCELLED.name().equals(request.getStatus())) {
            throw new BusinessException("La solicitud ya está rechazada o cancelada");
        }
        if (VacationStatus.APPROVED.name().equals(request.getStatus())
                && request.getStartDate() != null
                && !request.getStartDate().isAfter(LocalDate.now())) {
            throw new BusinessException("No se puede cancelar una solicitud ya iniciada");
        }
    }
}
