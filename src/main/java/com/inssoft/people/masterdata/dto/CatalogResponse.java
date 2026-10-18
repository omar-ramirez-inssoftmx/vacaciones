package com.inssoft.people.masterdata.dto;

import java.util.UUID;

public record CatalogResponse(
        UUID uuid,
        String code,
        String name,
        String description
) {
}
