package com.mx.vacaciones.service;

import com.inssoft.people.auth.service.UserPermissionService;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPermissionService userPermissionService;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadsRoleAndDatabasePermissions() {
        User user = new User();
        user.setUsername("omar");
        user.setPassword("hash");
        user.setRole(Role.ROLE_ADMIN);
        user.setEnabled(true);

        when(userRepository.findByUsername("omar")).thenReturn(Optional.of(user));
        when(userPermissionService.permissionsFor("omar"))
                .thenReturn(Set.of("EMPLOYEE_VIEW", "ORG_MANAGE"));

        UserDetails details = service.loadUserByUsername("omar");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder(
                        "ROLE_ADMIN",
                        "PERM_EMPLOYEE_VIEW",
                        "PERM_ORG_MANAGE");
    }

    @Test
    void defaultsMissingRoleToEmployee() {
        User user = new User();
        user.setUsername("omar");
        user.setPassword("hash");
        user.setRole(null);
        user.setEnabled(true);

        when(userRepository.findByUsername("omar")).thenReturn(Optional.of(user));
        when(userPermissionService.permissionsFor("omar")).thenReturn(Set.of());

        UserDetails details = service.loadUserByUsername("omar");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_COLABORADOR");
    }
}
