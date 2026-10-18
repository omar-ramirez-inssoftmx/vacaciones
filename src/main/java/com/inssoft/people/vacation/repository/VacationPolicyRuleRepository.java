package com.inssoft.people.vacation.repository;

import com.inssoft.people.vacation.entity.VacationPolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VacationPolicyRuleRepository extends JpaRepository<VacationPolicyRule, Long> {

    List<VacationPolicyRule> findByPolicy_IdAndEnabledTrueOrderByMinYearsAsc(Long policyId);

    @Query("""
            SELECT r FROM VacationPolicyRule r
            WHERE r.policy.id = :policyId
              AND r.enabled = true
              AND :years BETWEEN r.minYears AND r.maxYears
            ORDER BY r.minYears DESC
            """)
    List<VacationPolicyRule> findApplicable(
            @Param("policyId") Long policyId,
            @Param("years") int years);
}
