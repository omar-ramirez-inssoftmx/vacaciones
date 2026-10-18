package com.inssoft.people.profile.service;

import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserPreferenceService service;

    @Test
    void returnsStoredTheme() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user("LIGHT")));

        assertThat(service.getTheme("tester").theme()).isEqualTo("LIGHT");
    }

    @Test
    void returnsDarkWhenStoredValueIsInvalidOrEmpty() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user(null)));

        assertThat(service.getTheme("tester").theme()).isEqualTo("DARK");
    }

    @Test
    void updatesAndNormalizesTheme() {
        User user = user("DARK");
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));

        assertThat(service.updateTheme("tester", " light ").theme()).isEqualTo("LIGHT");
        assertThat(user.getThemePreference()).isEqualTo("LIGHT");
        verify(userRepository).save(user);
    }

    @Test
    void rejectsInvalidTheme() {
        assertThatThrownBy(() -> service.updateTheme("tester", "PURPLE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LIGHT o DARK");
    }

    @Test
    void failsWhenUserDoesNotExist() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTheme("nobody"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User user(String theme) {
        User user = new User();
        user.setId(1L);
        user.setUsername("tester");
        user.setPassword("hash");
        user.setThemePreference(theme);
        return user;
    }
}
