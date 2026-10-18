package com.inssoft.people.masterdata.repository;

import com.inssoft.people.masterdata.entity.CatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {

    List<CatalogItem> findByCatalogIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(Long catalogId);

    List<CatalogItem> findByCatalogIdAndDeletedAtIsNullAndEnabledTrueOrderBySortOrderAscNameAsc(Long catalogId);

    Optional<CatalogItem> findByUuidAndDeletedAtIsNull(UUID uuid);

    @Query("""
            SELECT i FROM CatalogItem i
            WHERE i.catalog.code = :catalogCode
              AND LOWER(i.code) = LOWER(:itemCode)
              AND i.deletedAt IS NULL
            """)
    Optional<CatalogItem> findByCatalogCodeAndItemCode(
            @Param("catalogCode") String catalogCode,
            @Param("itemCode") String itemCode);

    boolean existsByCatalogIdAndCodeIgnoreCaseAndDeletedAtIsNull(Long catalogId, String code);

    boolean existsByCatalogIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(Long catalogId, String code, UUID uuid);
}
