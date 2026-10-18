package com.mx.vacaciones.service;

import com.inssoft.people.common.exception.BusinessException;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService service;

    @Test
    void cannotDeactivateLastActiveAdmin() {
        User admin = user(Role.ROLE_ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleInAndEnabledTrue(List.of(Role.ROLE_ADMIN))).thenReturn(1L);

        assertThatThrownBy(() -> service.updateUser(
                1L, "Admin", "admin@example.com", "ROLE_COLABORADOR",
                10, LocalDate.now(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("al menos un administrador activo");
    }

    @Test
    void canDemoteAdminWhenAnotherActiveAdminExists() {
        User admin = user(Role.ROLE_ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleInAndEnabledTrue(List.of(Role.ROLE_ADMIN))).thenReturn(2L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.updateUser(
                1L, "Admin", "admin@example.com", "ROLE_COLABORADOR",
                10, LocalDate.now(), true);

        assertThat(updated.getRole()).isEqualTo(Role.ROLE_COLABORADOR);
    }

    private User user(Role role) {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setName("Admin");
        user.setPassword("hash");
        user.setRole(role);
        user.setEnabled(true);
        user.setHireDate(LocalDate.now());
        return user;
    }
}
