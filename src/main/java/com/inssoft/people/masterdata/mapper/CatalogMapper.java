package com.inssoft.people.masterdata.mapper;

import com.inssoft.people.masterdata.dto.CatalogItemRequest;
import com.inssoft.people.masterdata.dto.CatalogItemResponse;
import com.inssoft.people.masterdata.dto.CatalogResponse;
import com.inssoft.people.masterdata.entity.Catalog;
import com.inssoft.people.masterdata.entity.CatalogItem;

public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CatalogResponse toResponse(Catalog catalog) {
        return new CatalogResponse(
                catalog.getUuid(),
                catalog.getCode(),
                catalog.getName(),
                catalog.getDescription()
        );
    }

    public static CatalogItemResponse toResponse(CatalogItem item) {
        return new CatalogItemResponse(
                item.getUuid(),
                item.getCatalog().getCode(),
                item.getCode(),
                item.getName(),
                item.getDescription(),
                item.getSortOrder(),
                item.getMetadataJson(),
                item.isEnabled()
        );
    }

    public static void apply(CatalogItemRequest request, CatalogItem item) {
        item.setCode(request.code().trim().toUpperCase());
        item.setName(request.name().trim());
        item.setDescription(request.description() == null ? null : request.description().trim());
        item.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        item.setMetadataJson(request.metadataJson());
        if (request.enabled() != null) {
            item.setEnabled(request.enabled());
        }
    }
}
