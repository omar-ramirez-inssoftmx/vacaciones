package com.inssoft.people.vacation.service;

import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.inssoft.people.vacation.dto.VacationLedgerResponse;
import com.inssoft.people.vacation.dto.VacationPeriodResponse;
import com.inssoft.people.vacation.entity.VacationLedger;
import com.inssoft.people.vacation.entity.VacationMovementType;
import com.inssoft.people.vacation.entity.VacationPeriod;
import com.inssoft.people.vacation.entity.VacationPeriodStatus;
import com.inssoft.people.vacation.entity.VacationPolicy;
import com.inssoft.people.vacation.entity.VacationReferenceTypes;
import com.inssoft.people.vacation.mapper.VacationMapper;
import com.inssoft.people.vacation.repository.VacationLedgerRepository;
import com.inssoft.people.vacation.repository.VacationPeriodRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class VacationBalanceService {

    private final VacationPolicyService policyService;
    private final VacationPeriodRepository periodRepository;
    private final VacationLedgerRepository ledgerRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public VacationBalanceService(
            VacationPolicyService policyService,
            VacationPeriodRepository periodRepository,
            VacationLedgerRepository ledgerRepository,
            UserRepository userRepository,
            EmployeeRepository employeeRepository) {
        this.policyService = policyService;
        this.periodRepository = periodRepository;
        this.ledgerRepository = ledgerRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public VacationPeriod ensureCurrentPeriodById(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", employeeId));
        return ensureCurrentPeriod(employee);
    }

    @Transactional
    public VacationPeriod ensureCurrentPeriod(Employee employee) {
        LocalDate today = LocalDate.now();
        if (employee.getHireDate() == null || employee.getHireDate().isAfter(today)) {
            throw new BusinessException("El empleado no tiene una fecha de ingreso válida para vacaciones");
        }

        int completedYears = Period.between(employee.getHireDate(), today).getYears();
        LocalDate periodStart = employee.getHireDate().plusYears(completedYears);
        LocalDate periodEnd = periodStart.plusYears(1).minusDays(1);

        VacationPeriod existing = periodRepository
                .findByEmployee_IdAndPeriodStart(employee.getId(), periodStart)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        VacationPolicy policy = policyService.resolvePolicy(employee.getCompany());
        BigDecimal entitled = policyService.entitledDays(policy, completedYears);

        BigDecimal legacyOpening = BigDecimal.ZERO;
        if (completedYears == 0 && employee.getUser() != null) {
            legacyOpening = BigDecimal.valueOf(employee.getUser().getVacationDaysAvailable());
        }

        VacationPeriod period = new VacationPeriod();
        period.setEmployee(employee);
        period.setPolicy(policy);
        period.setPeriodStart(periodStart);
        period.setPeriodEnd(periodEnd);
        period.setAnniversaryDate(periodStart);
        period.setEntitledDays(scale(entitled));
        period.setOpeningBalance(scale(legacyOpening.max(BigDecimal.ZERO)));
        period.setStatus(VacationPeriodStatus.OPEN);
        VacationPeriod saved = periodRepository.save(period);

        if (legacyOpening.compareTo(BigDecimal.ZERO) > 0) {
            addMovement(saved.getEmployee(), saved, VacationMovementType.OPENING_BALANCE,
                    legacyOpening, legacyOpening, VacationReferenceTypes.MIGRATION,
                    employee.getId(), "Saldo inicial legado", null);
        } else if (entitled.compareTo(BigDecimal.ZERO) > 0) {
            addMovement(saved.getEmployee(), saved, VacationMovementType.ACCRUAL,
                    entitled, entitled, VacationReferenceTypes.PERIOD_ACCRUAL,
                    saved.getId(), "Acumulación del periodo " + periodStart, null);
        }

        syncLegacyFromPeriod(saved);
        return saved;
    }

    @Transactional
    public VacationPeriod lockCurrentPeriod(Employee employee) {
        VacationPeriod period = ensureCurrentPeriod(employee);
        return periodRepository
                .findByEmployeeAndStartForUpdate(employee.getId(), period.getPeriodStart())
                .orElseThrow(() -> new BusinessException("No se pudo bloquear el periodo de vacaciones"));
    }

    @Transactional
    public Balance balance(Employee employee) {
        VacationPeriod period = ensureCurrentPeriod(employee);
        return balanceForPeriod(period);
    }

    @Transactional
    public VacationBalanceResponse balanceResponse(Employee employee) {
        VacationPeriod period = ensureCurrentPeriod(employee);
        Balance balance = balanceForPeriod(period);
        return VacationMapper.toBalanceResponse(
                employee, period, balance.available(), balance.used(), balance.held());
    }

    @Transactional(readOnly = true)
    public List<VacationPeriodResponse> periods(Employee employee) {
        return periodRepository.findByEmployee_IdOrderByPeriodStartDesc(employee.getId()).stream()
                .map(VacationMapper::toPeriodResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VacationLedgerResponse> ledger(Employee employee) {
        return ledgerRepository.findByEmployee_IdOrderByCreatedAtDesc(employee.getId()).stream()
                .map(VacationMapper::toLedgerResponse)
                .toList();
    }

    @Transactional
    public VacationBalanceResponse adjust(
            Employee employee,
            BigDecimal newAvailable,
            String reason,
            User actor) {

        VacationPeriod period = lockCurrentPeriod(employee);
        Balance current = balanceForPeriod(period);
        BigDecimal target = scale(newAvailable);

        if (target.compareTo(BigDecimal.ZERO) < 0 && !period.getPolicy().isAllowNegative()) {
            throw new BusinessException("El saldo disponible no puede ser negativo");
        }

        BigDecimal difference = scale(target.subtract(current.available()));
        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            return VacationMapper.toBalanceResponse(
                    employee, period, current.available(), current.used(), current.held());
        }

        addMovement(employee, period, VacationMovementType.ADJUSTMENT,
                difference.abs(), difference, VacationReferenceTypes.ADJUSTMENT,
                employee.getId(), reason == null ? "Ajuste administrativo" : reason, actor);

        syncLegacyBalance(employee);
        Balance updated = balanceForPeriod(period);
        return VacationMapper.toBalanceResponse(
                employee, period, updated.available(), updated.used(), updated.held());
    }

    @Transactional
    public void syncLegacyBalance(Employee employee) {
        User user = employee.getUser();
        if (user == null) {
            return;
        }

        BigDecimal available = balanceForPeriod(ensureCurrentPeriod(employee)).available();
        updateLegacyBalance(user, available);
    }

    private void syncLegacyFromPeriod(VacationPeriod period) {
        User user = period.getEmployee().getUser();
        if (user == null) {
            return;
        }
        updateLegacyBalance(user, balanceForPeriod(period).available());
    }

    private void updateLegacyBalance(User user, BigDecimal available) {
        int days = available.setScale(0, RoundingMode.HALF_UP).intValue();
        if (user.getVacationDaysAvailable() != days) {
            user.setVacationDaysAvailable(days);
            userRepository.save(user);
        }
    }

    public Balance balanceForPeriod(VacationPeriod period) {
        BigDecimal available = nz(ledgerRepository.sumSignedByPeriod(period.getId()));
        BigDecimal used = nz(ledgerRepository.sumDaysByPeriodAndType(
                period.getId(), VacationMovementType.USAGE));
        BigDecimal reversed = nz(ledgerRepository.sumDaysByPeriodAndType(
                period.getId(), VacationMovementType.USAGE_REVERSAL));
        BigDecimal held = nz(ledgerRepository.sumDaysByPeriodAndType(
                period.getId(), VacationMovementType.HOLD));
        BigDecimal released = nz(ledgerRepository.sumDaysByPeriodAndType(
                period.getId(), VacationMovementType.RELEASE));

        BigDecimal netUsed = used.subtract(reversed).max(BigDecimal.ZERO);
        BigDecimal netHeld = held.subtract(released).max(BigDecimal.ZERO);

        return new Balance(
                period,
                scale(available),
                scale(netUsed),
                scale(netHeld),
                scale(period.getEntitledDays()),
                scale(period.getOpeningBalance()));
    }

    public VacationLedger addMovement(
            Employee employee,
            VacationPeriod period,
            VacationMovementType movementType,
            BigDecimal days,
            BigDecimal signedDays,
            String referenceType,
            Long referenceId,
            String notes,
            User actor) {

        if (days == null || days.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Los días del movimiento no pueden ser negativos");
        }

        VacationLedger ledger = new VacationLedger();
        ledger.setEmployee(employee);
        ledger.setPeriod(period);
        ledger.setMovementType(movementType);
        ledger.setDays(scale(days));
        ledger.setSignedDays(scale(signedDays));
        ledger.setReferenceType(referenceType);
        ledger.setReferenceId(referenceId);
        ledger.setNotes(notes);
        ledger.setCreatedBy(actor);
        return ledgerRepository.save(ledger);
    }

    public boolean hasMovement(Long requestId, VacationMovementType type) {
        return ledgerRepository.existsByReferenceTypeAndReferenceIdAndMovementType(
                VacationReferenceTypes.VACATION_REQUEST, requestId, type);
    }

    public record Balance(
            VacationPeriod period,
            BigDecimal available,
            BigDecimal used,
            BigDecimal held,
            BigDecimal entitled,
            BigDecimal opening
    ) {
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
