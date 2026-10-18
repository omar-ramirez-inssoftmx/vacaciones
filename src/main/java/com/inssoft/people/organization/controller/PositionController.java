package com.inssoft.people.organization.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.organization.dto.PositionRequest;
import com.inssoft.people.organization.dto.PositionResponse;
import com.inssoft.people.organization.service.PositionService;
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
@RequestMapping("/api/v1/positions")
@Validated
@Tag(name = "Organization — Positions", description = "Puestos por departamento")
@SecurityRequirement(name = "bearerAuth")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    @PreAuthorize("@accessGuard.has('ORG_VIEW')")
    @Operation(summary = "Lista puestos; filtrar por departmentUuid")
    public ApiResponse<PageResponse<PositionResponse>> list(
            @RequestParam(required = false) UUID departmentUuid,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("Puestos obtenidos",
                positionService.list(departmentUuid, q, enabled, page, size));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_VIEW')")
    @Operation(summary = "Obtiene un puesto por UUID")
    public ApiResponse<PositionResponse> get(@PathVariable UUID uuid) {
        return ApiResponse.ok("Puesto obtenido", positionService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un puesto")
    public ApiResponse<PositionResponse> create(@Valid @RequestBody PositionRequest request) {
        return ApiResponse.ok("Puesto creado", positionService.create(request));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @Operation(summary = "Actualiza un puesto")
    public ApiResponse<PositionResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody PositionRequest request) {
        return ApiResponse.ok("Puesto actualizado", positionService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("@accessGuard.has('ORG_MANAGE')")
    @Operation(summary = "Deshabilita lógicamente un puesto")
    public ApiResponse<Void> delete(@PathVariable UUID uuid) {
        positionService.delete(uuid);
        return ApiResponse.ok("Puesto deshabilitado");
    }
}
