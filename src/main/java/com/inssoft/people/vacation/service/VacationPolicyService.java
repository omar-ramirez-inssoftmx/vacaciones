package com.inssoft.people.vacation.service;

import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.vacation.dto.VacationPolicyResponse;
import com.inssoft.people.vacation.entity.VacationPolicy;
import com.inssoft.people.vacation.entity.VacationPolicyRule;
import com.inssoft.people.vacation.mapper.VacationMapper;
import com.inssoft.people.vacation.repository.VacationPolicyRepository;
import com.inssoft.people.vacation.repository.VacationPolicyRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class VacationPolicyService {

    private static final int[][] DEFAULT_RULES = {
            {0, 0, 0},
            {1, 1, 12},
            {2, 2, 14},
            {3, 3, 16},
            {4, 4, 18},
            {5, 5, 20},
            {6, 10, 22},
            {11, 15, 24},
            {16, 20, 26},
            {21, 25, 28},
            {26, 30, 30},
            {31, 35, 32},
            {36, 40, 34},
            {41, 45, 36},
            {46, 50, 38},
            {51, 999, 40}
    };

    private final VacationPolicyRepository policyRepository;
    private final VacationPolicyRuleRepository ruleRepository;

    public VacationPolicyService(
            VacationPolicyRepository policyRepository,
            VacationPolicyRuleRepository ruleRepository) {
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
    }

    @Transactional
    public VacationPolicy resolvePolicy(Company company) {
        List<VacationPolicy> policies = policyRepository.findActive(company.getId(), LocalDate.now());
        if (!policies.isEmpty()) {
            return policies.get(0);
        }
        return createDefaultPolicy(company);
    }

    private VacationPolicy createDefaultPolicy(Company company) {
        VacationPolicy policy = new VacationPolicy();
        policy.setCompany(company);
        policy.setCode("DEFAULT");
        policy.setName("Política de vacaciones");
        policy.setDescription("Política base por aniversario (LFT)");
        policy.setEffectiveFrom(LocalDate.now());
        policy.setDefaultPolicy(true);
        policy.setAccrualType("ANNIVERSARY");
        policy.setExpiryMonths(18);
        policy.setAllowNegative(false);
        policy.setEnabled(true);
        VacationPolicy saved = policyRepository.save(policy);

        for (int[] ruleValues : DEFAULT_RULES) {
            VacationPolicyRule rule = new VacationPolicyRule();
            rule.setPolicy(saved);
            rule.setMinYears(ruleValues[0]);
            rule.setMaxYears(ruleValues[1]);
            rule.setDays(BigDecimal.valueOf(ruleValues[2]));
            rule.setEnabled(true);
            ruleRepository.save(rule);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public BigDecimal entitledDays(VacationPolicy policy, int completedYears) {
        return ruleRepository.findApplicable(policy.getId(), completedYears).stream()
                .findFirst()
                .map(VacationPolicyRule::getDays)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<VacationPolicyResponse> listPolicies() {
        return policyRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(policy -> VacationMapper.toPolicyResponse(
                        policy,
                        ruleRepository.findByPolicy_IdAndEnabledTrueOrderByMinYearsAsc(policy.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public VacationPolicyResponse getPolicy(UUID uuid) {
        VacationPolicy policy = policyRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Política de vacaciones", uuid));
        return VacationMapper.toPolicyResponse(
                policy,
                ruleRepository.findByPolicy_IdAndEnabledTrueOrderByMinYearsAsc(policy.getId()));
    }
}
