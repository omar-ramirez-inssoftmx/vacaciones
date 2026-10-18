package com.inssoft.people.organization.controller;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.GlobalExceptionHandler;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.organization.dto.CompanyResponse;
import com.inssoft.people.organization.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyControllerTest {

    private CompanyService companyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        companyService = mock(CompanyService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CompanyController(companyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWrapsResponseInApiEnvelope() throws Exception {
        CompanyResponse company = company();
        PageResponse<CompanyResponse> page = new PageResponse<>(
                List.of(company), 0, 20, 1, 1, true, true);

        when(companyService.list(null, null, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].code").value("ACME"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void createValidatesRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void getReturns404Envelope() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(companyService.get(uuid))
                .thenThrow(new ResourceNotFoundException("Empresa", uuid));

        mockMvc.perform(get("/api/v1/companies/{uuid}", uuid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Empresa")));
    }

    @Test
    void createReturns201() throws Exception {
        when(companyService.create(any())).thenReturn(company());

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "ACME",
                                  "legalName": "Acme SA de CV",
                                  "timezone": "America/Mexico_City",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("ACME"));
    }

    private CompanyResponse company() {
        return new CompanyResponse(
                UUID.randomUUID(),
                "ACME",
                "Acme SA de CV",
                null,
                null,
                "America/Mexico_City",
                true,
                Instant.now(),
                null
        );
    }
}
