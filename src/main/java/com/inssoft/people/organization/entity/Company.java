package com.inssoft.people.organization.entity;

import com.inssoft.people.common.entity.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "org_company")
@Getter
@Setter
public class Company extends SoftDeleteEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "rfc", length = 13)
    private String rfc;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "America/Mexico_City";
}
