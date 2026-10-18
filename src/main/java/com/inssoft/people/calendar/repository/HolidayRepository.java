package com.inssoft.people.calendar.repository;

import com.inssoft.people.calendar.entity.Holiday;
import com.inssoft.people.calendar.entity.HolidayType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Optional<Holiday> findByUuidAndDeletedAtIsNull(UUID uuid);

    @Query("""
            SELECT h FROM Holiday h
            LEFT JOIN h.company c
            WHERE h.deletedAt IS NULL
              AND h.enabled = true
              AND h.holidayDate BETWEEN :from AND :to
              AND (c.id = :companyId OR c IS NULL)
            ORDER BY h.holidayDate ASC
            """)
    List<Holiday> findForCompanyBetween(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<Holiday> findByCompany_IdAndDeletedAtIsNullOrderByHolidayDateAsc(Long companyId);

    List<Holiday> findByCompanyIsNullAndDeletedAtIsNullOrderByHolidayDateAsc();

    List<Holiday> findByCompanyIsNullAndYearAndTypeAndDeletedAtIsNullOrderByHolidayDateAsc(
            int year, HolidayType type);

    boolean existsByCompany_IdAndHolidayDateAndTypeAndDeletedAtIsNull(
            Long companyId, LocalDate holidayDate, HolidayType type);

    boolean existsByCompanyIsNullAndHolidayDateAndTypeAndDeletedAtIsNull(
            LocalDate holidayDate, HolidayType type);

    boolean existsByCompany_IdAndHolidayDateAndTypeAndUuidNotAndDeletedAtIsNull(
            Long companyId, LocalDate holidayDate, HolidayType type, UUID uuid);

    boolean existsByCompanyIsNullAndHolidayDateAndTypeAndUuidNotAndDeletedAtIsNull(
            LocalDate holidayDate, HolidayType type, UUID uuid);

    boolean existsByCompanyIsNullAndYearAndTypeAndDeletedAtIsNull(
            int year, HolidayType type);
}
