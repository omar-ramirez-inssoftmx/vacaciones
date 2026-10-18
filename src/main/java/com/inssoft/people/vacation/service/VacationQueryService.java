package com.inssoft.people.vacation.service;

import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.inssoft.people.vacation.dto.VacationLedgerResponse;
import com.inssoft.people.vacation.dto.VacationPeriodResponse;
import com.inssoft.people.vacation.dto.VacationRequestResponse;
import com.inssoft.people.vacation.mapper.VacationMapper;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.VacationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class VacationQueryService {

    private final EmployeeRepository employeeRepository;
    private final VacationRepository vacationRepository;
    private final VacationBalanceService balanceService;

    public VacationQueryService(
            EmployeeRepository employeeRepository,
            VacationRepository vacationRepository,
            VacationBalanceService balanceService) {
        this.employeeRepository = employeeRepository;
        this.vacationRepository = vacationRepository;
        this.balanceService = balanceService;
    }

    @Transactional
    public VacationBalanceResponse myBalance(String username) {
        return balanceService.balanceResponse(requireEmployee(username));
    }

    @Transactional(readOnly = true)
    public List<VacationPeriodResponse> myPeriods(String username) {
        return balanceService.periods(requireEmployee(username));
    }

    @Transactional(readOnly = true)
    public List<VacationLedgerResponse> myLedger(String username) {
        return balanceService.ledger(requireEmployee(username));
    }

    @Transactional(readOnly = true)
    public PageResponse<VacationRequestResponse> myRequests(String username, int page, int size) {
        Employee employee = requireEmployee(username);
        Page<VacationRequest> result = vacationRepository
                .findByEmployee_IdAndDeletedAtIsNullOrderByStartDateDesc(
                        employee.getId(),
                        PageRequestFactory.of(page, size, Sort.by(Sort.Direction.DESC, "startDate")));
        return PageResponse.from(result, VacationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<VacationRequestResponse> search(
            UUID employeeUuid,
            String status,
            LocalDate from,
            LocalDate to,
            String q,
            int page,
            int size) {

        String normalizedStatus = status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);
        String query = q == null || q.isBlank() ? null : q.trim();

        Page<VacationRequest> result = vacationRepository.searchRequests(
                employeeUuid,
                normalizedStatus,
                from,
                to,
                query,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "startDate")));

        return PageResponse.from(result, VacationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public VacationRequestResponse get(UUID uuid, String username, boolean canViewAll) {
        VacationRequest request = vacationRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de vacaciones", uuid));

        if (!canViewAll && !belongsTo(request, username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "No puedes consultar una solicitud que no te pertenece");
        }

        return VacationMapper.toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<VacationRequestResponse> pendingForLeader(String username) {
        return vacationRepository
                .findByLeader_UsernameAndStatusOrderByStartDateAsc(username, "PENDING_LEADER")
                .stream()
                .map(VacationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Employee requireEmployee(String username) {
        return employeeRepository.findByUser_UsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe un empleado vinculado al usuario " + username));
    }

    @Transactional(readOnly = true)
    public Employee requireTargetEmployee(UUID employeeUuid, String actorUsername, boolean canManage) {
        Employee employee = employeeRepository.findByUuidAndDeletedAtIsNull(employeeUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", employeeUuid));

        if (canManage) {
            return employee;
        }

        Employee actorEmployee = requireEmployee(actorUsername);
        boolean isManager = employee.getManager() != null
                && employee.getManager().getId().equals(actorEmployee.getId());
        if (!isManager) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Solo puedes crear solicitudes para tus colaboradores directos");
        }
        return employee;
    }

    private boolean belongsTo(VacationRequest request, String username) {
        if (username == null) {
            return false;
        }
        if (request.getUser() != null && username.equals(request.getUser().getUsername())) {
            return true;
        }
        if (request.getLeader() != null && username.equals(request.getLeader().getUsername())) {
            return true;
        }
        return request.getEmployee() != null
                && request.getEmployee().getUser() != null
                && username.equals(request.getEmployee().getUser().getUsername());
    }
}
