package com.inssoft.people.vacation.entity;

import com.inssoft.people.common.entity.SoftDeleteEntity;
import com.inssoft.people.organization.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "vac_policy")
@Getter
@Setter
public class VacationPolicy extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "default_policy", nullable = false)
    private boolean defaultPolicy;

    @Column(name = "accrual_type", nullable = false, length = 30)
    private String accrualType = "ANNIVERSARY";

    @Column(name = "expiry_months")
    private Integer expiryMonths;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;
}
