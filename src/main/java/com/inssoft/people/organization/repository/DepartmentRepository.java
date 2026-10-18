package com.inssoft.people.organization.repository;

import com.inssoft.people.organization.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByUuidAndDeletedAtIsNull(UUID uuid);

    boolean existsByBranchIdAndCodeIgnoreCaseAndDeletedAtIsNull(Long branchId, String code);

    boolean existsByBranchIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(Long branchId, String code, UUID uuid);

    boolean existsByBranchIdAndDeletedAtIsNull(Long branchId);

    boolean existsByParentIdAndDeletedAtIsNull(Long parentId);

    @Query("""
            SELECT d FROM Department d JOIN d.branch b JOIN b.company c
            WHERE d.deletedAt IS NULL
              AND (:branchUuid IS NULL OR b.uuid = :branchUuid)
              AND (:enabled IS NULL OR d.enabled = :enabled)
              AND (:q IS NULL
                   OR LOWER(d.code) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Department> search(
            @Param("branchUuid") UUID branchUuid,
            @Param("q") String q,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}
