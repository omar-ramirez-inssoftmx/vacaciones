package com.inssoft.people.documents.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.security.AuthorizationService;
import com.inssoft.people.documents.dto.DocumentResponse;
import com.inssoft.people.documents.entity.DocumentType;
import com.inssoft.people.documents.entity.DocumentVisibility;
import com.inssoft.people.documents.entity.EmployeeDocument;
import com.inssoft.people.documents.repository.DocumentTypeRepository;
import com.inssoft.people.documents.repository.EmployeeDocumentRepository;
import com.inssoft.people.documents.storage.FileStorageService;
import com.inssoft.people.documents.storage.StoredFile;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.masterdata.entity.CatalogItem;
import com.inssoft.people.organization.entity.Company;
import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private EmployeeDocumentRepository documentRepository;

    @Mock
    private DocumentTypeRepository typeRepository;

    @Mock
    private FileStorageService storageService;

    @Mock
    private AuditService auditService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private DocumentService service;

    @Test
    void uploadsDocument() {
        Company company = company();
        User uploader = user(2L, "admin");
        Employee employee = employee(company);
        DocumentType type = type();
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrato.pdf", "application/pdf", "contenido".getBytes(StandardCharsets.UTF_8));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(typeRepository.findByCode("CONTRATO")).thenReturn(Optional.of(type));
        when(storageService.store(any(), anyString(), eq("application/pdf")))
                .thenReturn(new StoredFile(UUID.randomUUID(), "DATABASE", 9, "a".repeat(64)));
        when(documentRepository.save(any(EmployeeDocument.class))).thenAnswer(invocation -> {
            EmployeeDocument document = invocation.getArgument(0);
            document.setId(10L);
            document.setUuid(UUID.randomUUID());
            document.setCreatedAt(Instant.now());
            return document;
        });

        DocumentResponse response = service.upload(
                employee, "CONTRATO", "Contrato", "Descripción",
                DocumentVisibility.EMPLOYEE, null, file, uploader);

        assertThat(response.fileName()).isEqualTo("contrato.pdf");
        assertThat(response.typeCode()).isEqualTo("CONTRATO");
        assertThat(response.visibility()).isEqualTo(DocumentVisibility.EMPLOYEE);
        verify(auditService).record(eq("DOCUMENT_UPLOAD"), eq("EmployeeDocument"), any(), any());
    }

    @Test
    void rejectsFileLargerThanTypeLimit() {
        Company company = company();
        Employee employee = employee(company);
        DocumentType type = type();
        type.setMaxSizeMb(0);
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrato.pdf", "application/pdf", "contenido".getBytes(StandardCharsets.UTF_8));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(typeRepository.findByCode("CONTRATO")).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.upload(
                employee, "CONTRATO", "Contrato", null, null, null, file, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tamaño máximo");
    }

    @Test
    void rejectsDisallowedMimeType() {
        Company company = company();
        Employee employee = employee(company);
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrato.txt", "text/plain", "contenido".getBytes(StandardCharsets.UTF_8));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(typeRepository.findByCode("CONTRATO")).thenReturn(Optional.of(type()));

        assertThatThrownBy(() -> service.upload(
                employee, "CONTRATO", "Contrato", null, null, null, file, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no permitido");
    }

    @Test
    void requiresExpiryWhenTypeRequiresIt() {
        Company company = company();
        Employee employee = employee(company);
        DocumentType type = type();
        type.setRequiresExpiry(true);
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrato.pdf", "application/pdf", "contenido".getBytes(StandardCharsets.UTF_8));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(typeRepository.findByCode("CONTRATO")).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.upload(
                employee, "CONTRATO", "Contrato", null, null, null, file, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vencimiento");
    }

    @Test
    void downloadsOwnVisibleDocument() {
        Company company = company();
        User owner = user(2L, "omar");
        Employee employee = employee(company);
        employee.setUser(owner);
        EmployeeDocument document = document(company, employee, owner, DocumentVisibility.EMPLOYEE);

        when(documentRepository.findByUuidAndDeletedAtIsNull(document.getUuid()))
                .thenReturn(Optional.of(document));
        when(authorizationService.canViewDocument(owner, employee)).thenReturn(true);
        when(authorizationService.hasPermission("DOCUMENT_MANAGE")).thenReturn(false);
        when(storageService.open(document.getStorageKey()))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));

        DocumentService.Download download = service.download(
                document.getUuid(), owner, company, false);

        assertThat(download.fileName()).isEqualTo("contrato.pdf");
        assertThat(new String(download.content(), StandardCharsets.UTF_8)).isEqualTo("data");
        verify(auditService).record(eq("DOCUMENT_DOWNLOAD"), eq("EmployeeDocument"), any(), any());
    }

    @Test
    void forbidsOtherEmployeeDocument() {
        Company company = company();
        User owner = user(2L, "omar");
        User other = user(3L, "otro");
        Employee employee = employee(company);
        employee.setUser(owner);
        EmployeeDocument document = document(company, employee, owner, DocumentVisibility.EMPLOYEE);

        when(documentRepository.findByUuidAndDeletedAtIsNull(document.getUuid()))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.get(document.getUuid(), other, company, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No puedes");
    }

    @Test
    void deletesDocumentLogically() {
        Company company = company();
        EmployeeDocument document = document(company, employee(company), user(2L, "omar"),
                DocumentVisibility.EMPLOYEE);
        when(documentRepository.findByUuidAndDeletedAtIsNull(document.getUuid()))
                .thenReturn(Optional.of(document));
        when(documentRepository.save(any(EmployeeDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(document.getUuid(), user(2L, "admin"), company);

        assertThat(document.getDeletedAt()).isNotNull();
        assertThat(document.isEnabled()).isFalse();
        verify(auditService).record(eq("DOCUMENT_DELETE"), eq("EmployeeDocument"),
                any(), any(), any());
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
        employee.setId(1L);
        employee.setUuid(UUID.randomUUID());
        employee.setEmployeeNumber("EMP-001");
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");
        employee.setCompany(company);
        employee.setHireDate(LocalDate.of(2020, 1, 1));
        employee.setEnabled(true);
        return employee;
    }

    private User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUuid(UUID.randomUUID());
        user.setUsername(username);
        user.setName(username);
        user.setPassword("hash");
        user.setRole(Role.ROLE_ADMIN);
        user.setEnabled(true);
        return user;
    }

    private DocumentType type() {
        CatalogItem item = new CatalogItem();
        item.setId(5L);
        item.setUuid(UUID.randomUUID());
        item.setCode("CONTRATO");
        item.setName("Contrato");

        DocumentType type = new DocumentType();
        type.setId(7L);
        type.setUuid(UUID.randomUUID());
        type.setCatalogItem(item);
        type.setMaxSizeMb(1);
        type.setAllowedMime("application/pdf,image/jpeg,image/png");
        type.setEmployeeVisible(true);
        type.setEnabled(true);
        return type;
    }

    private EmployeeDocument document(
            Company company, Employee employee, User uploader, DocumentVisibility visibility) {
        EmployeeDocument document = new EmployeeDocument();
        document.setId(10L);
        document.setUuid(UUID.randomUUID());
        document.setCompany(company);
        document.setEmployee(employee);
        document.setType(type());
        document.setName("Contrato");
        document.setFileName("contrato.pdf");
        document.setMimeType("application/pdf");
        document.setSizeBytes(4);
        document.setSha256("a".repeat(64));
        document.setStorageKey(UUID.randomUUID());
        document.setStorageProvider("DATABASE");
        document.setVisibility(visibility);
        document.setUploadedBy(uploader);
        document.setCreatedAt(Instant.now());
        document.setEnabled(true);
        return document;
    }
}
