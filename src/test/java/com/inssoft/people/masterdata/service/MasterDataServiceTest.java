package com.inssoft.people.masterdata.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.masterdata.dto.CatalogItemRequest;
import com.inssoft.people.masterdata.dto.CatalogItemResponse;
import com.inssoft.people.masterdata.dto.CatalogResponse;
import com.inssoft.people.masterdata.entity.Catalog;
import com.inssoft.people.masterdata.entity.CatalogItem;
import com.inssoft.people.masterdata.repository.CatalogItemRepository;
import com.inssoft.people.masterdata.repository.CatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataServiceTest {

    @Mock
    private CatalogRepository catalogRepository;

    @Mock
    private CatalogItemRepository itemRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private MasterDataService masterDataService;

    @Test
    void createsItemNormalizingCode() {
        Catalog catalog = catalog(1L, "GENDER");
        when(catalogRepository.findByCodeAndDeletedAtIsNull("GENDER")).thenReturn(Optional.of(catalog));
        when(itemRepository.existsByCatalogIdAndCodeIgnoreCaseAndDeletedAtIsNull(1L, "MASCULINO"))
                .thenReturn(false);
        when(itemRepository.save(any(CatalogItem.class))).thenAnswer(invocation -> {
            CatalogItem item = invocation.getArgument(0);
            item.setUuid(UUID.randomUUID());
            item.setCreatedAt(Instant.now());
            return item;
        });

        CatalogItemRequest request = new CatalogItemRequest(
                "masculino", "Masculino", "Género masculino", 1, null, true);

        CatalogItemResponse response = masterDataService.createItem("gender", request);

        assertThat(response.code()).isEqualTo("MASCULINO");
        assertThat(response.catalogCode()).isEqualTo("GENDER");
        assertThat(response.enabled()).isTrue();
        verify(auditService).record(eq("MASTERDATA_ITEM_CREATE"), eq("CatalogItem"), anyString(), any());
    }

    @Test
    void rejectsDuplicateItemCode() {
        Catalog catalog = catalog(1L, "GENDER");
        when(catalogRepository.findByCodeAndDeletedAtIsNull("GENDER")).thenReturn(Optional.of(catalog));
        when(itemRepository.existsByCatalogIdAndCodeIgnoreCaseAndDeletedAtIsNull(1L, "MASCULINO"))
                .thenReturn(true);

        CatalogItemRequest request = new CatalogItemRequest(
                "MASCULINO", "Masculino", null, 1, null, true);

        assertThatThrownBy(() -> masterDataService.createItem("GENDER", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MASCULINO");
    }

    @Test
    void listsEnabledItems() {
        Catalog catalog = catalog(1L, "GENDER");
        CatalogItem item = new CatalogItem();
        item.setId(10L);
        item.setUuid(UUID.randomUUID());
        item.setCatalog(catalog);
        item.setCode("FEMENINO");
        item.setName("Femenino");
        item.setSortOrder(2);
        item.setEnabled(true);

        when(catalogRepository.findByCodeAndDeletedAtIsNull("GENDER")).thenReturn(Optional.of(catalog));
        when(itemRepository.findByCatalogIdAndDeletedAtIsNullAndEnabledTrueOrderBySortOrderAscNameAsc(1L))
                .thenReturn(List.of(item));

        List<CatalogItemResponse> items = masterDataService.listItems("GENDER", false);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).code()).isEqualTo("FEMENINO");
    }

    @Test
    void listsCatalogs() {
        when(catalogRepository.findByDeletedAtIsNullAndEnabledTrueOrderByNameAsc())
                .thenReturn(List.of(catalog(1L, "GENDER")));

        List<CatalogResponse> catalogs = masterDataService.listCatalogs();

        assertThat(catalogs).hasSize(1);
        assertThat(catalogs.get(0).code()).isEqualTo("GENDER");
    }

    @Test
    void getsItemByUuid() {
        Catalog catalog = catalog(1L, "GENDER");
        CatalogItem item = itemEntity(10L, "MASCULINO", catalog);
        when(itemRepository.findByUuidAndDeletedAtIsNull(item.getUuid())).thenReturn(Optional.of(item));

        CatalogItemResponse response = masterDataService.getItem(item.getUuid());

        assertThat(response.code()).isEqualTo("MASCULINO");
        assertThat(response.catalogCode()).isEqualTo("GENDER");
    }

    @Test
    void updatesItem() {
        Catalog catalog = catalog(1L, "GENDER");
        CatalogItem item = itemEntity(10L, "MASCULINO", catalog);

        when(itemRepository.findByUuidAndDeletedAtIsNull(item.getUuid())).thenReturn(Optional.of(item));
        when(itemRepository.existsByCatalogIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                1L, "FEMENINO", item.getUuid())).thenReturn(false);
        when(itemRepository.save(any(CatalogItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CatalogItemRequest request = new CatalogItemRequest(
                "femenino", "Femenino", null, 2, null, true);

        CatalogItemResponse response = masterDataService.updateItem(item.getUuid(), request);

        assertThat(response.code()).isEqualTo("FEMENINO");
        verify(auditService).record(eq("MASTERDATA_ITEM_UPDATE"), eq("CatalogItem"),
                eq(item.getUuid().toString()), any(), any());
    }

    @Test
    void deletesItemLogically() {
        CatalogItem item = itemEntity(10L, "MASCULINO", catalog(1L, "GENDER"));
        when(itemRepository.findByUuidAndDeletedAtIsNull(item.getUuid())).thenReturn(Optional.of(item));

        masterDataService.deleteItem(item.getUuid());

        assertThat(item.getDeletedAt()).isNotNull();
        assertThat(item.isEnabled()).isFalse();
        verify(auditService).record(eq("MASTERDATA_ITEM_DELETE"), eq("CatalogItem"),
                eq(item.getUuid().toString()), any(), any());
    }

    private CatalogItem itemEntity(long id, String code, Catalog catalog) {
        CatalogItem item = new CatalogItem();
        item.setId(id);
        item.setUuid(UUID.randomUUID());
        item.setCatalog(catalog);
        item.setCode(code);
        item.setName(code);
        item.setSortOrder(1);
        item.setEnabled(true);
        item.setCreatedAt(Instant.now());
        return item;
    }

    private Catalog catalog(long id, String code) {
        Catalog catalog = new Catalog();
        catalog.setId(id);
        catalog.setUuid(UUID.randomUUID());
        catalog.setCode(code);
        catalog.setName(code);
        catalog.setEnabled(true);
        return catalog;
    }
}
