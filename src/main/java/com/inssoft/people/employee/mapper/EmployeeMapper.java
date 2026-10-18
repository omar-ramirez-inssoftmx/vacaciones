package com.inssoft.people.employee.mapper;

import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.employee.dto.EmergencyContactResponse;
import com.inssoft.people.employee.dto.EmployeeRequest;
import com.inssoft.people.employee.dto.EmployeeResponse;
import com.inssoft.people.employee.dto.EmployeeSummaryResponse;
import com.inssoft.people.employee.entity.EmergencyContact;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.validation.MexicanIdentifiers;
import com.inssoft.people.masterdata.entity.CatalogItem;
import com.mx.vacaciones.model.User;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EmployeeMapper {

    private EmployeeMapper() {
    }

    public static EmployeeSummaryResponse toSummary(Employee employee) {
        return new EmployeeSummaryResponse(
                employee.getUuid(),
                employee.getEmployeeNumber(),
                fullName(employee),
                employee.getCorporateEmail(),
                employee.getCompany().getUuid(),
                employee.getCompany().getLegalName(),
                employee.getBranch().getUuid(),
                employee.getBranch().getName(),
                employee.getDepartment().getUuid(),
                employee.getDepartment().getName(),
                employee.getPosition().getUuid(),
                employee.getPosition().getName(),
                code(employee.getStatus()),
                name(employee.getStatus()),
                employee.isEnabled(),
                employee.getCreatedAt()
        );
    }

    public static EmployeeResponse toResponse(
            Employee employee,
            List<EmergencyContact> contacts,
            boolean sensitiveData) {

        CatalogItem gender = employee.getGender();
        CatalogItem maritalStatus = employee.getMaritalStatus();
        CatalogItem status = employee.getStatus();
        CatalogItem workModality = employee.getWorkModality();
        Employee manager = employee.getManager();
        User user = employee.getUser();

        List<EmergencyContactResponse> contactResponses = contacts == null
                ? List.of()
                : contacts.stream().map(EmployeeMapper::toContactResponse).toList();

        return new EmployeeResponse(
                employee.getUuid(),
                user == null ? null : user.getUuid(),
                user == null ? null : user.getEmail(),
                employee.getEmployeeNumber(),
                employee.getFirstName(),
                employee.getMiddleName(),
                employee.getLastName(),
                employee.getSecondLastName(),
                fullName(employee),
                employee.getBirthDate(),
                code(gender),
                name(gender),
                code(maritalStatus),
                name(maritalStatus),
                sensitiveData ? employee.getCurp() : MexicanIdentifiers.mask(employee.getCurp()),
                sensitiveData ? employee.getRfc() : MexicanIdentifiers.mask(employee.getRfc()),
                sensitiveData ? employee.getNss() : MexicanIdentifiers.mask(employee.getNss()),
                employee.getCorporateEmail(),
                employee.getPersonalEmail(),
                employee.getPhone(),
                employee.getAddressLine1(),
                employee.getAddressLine2(),
                employee.getCity(),
                employee.getState(),
                employee.getPostalCode(),
                employee.getCountry(),
                employee.getCompany().getUuid(),
                employee.getCompany().getLegalName(),
                employee.getBranch().getUuid(),
                employee.getBranch().getName(),
                employee.getDepartment().getUuid(),
                employee.getDepartment().getName(),
                employee.getPosition().getUuid(),
                employee.getPosition().getName(),
                manager == null ? null : manager.getUuid(),
                manager == null ? null : fullName(manager),
                employee.getHireDate(),
                employee.getTerminationDate(),
                employee.getTerminationReason(),
                code(status),
                name(status),
                code(workModality),
                name(workModality),
                employee.isEnabled(),
                employee.getCreatedAt(),
                employee.getUpdatedAt(),
                contactResponses
        );
    }

    public static EmergencyContactResponse toContactResponse(EmergencyContact contact) {
        return new EmergencyContactResponse(
                contact.getUuid(),
                contact.getName(),
                contact.getRelationship(),
                contact.getPhone(),
                contact.getEmail(),
                contact.isPrimary()
        );
    }

    public static void apply(EmployeeRequest request, Employee employee) {
        employee.setEmployeeNumber(request.employeeNumber().trim().toUpperCase(Locale.ROOT));
        employee.setFirstName(request.firstName().trim());
        employee.setMiddleName(CodeNormalizer.trimToNull(request.middleName()));
        employee.setLastName(request.lastName().trim());
        employee.setSecondLastName(CodeNormalizer.trimToNull(request.secondLastName()));
        employee.setBirthDate(request.birthDate());
        employee.setCurp(MexicanIdentifiers.normalize(request.curp()));
        employee.setRfc(MexicanIdentifiers.normalize(request.rfc()));
        employee.setNss(MexicanIdentifiers.normalize(request.nss()));
        employee.setCorporateEmail(lower(request.corporateEmail()));
        employee.setPersonalEmail(lower(request.personalEmail()));
        employee.setPhone(CodeNormalizer.trimToNull(request.phone()));
        employee.setAddressLine1(CodeNormalizer.trimToNull(request.addressLine1()));
        employee.setAddressLine2(CodeNormalizer.trimToNull(request.addressLine2()));
        employee.setCity(CodeNormalizer.trimToNull(request.city()));
        employee.setState(CodeNormalizer.trimToNull(request.state()));
        employee.setPostalCode(CodeNormalizer.trimToNull(request.postalCode()));
        employee.setCountry(CodeNormalizer.trimToNull(request.country()) == null
                ? "México"
                : request.country().trim());
        employee.setHireDate(request.hireDate());
        employee.setTerminationDate(request.terminationDate());
        employee.setTerminationReason(CodeNormalizer.trimToNull(request.terminationReason()));
        if (request.enabled() != null) {
            employee.setEnabled(request.enabled());
        }
    }

    public static String fullName(Employee employee) {
        return Stream.of(
                        employee.getFirstName(),
                        employee.getMiddleName(),
                        employee.getLastName(),
                        employee.getSecondLastName())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }

    private static String code(CatalogItem item) {
        return item == null ? null : item.getCode();
    }

    private static String name(CatalogItem item) {
        return item == null ? null : item.getName();
    }

    private static String lower(String value) {
        String trimmed = CodeNormalizer.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
