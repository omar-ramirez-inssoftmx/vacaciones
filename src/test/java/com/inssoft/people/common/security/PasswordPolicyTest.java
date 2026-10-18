package com.inssoft.people.common.security;

import com.inssoft.people.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsValidPassword() {
        assertThatCode(() -> PasswordPolicy.validate("Vacaciones2026"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("al menos 8");
    }

    @Test
    void rejectsPasswordWithoutNumber() {
        assertThatThrownBy(() -> PasswordPolicy.validate("sololetras"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("letra y un número");
    }

    @Test
    void rejectsPasswordWithoutLetter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("letra y un número");
    }
}
