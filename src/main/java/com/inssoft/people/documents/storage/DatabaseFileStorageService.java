package com.inssoft.people.documents.storage;

import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.documents.entity.DocumentContent;
import com.inssoft.people.documents.repository.DocumentContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DatabaseFileStorageService implements FileStorageService {

    private final DocumentContentRepository contentRepository;

    public DatabaseFileStorageService(DocumentContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Override
    @Transactional
    public StoredFile store(InputStream input, String originalFileName, String mimeType) {
        try {
            byte[] bytes = input.readAllBytes();
            if (bytes.length == 0) {
                throw new BusinessException("El archivo está vacío");
            }

            String sha256 = sha256(bytes);
            DocumentContent content = new DocumentContent();
            content.setStorageKey(UUID.randomUUID());
            content.setSha256(sha256);
            content.setSizeBytes(bytes.length);
            content.setContent(bytes);
            DocumentContent saved = contentRepository.save(content);

            return new StoredFile(saved.getStorageKey(), provider(), saved.getSizeBytes(), saved.getSha256());
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el archivo: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public InputStream open(UUID storageKey) {
        DocumentContent content = contentRepository.findByStorageKey(storageKey)
                .orElseThrow(() -> new BusinessException("Contenido de documento no encontrado"));
        return new java.io.ByteArrayInputStream(content.getContent());
    }

    @Override
    @Transactional
    public void delete(UUID storageKey) {
        contentRepository.deleteByStorageKey(storageKey);
    }

    @Override
    public String provider() {
        return "DATABASE";
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
