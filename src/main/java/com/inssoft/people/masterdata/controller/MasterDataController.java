package com.inssoft.people.masterdata.controller;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.masterdata.dto.CatalogItemRequest;
import com.inssoft.people.masterdata.dto.CatalogItemResponse;
import com.inssoft.people.masterdata.dto.CatalogResponse;
import com.inssoft.people.masterdata.service.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/masterdata")
@Validated
@Tag(name = "Master Data", description = "Catálogos reutilizables del sistema")
@SecurityRequirement(name = "bearerAuth")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @GetMapping("/catalogs")
    @PreAuthorize("@accessGuard.has('MASTERDATA_VIEW')")
    @Operation(summary = "Lista los catálogos activos")
    public ApiResponse<List<CatalogResponse>> listCatalogs() {
        return ApiResponse.ok("Catálogos obtenidos", masterDataService.listCatalogs());
    }

    @GetMapping("/catalogs/{catalogCode}/items")
    @PreAuthorize("@accessGuard.has('MASTERDATA_VIEW')")
    @Operation(summary = "Lista los items de un catálogo")
    public ApiResponse<List<CatalogItemResponse>> listItems(
            @PathVariable
            @NotBlank(message = "El código de catálogo es obligatorio")
            @Size(max = 50, message = "El código de catálogo no puede exceder 50 caracteres")
            String catalogCode,
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok("Items obtenidos", masterDataService.listItems(catalogCode, includeDisabled));
    }

    @GetMapping("/items/{uuid}")
    @PreAuthorize("@accessGuard.has('MASTERDATA_VIEW')")
    @Operation(summary = "Obtiene un item de catálogo por UUID")
    public ApiResponse<CatalogItemResponse> getItem(@PathVariable UUID uuid) {
        return ApiResponse.ok("Item obtenido", masterDataService.getItem(uuid));
    }

    @PostMapping("/catalogs/{catalogCode}/items")
    @PreAuthorize("@accessGuard.has('MASTERDATA_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un item en un catálogo")
    public ApiResponse<CatalogItemResponse> createItem(
            @PathVariable
            @NotBlank(message = "El código de catálogo es obligatorio")
            @Size(max = 50, message = "El código de catálogo no puede exceder 50 caracteres")
            String catalogCode,
            @Valid @RequestBody CatalogItemRequest request) {
        return ApiResponse.ok("Item creado", masterDataService.createItem(catalogCode, request));
    }

    @PutMapping("/items/{uuid}")
    @PreAuthorize("@accessGuard.has('MASTERDATA_MANAGE')")
    @Operation(summary = "Actualiza un item de catálogo")
    public ApiResponse<CatalogItemResponse> updateItem(
            @PathVariable UUID uuid,
            @Valid @RequestBody CatalogItemRequest request) {
        return ApiResponse.ok("Item actualizado", masterDataService.updateItem(uuid, request));
    }

    @DeleteMapping("/items/{uuid}")
    @PreAuthorize("@accessGuard.has('MASTERDATA_MANAGE')")
    @Operation(summary = "Deshabilita lógicamente un item de catálogo")
    public ApiResponse<Void> deleteItem(@PathVariable UUID uuid) {
        masterDataService.deleteItem(uuid);
        return ApiResponse.ok("Item deshabilitado");
    }
}
