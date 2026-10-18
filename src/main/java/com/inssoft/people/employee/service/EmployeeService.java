package com.inssoft.people.employee.service;

import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.employee.dto.EmergencyContactRequest;
import com.inssoft.people.employee.dto.EmergencyContactResponse;
import com.inssoft.people.employee.dto.EmployeeRequest;
import com.inssoft.people.employee.dto.EmployeeResponse;
import com.inssoft.people.employee.dto.EmployeeSummaryResponse;
import com.inssoft.people.employee.dto.TerminationRequest;
import com.inssoft.people.employee.entity.EmergencyContact;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.mapper.EmployeeMapper;
import com.inssoft.people.employee.repository.EmergencyContactRepository;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.employee.validation.EmployeeValidator;
import com.inssoft.people.employee.validation.MexicanIdentifiers;
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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmployeeService {

    private static final String CATALOG_GENDER = "GENDER";
    private static final String CATALOG_MARITAL = "MARITAL_STATUS";
    private static final String CATALOG_STATUS = "EMPLOYMENT_STATUS";
    private static final String CATALOG_MODALITY = "WORK_MODALITY";
    private static final String STATUS_ACTIVE = "ACTIVO";
    private static final String STATUS_TERMINATED = "BAJA";
    private static final String MODALITY_DEFAULT = "PRESENCIAL";

    private final EmployeeRepository employeeRepository;
    private final EmergencyContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final UserRepository userRepository;
    private final EmployeeValidator employeeValidator;
    private final AuditService auditService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            EmergencyContactRepository contactRepository,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository,
            CatalogItemRepository catalogItemRepository,
            UserRepository userRepository,
            EmployeeValidator employeeValidator,
            AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.contactRepository = contactRepository;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.userRepository = userRepository;
        this.employeeValidator = employeeValidator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeSummaryResponse> list(
            UUID companyUuid,
            UUID branchUuid,
            UUID departmentUuid,
            UUID positionUuid,
            String statusCode,
            String workModalityCode,
            String q,
            Boolean enabled,
            int page,
            int size) {

        Page<Employee> result = employeeRepository.search(
                companyUuid,
                branchUuid,
                departmentUuid,
                positionUuid,
                CodeNormalizer.trimToNull(statusCode) == null
                        ? null
                        : CodeNormalizer.normalize(statusCode),
                CodeNormalizer.trimToNull(workModalityCode) == null
                        ? null
                        : CodeNormalizer.normalize(workModalityCode),
                CodeNormalizer.trimToNull(q),
                enabled,
                PageRequestFactory.of(page, size, "lastName"));

        return PageResponse.from(result, EmployeeMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID uuid, boolean sensitiveData) {
        Employee employee = findEmployee(uuid);
        return toResponse(employee, sensitiveData);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSummaryResponse> listTeam(String username) {
        return employeeRepository
                .findByManager_User_UsernameAndDeletedAtIsNullOrderByLastNameAsc(username)
                .stream()
                .map(EmployeeMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getMe(String username) {
        Employee employee = employeeRepository.findByUser_UsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe un empleado vinculado al usuario " + username));
        return toResponse(employee, true);
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        Organization organization = resolveOrganization(request);
        employeeValidator.validateDates(
                request.birthDate(), request.hireDate(), request.terminationDate());

        Employee employee = new Employee();
        employee.setCompany(organization.company());
        employee.setBranch(organization.branch());
        employee.setDepartment(organization.department());
        employee.setPosition(organization.position());

        Employee manager = resolveManager(request.managerUuid());
        employeeValidator.validateManager(employee, manager, organization.company());
        employee.setManager(manager);

        String requestedStatus = CodeNormalizer.trimToNull(request.statusCode()) == null
                ? STATUS_ACTIVE
                : CodeNormalizer.normalize(request.statusCode());
        if (request.terminationDate() != null) {
            requestedStatus = STATUS_TERMINATED;
        }

        CatalogItem status = resolveCatalogItem(CATALOG_STATUS, requestedStatus, true);
        employee.setStatus(status);
        employee.setGender(resolveOptionalCatalogItem(CATALOG_GENDER, request.genderCode()));
        employee.setMaritalStatus(resolveOptionalCatalogItem(CATALOG_MARITAL, request.maritalStatusCode()));
        employee.setWorkModality(resolveOptionalCatalogItem(
                CATALOG_MODALITY,
                CodeNormalizer.trimToNull(request.workModalityCode()) == null
                        ? MODALITY_DEFAULT
                        : request.workModalityCode()));

        EmployeeMapper.apply(request, employee);
        validateTerminationConsistency(employee);

        validateUniqueFields(employee, null);

        User user = resolveUser(request.userUuid(), null);
        if (user != null && STATUS_TERMINATED.equalsIgnoreCase(employee.getStatus().getCode())) {
            user.setEnabled(false);
        }
        employee.setUser(user);

        Employee saved = employeeRepository.save(employee);
        syncUserLink(saved, null, user);

        EmployeeResponse response = toResponse(saved, true);
        auditService.record("EMPLOYEE_CREATE", "Employee", saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public EmployeeResponse update(UUID uuid, EmployeeRequest request) {
        Employee employee = findEmployee(uuid);
        EmployeeResponse previous = toResponse(employee, true);

        Organization organization = resolveOrganization(request);
        employeeValidator.validateDates(
                request.birthDate(), request.hireDate(), request.terminationDate());

        employee.setCompany(organization.company());
        employee.setBranch(organization.branch());
        employee.setDepartment(organization.department());
        employee.setPosition(organization.position());

        Employee manager = resolveManager(request.managerUuid());
        employeeValidator.validateManager(employee, manager, organization.company());
        employee.setManager(manager);

        CatalogItem status = CodeNormalizer.trimToNull(request.statusCode()) == null
                ? employee.getStatus()
                : resolveCatalogItem(CATALOG_STATUS, request.statusCode(), true);
        employee.setStatus(status);

        if (request.genderCode() != null) {
            employee.setGender(resolveOptionalCatalogItem(CATALOG_GENDER, request.genderCode()));
        }
        if (request.maritalStatusCode() != null) {
            employee.setMaritalStatus(resolveOptionalCatalogItem(CATALOG_MARITAL, request.maritalStatusCode()));
        }
        if (request.workModalityCode() != null) {
            employee.setWorkModality(resolveOptionalCatalogItem(
                    CATALOG_MODALITY, request.workModalityCode()));
        }

        LocalDate previousTerminationDate = employee.getTerminationDate();
        String previousTerminationReason = employee.getTerminationReason();
        User previousUser = employee.getUser();

        EmployeeMapper.apply(request, employee);

        boolean reactivating = request.statusCode() != null
                && !STATUS_TERMINATED.equalsIgnoreCase(request.statusCode());

        if (reactivating) {
            if (request.terminationDate() != null) {
                throw new BusinessException(
                        "No se puede indicar fecha de baja cuando el estatus es activo");
            }
            employee.setTerminationDate(null);
            employee.setTerminationReason(null);
            if (request.enabled() == null) {
                employee.setEnabled(true);
            }
        } else if (request.terminationDate() == null && previousTerminationDate != null) {
            employee.setTerminationDate(previousTerminationDate);
            employee.setTerminationReason(previousTerminationReason);
        }

        if (request.terminationDate() != null && !STATUS_TERMINATED.equals(employee.getStatus().getCode())) {
            employee.setStatus(resolveCatalogItem(CATALOG_STATUS, STATUS_TERMINATED, true));
        }

        validateTerminationConsistency(employee);
        validateUniqueFields(employee, uuid);

        User requestedUser = request.userUuid() == null
                ? employee.getUser()
                : resolveUser(request.userUuid(), uuid);
        if (reactivating && requestedUser != null) {
            requestedUser.setEnabled(true);
        }
        employee.setUser(requestedUser);

        Employee saved = employeeRepository.save(employee);
        syncUserLink(saved, previousUser, requestedUser);

        EmployeeResponse response = toResponse(saved, true);
        auditService.record("EMPLOYEE_UPDATE", "Employee", uuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public EmployeeResponse terminate(UUID uuid, TerminationRequest request) {
        Employee employee = findEmployee(uuid);
        EmployeeResponse previous = toResponse(employee, true);

        LocalDate terminationDate = request.terminationDate() == null
                ? LocalDate.now()
                : request.terminationDate();
        employeeValidator.validateDates(employee.getBirthDate(), employee.getHireDate(), terminationDate);

        String statusCode = CodeNormalizer.trimToNull(request.statusCode()) == null
                ? STATUS_TERMINATED
                : CodeNormalizer.normalize(request.statusCode());
        if (!STATUS_TERMINATED.equals(statusCode)) {
            throw new BusinessException("El estatus de baja debe ser BAJA");
        }
        CatalogItem status = resolveCatalogItem(CATALOG_STATUS, statusCode, true);

        employee.setTerminationDate(terminationDate);
        employee.setTerminationReason(CodeNormalizer.trimToNull(request.reason()));
        employee.setStatus(status);
        employee.setEnabled(false);

        Employee saved = employeeRepository.save(employee);

        if (saved.getUser() != null) {
            User user = saved.getUser();
            user.setEnabled(false);
            userRepository.save(user);
        }

        EmployeeResponse response = toResponse(saved, true);
        auditService.record("EMPLOYEE_TERMINATE", "Employee", uuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public void delete(UUID uuid) {
        Employee employee = findEmployee(uuid);
        if (employeeRepository.existsByManager_IdAndDeletedAtIsNull(employee.getId())) {
            throw new BusinessException(
                    "No se puede eliminar un empleado que tiene subordinados activos");
        }

        EmployeeResponse previous = toResponse(employee, true);

        if (employee.getUser() != null) {
            User user = employee.getUser();
            user.setEmployeeId(null);
            user.setEnabled(false);
            userRepository.save(user);
            employee.setUser(null);
        }

        employee.setDeletedAt(Instant.now());
        employee.setEnabled(false);
        employeeRepository.save(employee);

        auditService.record("EMPLOYEE_DELETE", "Employee", uuid.toString(), previous, null);
    }

    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> listContacts(UUID employeeUuid) {
        Employee employee = findEmployee(employeeUuid);
        return contactRepository.findActiveByEmployeeId(employee.getId()).stream()
                .map(EmployeeMapper::toContactResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> getMyContacts(String username) {
        Employee employee = findMyEmployee(username);
        return contactRepository.findActiveByEmployeeId(employee.getId()).stream()
                .map(EmployeeMapper::toContactResponse)
                .toList();
    }

    @Transactional
    public EmergencyContactResponse addMyContact(String username, EmergencyContactRequest request) {
        return addContact(findMyEmployee(username).getUuid(), request);
    }

    @Transactional
    public EmergencyContactResponse updateMyContact(
            String username, UUID contactUuid, EmergencyContactRequest request) {
        return updateContact(findMyEmployee(username).getUuid(), contactUuid, request);
    }

    @Transactional
    public void deleteMyContact(String username, UUID contactUuid) {
        deleteContact(findMyEmployee(username).getUuid(), contactUuid);
    }

    @Transactional
    public EmergencyContactResponse addContact(UUID employeeUuid, EmergencyContactRequest request) {
        Employee employee = findEmployee(employeeUuid);
        boolean primary = Boolean.TRUE.equals(request.primary())
                || !contactRepository.existsByEmployee_IdAndDeletedAtIsNull(employee.getId());

        if (primary) {
            clearPrimary(employee.getId(), null);
            contactRepository.flush();
        }

        EmergencyContact contact = new EmergencyContact();
        contact.setEmployee(employee);
        applyContact(request, contact);
        contact.setPrimary(primary);

        EmergencyContact saved = contactRepository.save(contact);
        EmergencyContactResponse response = EmployeeMapper.toContactResponse(saved);
        auditService.record("EMPLOYEE_CONTACT_CREATE", "EmergencyContact",
                saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public EmergencyContactResponse updateContact(
            UUID employeeUuid, UUID contactUuid, EmergencyContactRequest request) {

        Employee employee = findEmployee(employeeUuid);
        EmergencyContact contact = contactRepository
                .findByUuidAndEmployee_IdAndDeletedAtIsNull(contactUuid, employee.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Contacto de emergencia", contactUuid));

        EmergencyContactResponse previous = EmployeeMapper.toContactResponse(contact);

        if (Boolean.TRUE.equals(request.primary())) {
            clearPrimary(employee.getId(), contactUuid);
            contactRepository.flush();
        }

        applyContact(request, contact);
        if (request.primary() != null) {
            contact.setPrimary(request.primary());
        }

        EmergencyContact saved = contactRepository.save(contact);
        EmergencyContactResponse response = EmployeeMapper.toContactResponse(saved);
        auditService.record("EMPLOYEE_CONTACT_UPDATE", "EmergencyContact",
                contactUuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public void deleteContact(UUID employeeUuid, UUID contactUuid) {
        Employee employee = findEmployee(employeeUuid);
        EmergencyContact contact = contactRepository
                .findByUuidAndEmployee_IdAndDeletedAtIsNull(contactUuid, employee.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Contacto de emergencia", contactUuid));

        EmergencyContactResponse previous = EmployeeMapper.toContactResponse(contact);
        contact.setDeletedAt(Instant.now());
        contact.setEnabled(false);
        contactRepository.save(contact);

        auditService.record("EMPLOYEE_CONTACT_DELETE", "EmergencyContact",
                contactUuid.toString(), previous, null);
    }

    private EmployeeResponse toResponse(Employee employee, boolean sensitiveData) {
        List<EmergencyContact> contacts = contactRepository.findActiveByEmployeeId(employee.getId());
        return EmployeeMapper.toResponse(employee, contacts, sensitiveData);
    }

    private Employee findEmployee(UUID uuid) {
        return employeeRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", uuid));
    }

    private Employee findMyEmployee(String username) {
        return employeeRepository.findByUser_UsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe un empleado vinculado al usuario " + username));
    }

    private Employee resolveManager(UUID managerUuid) {
        return managerUuid == null ? null : findEmployee(managerUuid);
    }

    private Organization resolveOrganization(EmployeeRequest request) {
        Company company = companyRepository.findByUuidAndDeletedAtIsNull(request.companyUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", request.companyUuid()));
        Branch branch = branchRepository.findByUuidAndDeletedAtIsNull(request.branchUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal", request.branchUuid()));
        Department department = departmentRepository.findByUuidAndDeletedAtIsNull(request.departmentUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Departamento", request.departmentUuid()));
        Position position = positionRepository.findByUuidAndDeletedAtIsNull(request.positionUuid())
                .orElseThrow(() -> new ResourceNotFoundException("Puesto", request.positionUuid()));

        employeeValidator.validateOrganization(company, branch, department, position);
        return new Organization(company, branch, department, position);
    }

    private CatalogItem resolveOptionalCatalogItem(String catalogCode, String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return null;
        }
        return resolveCatalogItem(catalogCode, itemCode, true);
    }

    private CatalogItem resolveCatalogItem(String catalogCode, String itemCode, boolean required) {
        if (itemCode == null || itemCode.isBlank()) {
            if (required) {
                throw new BusinessException("El código de catálogo " + catalogCode + " es obligatorio");
            }
            return null;
        }

        return catalogItemRepository.findByCatalogCodeAndItemCode(
                        catalogCode, CodeNormalizer.normalize(itemCode))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item de catálogo " + catalogCode, itemCode));
    }

    private User resolveUser(UUID userUuid, UUID employeeUuid) {
        if (userUuid == null) {
            return null;
        }

        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userUuid));

        boolean linkedToAnother = employeeUuid == null
                ? employeeRepository.existsByUser_IdAndDeletedAtIsNull(user.getId())
                : employeeRepository.existsByUser_IdAndUuidNotAndDeletedAtIsNull(user.getId(), employeeUuid);

        if (linkedToAnother) {
            throw new ConflictException("El usuario ya está vinculado a otro empleado");
        }

        return user;
    }

    private void syncUserLink(Employee employee, User previousUser, User newUser) {
        if (previousUser != null
                && (newUser == null || !previousUser.getId().equals(newUser.getId()))) {
            previousUser.setEmployeeId(null);
            userRepository.save(previousUser);
        }

        if (newUser != null) {
            newUser.setEmployeeId(employee.getId());
            userRepository.save(newUser);
        }
    }

    private void validateUniqueFields(Employee employee, UUID currentUuid) {
        Long companyId = employee.getCompany().getId();
        String employeeNumber = employee.getEmployeeNumber();
        String curp = employee.getCurp();
        String rfc = employee.getRfc();
        String nss = employee.getNss();
        String corporateEmail = employee.getCorporateEmail();

        boolean employeeNumberExists = currentUuid == null
                ? employeeRepository.existsByCompany_IdAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(
                        companyId, employeeNumber)
                : employeeRepository.existsByCompany_IdAndEmployeeNumberIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                        companyId, employeeNumber, currentUuid);
        if (employeeNumberExists) {
            throw new ConflictException("Ya existe un empleado con el número " + employeeNumber
                    + " en la empresa");
        }

        if (curp != null) {
            boolean exists = currentUuid == null
                    ? employeeRepository.existsByCurpIgnoreCaseAndDeletedAtIsNull(curp)
                    : employeeRepository.existsByCurpIgnoreCaseAndUuidNotAndDeletedAtIsNull(curp, currentUuid);
            if (exists) {
                throw new ConflictException("La CURP ya está registrada en otro empleado");
            }
        }

        if (rfc != null) {
            boolean exists = currentUuid == null
                    ? employeeRepository.existsByRfcIgnoreCaseAndDeletedAtIsNull(rfc)
                    : employeeRepository.existsByRfcIgnoreCaseAndUuidNotAndDeletedAtIsNull(rfc, currentUuid);
            if (exists) {
                throw new ConflictException("El RFC ya está registrado en otro empleado");
            }
        }

        if (nss != null) {
            boolean exists = currentUuid == null
                    ? employeeRepository.existsByNssAndDeletedAtIsNull(nss)
                    : employeeRepository.existsByNssAndUuidNotAndDeletedAtIsNull(nss, currentUuid);
            if (exists) {
                throw new ConflictException("El NSS ya está registrado en otro empleado");
            }
        }

        if (corporateEmail != null) {
            boolean exists = currentUuid == null
                    ? employeeRepository.existsByCorporateEmailIgnoreCaseAndDeletedAtIsNull(corporateEmail)
                    : employeeRepository.existsByCorporateEmailIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                            corporateEmail, currentUuid);
            if (exists) {
                throw new ConflictException("El correo corporativo ya está registrado en otro empleado");
            }
        }
    }

    private void validateTerminationConsistency(Employee employee) {
        boolean terminated = STATUS_TERMINATED.equalsIgnoreCase(employee.getStatus().getCode());
        if (terminated && employee.getTerminationDate() == null) {
            throw new BusinessException("La fecha de baja es obligatoria cuando el estatus es BAJA");
        }
        if (!terminated && employee.getTerminationDate() != null) {
            throw new BusinessException("El estatus debe ser BAJA cuando se registra fecha de baja");
        }
    }

    private void clearPrimary(Long employeeId, UUID exceptContactUuid) {
        contactRepository.findActiveByEmployeeId(employeeId).forEach(contact -> {
            if (exceptContactUuid == null || !exceptContactUuid.equals(contact.getUuid())) {
                contact.setPrimary(false);
                contactRepository.save(contact);
            }
        });
    }

    private void applyContact(EmergencyContactRequest request, EmergencyContact contact) {
        contact.setName(request.name().trim());
        contact.setRelationship(CodeNormalizer.trimToNull(request.relationship()));
        contact.setPhone(request.phone().trim());
        contact.setEmail(CodeNormalizer.trimToNull(request.email()) == null
                ? null
                : request.email().trim().toLowerCase(Locale.ROOT));
    }

    private record Organization(Company company, Branch branch, Department department, Position position) {
    }
}
