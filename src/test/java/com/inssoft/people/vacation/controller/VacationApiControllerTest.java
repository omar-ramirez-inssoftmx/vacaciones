package com.inssoft.people.vacation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inssoft.people.common.exception.GlobalExceptionHandler;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.common.security.AuthorizationService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.vacation.dto.VacationBalanceResponse;
import com.inssoft.people.vacation.dto.VacationRequestResponse;
import com.inssoft.people.vacation.service.VacationAdminService;
import com.inssoft.people.vacation.service.VacationPolicyService;
import com.inssoft.people.vacation.service.VacationQueryService;
import com.inssoft.people.vacation.service.VacationWorkflowService;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VacationApiControllerTest {

    private VacationQueryService queryService;
    private VacationWorkflowService workflowService;
    private VacationAdminService adminService;
    private VacationPolicyService policyService;
    private UserRepository userRepository;
    private AccessGuard accessGuard;
    private AuthorizationService authorizationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(VacationQueryService.class);
        workflowService = mock(VacationWorkflowService.class);
        adminService = mock(VacationAdminService.class);
        policyService = mock(VacationPolicyService.class);
        userRepository = mock(UserRepository.class);
        accessGuard = mock(AccessGuard.class);
        authorizationService = mock(AuthorizationService.class);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new VacationApiController(
                        queryService, workflowService, adminService, policyService,
                        userRepository, accessGuard, authorizationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void returnsBalance() throws Exception {
        when(queryService.myBalance("omar")).thenReturn(balance());

        mockMvc.perform(get("/api/v1/vacations/balance")
                        .principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(10.0));
    }

    @Test
    void createsSelfRequest() throws Exception {
        User user = user();
        when(userRepository.findByUsername("omar")).thenReturn(Optional.of(user));
        when(queryService.requireEmployee("omar")).thenReturn(new Employee());

        VacationRequest request = new VacationRequest();
        request.setUuid(UUID.randomUUID());
        request.setId(1L);
        when(workflowService.submit(any(), any(), any(), any(), any(), any())).thenReturn(request);
        when(queryService.getScoped(eq(request.getUuid()), any(User.class)))
                .thenReturn(response(request.getUuid()));

        mockMvc.perform(post("/api/v1/vacations")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-08-03",
                                  "endDate": "2026-08-07"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_ADMIN"));
    }

    @Test
    void validatesCreateBody() throws Exception {
        mockMvc.perform(post("/api/v1/vacations")
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void getsRequestForOwner() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(userRepository.findByUsername("omar")).thenReturn(Optional.of(user()));
        when(queryService.getScoped(eq(uuid), any(User.class))).thenReturn(response(uuid));

        mockMvc.perform(get("/api/v1/vacations/{uuid}", uuid)
                        .principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uuid").value(uuid.toString()));
    }

    @Test
    void approvesRequest() throws Exception {
        UUID uuid = UUID.randomUUID();
        User user = user();
        VacationRequest request = new VacationRequest();
        request.setId(1L);
        request.setUuid(uuid);

        when(userRepository.findByUsername("omar")).thenReturn(Optional.of(user));
        when(workflowService.approve(uuid, user, false)).thenReturn(request);
        when(queryService.getScoped(eq(uuid), any(User.class))).thenReturn(response(uuid));

        mockMvc.perform(post("/api/v1/vacations/{uuid}/approve", uuid)
                        .principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Solicitud aprobada"));
    }

    private UsernamePasswordAuthenticationToken principal() {
        return new UsernamePasswordAuthenticationToken("omar", null, List.of());
    }

    private User user() {
        User user = new User();
        user.setId(2L);
        user.setUuid(UUID.randomUUID());
        user.setUsername("omar");
        user.setName("Omar");
        user.setPassword("hash");
        user.setRole(Role.ROLE_COLABORADOR);
        user.setEnabled(true);
        return user;
    }

    private VacationBalanceResponse balance() {
        return new VacationBalanceResponse(
                UUID.randomUUID(), "Omar Ramírez", UUID.randomUUID(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1), "DEFAULT", "Política",
                BigDecimal.valueOf(12), BigDecimal.ZERO,
                BigDecimal.valueOf(10), BigDecimal.valueOf(2), BigDecimal.ZERO);
    }

    private VacationRequestResponse response(UUID uuid) {
        return new VacationRequestResponse(
                uuid, UUID.randomUUID(), "Omar Ramírez", "EMP-001",
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), 5,
                "PENDING_ADMIN", null, "omar", null, null,
                null, null, null, null, null, null,
                LocalDateTime.now(), null, null, null, false);
    }
}
