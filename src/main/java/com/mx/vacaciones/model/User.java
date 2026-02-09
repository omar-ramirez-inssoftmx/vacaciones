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

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name")
	private String name;
	
	@Column(unique = true, nullable = false)
	private String username;

	@Column(nullable = false)
	private String password;

	@Column(name = "email", length = 150, unique = true)
	private String email;

	@Column(name = "vacation_days_available", nullable = false)
	private int vacationDaysAvailable = 12;

	public void deductVacationDays(int days) {
		if (days <= 0)
			throw new IllegalArgumentException("Días inválidos");
		if (vacationDaysAvailable < days)
			throw new IllegalStateException("Saldo insuficiente");
		vacationDaysAvailable -= days;
	}

	@Enumerated(EnumType.STRING)
	private Role role;

	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;
	
	@Column(nullable = false)
	private LocalDate hireDate; // fecha de ingreso

	@Column(nullable = false)
	private Integer vacationDaysEntitled = 0; // opcional pero útil para dashboard


}