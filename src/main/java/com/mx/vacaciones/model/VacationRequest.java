package com.mx.vacaciones.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "vacation_requests")
@Data
public class VacationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // user who requested
    @Column(nullable = false)
    private String username;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Column(nullable = false)
    private Integer days;
    // PENDING | APPROVED | REJECTED
    @Column(nullable = false)
    private String status;
    
    private String decidedBy;         // username del admin
    private LocalDateTime decidedAt;  // fecha/hora de decisión
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    private LocalDateTime createdAt;


    private String adminComment;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = "PENDIENTE";
    }
    
}