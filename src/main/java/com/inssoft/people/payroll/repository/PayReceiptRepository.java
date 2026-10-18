package com.inssoft.people.payroll.repository;

import com.inssoft.people.payroll.entity.PayReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PayReceiptRepository extends JpaRepository<PayReceipt, Long> {

    @EntityGraph(attributePaths = {"employee", "period", "company", "uploadedBy"})
    Optional<PayReceipt> findByUuidAndDeletedAtIsNull(UUID uuid);

    @EntityGraph(attributePaths = {"period", "employee"})
    Page<PayReceipt> findByEmployee_IdAndDeletedAtIsNull(Long employeeId, Pageable pageable);

    boolean existsByEmployee_IdAndPeriod_IdAndDeletedAtIsNull(Long employeeId, Long periodId);

    @Query("""
            SELECT r FROM PayReceipt r
            WHERE r.deletedAt IS NULL
              AND (:companyId IS NULL OR r.company.id = :companyId)
              AND (:employeeUuid IS NULL OR r.employee.uuid = :employeeUuid)
              AND (:periodUuid IS NULL OR r.period.uuid = :periodUuid)
              AND (:q IS NULL
                   OR LOWER(r.fileName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(r.employee.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(r.employee.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(CONCAT(r.employee.firstName, ' ', r.employee.lastName))
                      LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<PayReceipt> search(
            @Param("companyId") Long companyId,
            @Param("employeeUuid") UUID employeeUuid,
            @Param("periodUuid") UUID periodUuid,
            @Param("q") String q,
            Pageable pageable);
}
