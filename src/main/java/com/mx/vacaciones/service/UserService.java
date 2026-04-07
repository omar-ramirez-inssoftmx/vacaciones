package com.mx.vacaciones.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Servicio encargado de la administración de usuarios.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.system.url}")
    private String systemUrl;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public User createUser(
            String name,
            String username,
            String email,
            int vacationDaysAvailable,
            String role,
            LocalDate hireDate) {

    	if (userRepository.existsByUsername(username)) {
    	    log.info("El Username ya existe: {}", username);
    	    throw new RuntimeException("USERNAME_DUPLICADO");
    	}

        String temporaryPassword = generateTemporaryPassword();

        User u = new User();
        u.setName(name);
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(temporaryPassword));
        u.setVacationDaysAvailable(vacationDaysAvailable);
        u.setRole(Role.valueOf(role));
        u.setEnabled(true);
        u.setHireDate(hireDate);
        u.setVacationDaysEntitled(vacationDaysAvailable);
        u.setTemporaryPassword(true);

        User savedUser = userRepository.save(u);

        if (savedUser.getEmail() != null && !savedUser.getEmail().trim().isEmpty()) {
            emailService.sendNewUserCredentialsEmail(
                    savedUser.getName(),
                    savedUser.getEmail(),
                    savedUser.getUsername(),
                    temporaryPassword,
                    systemUrl
            );
        }

        log.info("Usuario creado correctamente: {}", username);

        return savedUser;
    }

    public User updateUser(
            Long id,
            String name,
            String email,
            String role,
            int vacationDaysAvailable,
            boolean enabled) {

        User u = findById(id);
        u.setName(name);
        u.setRole(Role.valueOf(role));
        u.setVacationDaysAvailable(vacationDaysAvailable);
        u.setEmail(email);
        u.setEnabled(enabled);
        return userRepository.save(u);
    }

    public User resetPassword(Long id) {
        User u = findById(id);

        String temporaryPassword = generateTemporaryPassword();

        u.setPassword(passwordEncoder.encode(temporaryPassword));
        u.setTemporaryPassword(true);

        User savedUser = userRepository.save(u);

        if (savedUser.getEmail() != null && !savedUser.getEmail().trim().isEmpty()) {
            emailService.sendResetPasswordEmail(
                    savedUser.getName(),
                    savedUser.getEmail(),
                    savedUser.getUsername(),
                    temporaryPassword,
                    systemUrl
            );
        }

        log.info("Password temporal generado para usuario: {}", savedUser.getUsername());

        return savedUser;
    }

    public User changeOwnPassword(String username, String newPassword) {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        u.setPassword(passwordEncoder.encode(newPassword));
        u.setTemporaryPassword(false);

        return userRepository.save(u);
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            int index = random.nextInt(TEMP_PASSWORD_CHARS.length());
            password.append(TEMP_PASSWORD_CHARS.charAt(index));
        }

        return password.toString();
    }
}