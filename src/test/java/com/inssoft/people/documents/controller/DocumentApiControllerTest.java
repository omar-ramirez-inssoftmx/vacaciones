package com.inssoft.people.documents.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.GlobalExceptionHandler;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.documents.dto.DocumentResponse;
import com.inssoft.people.documents.dto.DocumentTypeResponse;
import com.inssoft.people.documents.entity.DocumentVisibility;
import com.inssoft.people.documents.service.DocumentService;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentApiControllerTest {

    private DocumentService documentService;
    private EmployeeRepository employeeRepository;
    private CompanyRepository companyRepository;
    private UserRepository userRepository;
    private AccessGuard accessGuard;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        employeeRepository = mock(EmployeeRepository.class);
        companyRepository = mock(CompanyRepository.class);
        userRepository = mock(UserRepository.class);
        accessGuard = mock(AccessGuard.class);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DocumentApiController(
                        documentService, employeeRepository, companyRepository, userRepository, accessGuard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void listsTypes() throws Exception {
        when(documentService.types()).thenReturn(List.of(new DocumentTypeResponse(
                UUID.randomUUID(), "CONTRATO", "Contrato", false, true, 20,
                "application/pdf")));

        mockMvc.perform(get("/api/v1/documents/types").principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("CONTRATO"));
    }

    @Test
    void listsMyDocuments() throws Exception {
        Company company = company();
        Employee employee = employee(company);
        when(employeeRepository.findByUser_UsernameAndDeletedAtIsNull("omar"))
                .thenReturn(Optional.of(employee));
        when(documentService.listForEmployee(employee, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/documents/me").principal(principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].fileName").value("contrato.pdf"));
    }

    @Test
    void uploadsDocument() throws Exception {
        Company company = company();
        Employee employee = employee(company);
        User user = user();
        UUID employeeUuid = employee.getUuid();
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrato.pdf", "application/pdf", "contenido".getBytes(StandardCharsets.UTF_8));

        when(employeeRepository.findByUuidAndDeletedAtIsNull(employeeUuid)).thenReturn(Optional.of(employee));
        when(userRepository.findByUsername("omar")).thenReturn(Optional.of(user));
        when(documentService.upload(eq(employee), eq("CONTRATO"), eq("Contrato"), any(), any(), any(), any(), eq(user)))
                .thenReturn(response());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("employeeUuid", employeeUuid.toString())
                        .param("typeCode", "CONTRATO")
                        .param("name", "Contrato")
                        .principal(principal()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("contrato.pdf"));
    }

    @Test
    void downloadsDocument() throws Exception {
        Company company = company();
        Employee employee = employee(company);
        User user = user();
        UUID uuid = UUID.randomUUID();

        when(employeeRepository.findByUser_UsernameAndDeletedAtIsNull("omar"))
                .thenReturn(Optional.of(employee));
        when(accessGuard.has("DOCUMENT_VIEW")).thenReturn(false);
        when(userRepository.findByUsername("omar")).thenReturn(Optional.of(user));
        when(documentService.download(uuid, user, company, false))
                .thenReturn(new DocumentService.Download(
                        "data".getBytes(StandardCharsets.UTF_8), "contrato.pdf", "application/pdf", 4));

        mockMvc.perform(get("/api/v1/documents/{uuid}/download", uuid).principal(principal()))
                .andExpect(status().isOk())
                .andExpect(content().bytes("data".getBytes(StandardCharsets.UTF_8)))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("contrato.pdf")));
    }

    @Test
    void rejectsUploadWithoutFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents")
                        .param("employeeUuid", UUID.randomUUID().toString())
                        .param("typeCode", "CONTRATO")
                        .principal(principal()))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken principal() {
        return new UsernamePasswordAuthenticationToken("omar", null, List.of());
    }

    private Company company() {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("INSSOFT");
        company.setLegalName("INSSOFT");
        company.setEnabled(true);
        return company;
    }

    private Employee employee(Company company) {
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setUuid(UUID.randomUUID());
        employee.setCompany(company);
        employee.setEmployeeNumber("EMP-001");
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");
        employee.setEnabled(true);
        return employee;
    }

    private User user() {
        User user = new User();
        user.setId(2L);
        user.setUuid(UUID.randomUUID());
        user.setUsername("omar");
        user.setName("Omar");
        user.setPassword("hash");
        user.setEnabled(true);
        return user;
    }

    private DocumentResponse response() {
        return new DocumentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Omar Ramírez",
                "CONTRATO", "Contrato", "Contrato", null,
                "contrato.pdf", "application/pdf", 4, "a".repeat(64),
                DocumentVisibility.EMPLOYEE, null, "omar", Instant.now(), true);
    }
}
