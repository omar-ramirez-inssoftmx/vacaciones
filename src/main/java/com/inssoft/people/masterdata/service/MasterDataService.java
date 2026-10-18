package com.inssoft.people.masterdata.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.masterdata.dto.CatalogItemRequest;
import com.inssoft.people.masterdata.dto.CatalogItemResponse;
import com.inssoft.people.masterdata.dto.CatalogResponse;
import com.inssoft.people.masterdata.entity.Catalog;
import com.inssoft.people.masterdata.entity.CatalogItem;
import com.inssoft.people.masterdata.mapper.CatalogMapper;
import com.inssoft.people.masterdata.repository.CatalogItemRepository;
import com.inssoft.people.masterdata.repository.CatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MasterDataService {

    private final CatalogRepository catalogRepository;
    private final CatalogItemRepository itemRepository;
    private final AuditService auditService;

    public MasterDataService(
            CatalogRepository catalogRepository,
            CatalogItemRepository itemRepository,
            AuditService auditService) {
        this.catalogRepository = catalogRepository;
        this.itemRepository = itemRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CatalogResponse> listCatalogs() {
        return catalogRepository.findByDeletedAtIsNullAndEnabledTrueOrderByNameAsc().stream()
                .map(CatalogMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listItems(String catalogCode, boolean includeDisabled) {
        Catalog catalog = findCatalog(catalogCode);
        List<CatalogItem> items = includeDisabled
                ? itemRepository.findByCatalogIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(catalog.getId())
                : itemRepository.findByCatalogIdAndDeletedAtIsNullAndEnabledTrueOrderBySortOrderAscNameAsc(catalog.getId());
        return items.stream().map(CatalogMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CatalogItemResponse getItem(UUID uuid) {
        return CatalogMapper.toResponse(findItem(uuid));
    }

    @Transactional
    public CatalogItemResponse createItem(String catalogCode, CatalogItemRequest request) {
        Catalog catalog = findCatalog(catalogCode);
        String normalizedCode = normalizeCode(request.code());

        if (itemRepository.existsByCatalogIdAndCodeIgnoreCaseAndDeletedAtIsNull(catalog.getId(), normalizedCode)) {
            throw new ConflictException("Ya existe el código " + normalizedCode + " en el catálogo " + catalog.getCode());
        }

        CatalogItem item = new CatalogItem();
        item.setCatalog(catalog);
        CatalogMapper.apply(request, item);
        item.setCode(normalizedCode);

        CatalogItem saved = itemRepository.save(item);
        CatalogItemResponse response = CatalogMapper.toResponse(saved);
        auditService.record("MASTERDATA_ITEM_CREATE", "CatalogItem", saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public CatalogItemResponse updateItem(UUID uuid, CatalogItemRequest request) {
        CatalogItem item = findItem(uuid);
        CatalogItemResponse previous = CatalogMapper.toResponse(item);

        String normalizedCode = normalizeCode(request.code());
        if (itemRepository.existsByCatalogIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                item.getCatalog().getId(), normalizedCode, uuid)) {
            throw new ConflictException("Ya existe el código " + normalizedCode + " en el catálogo "
                    + item.getCatalog().getCode());
        }

        CatalogMapper.apply(request, item);
        item.setCode(normalizedCode);

        CatalogItem saved = itemRepository.save(item);
        CatalogItemResponse response = CatalogMapper.toResponse(saved);
        auditService.record("MASTERDATA_ITEM_UPDATE", "CatalogItem", saved.getUuid().toString(), previous, response);
        return response;
    }

    @Transactional
    public void deleteItem(UUID uuid) {
        CatalogItem item = findItem(uuid);
        CatalogItemResponse previous = CatalogMapper.toResponse(item);

        item.setDeletedAt(Instant.now());
        item.setEnabled(false);
        itemRepository.save(item);

        auditService.record("MASTERDATA_ITEM_DELETE", "CatalogItem", uuid.toString(), previous, null);
    }

    private Catalog findCatalog(String catalogCode) {
        String normalized = normalizeCode(catalogCode);
        return catalogRepository.findByCodeAndDeletedAtIsNull(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo", normalized));
    }

    private CatalogItem findItem(UUID uuid) {
        return itemRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Item de catálogo", uuid));
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
