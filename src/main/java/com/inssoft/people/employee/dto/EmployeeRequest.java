package com.inssoft.people.employee.dto;

import com.inssoft.people.employee.validation.Curp;
import com.inssoft.people.employee.validation.Nss;
import com.inssoft.people.employee.validation.Rfc;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeRequest(
        UUID userUuid,

        @NotNull(message = "La empresa es obligatoria")
        UUID companyUuid,

        @NotNull(message = "La sucursal es obligatoria")
        UUID branchUuid,

        @NotNull(message = "El departamento es obligatorio")
        UUID departmentUuid,

        @NotNull(message = "El puesto es obligatorio")
        UUID positionUuid,

        UUID managerUuid,

        @NotBlank(message = "El número de empleado es obligatorio")
        @Size(max = 30, message = "El número de empleado no puede exceder 30 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$",
                message = "El número de empleado solo permite letras, números, guion y guion bajo")
        String employeeNumber,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String firstName,

        @Size(max = 100, message = "El segundo nombre no puede exceder 100 caracteres")
        String middleName,

        @NotBlank(message = "El apellido paterno es obligatorio")
        @Size(max = 100, message = "El apellido paterno no puede exceder 100 caracteres")
        String lastName,

        @Size(max = 100, message = "El apellido materno no puede exceder 100 caracteres")
        String secondLastName,

        @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
        LocalDate birthDate,

        @Curp
        String curp,

        @Rfc
        String rfc,

        @Nss
        String nss,

        @Email(message = "El correo corporativo no es válido")
        @Size(max = 150, message = "El correo corporativo no puede exceder 150 caracteres")
        String corporateEmail,

        @Email(message = "El correo personal no es válido")
        @Size(max = 150, message = "El correo personal no puede exceder 150 caracteres")
        String personalEmail,

        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,20}$", message = "El teléfono no tiene un formato válido")
        String phone,

        @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
        String addressLine1,

        @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
        String addressLine2,

        @Size(max = 100, message = "La ciudad no puede exceder 100 caracteres")
        String city,

        @Size(max = 100, message = "El estado no puede exceder 100 caracteres")
        String state,

        @Pattern(regexp = "^$|^[0-9]{4,10}$", message = "El código postal no tiene un formato válido")
        String postalCode,

        @Size(max = 100, message = "El país no puede exceder 100 caracteres")
        String country,

        @NotNull(message = "La fecha de ingreso es obligatoria")
        LocalDate hireDate,

        LocalDate terminationDate,

        @Size(max = 500, message = "El motivo de baja no puede exceder 500 caracteres")
        String terminationReason,

        String genderCode,
        String maritalStatusCode,
        String statusCode,
        String workModalityCode,

        Boolean enabled
) {
}
