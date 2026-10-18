package com.inssoft.people.employee.validation;

import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.organization.entity.Branch;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.entity.Department;
import com.inssoft.people.organization.entity.Position;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Reglas de negocio que cruzan varios campos o entidades del empleado.
 */
@Component
public class EmployeeValidator {

    private static final int MAX_HIERARCHY_DEPTH = 100;

    public void validateDates(LocalDate birthDate, LocalDate hireDate, LocalDate terminationDate) {
        if (hireDate == null) {
            throw new BusinessException("La fecha de ingreso es obligatoria");
        }
        if (birthDate != null && !birthDate.isBefore(hireDate)) {
            throw new BusinessException("La fecha de nacimiento debe ser anterior a la fecha de ingreso");
        }
        if (terminationDate != null && terminationDate.isBefore(hireDate)) {
            throw new BusinessException("La fecha de baja no puede ser anterior a la fecha de ingreso");
        }
    }

    public void validateOrganization(
            Company company, Branch branch, Department department, Position position) {

        if (!branch.getCompany().getId().equals(company.getId())) {
            throw new BusinessException("La sucursal no pertenece a la empresa seleccionada");
        }
        if (!department.getBranch().getId().equals(branch.getId())) {
            throw new BusinessException("El departamento no pertenece a la sucursal seleccionada");
        }
        if (!position.getDepartment().getId().equals(department.getId())) {
            throw new BusinessException("El puesto no pertenece al departamento seleccionado");
        }
    }

    public void validateManager(Employee employee, Employee manager, Company company) {
        if (manager == null) {
            return;
        }

        if (employee != null
                && employee.getId() != null
                && employee.getId().equals(manager.getId())) {
            throw new BusinessException("Un empleado no puede ser su propio jefe directo");
        }

        if (!manager.getCompany().getId().equals(company.getId())) {
            throw new BusinessException("El jefe directo debe pertenecer a la misma empresa");
        }

        Employee current = manager;
        int depth = 0;
        while (current != null && depth++ < MAX_HIERARCHY_DEPTH) {
            if (employee != null
                    && employee.getId() != null
                    && employee.getId().equals(current.getId())) {
                throw new BusinessException("La jerarquía de jefes directos no puede contener ciclos");
            }
            current = current.getManager();
        }
    }
}
