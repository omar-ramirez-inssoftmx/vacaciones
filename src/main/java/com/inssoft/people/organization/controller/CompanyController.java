package com.inssoft.people.organization.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.organization.dto.CompanyRequest;
import com.inssoft.people.organization.dto.CompanyResponse;
import com.inssoft.people.organization.service.CompanyService;
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
@RequestMapping("/api/v1/companies")
@Validated
@Tag(name = "Organization — Companies", description = "Empresas del grupo")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('ORG_VIEW')")
    @Operation(summary = "Lista empresas con paginación y filtros")
    public ApiResponse<PageResponse<CompanyResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Empresas obtenidas", companyService.list(q, enabled, page, size));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_VIEW')")
    @Operation(summary = "Obtiene una empresa por UUID")
    public ApiResponse<CompanyResponse> get(@PathVariable UUID uuid) {
        return ApiResponse.ok("Empresa obtenida", companyService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una empresa")
    public ApiResponse<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
        return ApiResponse.ok("Empresa creada", companyService.create(request));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @Operation(summary = "Actualiza una empresa")
    public ApiResponse<CompanyResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody CompanyRequest request) {
        return ApiResponse.ok("Empresa actualizada", companyService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @Operation(summary = "Deshabilita lógicamente una empresa sin dependencias")
    public ApiResponse<Void> delete(@PathVariable UUID uuid) {
        companyService.delete(uuid);
        return ApiResponse.ok("Empresa deshabilitada");
    }
}
