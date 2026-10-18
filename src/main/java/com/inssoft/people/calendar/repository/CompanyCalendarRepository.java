package com.inssoft.people.calendar.repository;

import com.inssoft.people.calendar.entity.CompanyCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyCalendarRepository extends JpaRepository<CompanyCalendar, Long> {

    Optional<CompanyCalendar> findByUuidAndDeletedAtIsNull(UUID uuid);

    Optional<CompanyCalendar> findByCompany_IdAndDeletedAtIsNull(Long companyId);

    List<CompanyCalendar> findByDeletedAtIsNull();
}
