package com.inssoft.people.masterdata.repository;

import com.inssoft.people.masterdata.entity.Catalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogRepository extends JpaRepository<Catalog, Long> {

    Optional<Catalog> findByUuidAndDeletedAtIsNull(UUID uuid);

    Optional<Catalog> findByCodeAndDeletedAtIsNull(String code);

    List<Catalog> findByDeletedAtIsNullAndEnabledTrueOrderByNameAsc();

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);
}
