package com.inssoft.people.documents.repository;

import com.inssoft.people.documents.entity.DocumentVisibility;
import com.inssoft.people.documents.entity.EmployeeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    Optional<EmployeeDocument> findByUuidAndDeletedAtIsNull(UUID uuid);

    Page<EmployeeDocument> findByEmployee_IdAndDeletedAtIsNullAndVisibilityOrderByCreatedAtDesc(
            Long employeeId, DocumentVisibility visibility, Pageable pageable);

    @Query("""
            SELECT d FROM EmployeeDocument d
            WHERE d.employee.id = :employeeId
              AND d.deletedAt IS NULL
              AND d.visibility = :visibility
              AND d.type.employeeVisible = true
            """)
    Page<EmployeeDocument> findVisibleForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("visibility") DocumentVisibility visibility,
            Pageable pageable);

    Page<EmployeeDocument> findByEmployee_IdAndDeletedAtIsNull(Long employeeId, Pageable pageable);

    long countByEmployee_IdAndDeletedAtIsNull(Long employeeId);

    @Query("""
            SELECT d FROM EmployeeDocument d
            LEFT JOIN d.employee e
            WHERE d.deletedAt IS NULL
              AND d.company.id = :companyId
              AND (:employeeUuid IS NULL OR e.uuid = :employeeUuid)
              AND (:typeCode IS NULL OR LOWER(d.type.catalogItem.code) = LOWER(:typeCode))
              AND (:q IS NULL
                   OR LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.firstName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.lastName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<EmployeeDocument> search(
            @Param("companyId") Long companyId,
            @Param("employeeUuid") UUID employeeUuid,
            @Param("typeCode") String typeCode,
            @Param("q") String q,
            Pageable pageable);
}
