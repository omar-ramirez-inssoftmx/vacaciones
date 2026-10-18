package com.inssoft.people.vacation.entity;

import com.inssoft.people.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "vac_policy_rule")
@Getter
@Setter
public class VacationPolicyRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private VacationPolicy policy;

    @Column(name = "min_years", nullable = false)
    private int minYears;

    @Column(name = "max_years", nullable = false)
    private int maxYears;

    @Column(name = "days", nullable = false, precision = 5, scale = 2)
    private BigDecimal days;
}
