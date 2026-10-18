package com.inssoft.people.vacation.repository;

import com.inssoft.people.vacation.entity.VacationLedger;
import com.inssoft.people.vacation.entity.VacationMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface VacationLedgerRepository extends JpaRepository<VacationLedger, Long> {

    @Query("""
            SELECT COALESCE(SUM(l.signedDays), 0)
            FROM VacationLedger l
            WHERE l.period.id = :periodId
            """)
    BigDecimal sumSignedByPeriod(@Param("periodId") Long periodId);

    @Query("""
            SELECT COALESCE(SUM(l.days), 0)
            FROM VacationLedger l
            WHERE l.period.id = :periodId
              AND l.movementType = :movementType
            """)
    BigDecimal sumDaysByPeriodAndType(
            @Param("periodId") Long periodId,
            @Param("movementType") VacationMovementType movementType);

    @Query("""
            SELECT COALESCE(SUM(l.days), 0)
            FROM VacationLedger l
            WHERE l.period.id = :periodId
              AND l.movementType IN :movementTypes
            """)
    BigDecimal sumDaysByPeriodAndTypes(
            @Param("periodId") Long periodId,
            @Param("movementTypes") Collection<VacationMovementType> movementTypes);

    boolean existsByReferenceTypeAndReferenceIdAndMovementType(
            String referenceType, Long referenceId, VacationMovementType movementType);

    List<VacationLedger> findByEmployee_IdOrderByCreatedAtDesc(Long employeeId);

    List<VacationLedger> findByPeriod_IdOrderByCreatedAtAsc(Long periodId);
}
