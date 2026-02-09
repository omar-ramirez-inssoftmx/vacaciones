package com.mx.vacaciones.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public List<User> findAll() {
		return userRepository.findAll();
	}

	public User findById(Long id) {
		return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found: " + id));
	}

	public User createUser(String name, String username, String email, String rawPassword, int vacationDaysAvailable, String role, LocalDate hireDate) {

		if (userRepository.existsByUsername(username)) {
			throw new RuntimeException("El Username ya existe");
		}
		User u = new User();
		u.setName(name);
		u.setUsername(username);
		u.setEmail(email);
		u.setPassword(passwordEncoder.encode(rawPassword));
		u.setVacationDaysAvailable(vacationDaysAvailable);
		u.setRole(Role.valueOf(role));
		u.setEnabled(true);
		u.setHireDate(hireDate);
		u.setVacationDaysEntitled(vacationDaysAvailable);
		return userRepository.save(u);
	}

	public User updateUser(Long id, String name, String email, String role, int vacationDaysAvailable,  boolean enabled) {

		User u = findById(id);
		u.setName(name);
		u.setRole(Role.valueOf(role));
		u.setVacationDaysAvailable(vacationDaysAvailable);
		u.setEmail(email);
		u.setEnabled(enabled);
		return userRepository.save(u);
	}

	public User resetPassword(Long id, String rawPassword) {
		User u = findById(id);
		u.setPassword(passwordEncoder.encode(rawPassword));
		return userRepository.save(u);
	}
}
