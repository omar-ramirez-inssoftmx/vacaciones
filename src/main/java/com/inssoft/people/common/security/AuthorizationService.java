package com.inssoft.people.common.security;

import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.entity.VacationStatus;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Punto único de autorización por recurso.
 *
 * <p>La decisión final combina:</p>
 * <ul>
 *   <li>rol/permiso ({@code PERM_*});</li>
 *   <li>alcance (self / team / all);</li>
 *   <li>relación jerárquica;</li>
 *   <li>propiedad del recurso;</li>
 *   <li>segregación de funciones.</li>
 * </ul>
 *
 * <p>Nunca debe reemplazarse por {@code hasRole("ADMIN")} disperso en los
 * controllers. Los controllers validan permiso y este servicio valida el
 * recurso concreto.</p>
 */
@Component("authorizationService")
public class AuthorizationService {

    private final AccessGuard accessGuard;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public AuthorizationService(
            AccessGuard accessGuard,
            EmployeeRepository employeeRepository,
            UserRepository userRepository) {
        this.accessGuard = accessGuard;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    public boolean hasPermission(String permission) {
        return accessGuard.has(permission);
    }

    public boolean hasAnyPermission(String... permissions) {
        if (permissions == null) {
            return false;
        }
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public User currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    public User requireCurrentUser() {
        User user = currentUserOrNull();
        if (user == null) {
            throw forbidden("Usuario no autenticado");
        }
        return user;
    }

    public Employee currentEmployeeOrNull() {
        User user = currentUserOrNull();
        if (user == null) {
            return null;
        }
        return employeeRepository.findByUser_IdAndDeletedAtIsNull(user.getId()).orElse(null);
    }

    // ---------------------------------------------------------------------
    // Empleados
    // ---------------------------------------------------------------------

    public boolean isSelf(User actor, Employee target) {
        if (actor == null || target == null) {
            return false;
        }
        if (target.getUser() != null && target.getUser().getId().equals(actor.getId())) {
            return true;
        }
        return actor.getEmployeeId() != null && actor.getEmployeeId().equals(target.getId());
    }

    public boolean isTeamMember(User actor, Employee target) {
        if (actor == null || target == null || target.getManager() == null) {
            return false;
        }
        Employee actorEmployee = employeeRepository
                .findByUser_IdAndDeletedAtIsNull(actor.getId())
                .orElse(null);
        if (actorEmployee != null) {
            return target.getManager().getId().equals(actorEmployee.getId());
        }
        return target.getManager().getUser() != null
                && target.getManager().getUser().getId().equals(actor.getId());
    }

    public boolean canViewEmployee(User actor, Employee target) {
        if (actor == null || target == null) {
            return false;
        }
        return isSelf(actor, target)
                || hasPermission("EMPLOYEE_VIEW_ALL")
                || (hasPermission("EMPLOYEE_VIEW_TEAM") && isTeamMember(actor, target));
    }

    /**
     * Líder no puede ver RFC/CURP/NSS/documentos personales/recibos por el
     * simple hecho de ser su colaborador. Solo self o alcance global sensible.
     */
    public boolean canViewSensitiveEmployee(User actor, Employee target) {
        return actor != null
                && target != null
                && (isSelf(actor, target)
                    || hasPermission("EMPLOYEE_VIEW_SENSITIVE")
                    || hasPermission("EMPLOYEE_VIEW_ALL"));
    }

    public boolean canListAllEmployees() {
        return hasPermission("EMPLOYEE_VIEW_ALL");
    }

    public boolean canListTeamEmployees() {
        return hasPermission("EMPLOYEE_VIEW_TEAM");
    }

    public boolean canManageEmployees() {
        return hasAnyPermission("EMPLOYEE_CREATE", "EMPLOYEE_UPDATE", "EMPLOYEE_DISABLE");
    }

    public boolean canUpdateEmployee(User actor, Employee target) {
        return actor != null && target != null
                && (hasPermission("EMPLOYEE_UPDATE") || hasPermission("EMPLOYEE_DISABLE"))
                && (hasPermission("EMPLOYEE_VIEW_ALL") || isSelf(actor, target));
    }

    public void assertCanViewEmployee(User actor, Employee target) {
        if (!canViewEmployee(actor, target)) {
            throw forbidden("No puedes consultar este empleado");
        }
    }

    public void assertCanViewSensitiveEmployee(User actor, Employee target) {
        if (!canViewSensitiveEmployee(actor, target)) {
            throw forbidden("No tienes permiso para consultar datos sensibles de este empleado");
        }
    }

    public void assertCanUpdateEmployee(User actor, Employee target) {
        if (!canUpdateEmployee(actor, target)) {
            throw forbidden("No puedes modificar este empleado");
        }
    }

    // ---------------------------------------------------------------------
    // Vacaciones
    // ---------------------------------------------------------------------

    public boolean isRequester(User actor, VacationRequest request) {
        if (actor == null || request == null) {
            return false;
        }
        if (request.getUser() != null && request.getUser().getId().equals(actor.getId())) {
            return true;
        }
        return request.getRequestedBy() != null
                && request.getRequestedBy().getId().equals(actor.getId());
    }

    public boolean isAssignedLeader(User actor, VacationRequest request) {
        return actor != null
                && request != null
                && request.getLeader() != null
                && request.getLeader().getId().equals(actor.getId());
    }

    public boolean canViewVacation(User actor, VacationRequest request) {
        if (actor == null || request == null) {
            return false;
        }
        if (isRequester(actor, request) || isAssignedLeader(actor, request)) {
            return true;
        }
        if (hasPermission("VACATION_MANAGE_ALL") || hasPermission("VACATION_ADJUST")) {
            return true;
        }
        return hasPermission("VACATION_VIEW_TEAM")
                && request.getEmployee() != null
                && isTeamMember(actor, request.getEmployee());
    }

    public boolean canCreateVacationFor(User actor, Employee target) {
        if (actor == null || target == null) {
            return false;
        }
        if (isSelf(actor, target)) {
            return hasPermission("VACATION_CREATE_SELF") || hasPermission("VACATION_CREATE");
        }
        if (hasPermission("VACATION_MANAGE_ALL") || hasPermission("VACATION_ADJUST")) {
            return true;
        }
        return hasPermission("VACATION_VIEW_TEAM") && isTeamMember(actor, target);
    }

    public boolean canApproveVacation(User actor, VacationRequest request) {
        if (actor == null || request == null || isRequester(actor, request)) {
            return false;
        }
        if (VacationStatus.PENDING_LEADER.name().equals(request.getStatus())) {
            return isAssignedLeader(actor, request)
                    && hasAnyPermission("VACATION_APPROVE_TEAM", "VACATION_APPROVE");
        }
        if (VacationStatus.PENDING_ADMIN.name().equals(request.getStatus())) {
            return hasAnyPermission("VACATION_MANAGE_ALL", "VACATION_ADJUST");
        }
        return false;
    }

    public boolean canCancelVacation(User actor, VacationRequest request) {
        if (actor == null || request == null) {
            return false;
        }
        if (hasAnyPermission("VACATION_MANAGE_ALL", "VACATION_ADJUST")) {
            return true;
        }
        return isRequester(actor, request)
                && hasAnyPermission("VACATION_CANCEL_SELF", "VACATION_CANCEL");
    }

    public void assertCanViewVacation(User actor, VacationRequest request) {
        if (!canViewVacation(actor, request)) {
            throw forbidden("No puedes consultar esta solicitud de vacaciones");
        }
    }

    public void assertCanCreateVacationFor(User actor, Employee target) {
        if (!canCreateVacationFor(actor, target)) {
            throw forbidden("No puedes crear solicitudes para este empleado");
        }
    }

    public void assertCanApproveVacation(User actor, VacationRequest request) {
        if (!canApproveVacation(actor, request)) {
            if (isRequester(actor, request)) {
                throw forbidden("No puedes aprobar o rechazar una solicitud que tú creaste");
            }
            throw forbidden("No puedes decidir esta solicitud de vacaciones");
        }
    }

    public void assertCanCancelVacation(User actor, VacationRequest request) {
        if (!canCancelVacation(actor, request)) {
            throw forbidden("No puedes cancelar esta solicitud de vacaciones");
        }
    }

    // ---------------------------------------------------------------------
    // Nómina / documentos
    // ---------------------------------------------------------------------

    public boolean canViewPayroll(User actor, Employee target) {
        if (actor == null || target == null) {
            return false;
        }
        return isSelf(actor, target)
                || hasAnyPermission("PAYROLL_MANAGE", "PAYROLL_VIEW")
                || (hasPermission("PAYROLL_VIEW_TEAM") && isTeamMember(actor, target));
    }

    public boolean canManagePayroll() {
        return hasAnyPermission("PAYROLL_MANAGE", "PAYROLL_UPLOAD");
    }

    public boolean canViewDocument(User actor, Employee target) {
        if (actor == null || target == null) {
            return false;
        }
        return isSelf(actor, target)
                || hasAnyPermission("DOCUMENT_MANAGE", "DOCUMENT_VIEW")
                || (hasPermission("DOCUMENT_VIEW_TEAM") && isTeamMember(actor, target));
    }

    public boolean canManageDocument() {
        return hasAnyPermission("DOCUMENT_MANAGE", "DOCUMENT_UPLOAD", "DOCUMENT_DELETE");
    }

    public void assertCanViewPayroll(User actor, Employee target) {
        if (!canViewPayroll(actor, target)) {
            throw forbidden("No puedes consultar la nómina de este empleado");
        }
    }

    public void assertCanViewDocument(User actor, Employee target) {
        if (!canViewDocument(actor, target)) {
            throw forbidden("No puedes consultar los documentos de este empleado");
        }
    }

    // ---------------------------------------------------------------------
    // Comunicaciones / módulos futuros
    // ---------------------------------------------------------------------

    public boolean canViewCommunication() {
        return hasPermission("COMMUNICATION_VIEW");
    }

    public boolean canPublishCommunication() {
        return hasAnyPermission("COMMUNICATION_PUBLISH", "COMMUNICATION_CREATE");
    }

    public boolean canViewIncident(User actor, Employee target) {
        if (actor == null || target == null) {
            return false;
        }
        return isSelf(actor, target)
                || hasPermission("INCIDENT_MANAGE")
                || (hasAnyPermission("INCIDENT_VIEW_TEAM", "INCIDENT_VIEW")
                    && isTeamMember(actor, target));
    }

    public boolean canCreateIncidentFor(User actor, Employee target) {
        return actor != null && target != null
                && (isSelf(actor, target) && hasPermission("INCIDENT_CREATE_SELF")
                    || hasPermission("INCIDENT_CREATE_TEAM") && isTeamMember(actor, target)
                    || hasPermission("INCIDENT_MANAGE"));
    }

    public boolean canViewTelework(User actor, Employee target) {
        return actor != null && target != null
                && (isSelf(actor, target)
                    || hasPermission("TELEWORK_MANAGE")
                    || (hasPermission("TELEWORK_VIEW_TEAM") && isTeamMember(actor, target)));
    }

    public boolean canViewWorkplan(User actor, Employee target) {
        return actor != null && target != null
                && (isSelf(actor, target)
                    || hasPermission("WORKPLAN_MANAGE")
                    || (hasPermission("WORKPLAN_VIEW_TEAM") && isTeamMember(actor, target)));
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }
}
