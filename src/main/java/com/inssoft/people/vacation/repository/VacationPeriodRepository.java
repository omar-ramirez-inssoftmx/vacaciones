package com.inssoft.people.vacation.repository;

import com.inssoft.people.vacation.entity.VacationPeriod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VacationPeriodRepository extends JpaRepository<VacationPeriod, Long> {

    Optional<VacationPeriod> findByUuid(UUID uuid);

    Optional<VacationPeriod> findByEmployee_IdAndPeriodStart(Long employeeId, LocalDate periodStart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM VacationPeriod p
            WHERE p.employee.id = :employeeId
              AND p.periodStart = :periodStart
            """)
    Optional<VacationPeriod> findByEmployeeAndStartForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("periodStart") LocalDate periodStart);

    @Query("""
            SELECT p FROM VacationPeriod p
            WHERE p.employee.id = :employeeId
              AND :date BETWEEN p.periodStart AND p.periodEnd
            ORDER BY p.periodStart DESC
            """)
    List<VacationPeriod> findCurrent(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date);

    List<VacationPeriod> findByEmployee_IdOrderByPeriodStartDesc(Long employeeId);
}
