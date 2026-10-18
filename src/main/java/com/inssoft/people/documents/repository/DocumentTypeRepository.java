package com.inssoft.people.documents.repository;

import com.inssoft.people.documents.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    Optional<DocumentType> findByUuidAndDeletedAtIsNull(UUID uuid);

    @Query("""
            SELECT t FROM DocumentType t
            WHERE LOWER(t.catalogItem.code) = LOWER(:code)
              AND t.deletedAt IS NULL
              AND t.enabled = true
            """)
    Optional<DocumentType> findByCode(@Param("code") String code);

    List<DocumentType> findByDeletedAtIsNullAndEnabledTrueOrderBySortOrderAsc();
}
