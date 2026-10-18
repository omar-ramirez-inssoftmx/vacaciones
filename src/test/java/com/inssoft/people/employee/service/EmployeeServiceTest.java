package com.inssoft.people.employee.service;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.employee.dto.EmergencyContactRequest;
import com.inssoft.people.employee.dto.EmergencyContactResponse;
import com.inssoft.people.employee.dto.EmployeeRequest;
import com.inssoft.people.employee.dto.EmployeeResponse;
import com.inssoft.people.employee.dto.EmployeeSummaryResponse;
import com.inssoft.people.employee.dto.TerminationRequest;
import com.inssoft.people.employee.entity.EmergencyContact;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmergencyContactRepository;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.employee.validation.EmployeeValidator;
import com.inssoft.people.masterdata.entity.CatalogItem;
import com.inssoft.people.masterdata.repository.CatalogItemRepository;
import com.inssoft.people.organization.entity.Branch;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.entity.Department;
import com.inssoft.people.organization.entity.Position;
import com.inssoft.people.organization.repository.BranchRepository;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.inssoft.people.organization.repository.DepartmentRepository;
import com.inssoft.people.organization.repository.PositionRepository;
import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmergencyContactRepository contactRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private CatalogItemRepository catalogItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private EmployeeValidator employeeValidator = new EmployeeValidator();

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void createsEmployee() {
        Organization org = organization();
        CatalogItem active = catalogItem(10L, "ACTIVO", "Activo");
        CatalogItem presencial = catalogItem(11L, "PRESENCIAL", "Presencial");
        stubOrganization(org);
        when(catalogItemRepository.findByCatalogCodeAndItemCode("EMPLOYMENT_STATUS", "ACTIVO"))
                .thenReturn(Optional.of(active));
        when(catalogItemRepository.findByCatalogCodeAndItemCode("WORK_MODALITY", "PRESENCIAL"))
                .thenReturn(Optional.of(presencial));
        when(employeeRepository.existsByCompany_IdAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(
                1L, "EMP-001")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(100L);
            employee.setUuid(UUID.randomUUID());
            employee.setCreatedAt(Instant.now());
            return employee;
        });
        when(contactRepository.findActiveByEmployeeId(100L)).thenReturn(List.of());

        EmployeeResponse response = employeeService.create(request(org));

        assertThat(response.employeeNumber()).isEqualTo("EMP-001");
        assertThat(response.statusCode()).isEqualTo("ACTIVO");
        assertThat(response.companyUuid()).isEqualTo(org.company().getUuid());
        verify(auditService).record(eq("EMPLOYEE_CREATE"), eq("Employee"), anyString(), any());
    }

    @Test
    void rejectsDuplicateEmployeeNumber() {
        Organization org = organization();
        CatalogItem active = catalogItem(10L, "ACTIVO", "Activo");
        CatalogItem presencial = catalogItem(11L, "PRESENCIAL", "Presencial");
        stubOrganization(org);
        when(catalogItemRepository.findByCatalogCodeAndItemCode("EMPLOYMENT_STATUS", "ACTIVO"))
                .thenReturn(Optional.of(active));
        when(catalogItemRepository.findByCatalogCodeAndItemCode("WORK_MODALITY", "PRESENCIAL"))
                .thenReturn(Optional.of(presencial));
        when(employeeRepository.existsByCompany_IdAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(
                1L, "EMP-001")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(request(org)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EMP-001");
    }

    @Test
    void getMeReturnsNotFoundWhenUserHasNoEmployee() {
        when(employeeRepository.findByUser_UsernameAndDeletedAtIsNull("omar"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getMe("omar"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void terminatesEmployeeAndDisablesUser() {
        Organization org = organization();
        Employee employee = employeeEntity(100L, org, catalogItem(10L, "ACTIVO", "Activo"));
        CatalogItem terminated = catalogItem(12L, "BAJA", "Baja");

        when(employeeRepository.findByUuidAndDeletedAtIsNull(employee.getUuid()))
                .thenReturn(Optional.of(employee));
        when(catalogItemRepository.findByCatalogCodeAndItemCode("EMPLOYMENT_STATUS", "BAJA"))
                .thenReturn(Optional.of(terminated));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(contactRepository.findActiveByEmployeeId(100L)).thenReturn(List.of());

        EmployeeResponse response = employeeService.terminate(
                employee.getUuid(),
                new TerminationRequest(LocalDate.of(2024, 1, 1), "Renuncia", null));

        assertThat(response.statusCode()).isEqualTo("BAJA");
        assertThat(response.enabled()).isFalse();
        assertThat(response.terminationDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        verify(auditService).record(eq("EMPLOYEE_TERMINATE"), eq("Employee"), anyString(), any(), any());
    }

    @Test
    void firstEmergencyContactBecomesPrimary() {
        Organization org = organization();
        Employee employee = employeeEntity(100L, org, catalogItem(10L, "ACTIVO", "Activo"));

        when(employeeRepository.findByUuidAndDeletedAtIsNull(employee.getUuid()))
                .thenReturn(Optional.of(employee));
        when(contactRepository.existsByEmployee_IdAndDeletedAtIsNull(100L)).thenReturn(false);
        when(contactRepository.findActiveByEmployeeId(100L)).thenReturn(List.of());
        when(contactRepository.save(any(EmergencyContact.class))).thenAnswer(invocation -> {
            EmergencyContact contact = invocation.getArgument(0);
            contact.setUuid(UUID.randomUUID());
            contact.setCreatedAt(Instant.now());
            return contact;
        });

        EmergencyContactResponse response = employeeService.addContact(
                employee.getUuid(),
                new EmergencyContactRequest("Ana", "Hermana", "5551234567", "ana@example.com", null));

        assertThat(response.primary()).isTrue();
        assertThat(response.name()).isEqualTo("Ana");
        verify(auditService).record(eq("EMPLOYEE_CONTACT_CREATE"),
                eq("EmergencyContact"), anyString(), any());
    }

    @Test
    void cannotDeleteEmployeeWithSubordinates() {
        Organization org = organization();
        Employee employee = employeeEntity(100L, org, catalogItem(10L, "ACTIVO", "Activo"));

        when(employeeRepository.findByUuidAndDeletedAtIsNull(employee.getUuid()))
                .thenReturn(Optional.of(employee));
        when(employeeRepository.existsByManager_IdAndDeletedAtIsNull(100L)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.delete(employee.getUuid()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("subordinados");
    }

    @Test
    void updatesEmployee() {
        Organization org = organization();
        Employee employee = employeeEntity(100L, org, catalogItem(10L, "ACTIVO", "Activo"));
        CatalogItem active = catalogItem(10L, "ACTIVO", "Activo");
        CatalogItem presencial = catalogItem(11L, "PRESENCIAL", "Presencial");

        stubOrganization(org);
        when(employeeRepository.findByUuidAndDeletedAtIsNull(employee.getUuid()))
                .thenReturn(Optional.of(employee));
        when(catalogItemRepository.findByCatalogCodeAndItemCode("EMPLOYMENT_STATUS", "ACTIVO"))
                .thenReturn(Optional.of(active));
        when(catalogItemRepository.findByCatalogCodeAndItemCode("WORK_MODALITY", "PRESENCIAL"))
                .thenReturn(Optional.of(presencial));
        when(employeeRepository.existsByCompany_IdAndEmployeeNumberIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                1L, "EMP-001", employee.getUuid())).thenReturn(false);
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(contactRepository.findActiveByEmployeeId(100L)).thenReturn(List.of());

        EmployeeResponse response = employeeService.update(employee.getUuid(), request(org));

        assertThat(response.employeeNumber()).isEqualTo("EMP-001");
        assertThat(response.statusCode()).isEqualTo("ACTIVO");
        verify(auditService).record(eq("EMPLOYEE_UPDATE"), eq("Employee"), anyString(), any(), any());
    }

    @Test
    void deletesEmployeeLogically() {
        Organization org = organization();
        Employee employee = employeeEntity(100L, org, catalogItem(10L, "ACTIVO", "Activo"));

        when(employeeRepository.findByUuidAndDeletedAtIsNull(employee.getUuid()))
                .thenReturn(Optional.of(employee));
        when(employeeRepository.existsByManager_IdAndDeletedAtIsNull(100L)).thenReturn(false);
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(contactRepository.findActiveByEmployeeId(100L)).thenReturn(List.of());

        employeeService.delete(employee.getUuid());

        assertThat(employee.getDeletedAt()).isNotNull();
        assertThat(employee.isEnabled()).isFalse();
        verify(auditService).record(eq("EMPLOYEE_DELETE"), eq("Employee"), anyString(), any(), any());
    }

    @Test
    void getsEmployeeByUsername() {
        Organization org = organization();
        Employee employee = employeeEntity(100L, org, catalogItem(10L, "ACTIVO", "Activo"));

        when(employeeRepository.findByUser_UsernameAndDeletedAtIsNull("omar"))
                .thenReturn(Optional.of(employee));
        when(contactRepository.findActiveByEmployeeId(100L)).thenReturn(List.of());

        EmployeeResponse response = employeeService.getMe("omar");

        assertThat(response.employeeNumber()).isEqualTo("EMP-001");
    }

    @Test
    void linksUserWhenCreatingEmployee() {
        Organization org = organization();
        CatalogItem active = catalogItem(10L, "ACTIVO", "Activo");
        CatalogItem presencial = catalogItem(11L, "PRESENCIAL", "Presencial");
        User user = new User();
        user.setId(50L);
        user.setUuid(UUID.randomUUID());
        user.setUsername("omar");
        user.setPassword("hash");
        user.setEnabled(true);

        stubOrganization(org);
        when(catalogItemRepository.findByCatalogCodeAndItemCode("EMPLOYMENT_STATUS", "ACTIVO"))
                .thenReturn(Optional.of(active));
        when(catalogItemRepository.findByCatalogCodeAndItemCode("WORK_MODALITY", "PRESENCIAL"))
                .thenReturn(Optional.of(presencial));
        when(employeeRepository.existsByCompany_IdAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(
                1L, "EMP-001")).thenReturn(false);
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(employeeRepository.existsByUser_IdAndDeletedAtIsNull(50L)).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(100L);
            employee.setUuid(UUID.randomUUID());
            employee.setCreatedAt(Instant.now());
            return employee;
        });
        when(contactRepository.findActiveByEmployeeId(100L)).thenReturn(List.of());

        EmployeeRequest base = request(org);
        EmployeeRequest request = new EmployeeRequest(
                user.getUuid(), base.companyUuid(), base.branchUuid(), base.departmentUuid(),
                base.positionUuid(), null, base.employeeNumber(), base.firstName(), null,
                base.lastName(), null, base.birthDate(), null, null, null,
                base.corporateEmail(), null, base.phone(), null, null, null, null, null, null,
                base.hireDate(), null, null, null, null, base.statusCode(),
                base.workModalityCode(), true);

        employeeService.create(request);

        assertThat(user.getEmployeeId()).isEqualTo(100L);
        verify(userRepository).save(user);
    }

    @Test
    void updatesAndDeletesEmergencyContact() {
        Organization org = organization();
        Employee employee = employeeEntity(100L, org, catalogItem(10L, "ACTIVO", "Activo"));
        EmergencyContact contact = new EmergencyContact();
        contact.setId(7L);
        contact.setUuid(UUID.randomUUID());
        contact.setEmployee(employee);
        contact.setName("Ana");
        contact.setPhone("5551234567");
        contact.setPrimary(false);
        contact.setEnabled(true);

        when(employeeRepository.findByUuidAndDeletedAtIsNull(employee.getUuid()))
                .thenReturn(Optional.of(employee));
        when(contactRepository.findByUuidAndEmployee_IdAndDeletedAtIsNull(contact.getUuid(), 100L))
                .thenReturn(Optional.of(contact));
        when(contactRepository.save(any(EmergencyContact.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmergencyContactResponse updated = employeeService.updateContact(
                employee.getUuid(), contact.getUuid(),
                new EmergencyContactRequest("Ana María", "Hermana", "5559876543", "ana@example.com", false));

        assertThat(updated.name()).isEqualTo("Ana María");

        employeeService.deleteContact(employee.getUuid(), contact.getUuid());
        assertThat(contact.getDeletedAt()).isNotNull();
    }

    @Test
    void listsEmployees() {
        Organization org = organization();
        Employee employee = employeeEntity(100L, org, catalogItem(10L, "ACTIVO", "Activo"));
        when(employeeRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));

        PageResponse<EmployeeSummaryResponse> response =
                employeeService.list(null, null, null, null, null, null, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).employeeNumber()).isEqualTo("EMP-001");
    }

    private void stubOrganization(Organization org) {
        when(companyRepository.findByUuidAndDeletedAtIsNull(org.company().getUuid()))
                .thenReturn(Optional.of(org.company()));
        when(branchRepository.findByUuidAndDeletedAtIsNull(org.branch().getUuid()))
                .thenReturn(Optional.of(org.branch()));
        when(departmentRepository.findByUuidAndDeletedAtIsNull(org.department().getUuid()))
                .thenReturn(Optional.of(org.department()));
        when(positionRepository.findByUuidAndDeletedAtIsNull(org.position().getUuid()))
                .thenReturn(Optional.of(org.position()));
    }

    private EmployeeRequest request(Organization org) {
        return new EmployeeRequest(
                null,
                org.company().getUuid(),
                org.branch().getUuid(),
                org.department().getUuid(),
                org.position().getUuid(),
                null,
                "emp-001",
                "Omar",
                null,
                "Ramírez",
                null,
                LocalDate.of(1990, 1, 1),
                null,
                null,
                null,
                "omar@example.com",
                null,
                "5551234567",
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2020, 1, 1),
                null,
                null,
                null,
                null,
                "ACTIVO",
                "PRESENCIAL",
                true);
    }

    private Organization organization() {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("ACME");
        company.setLegalName("Acme SA de CV");
        company.setEnabled(true);

        Branch branch = new Branch();
        branch.setId(2L);
        branch.setUuid(UUID.randomUUID());
        branch.setCompany(company);
        branch.setCode("MATRIZ");
        branch.setName("Matriz");
        branch.setEnabled(true);

        Department department = new Department();
        department.setId(3L);
        department.setUuid(UUID.randomUUID());
        department.setBranch(branch);
        department.setCode("TECNOLOGIA");
        department.setName("Tecnología");
        department.setEnabled(true);

        Position position = new Position();
        position.setId(4L);
        position.setUuid(UUID.randomUUID());
        position.setDepartment(department);
        position.setCode("DEV");
        position.setName("Desarrollador");
        position.setEnabled(true);

        return new Organization(company, branch, department, position);
    }

    private Employee employeeEntity(long id, Organization org, CatalogItem status) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setUuid(UUID.randomUUID());
        employee.setCompany(org.company());
        employee.setBranch(org.branch());
        employee.setDepartment(org.department());
        employee.setPosition(org.position());
        employee.setEmployeeNumber("EMP-001");
        employee.setFirstName("Omar");
        employee.setLastName("Ramírez");
        employee.setHireDate(LocalDate.of(2020, 1, 1));
        employee.setBirthDate(LocalDate.of(1990, 1, 1));
        employee.setStatus(status);
        employee.setWorkModality(catalogItem(11L, "PRESENCIAL", "Presencial"));
        employee.setEnabled(true);
        employee.setCreatedAt(Instant.now());
        return employee;
    }

    private CatalogItem catalogItem(long id, String code, String name) {
        CatalogItem item = new CatalogItem();
        item.setId(id);
        item.setUuid(UUID.randomUUID());
        item.setCode(code);
        item.setName(name);
        item.setEnabled(true);
        return item;
    }

    private record Organization(Company company, Branch branch, Department department, Position position) {
    }
}
