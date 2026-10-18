package com.inssoft.people.organization.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.organization.dto.DepartmentRequest;
import com.inssoft.people.organization.dto.DepartmentResponse;
import com.inssoft.people.organization.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@Validated
@Tag(name = "Organization — Departments", description = "Departamentos por sucursal y jerarquía")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('ORG_VIEW')")
    @Operation(summary = "Lista departamentos; filtrar por branchUuid")
    public ApiResponse<PageResponse<DepartmentResponse>> list(
            @RequestParam(required = false) UUID branchUuid,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Departamentos obtenidos",
                departmentService.list(branchUuid, q, enabled, page, size));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_VIEW')")
    @Operation(summary = "Obtiene un departamento por UUID")
    public ApiResponse<DepartmentResponse> get(@PathVariable UUID uuid) {
        return ApiResponse.ok("Departamento obtenido", departmentService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un departamento")
    public ApiResponse<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.ok("Departamento creado", departmentService.create(request));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @Operation(summary = "Actualiza un departamento")
    public ApiResponse<DepartmentResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.ok("Departamento actualizado", departmentService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @Operation(summary = "Deshabilita lógicamente un departamento sin dependencias")
    public ApiResponse<Void> delete(@PathVariable UUID uuid) {
        departmentService.delete(uuid);
        return ApiResponse.ok("Departamento deshabilitado");
    }
}
