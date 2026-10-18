package com.inssoft.people.organization.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.organization.dto.BranchRequest;
import com.inssoft.people.organization.dto.BranchResponse;
import com.inssoft.people.organization.service.BranchService;
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
@RequestMapping("/api/v1/branches")
@Validated
@Tag(name = "Organization — Branches", description = "Sucursales por empresa")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('ORG_VIEW')")
    @Operation(summary = "Lista sucursales; filtrar por companyUuid")
    public ApiResponse<PageResponse<BranchResponse>> list(
            @RequestParam(required = false) UUID companyUuid,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Sucursales obtenidas",
                branchService.list(companyUuid, q, enabled, page, size));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_VIEW')")
    @Operation(summary = "Obtiene una sucursal por UUID")
    public ApiResponse<BranchResponse> get(@PathVariable UUID uuid) {
        return ApiResponse.ok("Sucursal obtenida", branchService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una sucursal")
    public ApiResponse<BranchResponse> create(@Valid @RequestBody BranchRequest request) {
        return ApiResponse.ok("Sucursal creada", branchService.create(request));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @Operation(summary = "Actualiza una sucursal")
    public ApiResponse<BranchResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody BranchRequest request) {
        return ApiResponse.ok("Sucursal actualizada", branchService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @Operation(summary = "Deshabilita lógicamente una sucursal sin dependencias")
    public ApiResponse<Void> delete(@PathVariable UUID uuid) {
        branchService.delete(uuid);
        return ApiResponse.ok("Sucursal deshabilitada");
    }
}
