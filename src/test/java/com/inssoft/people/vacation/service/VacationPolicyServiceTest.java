package com.inssoft.people.vacation.service;

import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.vacation.dto.VacationPolicyResponse;
import com.inssoft.people.vacation.entity.VacationPolicy;
import com.inssoft.people.vacation.entity.VacationPolicyRule;
import com.inssoft.people.vacation.repository.VacationPolicyRepository;
import com.inssoft.people.vacation.repository.VacationPolicyRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationPolicyServiceTest {

    @Mock
    private VacationPolicyRepository policyRepository;

    @Mock
    private VacationPolicyRuleRepository ruleRepository;

    @InjectMocks
    private VacationPolicyService service;

    @Test
    void resolvesActiveDefaultPolicy() {
        VacationPolicy policy = policy();
        when(policyRepository.findActive(any(), any())).thenReturn(List.of(policy));

        assertThat(service.resolvePolicy(policy.getCompany())).isSameAs(policy);
    }

    @Test
    void createsDefaultPolicyWhenMissing() {
        VacationPolicy policy = policy();
        when(policyRepository.findActive(any(), any())).thenReturn(List.of());
        when(policyRepository.save(any(VacationPolicy.class))).thenAnswer(invocation -> {
            VacationPolicy saved = invocation.getArgument(0);
            saved.setId(20L);
            saved.setUuid(UUID.randomUUID());
            return saved;
        });
        when(ruleRepository.save(any(VacationPolicyRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationPolicy created = service.resolvePolicy(policy.getCompany());

        assertThat(created.getCode()).isEqualTo("DEFAULT");
        org.mockito.Mockito.verify(ruleRepository, org.mockito.Mockito.times(16))
                .save(any(VacationPolicyRule.class));
    }

    @Test
    void returnsEntitledDaysByYears() {
        VacationPolicy policy = policy();
        VacationPolicyRule rule = rule(policy, 6, 10, BigDecimal.valueOf(22));
        when(ruleRepository.findApplicable(policy.getId(), 7)).thenReturn(List.of(rule));

        assertThat(service.entitledDays(policy, 7)).isEqualByComparingTo("22");
    }

    @Test
    void returnsZeroWhenNoRuleMatches() {
        VacationPolicy policy = policy();
        when(ruleRepository.findApplicable(policy.getId(), 99)).thenReturn(List.of());

        assertThat(service.entitledDays(policy, 99)).isEqualByComparingTo("0");
    }

    @Test
    void listsPoliciesWithRules() {
        VacationPolicy policy = policy();
        VacationPolicyRule rule = rule(policy, 1, 1, BigDecimal.valueOf(12));
        when(policyRepository.findByDeletedAtIsNullOrderByNameAsc()).thenReturn(List.of(policy));
        when(ruleRepository.findByPolicy_IdAndEnabledTrueOrderByMinYearsAsc(policy.getId()))
                .thenReturn(List.of(rule));

        List<VacationPolicyResponse> policies = service.listPolicies();

        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).rules()).hasSize(1);
        assertThat(policies.get(0).rules().get(0).days()).isEqualByComparingTo("12");
    }

    @Test
    void getsPolicyByUuid() {
        VacationPolicy policy = policy();
        when(policyRepository.findByUuidAndDeletedAtIsNull(policy.getUuid()))
                .thenReturn(Optional.of(policy));
        when(ruleRepository.findByPolicy_IdAndEnabledTrueOrderByMinYearsAsc(policy.getId()))
                .thenReturn(List.of());

        VacationPolicyResponse response = service.getPolicy(policy.getUuid());

        assertThat(response.code()).isEqualTo("DEFAULT");
    }

    @Test
    void policyNotFound() {
        UUID uuid = UUID.randomUUID();
        when(policyRepository.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPolicy(uuid))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private VacationPolicy policy() {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("INSSOFT");
        company.setLegalName("INSSOFT");
        company.setEnabled(true);

        VacationPolicy policy = new VacationPolicy();
        policy.setId(10L);
        policy.setUuid(UUID.randomUUID());
        policy.setCompany(company);
        policy.setCode("DEFAULT");
        policy.setName("Política de vacaciones");
        policy.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        policy.setDefaultPolicy(true);
        policy.setEnabled(true);
        policy.setAllowNegative(false);
        return policy;
    }

    private VacationPolicyRule rule(VacationPolicy policy, int min, int max, BigDecimal days) {
        VacationPolicyRule rule = new VacationPolicyRule();
        rule.setId(1L);
        rule.setUuid(UUID.randomUUID());
        rule.setPolicy(policy);
        rule.setMinYears(min);
        rule.setMaxYears(max);
        rule.setDays(days);
        rule.setEnabled(true);
        return rule;
    }
}
