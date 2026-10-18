package com.inssoft.people.calendar.entity;

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

@Entity
@Table(name = "cal_calendar")
@Getter
@Setter
public class CompanyCalendar extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "America/Mexico_City";

    @Column(name = "workweek", nullable = false, length = 50)
    private String workweek = "MON,TUE,WED,THU,FRI";
}
