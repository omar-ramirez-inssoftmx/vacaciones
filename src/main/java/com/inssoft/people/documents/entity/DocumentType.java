package com.inssoft.people.documents.entity;

import com.inssoft.people.common.entity.SoftDeleteEntity;
import com.inssoft.people.masterdata.entity.CatalogItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "doc_document_type")
@Getter
@Setter
public class DocumentType extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_item_id", nullable = false)
    private CatalogItem catalogItem;

    @Column(name = "requires_expiry", nullable = false)
    private boolean requiresExpiry;

    @Column(name = "employee_visible", nullable = false)
    private boolean employeeVisible = true;

    @Column(name = "max_size_mb", nullable = false)
    private int maxSizeMb = 20;

    @Column(name = "allowed_mime", nullable = false, length = 300)
    private String allowedMime = "application/pdf,image/jpeg,image/png";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
