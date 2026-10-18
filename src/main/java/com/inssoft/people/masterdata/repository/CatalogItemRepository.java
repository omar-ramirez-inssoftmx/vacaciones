package com.inssoft.people.masterdata.repository;

import com.inssoft.people.masterdata.entity.CatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {

    List<CatalogItem> findByCatalogIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(Long catalogId);

    List<CatalogItem> findByCatalogIdAndDeletedAtIsNullAndEnabledTrueOrderBySortOrderAscNameAsc(Long catalogId);

    Optional<CatalogItem> findByUuidAndDeletedAtIsNull(UUID uuid);

    boolean existsByCatalogIdAndCodeIgnoreCaseAndDeletedAtIsNull(Long catalogId, String code);

    boolean existsByCatalogIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(Long catalogId, String code, UUID uuid);
}
