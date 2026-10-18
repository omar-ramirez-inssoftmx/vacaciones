package com.inssoft.people.vacation.mapper;

import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.mapper.EmployeeMapper;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.inssoft.people.vacation.dto.VacationLedgerResponse;
import com.inssoft.people.vacation.dto.VacationPeriodResponse;
import com.inssoft.people.vacation.dto.VacationPolicyResponse;
import com.inssoft.people.vacation.dto.VacationPolicyRuleResponse;
import com.inssoft.people.vacation.dto.VacationRequestEventResponse;
import com.inssoft.people.vacation.dto.VacationRequestResponse;
import com.inssoft.people.vacation.entity.VacationLedger;
import com.inssoft.people.vacation.entity.VacationPeriod;
import com.inssoft.people.vacation.entity.VacationPolicy;
import com.inssoft.people.vacation.entity.VacationPolicyRule;
import com.inssoft.people.vacation.entity.VacationRequestEvent;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class VacationMapper {

    private VacationMapper() {
    }

    public static VacationRequestResponse toResponse(VacationRequest request) {
        Employee employee = request.getEmployee();
        User requestedBy = request.getRequestedBy();
        User leader = request.getLeader();

        String employeeName = employee == null ? null : EmployeeMapper.fullName(employee);
        String employeeNumber = employee == null ? null : employee.getEmployeeNumber();
        String leaderName = leader == null ? null : displayName(leader);
        String requestedByUsername = requestedBy != null
                ? requestedBy.getUsername()
                : request.getRequestedByUsername();

        return new VacationRequestResponse(
                request.getUuid(),
                employee == null ? null : employee.getUuid(),
                employeeName,
                employeeNumber,
                request.getStartDate(),
                request.getEndDate(),
                request.getDays() == null ? 0 : request.getDays(),
                request.getStatus(),
                requestedBy == null ? null : requestedBy.getUuid(),
                requestedByUsername,
                leader == null ? null : leader.getUuid(),
                leaderName,
                request.getLeaderDecision(),
                request.getLeaderDecidedAt(),
                request.getLeaderComment(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getAdminComment(),
                request.getCreatedAt(),
                request.getCancelledAt(),
                request.getCancelReason(),
                request.getPeriod() == null ? null : request.getPeriod().getUuid(),
                request.isLegacyBalance()
        );
    }

    public static VacationBalanceResponse toBalanceResponse(
            Employee employee,
            VacationPeriod period,
            BigDecimal available,
            BigDecimal used,
            BigDecimal held) {

        VacationPolicy policy = period.getPolicy();
        return new VacationBalanceResponse(
                employee.getUuid(),
                EmployeeMapper.fullName(employee),
                period.getUuid(),
                period.getPeriodStart(),
                period.getPeriodEnd(),
                period.getAnniversaryDate(),
                policy.getCode(),
                policy.getName(),
                scale(period.getEntitledDays()),
                scale(period.getOpeningBalance()),
                scale(available),
                scale(used),
                scale(held)
        );
    }

    public static VacationLedgerResponse toLedgerResponse(VacationLedger ledger) {
        return new VacationLedgerResponse(
                ledger.getUuid(),
                ledger.getMovementType().name(),
                scale(ledger.getDays()),
                scale(ledger.getSignedDays()),
                ledger.getReferenceType(),
                ledger.getReferenceId(),
                ledger.getNotes(),
                ledger.getCreatedAt()
        );
    }

    public static VacationPeriodResponse toPeriodResponse(VacationPeriod period) {
        return new VacationPeriodResponse(
                period.getUuid(),
                period.getPeriodStart(),
                period.getPeriodEnd(),
                period.getAnniversaryDate(),
                scale(period.getEntitledDays()),
                scale(period.getOpeningBalance()),
                period.getStatus().name(),
                period.getPolicy().getCode(),
                period.getPolicy().getName()
        );
    }

    public static VacationRequestEventResponse toEventResponse(VacationRequestEvent event) {
        return new VacationRequestEventResponse(
                event.getUuid(),
                event.getAction(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getActor() == null ? null : event.getActor().getUsername(),
                event.getComment(),
                event.getCreatedAt()
        );
    }

    public static VacationPolicyResponse toPolicyResponse(
            VacationPolicy policy,
            List<VacationPolicyRule> rules) {

        List<VacationPolicyRuleResponse> ruleResponses = rules == null
                ? List.of()
                : rules.stream()
                        .map(rule -> new VacationPolicyRuleResponse(
                                rule.getMinYears(),
                                rule.getMaxYears(),
                                scale(rule.getDays())))
                        .toList();

        return new VacationPolicyResponse(
                policy.getUuid(),
                policy.getCompany().getUuid(),
                policy.getCompany().getLegalName(),
                policy.getCode(),
                policy.getName(),
                policy.getDescription(),
                policy.getEffectiveFrom(),
                policy.getEffectiveTo(),
                policy.isDefaultPolicy(),
                policy.getAccrualType(),
                policy.getExpiryMonths(),
                policy.isAllowNegative(),
                ruleResponses
        );
    }

    private static String displayName(User user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getUsername();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }
}
