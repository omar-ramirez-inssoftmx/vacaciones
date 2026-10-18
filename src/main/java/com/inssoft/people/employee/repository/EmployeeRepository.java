package com.inssoft.people.employee.repository;

import com.inssoft.people.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUuidAndDeletedAtIsNull(UUID uuid);

    Optional<Employee> findByUser_UsernameAndDeletedAtIsNull(String username);

    boolean existsByUser_IdAndDeletedAtIsNull(Long userId);

    boolean existsByUser_IdAndUuidNotAndDeletedAtIsNull(Long userId, UUID uuid);

    boolean existsByCompany_IdAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(
            Long companyId, String employeeNumber);

    boolean existsByCompany_IdAndEmployeeNumberIgnoreCaseAndUuidNotAndDeletedAtIsNull(
            Long companyId, String employeeNumber, UUID uuid);

    boolean existsByCurpIgnoreCaseAndDeletedAtIsNull(String curp);

    boolean existsByCurpIgnoreCaseAndUuidNotAndDeletedAtIsNull(String curp, UUID uuid);

    boolean existsByRfcIgnoreCaseAndDeletedAtIsNull(String rfc);

    boolean existsByRfcIgnoreCaseAndUuidNotAndDeletedAtIsNull(String rfc, UUID uuid);

    boolean existsByNssAndDeletedAtIsNull(String nss);

    boolean existsByNssAndUuidNotAndDeletedAtIsNull(String nss, UUID uuid);

    boolean existsByCorporateEmailIgnoreCaseAndDeletedAtIsNull(String corporateEmail);

    boolean existsByCorporateEmailIgnoreCaseAndUuidNotAndDeletedAtIsNull(
            String corporateEmail, UUID uuid);

    boolean existsByManager_IdAndDeletedAtIsNull(Long managerId);

    @Query("""
            SELECT e FROM Employee e
            JOIN e.company c
            JOIN e.branch b
            JOIN e.department d
            JOIN e.position p
            JOIN e.status s
            LEFT JOIN e.workModality w
            WHERE e.deletedAt IS NULL
              AND (:companyUuid IS NULL OR c.uuid = :companyUuid)
              AND (:branchUuid IS NULL OR b.uuid = :branchUuid)
              AND (:departmentUuid IS NULL OR d.uuid = :departmentUuid)
              AND (:positionUuid IS NULL OR p.uuid = :positionUuid)
              AND (:statusCode IS NULL OR s.code = :statusCode)
              AND (:workModalityCode IS NULL OR w.code = :workModalityCode)
              AND (:enabled IS NULL OR e.enabled = :enabled)
              AND (:q IS NULL
                   OR LOWER(e.employeeNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.middleName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.secondLastName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(e.corporateEmail, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Employee> search(
            @Param("companyUuid") UUID companyUuid,
            @Param("branchUuid") UUID branchUuid,
            @Param("departmentUuid") UUID departmentUuid,
            @Param("positionUuid") UUID positionUuid,
            @Param("statusCode") String statusCode,
            @Param("workModalityCode") String workModalityCode,
            @Param("q") String q,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}
