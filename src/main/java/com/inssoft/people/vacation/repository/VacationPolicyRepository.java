package com.inssoft.people.vacation.repository;

import com.inssoft.people.vacation.entity.VacationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VacationPolicyRepository extends JpaRepository<VacationPolicy, Long> {

    Optional<VacationPolicy> findByUuidAndDeletedAtIsNull(UUID uuid);

    List<VacationPolicy> findByDeletedAtIsNullOrderByNameAsc();

    @Query("""
            SELECT p FROM VacationPolicy p
            WHERE p.company.id = :companyId
              AND p.deletedAt IS NULL
              AND p.enabled = true
              AND p.effectiveFrom <= :today
              AND (p.effectiveTo IS NULL OR p.effectiveTo >= :today)
            ORDER BY p.defaultPolicy DESC, p.effectiveFrom DESC
            """)
    List<VacationPolicy> findActive(
            @Param("companyId") Long companyId,
            @Param("today") LocalDate today);
}
