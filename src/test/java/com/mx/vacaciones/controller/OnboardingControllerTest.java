package com.mx.vacaciones.controller;

import com.inssoft.people.onboarding.service.OnboardingService;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

    @Mock
    private OnboardingService onboardingService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OnboardingController controller;

    @Test
    void completesTourForAuthenticatedUser() {
        User user = new User();
        user.setUsername("tester");
        user.setRole(Role.ROLE_COLABORADOR);
        user.setEnabled(true);

        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));

        var response = controller.complete(
                new UsernamePasswordAuthenticationToken("tester", null, List.of()));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(onboardingService).complete(user);
    }
}
