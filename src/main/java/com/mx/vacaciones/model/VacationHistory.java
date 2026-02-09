package com.mx.vacaciones.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
public class VacationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fecha;
    private String accion;
    private String comentario;

    @ManyToOne
    private VacationRequest vacation;

    @ManyToOne
    private User admin;
}