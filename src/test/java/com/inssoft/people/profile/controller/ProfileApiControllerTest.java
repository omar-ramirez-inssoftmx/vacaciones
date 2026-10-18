package com.inssoft.people.profile.controller;

import com.inssoft.people.common.exception.GlobalExceptionHandler;
import com.inssoft.people.profile.dto.ThemePreferenceResponse;
import com.inssoft.people.profile.service.UserPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileApiControllerTest {

    private UserPreferenceService preferenceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        preferenceService = mock(UserPreferenceService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileApiController(preferenceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTheme() throws Exception {
        when(preferenceService.getTheme("tester")).thenReturn(new ThemePreferenceResponse("LIGHT"));

        mockMvc.perform(get("/api/v1/profile/theme").principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("LIGHT"));
    }

    @Test
    void updatesTheme() throws Exception {
        when(preferenceService.updateTheme(eq("tester"), any())).thenReturn(new ThemePreferenceResponse("DARK"));

        mockMvc.perform(put("/api/v1/profile/theme")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("DARK"));
    }

    @Test
    void rejectsInvalidTheme() throws Exception {
        mockMvc.perform(put("/api/v1/profile/theme")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"PURPLE\"}"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken principal() {
        return new UsernamePasswordAuthenticationToken("tester", null, List.of());
    }
}
