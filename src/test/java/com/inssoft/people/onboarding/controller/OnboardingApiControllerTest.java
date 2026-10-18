package com.inssoft.people.onboarding.controller;

import com.inssoft.people.common.exception.GlobalExceptionHandler;
import com.inssoft.people.onboarding.dto.OnboardingResponse;
import com.inssoft.people.onboarding.dto.TourStep;
import com.inssoft.people.onboarding.service.OnboardingService;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingApiControllerTest {

    private OnboardingService onboardingService;
    private UserRepository userRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        onboardingService = mock(OnboardingService.class);
        userRepository = mock(UserRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OnboardingApiController(onboardingService, userRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsStatus() throws Exception {
        User user = user();
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(onboardingService.statusFor(user)).thenReturn(new OnboardingResponse(
                false,
                List.of(new TourStep("a[href=\"/vacations/request\"]", "Solicitar", "Descripción"))));

        mockMvc.perform(get("/api/v1/onboarding").principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.steps[0].title").value("Solicitar"));
    }

    @Test
    void completesOnboarding() throws Exception {
        User user = user();
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/onboarding/complete").principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(onboardingService).complete(user);
    }

    private UsernamePasswordAuthenticationToken principal() {
        return new UsernamePasswordAuthenticationToken("tester", null, List.of());
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("tester");
        user.setName("Tester");
        user.setPassword("hash");
        user.setRole(Role.ROLE_COLABORADOR);
        user.setEnabled(true);
        return user;
    }
}
