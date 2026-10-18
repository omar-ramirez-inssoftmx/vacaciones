package com.inssoft.people.documents.storage;

import com.inssoft.people.documents.entity.DocumentContent;
import com.inssoft.people.documents.repository.DocumentContentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseFileStorageServiceTest {

    @Mock
    private DocumentContentRepository contentRepository;

    @InjectMocks
    private DatabaseFileStorageService service;

    @Test
    void storesContentAndComputesHash() {
        when(contentRepository.save(any(DocumentContent.class))).thenAnswer(invocation -> {
            DocumentContent content = invocation.getArgument(0);
            content.setId(1L);
            return content;
        });

        byte[] bytes = "contenido".getBytes(StandardCharsets.UTF_8);
        StoredFile stored = service.store(
                new ByteArrayInputStream(bytes), "archivo.pdf", "application/pdf");

        assertThat(stored.provider()).isEqualTo("DATABASE");
        assertThat(stored.sizeBytes()).isEqualTo(bytes.length);
        assertThat(stored.sha256()).hasSize(64);
        assertThat(stored.storageKey()).isNotNull();
        verify(contentRepository).save(any(DocumentContent.class));
    }

    @Test
    void opensAndDeletesContent() throws Exception {
        DocumentContent content = new DocumentContent();
        content.setStorageKey(java.util.UUID.randomUUID());
        content.setContent("hola".getBytes(StandardCharsets.UTF_8));
        content.setSizeBytes(4);
        content.setSha256("hash");

        when(contentRepository.findByStorageKey(content.getStorageKey()))
                .thenReturn(Optional.of(content));

        try (InputStream input = service.open(content.getStorageKey())) {
            assertThat(input.readAllBytes()).isEqualTo("hola".getBytes(StandardCharsets.UTF_8));
        }

        service.delete(content.getStorageKey());
        verify(contentRepository).deleteByStorageKey(content.getStorageKey());
    }
}
