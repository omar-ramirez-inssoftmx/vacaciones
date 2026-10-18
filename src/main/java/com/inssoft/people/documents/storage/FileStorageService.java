package com.inssoft.people.documents.storage;

import java.io.InputStream;
import java.util.UUID;

public interface FileStorageService {

    StoredFile store(InputStream input, String originalFileName, String mimeType);

    InputStream open(UUID storageKey);

    void delete(UUID storageKey);

    String provider();
}
