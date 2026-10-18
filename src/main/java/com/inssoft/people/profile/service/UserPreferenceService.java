package com.inssoft.people.profile.service;

import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.profile.dto.ThemePreferenceResponse;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserPreferenceService {

    public static final String DEFAULT_THEME = "DARK";

    private final UserRepository userRepository;

    public UserPreferenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ThemePreferenceResponse getTheme(String username) {
        User user = findUser(username);
        return new ThemePreferenceResponse(normalizeOrDefault(user.getThemePreference()));
    }

    @Transactional
    public ThemePreferenceResponse updateTheme(String username, String theme) {
        String normalized = normalize(theme);
        User user = findUser(username);
        user.setThemePreference(normalized);
        userRepository.save(user);
        return new ThemePreferenceResponse(normalized);
    }

    public String normalizeOrDefault(String theme) {
        try {
            return normalize(theme);
        } catch (BusinessException ex) {
            return DEFAULT_THEME;
        }
    }

    private String normalize(String theme) {
        if (theme == null || theme.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El tema es obligatorio");
        }
        String value = theme.trim().toUpperCase(Locale.ROOT);
        if (!"LIGHT".equals(value) && !"DARK".equals(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El tema debe ser LIGHT o DARK");
        }
        return value;
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", username));
    }
}
