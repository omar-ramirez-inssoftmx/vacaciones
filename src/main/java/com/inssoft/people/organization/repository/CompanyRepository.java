package com.inssoft.people.organization.repository;

import com.inssoft.people.organization.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByUuidAndDeletedAtIsNull(UUID uuid);

    Optional<Company> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(String code, UUID uuid);

    @Query("""
            SELECT c FROM Company c
            WHERE c.deletedAt IS NULL
              AND (:enabled IS NULL OR c.enabled = :enabled)
              AND (:q IS NULL
                   OR LOWER(c.code) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(c.legalName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(c.tradeName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Company> search(
            @Param("q") String q,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}
