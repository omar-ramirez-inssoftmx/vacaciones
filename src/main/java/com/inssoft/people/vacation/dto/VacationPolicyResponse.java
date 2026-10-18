package com.inssoft.people.vacation.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VacationPolicyResponse(
        UUID uuid,
        UUID companyUuid,
        String companyName,
        String code,
        String name,
        String description,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean defaultPolicy,
        String accrualType,
        Integer expiryMonths,
        boolean allowNegative,
        List<VacationPolicyRuleResponse> rules
) {
}
