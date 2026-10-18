package com.inssoft.people.vacation.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.vacation.dto.VacationAdjustmentRequest;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.inssoft.people.vacation.dto.VacationCancelRequest;
import com.inssoft.people.vacation.dto.VacationDecisionRequest;
import com.inssoft.people.vacation.dto.VacationLedgerResponse;
import com.inssoft.people.vacation.dto.VacationPeriodResponse;
import com.inssoft.people.vacation.dto.VacationPolicyResponse;
import com.inssoft.people.vacation.dto.VacationRequestCreateRequest;
import com.inssoft.people.vacation.dto.VacationRequestEventResponse;
import com.inssoft.people.vacation.dto.VacationRequestResponse;
import com.inssoft.people.vacation.service.VacationAdminService;
import com.inssoft.people.vacation.service.VacationPolicyService;
import com.inssoft.people.vacation.service.VacationQueryService;
import com.inssoft.people.vacation.service.VacationWorkflowService;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vacations")
@Validated
@Tag(name = "Vacations", description = "Saldo, periodos, ledger, solicitudes y workflow de vacaciones")
@SecurityRequirement(name = "bearerAuth")
public class VacationApiController {

    private final VacationQueryService queryService;
    private final VacationWorkflowService workflowService;
    private final VacationAdminService adminService;
    private final VacationPolicyService policyService;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public VacationApiController(
            VacationQueryService queryService,
            VacationWorkflowService workflowService,
            VacationAdminService adminService,
            VacationPolicyService policyService,
            UserRepository userRepository,
            AccessGuard accessGuard) {
        this.queryService = queryService;
        this.workflowService = workflowService;
        this.adminService = adminService;
        this.policyService = policyService;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/balance")
    @PreAuthorize("@accessGuard.has('VACATION_VIEW_SELF')")
    @Operation(summary = "Saldo de vacaciones del usuario autenticado")
    public ApiResponse<VacationBalanceResponse> myBalance(Authentication authentication) {
        return ApiResponse.ok("Saldo obtenido", queryService.myBalance(authentication.getName()));
    }

    @GetMapping("/periods")
    @PreAuthorize("@accessGuard.has('VACATION_VIEW_SELF')")
    @Operation(summary = "Periodos de vacaciones del usuario autenticado")
    public ApiResponse<List<VacationPeriodResponse>> myPeriods(Authentication authentication) {
        return ApiResponse.ok("Periodos obtenidos", queryService.myPeriods(authentication.getName()));
    }

    @GetMapping("/ledger")
    @PreAuthorize("@accessGuard.has('VACATION_VIEW_SELF')")
    @Operation(summary = "Movimientos de ledger del usuario autenticado")
    public ApiResponse<List<VacationLedgerResponse>> myLedger(Authentication authentication) {
        return ApiResponse.ok("Movimientos obtenidos", queryService.myLedger(authentication.getName()));
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('VACATION_VIEW_SELF')")
    @Operation(summary = "Solicitudes del usuario autenticado")
    public ApiResponse<PageResponse<VacationRequestResponse>> myRequests(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Solicitudes obtenidas",
                queryService.myRequests(authentication.getName(), page, size));
    }

    @PostMapping
    @PreAuthorize("@accessGuard.has('VACATION_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una solicitud de vacaciones (propia o para un empleado autorizado)")
    public ApiResponse<VacationRequestResponse> create(
            Authentication authentication,
            @Valid @RequestBody VacationRequestCreateRequest request) {

        User actor = currentUser(authentication);
        Employee employee = resolveTargetEmployee(request.employeeUuid(), actor);
        User leader = request.leaderUuid() == null
                ? null
                : userRepository.findByUuid(request.leaderUuid())
                        .orElseThrow(() -> new ResourceNotFoundException("Líder", request.leaderUuid()));

        VacationRequest saved = workflowService.submit(
                employee, request.startDate(), request.endDate(), leader, actor, request.comment());

        return ApiResponse.ok("Solicitud creada",
                queryService.get(saved.getUuid(), authentication.getName(), true));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("@accessGuard.hasAny('VACATION_VIEW', 'VACATION_VIEW_SELF')")
    @Operation(summary = "Obtiene una solicitud por UUID")
    public ApiResponse<VacationRequestResponse> get(
            Authentication authentication,
            @PathVariable UUID uuid) {
        return ApiResponse.ok("Solicitud obtenida",
                queryService.get(uuid, authentication.getName(), accessGuard.has("VACATION_ADJUST")));
    }

    @GetMapping("/{uuid}/events")
    @PreAuthorize("@accessGuard.hasAny('VACATION_VIEW', 'VACATION_VIEW_SELF')")
    @Operation(summary = "Historial de eventos de una solicitud")
    public ApiResponse<List<VacationRequestEventResponse>> events(
            Authentication authentication,
            @PathVariable UUID uuid) {
        queryService.get(uuid, authentication.getName(), accessGuard.has("VACATION_ADJUST"));
        return ApiResponse.ok("Eventos obtenidos", workflowService.events(uuid));
    }

    @PostMapping("/{uuid}/approve")
    @PreAuthorize("@accessGuard.has('VACATION_APPROVE')")
    @Operation(summary = "Aprueba una solicitud (líder o administración según el estado)")
    public ApiResponse<VacationRequestResponse> approve(
            Authentication authentication,
            @PathVariable UUID uuid) {
        User actor = currentUser(authentication);
        VacationRequest saved = workflowService.approve(
                uuid, actor, accessGuard.has("VACATION_ADJUST"));
        return ApiResponse.ok("Solicitud aprobada",
                queryService.get(saved.getUuid(), authentication.getName(), true));
    }

    @PostMapping("/{uuid}/reject")
    @PreAuthorize("@accessGuard.has('VACATION_APPROVE')")
    @Operation(summary = "Rechaza una solicitud (líder o administración según el estado)")
    public ApiResponse<VacationRequestResponse> reject(
            Authentication authentication,
            @PathVariable UUID uuid,
            @Valid @RequestBody VacationDecisionRequest request) {
        User actor = currentUser(authentication);
        VacationRequest saved = workflowService.reject(
                uuid, actor, request.comment(), accessGuard.has("VACATION_ADJUST"));
        return ApiResponse.ok("Solicitud rechazada",
                queryService.get(saved.getUuid(), authentication.getName(), true));
    }

    @PostMapping("/{uuid}/cancel")
    @PreAuthorize("@accessGuard.has('VACATION_CANCEL')")
    @Operation(summary = "Cancela una solicitud pendiente o aprobada no iniciada")
    public ApiResponse<VacationRequestResponse> cancel(
            Authentication authentication,
            @PathVariable UUID uuid,
            @Valid @RequestBody VacationCancelRequest request) {
        User actor = currentUser(authentication);
        VacationRequest saved = workflowService.cancel(
                uuid, actor, request.reason(), accessGuard.has("VACATION_ADJUST"));
        return ApiResponse.ok("Solicitud cancelada",
                queryService.get(saved.getUuid(), authentication.getName(), true));
    }

    @GetMapping("/pending")
    @PreAuthorize("@accessGuard.has('VACATION_APPROVE')")
    @Operation(summary = "Solicitudes pendientes del líder autenticado")
    public ApiResponse<List<VacationRequestResponse>> pending(Authentication authentication) {
        return ApiResponse.ok("Pendientes obtenidas",
                queryService.pendingForLeader(authentication.getName()));
    }

    @GetMapping("/search")
    @PreAuthorize("@accessGuard.has('VACATION_ADJUST')")
    @Operation(summary = "Búsqueda administrativa de solicitudes")
    public ApiResponse<PageResponse<VacationRequestResponse>> search(
            @RequestParam(required = false) UUID employeeUuid,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Solicitudes obtenidas",
                queryService.search(employeeUuid, status, from, to, q, page, size));
    }

    @GetMapping("/employees/{employeeUuid}/balance")
    @PreAuthorize("@accessGuard.has('VACATION_ADJUST')")
    @Operation(summary = "Saldo de vacaciones de un empleado")
    public ApiResponse<VacationBalanceResponse> employeeBalance(@PathVariable UUID employeeUuid) {
        return ApiResponse.ok("Saldo obtenido", adminService.balance(employeeUuid));
    }

    @PostMapping("/employees/{employeeUuid}/adjust")
    @PreAuthorize("@accessGuard.has('VACATION_ADJUST')")
    @Operation(summary = "Ajusta el saldo disponible de un empleado")
    public ApiResponse<VacationBalanceResponse> adjust(
            Authentication authentication,
            @PathVariable UUID employeeUuid,
            @Valid @RequestBody VacationAdjustmentRequest request) {
        User actor = currentUser(authentication);
        return ApiResponse.ok("Saldo ajustado",
                adminService.adjust(employeeUuid, request.available(), request.reason(), actor));
    }

    @GetMapping("/policies")
    @PreAuthorize("@accessGuard.hasAny('VACATION_POLICY_MANAGE', 'VACATION_VIEW')")
    @Operation(summary = "Lista las políticas de vacaciones")
    public ApiResponse<List<VacationPolicyResponse>> policies() {
        return ApiResponse.ok("Políticas obtenidas", policyService.listPolicies());
    }

    @GetMapping("/policies/{uuid}")
    @PreAuthorize("@accessGuard.hasAny('VACATION_POLICY_MANAGE', 'VACATION_VIEW')")
    @Operation(summary = "Obtiene una política de vacaciones")
    public ApiResponse<VacationPolicyResponse> policy(@PathVariable UUID uuid) {
        return ApiResponse.ok("Política obtenida", policyService.getPolicy(uuid));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", authentication.getName()));
    }

    private Employee resolveTargetEmployee(UUID employeeUuid, User actor) {
        if (employeeUuid == null) {
            return queryService.requireEmployee(actor.getUsername());
        }

        if (!accessGuard.has("VACATION_VIEW")) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "No puedes crear solicitudes para otros empleados");
        }

        return queryService.requireTargetEmployee(
                employeeUuid,
                actor.getUsername(),
                accessGuard.has("VACATION_ADJUST"));
    }
}
