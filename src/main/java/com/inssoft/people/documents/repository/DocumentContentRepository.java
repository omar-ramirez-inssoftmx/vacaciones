package com.inssoft.people.documents.repository;

import com.inssoft.people.documents.entity.DocumentContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentContentRepository extends JpaRepository<DocumentContent, Long> {

    Optional<DocumentContent> findByStorageKey(UUID storageKey);

    long deleteByStorageKey(UUID storageKey);
}
