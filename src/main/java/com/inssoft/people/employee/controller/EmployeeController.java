package com.inssoft.people.employee.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.security.AccessGuard;
import com.inssoft.people.employee.dto.EmergencyContactRequest;
import com.inssoft.people.employee.dto.EmergencyContactResponse;
import com.inssoft.people.employee.dto.EmployeeRequest;
import com.inssoft.people.employee.dto.EmployeeResponse;
import com.inssoft.people.employee.dto.EmployeeSummaryResponse;
import com.inssoft.people.employee.dto.TerminationRequest;
import com.inssoft.people.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@Validated
@Tag(name = "Employees", description = "Empleados, datos laborales, contacto de emergencia y /me")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AccessGuard accessGuard;

    public EmployeeController(EmployeeService employeeService, AccessGuard accessGuard) {
        this.employeeService = employeeService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('EMPLOYEE_VIEW')")
    @Operation(summary = "Lista empleados con paginación y filtros")
    public ApiResponse<PageResponse<EmployeeSummaryResponse>> list(
            @RequestParam(required = false) UUID companyUuid,
            @RequestParam(required = false) UUID branchUuid,
            @RequestParam(required = false) UUID departmentUuid,
            @RequestParam(required = false) UUID positionUuid,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workModality,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Empleados obtenidos", employeeService.list(
                companyUuid, branchUuid, departmentUuid, positionUuid,
                status, workModality, q, enabled, page, size));
    }

    @GetMapping("/me")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_VIEW_SELF')")
    @Operation(summary = "Obtiene el empleado vinculado al usuario autenticado")
    public ApiResponse<EmployeeResponse> me(Authentication authentication) {
        return ApiResponse.ok("Empleado obtenido", employeeService.getMe(authentication.getName()));
    }

    @GetMapping("/me/emergency-contacts")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_VIEW_SELF')")
    @Operation(summary = "Lista los contactos de emergencia del usuario autenticado")
    public ApiResponse<List<EmergencyContactResponse>> myContacts(Authentication authentication) {
        return ApiResponse.ok("Contactos obtenidos",
                employeeService.getMyContacts(authentication.getName()));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_VIEW')")
    @Operation(summary = "Obtiene un empleado por UUID; los datos sensibles se enmascaran sin permiso")
    public ApiResponse<EmployeeResponse> get(@PathVariable UUID uuid) {
        boolean sensitive = accessGuard.has("EMPLOYEE_VIEW_SENSITIVE");
        return ApiResponse.ok("Empleado obtenido", employeeService.get(uuid, sensitive));
    }

    @PostMapping
    @PreAuthorize("@accessGuard.has('EMPLOYEE_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un empleado")
    public ApiResponse<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        return ApiResponse.ok("Empleado creado", employeeService.create(request));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_UPDATE')")
    @Operation(summary = "Actualiza un empleado")
    public ApiResponse<EmployeeResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody EmployeeRequest request) {
        return ApiResponse.ok("Empleado actualizado", employeeService.update(uuid, request));
    }

    @PostMapping("/{uuid}/terminate")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_TERMINATE')")
    @Operation(summary = "Registra la baja laboral de un empleado")
    public ApiResponse<EmployeeResponse> terminate(
            @PathVariable UUID uuid,
            @Valid @RequestBody TerminationRequest request) {
        return ApiResponse.ok("Empleado dado de baja", employeeService.terminate(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_TERMINATE')")
    @Operation(summary = "Deshabilita lógicamente un empleado sin subordinados")
    public ApiResponse<Void> delete(@PathVariable UUID uuid) {
        employeeService.delete(uuid);
        return ApiResponse.ok("Empleado deshabilitado");
    }

    @GetMapping("/{uuid}/emergency-contacts")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_VIEW')")
    @Operation(summary = "Lista los contactos de emergencia de un empleado")
    public ApiResponse<List<EmergencyContactResponse>> listContacts(@PathVariable UUID uuid) {
        return ApiResponse.ok("Contactos obtenidos", employeeService.listContacts(uuid));
    }

    @PostMapping("/{uuid}/emergency-contacts")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_UPDATE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega un contacto de emergencia")
    public ApiResponse<EmergencyContactResponse> addContact(
            @PathVariable UUID uuid,
            @Valid @RequestBody EmergencyContactRequest request) {
        return ApiResponse.ok("Contacto creado", employeeService.addContact(uuid, request));
    }

    @PutMapping("/{uuid}/emergency-contacts/{contactUuid}")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_UPDATE')")
    @Operation(summary = "Actualiza un contacto de emergencia")
    public ApiResponse<EmergencyContactResponse> updateContact(
            @PathVariable UUID uuid,
            @PathVariable UUID contactUuid,
            @Valid @RequestBody EmergencyContactRequest request) {
        return ApiResponse.ok("Contacto actualizado",
                employeeService.updateContact(uuid, contactUuid, request));
    }

    @DeleteMapping("/{uuid}/emergency-contacts/{contactUuid}")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_UPDATE')")
    @Operation(summary = "Deshabilita lógicamente un contacto de emergencia")
    public ApiResponse<Void> deleteContact(
            @PathVariable UUID uuid,
            @PathVariable UUID contactUuid) {
        employeeService.deleteContact(uuid, contactUuid);
        return ApiResponse.ok("Contacto deshabilitado");
    }
}
