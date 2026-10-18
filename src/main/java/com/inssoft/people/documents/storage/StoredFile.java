package com.inssoft.people.documents.storage;

import java.util.UUID;

public record StoredFile(
        UUID storageKey,
        String provider,
        long sizeBytes,
        String sha256
) {
}
