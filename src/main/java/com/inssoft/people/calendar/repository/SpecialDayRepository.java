package com.inssoft.people.calendar.repository;

import com.inssoft.people.calendar.entity.SpecialDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecialDayRepository extends JpaRepository<SpecialDay, Long> {

    Optional<SpecialDay> findByUuidAndDeletedAtIsNull(UUID uuid);

    @Query("""
            SELECT s FROM SpecialDay s
            WHERE s.deletedAt IS NULL
              AND s.enabled = true
              AND s.company.id = :companyId
              AND s.startDate <= :to
              AND s.endDate >= :from
            ORDER BY s.startDate ASC
            """)
    List<SpecialDay> findForCompanyBetween(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<SpecialDay> findByCompany_IdAndDeletedAtIsNullOrderByStartDateAsc(Long companyId);
}
