package com.inssoft.people.employee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.GlobalExceptionHandler;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.employee.dto.EmployeeResponse;
import com.inssoft.people.employee.dto.EmployeeSummaryResponse;
import com.inssoft.people.employee.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmployeeControllerTest {

    private EmployeeService employeeService;
    private AccessGuard accessGuard;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        employeeService = mock(EmployeeService.class);
        accessGuard = mock(AccessGuard.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new EmployeeController(employeeService, accessGuard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void listWrapsResponse() throws Exception {
        EmployeeSummaryResponse summary = new EmployeeSummaryResponse(
                UUID.randomUUID(), "EMP-001", "Omar Ramírez", "omar@example.com",
                UUID.randomUUID(), "Acme", UUID.randomUUID(), "Matriz",
                UUID.randomUUID(), "Tecnología", UUID.randomUUID(), "Desarrollador",
                "ACTIVO", "Activo", true, Instant.now());

        when(employeeService.list(null, null, null, null, null, null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].employeeNumber").value("EMP-001"));
    }

    @Test
    void meReturnsAuthenticatedEmployee() throws Exception {
        when(employeeService.getMe("omar")).thenReturn(employeeResponse());

        mockMvc.perform(get("/api/v1/employees/me")
                        .principal(new UsernamePasswordAuthenticationToken("omar", null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeNumber").value("EMP-001"))
                .andExpect(jsonPath("$.data.curp").value("****RN09"));
    }

    @Test
    void getMasksSensitiveDataWithoutPermission() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(accessGuard.has("EMPLOYEE_VIEW_SENSITIVE")).thenReturn(false);
        when(employeeService.get(uuid, false)).thenReturn(employeeResponse());

        mockMvc.perform(get("/api/v1/employees/{uuid}", uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curp").value("****RN09"));

        verify(employeeService).get(uuid, false);
    }

    @Test
    void createValidatesRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createReturns201() throws Exception {
        when(employeeService.create(any())).thenReturn(employeeResponse());

        String company = UUID.randomUUID().toString();
        String branch = UUID.randomUUID().toString();
        String department = UUID.randomUUID().toString();
        String position = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyUuid": "%s",
                                  "branchUuid": "%s",
                                  "departmentUuid": "%s",
                                  "positionUuid": "%s",
                                  "employeeNumber": "EMP-001",
                                  "firstName": "Omar",
                                  "lastName": "Ramírez",
                                  "hireDate": "2020-01-01",
                                  "statusCode": "ACTIVO"
                                }
                                """.formatted(company, branch, department, position)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeNumber").value("EMP-001"));
    }

    private EmployeeResponse employeeResponse() {
        return new EmployeeResponse(
                UUID.randomUUID(),
                null, null,
                "EMP-001",
                "Omar", null, "Ramírez", null,
                "Omar Ramírez",
                LocalDate.of(1990, 1, 1),
                null, null, null, null,
                "****RN09", "****AB1", "****8901",
                "omar@example.com", null, "5551234567",
                null, null, null, null, null, "México",
                UUID.randomUUID(), "Acme",
                UUID.randomUUID(), "Matriz",
                UUID.randomUUID(), "Tecnología",
                UUID.randomUUID(), "Desarrollador",
                null, null,
                LocalDate.of(2020, 1, 1), null, null,
                "ACTIVO", "Activo", "PRESENCIAL", "Presencial",
                true, Instant.now(), null, List.of());
    }
}
