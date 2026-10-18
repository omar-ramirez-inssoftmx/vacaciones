package com.inssoft.people.masterdata.dto;

import java.util.UUID;

public record CatalogItemResponse(
        UUID uuid,
        String catalogCode,
        String code,
        String name,
        String description,
        int sortOrder,
        String metadataJson,
        boolean enabled
) {
}
