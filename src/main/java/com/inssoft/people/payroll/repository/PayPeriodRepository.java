package com.inssoft.people.payroll.repository;

import com.inssoft.people.payroll.entity.PayPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayPeriodRepository extends JpaRepository<PayPeriod, Long> {

    Optional<PayPeriod> findByUuidAndDeletedAtIsNull(UUID uuid);

    List<PayPeriod> findByCompany_IdAndDeletedAtIsNullOrderByPeriodYearDescPeriodMonthDesc(Long companyId);

    boolean existsByCompany_IdAndPeriodYearAndPeriodMonthAndPeriodTypeAndDeletedAtIsNull(
            Long companyId, Integer periodYear, Integer periodMonth, String periodType);
}
