package com.inssoft.people.vacation.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.security.AuthorizationService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.dto.VacationRequestEventResponse;
import com.inssoft.people.vacation.entity.VacationMovementType;
import com.inssoft.people.vacation.entity.VacationPeriod;
import com.inssoft.people.vacation.entity.VacationReferenceTypes;
import com.inssoft.people.vacation.entity.VacationRequestEvent;
import com.inssoft.people.vacation.entity.VacationStatus;
import com.inssoft.people.vacation.mapper.VacationMapper;
import com.inssoft.people.vacation.repository.VacationRequestEventRepository;
import com.inssoft.people.vacation.validation.VacationValidator;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.VacationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VacationWorkflowService {

    private final VacationRepository vacationRepository;
    private final VacationBalanceService balanceService;
    private final VacationRequestEventRepository eventRepository;
    private final VacationValidator validator;
    private final AuditService auditService;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;

    public VacationWorkflowService(
            VacationRepository vacationRepository,
            VacationBalanceService balanceService,
            VacationRequestEventRepository eventRepository,
            VacationValidator validator,
            AuditService auditService,
            EmployeeRepository employeeRepository,
            AuthorizationService authorizationService) {
        this.vacationRepository = vacationRepository;
        this.balanceService = balanceService;
        this.eventRepository = eventRepository;
        this.validator = validator;
        this.auditService = auditService;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public VacationRequest submit(
            Employee employee,
            LocalDate startDate,
            LocalDate endDate,
            User leader,
            User requestedBy,
            String comment) {
        return submit(employee, startDate, endDate, leader, requestedBy, comment, true);
    }

    @Transactional
    public VacationRequest submit(
            Employee employee,
            LocalDate startDate,
            LocalDate endDate,
            User leader,
            User requestedBy,
            String comment,
            boolean allowManagerFallback) {

        // El controller puede entregar una entidad detached; se recarga dentro
        // de la transacción para poder navegar relaciones lazy con seguridad.
        Long employeeId = employee.getId();
        employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("Empleado no encontrado: " + employeeId));

        validator.validateRequestDates(employee.getCompany().getId(), startDate, endDate);
        int days = validator.businessDays(employee.getCompany().getId(), startDate, endDate);

        List<String> blockingStatuses = List.of(
                VacationStatus.PENDING_LEADER.name(),
                VacationStatus.PENDING_ADMIN.name(),
                VacationStatus.APPROVED.name());

        if (vacationRepository.existsOverlappingByEmployee(
                employee.getId(), startDate, endDate, blockingStatuses)) {
            throw new BusinessException(
                    "Ya existe una solicitud pendiente o aprobada que se traslapa con esas fechas");
        }

        User resolvedLeader = allowManagerFallback ? resolveLeader(employee, leader) : leader;
        validator.validateLeader(employee, resolvedLeader);

        VacationPeriod period = balanceService.lockCurrentPeriod(employee);
        VacationBalanceService.Balance balance = balanceService.balanceForPeriod(period);

        if (!period.getPolicy().isAllowNegative()
                && balance.available().compareTo(BigDecimal.valueOf(days)) < 0) {
            throw new BusinessException(
                    "Saldo insuficiente. Disponible: " + balance.available().stripTrailingZeros().toPlainString()
                            + " días, solicitados: " + days);
        }

        String status = resolvedLeader == null
                ? VacationStatus.PENDING_ADMIN.name()
                : VacationStatus.PENDING_LEADER.name();

        VacationRequest request = new VacationRequest();
        request.setEmployee(employee);
        request.setUser(employee.getUser());
        request.setUsername(employee.getUser() != null
                ? employee.getUser().getUsername()
                : employee.getEmployeeNumber());
        request.setRequestedBy(requestedBy);
        request.setRequestedByUsername(requestedBy == null ? null : requestedBy.getUsername());
        request.setPeriod(period);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setDays(days);
        request.setStatus(status);
        request.setLeader(resolvedLeader);
        request.setLegacyBalance(false);

        VacationRequest saved = vacationRepository.save(request);

        balanceService.addMovement(
                employee, period, VacationMovementType.HOLD,
                BigDecimal.valueOf(days), BigDecimal.valueOf(-days),
                VacationReferenceTypes.VACATION_REQUEST, saved.getId(),
                "Retención por solicitud de vacaciones", requestedBy);

        event(saved, "SUBMITTED", null, status, requestedBy, comment);
        balanceService.syncLegacyBalance(employee);
        auditService.record("VACATION_SUBMIT", "VacationRequest",
                saved.getUuid().toString(), VacationMapper.toResponse(saved));

        return saved;
    }

    @Transactional
    public VacationRequest approveByLeader(Long requestId, User leader) {
        VacationRequest request = findForUpdate(requestId);
        authorizationService.assertCanApproveVacation(leader, request);
        validator.validateLeaderDecision(request, leader);

        String previous = request.getStatus();
        request.setStatus(VacationStatus.PENDING_ADMIN.name());
        request.setLeaderDecision("APPROVED");
        request.setLeaderDecidedAt(LocalDateTime.now());
        VacationRequest saved = vacationRepository.save(request);

        event(saved, "LEADER_APPROVED", previous, saved.getStatus(), leader, null);
        audit("VACATION_LEADER_APPROVE", saved, leader);
        return saved;
    }

    @Transactional
    public VacationRequest rejectByLeader(Long requestId, User leader, String comment) {
        VacationRequest request = findForUpdate(requestId);
        authorizationService.assertCanApproveVacation(leader, request);
        validator.validateLeaderDecision(request, leader);

        String previous = request.getStatus();
        request.setStatus(VacationStatus.REJECTED.name());
        request.setLeaderDecision("REJECTED");
        request.setLeaderComment(trim(comment));
        request.setLeaderDecidedAt(LocalDateTime.now());
        VacationRequest saved = vacationRepository.save(request);

        releaseHold(saved, leader);
        syncBalance(saved);
        event(saved, "LEADER_REJECTED", previous, saved.getStatus(), leader, comment);
        audit("VACATION_LEADER_REJECT", saved, leader);
        return saved;
    }

    @Transactional
    public VacationRequest approveByAdmin(Long requestId, User admin) {
        VacationRequest request = findForUpdate(requestId);
        authorizationService.assertCanApproveVacation(admin, request);
        validator.validateAdminDecision(request);
        validateNotSelfDecision(request, admin);

        String previous = request.getStatus();
        request.setStatus(VacationStatus.APPROVED.name());
        request.setDecidedBy(admin == null ? null : admin.getUsername());
        request.setDecidedAt(LocalDateTime.now());
        VacationRequest saved = vacationRepository.save(request);

        consumeHold(saved, admin);
        syncBalance(saved);
        event(saved, "APPROVED", previous, saved.getStatus(), admin, null);
        audit("VACATION_APPROVE", saved, admin);
        return saved;
    }

    @Transactional
    public VacationRequest rejectByAdmin(Long requestId, User admin, String comment) {
        VacationRequest request = findForUpdate(requestId);
        authorizationService.assertCanApproveVacation(admin, request);
        validator.validateAdminDecision(request);
        validateNotSelfDecision(request, admin);

        String previous = request.getStatus();
        request.setStatus(VacationStatus.REJECTED.name());
        request.setDecidedBy(admin == null ? null : admin.getUsername());
        request.setDecidedAt(LocalDateTime.now());
        request.setAdminComment(trim(comment));
        VacationRequest saved = vacationRepository.save(request);

        releaseHold(saved, admin);
        syncBalance(saved);
        event(saved, "REJECTED", previous, saved.getStatus(), admin, comment);
        audit("VACATION_REJECT", saved, admin);
        return saved;
    }

    @Transactional
    public VacationRequest cancel(Long requestId, User actor, String reason, boolean canManage) {
        VacationRequest request = findForUpdate(requestId);
        validator.validateCancelable(request);
        authorizationService.assertCanCancelVacation(actor, request);

        boolean owner = request.getUser() != null
                && actor != null
                && request.getUser().getId().equals(actor.getId());
        if (!owner && !canManage) {
            throw new BusinessException(com.inssoft.people.common.api.ErrorCode.FORBIDDEN,
                    "No puedes cancelar una solicitud que no te pertenece");
        }

        String previous = request.getStatus();
        if (VacationStatus.APPROVED.name().equals(previous)) {
            reverseUsage(request, actor);
        } else {
            releaseHold(request, actor);
        }

        request.setStatus(VacationStatus.CANCELLED.name());
        request.setCancelledAt(LocalDateTime.now());
        request.setCancelReason(trim(reason));
        VacationRequest saved = vacationRepository.save(request);

        syncBalance(saved);
        event(saved, "CANCELLED", previous, saved.getStatus(), actor, reason);
        audit("VACATION_CANCEL", saved, actor);
        return saved;
    }

    @Transactional
    public VacationRequest approve(java.util.UUID uuid, User actor, boolean canManage) {
        VacationRequest request = findByUuid(uuid);
        if (VacationStatus.PENDING_LEADER.name().equals(request.getStatus())) {
            return approveByLeader(request.getId(), actor);
        }
        if (!canManage) {
            throw new BusinessException(com.inssoft.people.common.api.ErrorCode.FORBIDDEN,
                    "Solo administración o RRHH puede aprobar solicitudes en etapa administrativa");
        }
        return approveByAdmin(request.getId(), actor);
    }

    @Transactional
    public VacationRequest reject(java.util.UUID uuid, User actor, String comment, boolean canManage) {
        VacationRequest request = findByUuid(uuid);
        if (VacationStatus.PENDING_LEADER.name().equals(request.getStatus())) {
            return rejectByLeader(request.getId(), actor, comment);
        }
        if (!canManage) {
            throw new BusinessException(com.inssoft.people.common.api.ErrorCode.FORBIDDEN,
                    "Solo administración o RRHH puede rechazar solicitudes en etapa administrativa");
        }
        return rejectByAdmin(request.getId(), actor, comment);
    }

    @Transactional
    public VacationRequest cancel(java.util.UUID uuid, User actor, String reason, boolean canManage) {
        VacationRequest request = findByUuid(uuid);
        return cancel(request.getId(), actor, reason, canManage);
    }

    @Transactional(readOnly = true)
    public List<VacationRequestEventResponse> events(java.util.UUID uuid) {
        VacationRequest request = findByUuid(uuid);
        return events(request.getId());
    }

    @Transactional(readOnly = true)
    public List<VacationRequestEventResponse> events(Long requestId) {
        VacationRequest request = vacationRepository.findByIdAndDeletedAtIsNull(requestId)
                .orElseThrow(() -> new BusinessException("Solicitud no encontrada: " + requestId));
        return eventRepository.findByRequest_IdOrderByCreatedAtAsc(request.getId()).stream()
                .map(VacationMapper::toEventResponse)
                .toList();
    }

    private VacationRequest findByUuid(java.util.UUID uuid) {
        return vacationRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de vacaciones", uuid));
    }

    private VacationRequest findForUpdate(Long requestId) {
        return vacationRepository.findByIdForUpdate(requestId)
                .filter(request -> request.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("Solicitud no encontrada: " + requestId));
    }

    private User resolveLeader(Employee employee, User leader) {
        if (leader != null) {
            return leader;
        }
        if (employee.getManager() == null || employee.getManager().getUser() == null) {
            return null;
        }
        User managerUser = employee.getManager().getUser();
        Role role = managerUser.getRole();
        if (role == Role.ROLE_LIDER || role == Role.ROLE_ADMIN) {
            return managerUser;
        }
        return null;
    }

    private void syncBalance(VacationRequest request) {
        if (request.getEmployee() != null) {
            balanceService.syncLegacyBalance(request.getEmployee());
        }
    }

    private void releaseHold(VacationRequest request, User actor) {
        if (request.getEmployee() == null) {
            return;
        }
        if (request.isLegacyBalance()) {
            legacyRelease(request, actor);
            return;
        }
        if (request.getPeriod() == null
                || !balanceService.hasMovement(request.getId(), VacationMovementType.HOLD)
                || balanceService.hasMovement(request.getId(), VacationMovementType.RELEASE)) {
            return;
        }

        BigDecimal days = BigDecimal.valueOf(request.getDays());
        balanceService.addMovement(
                request.getEmployee(), request.getPeriod(), VacationMovementType.RELEASE,
                days, days, VacationReferenceTypes.VACATION_REQUEST, request.getId(),
                "Liberación de retención por rechazo/cancelación", actor);
    }

    private void consumeHold(VacationRequest request, User actor) {
        if (request.isLegacyBalance()
                || request.getEmployee() == null
                || request.getPeriod() == null
                || !balanceService.hasMovement(request.getId(), VacationMovementType.HOLD)) {
            return;
        }

        BigDecimal days = BigDecimal.valueOf(request.getDays());

        if (!balanceService.hasMovement(request.getId(), VacationMovementType.RELEASE)) {
            balanceService.addMovement(
                    request.getEmployee(), request.getPeriod(), VacationMovementType.RELEASE,
                    days, days, VacationReferenceTypes.VACATION_REQUEST, request.getId(),
                    "Liberación de retención por aprobación", actor);
        }

        if (!balanceService.hasMovement(request.getId(), VacationMovementType.USAGE)) {
            balanceService.addMovement(
                    request.getEmployee(), request.getPeriod(), VacationMovementType.USAGE,
                    days, days.negate(), VacationReferenceTypes.VACATION_REQUEST, request.getId(),
                    "Consumo de vacaciones aprobadas", actor);
        }
    }

    private void reverseUsage(VacationRequest request, User actor) {
        if (request.getEmployee() == null) {
            return;
        }
        if (request.isLegacyBalance()) {
            legacyRelease(request, actor);
            return;
        }
        if (request.getPeriod() == null
                || !balanceService.hasMovement(request.getId(), VacationMovementType.USAGE)
                || balanceService.hasMovement(request.getId(), VacationMovementType.USAGE_REVERSAL)) {
            return;
        }

        BigDecimal days = BigDecimal.valueOf(request.getDays());
        balanceService.addMovement(
                request.getEmployee(), request.getPeriod(), VacationMovementType.USAGE_REVERSAL,
                days, days, VacationReferenceTypes.VACATION_REQUEST, request.getId(),
                "Reverso de consumo por cancelación", actor);
    }

    /**
     * Las solicitudes legadas ya descontaron días del saldo inicial (opening
     * balance). Si se rechazan/cancelan después de la migración, se devuelven
     * con un movimiento ADJUSTMENT referenciado a la solicitud.
     */
    private void legacyRelease(VacationRequest request, User actor) {
        if (request.getId() == null
                || request.getDays() == null
                || request.getEmployee() == null
                || balanceService.hasMovement(request.getId(), VacationMovementType.ADJUSTMENT)) {
            return;
        }

        VacationPeriod period = request.getPeriod() != null
                ? request.getPeriod()
                : balanceService.ensureCurrentPeriod(request.getEmployee());

        BigDecimal days = BigDecimal.valueOf(request.getDays());
        balanceService.addMovement(
                request.getEmployee(), period, VacationMovementType.ADJUSTMENT,
                days, days, VacationReferenceTypes.VACATION_REQUEST, request.getId(),
                "Liberación de solicitud legada rechazada/cancelada", actor);
    }

    private void validateNotSelfDecision(VacationRequest request, User actor) {
        if (actor == null) {
            return;
        }
        boolean owner = request.getUser() != null
                && request.getUser().getId().equals(actor.getId());
        boolean creator = request.getRequestedBy() != null
                && request.getRequestedBy().getId().equals(actor.getId());
        if (owner || creator) {
            throw new BusinessException(com.inssoft.people.common.api.ErrorCode.FORBIDDEN,
                    "No puedes decidir tu propia solicitud de vacaciones o una que tú creaste");
        }
    }

    private void event(
            VacationRequest request,
            String action,
            String fromStatus,
            String toStatus,
            User actor,
            String comment) {

        VacationRequestEvent event = new VacationRequestEvent();
        event.setRequest(request);
        event.setAction(action);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActor(actor);
        event.setComment(trim(comment));
        eventRepository.save(event);
    }

    private void audit(String action, VacationRequest request, User actor) {
        auditService.record(action, "VacationRequest",
                request.getUuid().toString(), VacationMapper.toResponse(request));
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
