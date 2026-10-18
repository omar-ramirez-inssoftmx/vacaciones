package com.inssoft.people.organization.repository;

import com.inssoft.people.organization.entity.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByUuidAndDeletedAtIsNull(UUID uuid);

    boolean existsByDepartmentIdAndCodeIgnoreCaseAndDeletedAtIsNull(Long departmentId, String code);

    boolean existsByDepartmentIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(Long departmentId, String code, UUID uuid);

    boolean existsByDepartmentIdAndDeletedAtIsNull(Long departmentId);

    @Query("""
            SELECT p FROM Position p JOIN p.department d JOIN d.branch b
            WHERE p.deletedAt IS NULL
              AND (:departmentUuid IS NULL OR d.uuid = :departmentUuid)
              AND (:enabled IS NULL OR p.enabled = :enabled)
              AND (:q IS NULL
                   OR LOWER(p.code) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Position> search(
            @Param("departmentUuid") UUID departmentUuid,
            @Param("q") String q,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}
