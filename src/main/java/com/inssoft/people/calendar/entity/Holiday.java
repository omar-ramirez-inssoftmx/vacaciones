package com.inssoft.people.calendar.entity;

import com.inssoft.people.common.entity.SoftDeleteEntity;
import com.inssoft.people.organization.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "cal_holiday")
@Getter
@Setter
public class Holiday extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private HolidayType type;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "description", length = 500)
    private String description;

    @PrePersist
    @PreUpdate
    protected void syncYear() {
        if (holidayDate != null) {
            year = holidayDate.getYear();
        }
    }
}
