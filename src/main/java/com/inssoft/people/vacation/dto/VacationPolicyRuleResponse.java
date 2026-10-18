package com.inssoft.people.vacation.dto;

import java.math.BigDecimal;

public record VacationPolicyRuleResponse(
        int minYears,
        int maxYears,
        BigDecimal days
) {
}
