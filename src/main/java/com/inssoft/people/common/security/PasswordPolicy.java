package com.inssoft.people.common.security;

import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.exception.BusinessException;

/**
 * Política mínima de contraseñas. Se aplica en cambio de contraseña y en
 * cualquier flujo que reciba una contraseña en texto plano.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La contraseña es obligatoria");
        }
        if (password.length() < MIN_LENGTH) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "La contraseña no puede exceder " + MAX_LENGTH + " caracteres");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "La contraseña debe incluir al menos una letra y un número");
        }
    }
}
