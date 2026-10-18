package com.inssoft.people.organization.repository;

import com.inssoft.people.organization.entity.Branch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByUuidAndDeletedAtIsNull(UUID uuid);

    boolean existsByCompanyIdAndCodeIgnoreCaseAndDeletedAtIsNull(Long companyId, String code);

    boolean existsByCompanyIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(Long companyId, String code, UUID uuid);

    boolean existsByCompanyIdAndDeletedAtIsNull(Long companyId);

    @Query("""
            SELECT b FROM Branch b JOIN b.company c
            WHERE b.deletedAt IS NULL
              AND (:companyUuid IS NULL OR c.uuid = :companyUuid)
              AND (:enabled IS NULL OR b.enabled = :enabled)
              AND (:q IS NULL
                   OR LOWER(b.code) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Branch> search(
            @Param("companyUuid") UUID companyUuid,
            @Param("q") String q,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}
