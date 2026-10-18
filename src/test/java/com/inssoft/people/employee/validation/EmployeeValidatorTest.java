package com.inssoft.people.employee.validation;

import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.organization.entity.Branch;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.entity.Department;
import com.inssoft.people.organization.entity.Position;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeValidatorTest {

    private final EmployeeValidator validator = new EmployeeValidator();

    @Test
    void acceptsConsistentDatesAndOrganization() {
        Organization org = organization();
        assertThatCode(() -> {
            validator.validateDates(
                    LocalDate.of(1990, 1, 1),
                    LocalDate.of(2020, 1, 1),
                    LocalDate.of(2024, 1, 1));
            validator.validateOrganization(org.company, org.branch, org.department, org.position);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsBirthDateAfterHireDate() {
        assertThatThrownBy(() -> validator.validateDates(
                LocalDate.of(2021, 1, 1),
                LocalDate.of(2020, 1, 1),
                null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nacimiento");
    }

    @Test
    void rejectsTerminationBeforeHireDate() {
        assertThatThrownBy(() -> validator.validateDates(
                null,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2019, 1, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("baja");
    }

    @Test
    void rejectsBranchFromAnotherCompany() {
        Organization org = organization();
        Company another = company(99L);
        org.branch.setCompany(another);

        assertThatThrownBy(() -> validator.validateOrganization(
                org.company, org.branch, org.department, org.position))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sucursal");
    }

    @Test
    void rejectsSelfAsManager() {
        Organization org = organization();
        Employee employee = employee(10L, org.company);
        employee.setManager(employee);

        assertThatThrownBy(() -> validator.validateManager(employee, employee, org.company))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("propio jefe");
    }

    @Test
    void rejectsManagerFromAnotherCompany() {
        Organization org = organization();
        Employee employee = employee(10L, org.company);
        Employee manager = employee(20L, company(99L));

        assertThatThrownBy(() -> validator.validateManager(employee, manager, org.company))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("misma empresa");
    }

    @Test
    void rejectsManagerCycle() {
        Organization org = organization();
        Employee employee = employee(10L, org.company);
        Employee manager = employee(20L, org.company);
        manager.setManager(employee);

        assertThatThrownBy(() -> validator.validateManager(employee, manager, org.company))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ciclos");
    }

    private Organization organization() {
        Company company = company(1L);
        Branch branch = new Branch();
        branch.setId(2L);
        branch.setUuid(UUID.randomUUID());
        branch.setCompany(company);
        Department department = new Department();
        department.setId(3L);
        department.setUuid(UUID.randomUUID());
        department.setBranch(branch);
        Position position = new Position();
        position.setId(4L);
        position.setUuid(UUID.randomUUID());
        position.setDepartment(department);
        return new Organization(company, branch, department, position);
    }

    private Company company(long id) {
        Company company = new Company();
        company.setId(id);
        company.setUuid(UUID.randomUUID());
        company.setCode("C" + id);
        company.setLegalName("Company " + id);
        company.setEnabled(true);
        return company;
    }

    private Employee employee(long id, Company company) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setUuid(UUID.randomUUID());
        employee.setCompany(company);
        employee.setEnabled(true);
        return employee;
    }

    private record Organization(Company company, Branch branch, Department department, Position position) {
    }
}
