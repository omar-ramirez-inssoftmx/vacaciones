package com.mx.vacaciones.model;

import java.time.LocalDate;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
@Entity
@Table(name = "vacaciones")
@Data
public class Vacacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String usuario;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estatus; // PENDIENTE / APROBADA / RECHAZADA
}

