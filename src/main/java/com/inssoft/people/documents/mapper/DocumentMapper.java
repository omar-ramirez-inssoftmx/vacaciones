package com.inssoft.people.documents.mapper;

import com.inssoft.people.documents.dto.DocumentResponse;
import com.inssoft.people.documents.dto.DocumentTypeResponse;
import com.inssoft.people.documents.entity.DocumentType;
import com.inssoft.people.documents.entity.EmployeeDocument;
import com.inssoft.people.employee.mapper.EmployeeMapper;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    public static DocumentTypeResponse toTypeResponse(DocumentType type) {
        return new DocumentTypeResponse(
                type.getUuid(),
                type.getCatalogItem().getCode(),
                type.getCatalogItem().getName(),
                type.isRequiresExpiry(),
                type.isEmployeeVisible(),
                type.getMaxSizeMb(),
                type.getAllowedMime()
        );
    }

    public static DocumentResponse toResponse(EmployeeDocument document) {
        return new DocumentResponse(
                document.getUuid(),
                document.getEmployee() == null ? null : document.getEmployee().getUuid(),
                document.getEmployee() == null ? null : EmployeeMapper.fullName(document.getEmployee()),
                document.getType().getCatalogItem().getCode(),
                document.getType().getCatalogItem().getName(),
                document.getName(),
                document.getDescription(),
                document.getFileName(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getSha256(),
                document.getVisibility(),
                document.getExpiresAt(),
                document.getUploadedBy() == null ? null : document.getUploadedBy().getUsername(),
                document.getCreatedAt(),
                document.isEnabled()
        );
    }
}
