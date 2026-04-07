package com.mx.vacaciones.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Entidad que representa a un usuario del sistema.
 *
 * <p>
 * Contiene información de acceso, datos generales y saldo de vacaciones.
 * </p>
 *
 * <p>
 * Se agregó el campo {@code temporaryPassword} para identificar si el usuario
 * tiene una contraseña temporal y debe cambiarla al iniciar sesión.
 * </p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    /**
     * Identificador único del usuario.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre completo del usuario.
     */
    @Column(name = "name")
    private String name;

    /**
     * Nombre de usuario único para iniciar sesión.
     */
    @Column(unique = true, nullable = false)
    private String username;

    /**
     * Contraseña encriptada del usuario.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Correo electrónico del usuario.
     */
    @Column(name = "email", length = 150, unique = true)
    private String email;

    /**
     * Días de vacaciones disponibles del usuario.
     */
    @Column(name = "vacation_days_available", nullable = false)
    private int vacationDaysAvailable = 12;

    /**
     * Rol del usuario dentro del sistema.
     */
    @Enumerated(EnumType.STRING)
    private Role role;

    /**
     * Indica si el usuario está habilitado para usar el sistema.
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * Fecha de ingreso del usuario a la empresa.
     */
    @Column(nullable = false)
    private LocalDate hireDate;

    /**
     * Días de vacaciones que le corresponden al usuario según su antigüedad.
     */
    @Column(nullable = false)
    private Integer vacationDaysEntitled = 0;

    /**
     * Indica si la contraseña actual es temporal.
     *
     * <p>
     * Cuando este valor es {@code true}, el usuario deberá cambiar su contraseña
     * al iniciar sesión antes de continuar usando el sistema.
     * </p>
     */
    @Column(name = "temporary_password", nullable = false)
    private Boolean temporaryPassword = false;

    /**
     * Descuenta días de vacaciones del saldo disponible.
     *
     * @param days número de días a descontar
     * @throws IllegalArgumentException si los días son menores o iguales a cero
     * @throws IllegalStateException si el usuario no cuenta con saldo suficiente
     */
    public void deductVacationDays(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("Días inválidos");
        }
        if (vacationDaysAvailable < days) {
            throw new IllegalStateException("Saldo insuficiente");
        }
        vacationDaysAvailable -= days;
    }
}