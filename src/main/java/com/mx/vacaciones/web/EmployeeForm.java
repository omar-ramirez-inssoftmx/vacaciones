package com.mx.vacaciones.web;

import com.inssoft.people.employee.dto.EmployeeRequest;
import com.inssoft.people.employee.dto.EmployeeResponse;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Formulario web de alta/edición de empleado. La validación de negocio
 * permanece en EmployeeService/EmployeeValidator.
 */
public class EmployeeForm {

    private UUID companyUuid;
    private UUID branchUuid;
    private UUID departmentUuid;
    private UUID positionUuid;
    private UUID managerUuid;

    private String employeeNumber;
    private String firstName;
    private String middleName;
    private String lastName;
    private String secondLastName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    private String curp;
    private String rfc;
    private String nss;
    private String corporateEmail;
    private String personalEmail;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country = "México";

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hireDate = LocalDate.now();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate terminationDate;

    private String terminationReason;
    private String genderCode;
    private String maritalStatusCode;
    private String statusCode = "ACTIVO";
    private String workModalityCode = "PRESENCIAL";
    private Boolean enabled = Boolean.TRUE;

    public static EmployeeForm from(EmployeeResponse employee) {
        EmployeeForm form = new EmployeeForm();
        form.companyUuid = employee.companyUuid();
        form.branchUuid = employee.branchUuid();
        form.departmentUuid = employee.departmentUuid();
        form.positionUuid = employee.positionUuid();
        form.managerUuid = employee.managerUuid();
        form.employeeNumber = employee.employeeNumber();
        form.firstName = employee.firstName();
        form.middleName = employee.middleName();
        form.lastName = employee.lastName();
        form.secondLastName = employee.secondLastName();
        form.birthDate = employee.birthDate();
        form.curp = employee.curp();
        form.rfc = employee.rfc();
        form.nss = employee.nss();
        form.corporateEmail = employee.corporateEmail();
        form.personalEmail = employee.personalEmail();
        form.phone = employee.phone();
        form.addressLine1 = employee.addressLine1();
        form.addressLine2 = employee.addressLine2();
        form.city = employee.city();
        form.state = employee.state();
        form.postalCode = employee.postalCode();
        form.country = employee.country();
        form.hireDate = employee.hireDate();
        form.terminationDate = employee.terminationDate();
        form.terminationReason = employee.terminationReason();
        form.genderCode = employee.genderCode();
        form.maritalStatusCode = employee.maritalStatusCode();
        form.statusCode = employee.statusCode();
        form.workModalityCode = employee.workModalityCode();
        form.enabled = employee.enabled();
        return form;
    }

    public EmployeeRequest toRequest() {
        return new EmployeeRequest(
                null,
                companyUuid,
                branchUuid,
                departmentUuid,
                positionUuid,
                managerUuid,
                employeeNumber,
                firstName,
                middleName,
                lastName,
                secondLastName,
                birthDate,
                curp,
                rfc,
                nss,
                corporateEmail,
                personalEmail,
                phone,
                addressLine1,
                addressLine2,
                city,
                state,
                postalCode,
                country,
                hireDate,
                terminationDate,
                terminationReason,
                genderCode,
                maritalStatusCode,
                statusCode,
                workModalityCode,
                enabled);
    }

    public UUID getCompanyUuid() { return companyUuid; }
    public void setCompanyUuid(UUID companyUuid) { this.companyUuid = companyUuid; }
    public UUID getBranchUuid() { return branchUuid; }
    public void setBranchUuid(UUID branchUuid) { this.branchUuid = branchUuid; }
    public UUID getDepartmentUuid() { return departmentUuid; }
    public void setDepartmentUuid(UUID departmentUuid) { this.departmentUuid = departmentUuid; }
    public UUID getPositionUuid() { return positionUuid; }
    public void setPositionUuid(UUID positionUuid) { this.positionUuid = positionUuid; }
    public UUID getManagerUuid() { return managerUuid; }
    public void setManagerUuid(UUID managerUuid) { this.managerUuid = managerUuid; }
    public String getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getSecondLastName() { return secondLastName; }
    public void setSecondLastName(String secondLastName) { this.secondLastName = secondLastName; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getCurp() { return curp; }
    public void setCurp(String curp) { this.curp = curp; }
    public String getRfc() { return rfc; }
    public void setRfc(String rfc) { this.rfc = rfc; }
    public String getNss() { return nss; }
    public void setNss(String nss) { this.nss = nss; }
    public String getCorporateEmail() { return corporateEmail; }
    public void setCorporateEmail(String corporateEmail) { this.corporateEmail = corporateEmail; }
    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public LocalDate getTerminationDate() { return terminationDate; }
    public void setTerminationDate(LocalDate terminationDate) { this.terminationDate = terminationDate; }
    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }
    public String getGenderCode() { return genderCode; }
    public void setGenderCode(String genderCode) { this.genderCode = genderCode; }
    public String getMaritalStatusCode() { return maritalStatusCode; }
    public void setMaritalStatusCode(String maritalStatusCode) { this.maritalStatusCode = maritalStatusCode; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getWorkModalityCode() { return workModalityCode; }
    public void setWorkModalityCode(String workModalityCode) { this.workModalityCode = workModalityCode; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
