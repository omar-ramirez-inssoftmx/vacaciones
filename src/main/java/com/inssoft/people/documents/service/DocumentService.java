package com.inssoft.people.documents.service;

import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.security.AuthorizationService;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.documents.dto.DocumentResponse;
import com.inssoft.people.documents.dto.DocumentTypeResponse;
import com.inssoft.people.documents.entity.DocumentType;
import com.inssoft.people.documents.entity.DocumentVisibility;
import com.inssoft.people.documents.entity.EmployeeDocument;
import com.inssoft.people.documents.mapper.DocumentMapper;
import com.inssoft.people.documents.repository.DocumentTypeRepository;
import com.inssoft.people.documents.repository.EmployeeDocumentRepository;
import com.inssoft.people.documents.storage.FileStorageService;
import com.inssoft.people.documents.storage.StoredFile;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.mx.vacaciones.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentService {

    private final EmployeeDocumentRepository documentRepository;
    private final DocumentTypeRepository typeRepository;
    private final FileStorageService storageService;
    private final AuditService auditService;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;

    public DocumentService(
            EmployeeDocumentRepository documentRepository,
            DocumentTypeRepository typeRepository,
            FileStorageService storageService,
            AuditService auditService,
            EmployeeRepository employeeRepository,
            AuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.typeRepository = typeRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> types() {
        return typeRepository.findByDeletedAtIsNullAndEnabledTrueOrderBySortOrderAsc().stream()
                .map(DocumentMapper::toTypeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> listForEmployee(Employee employee, int page, int size) {
        Page<EmployeeDocument> result = documentRepository.findVisibleForEmployee(
                employee.getId(),
                DocumentVisibility.EMPLOYEE,
                PageRequestFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, DocumentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> search(
            Company company,
            UUID employeeUuid,
            String typeCode,
            String q,
            int page,
            int size) {

        String normalizedTypeCode = CodeNormalizer.trimToNull(typeCode) == null
                ? null
                : CodeNormalizer.normalize(typeCode);
        String query = CodeNormalizer.trimToNull(q);

        Page<EmployeeDocument> result = documentRepository.search(
                company.getId(),
                employeeUuid,
                normalizedTypeCode,
                query,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.from(result, DocumentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(
            UUID uuid,
            User actor,
            Company company,
            boolean canViewAll) {

        EmployeeDocument document = findDocument(uuid);
        authorize(document, actor, company, canViewAll);
        return DocumentMapper.toResponse(document);
    }

    @Transactional(readOnly = true)
    public Download download(
            UUID uuid,
            User actor,
            Company company,
            boolean canViewAll) {

        EmployeeDocument document = findDocument(uuid);
        authorize(document, actor, company, canViewAll);

        try (InputStream input = storageService.open(document.getStorageKey())) {
            byte[] content = input.readAllBytes();
            auditService.record("DOCUMENT_DOWNLOAD", "EmployeeDocument",
                    document.getUuid().toString(), DocumentMapper.toResponse(document));
            return new Download(content, document.getFileName(), document.getMimeType(), document.getSizeBytes());
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el documento: " + ex.getMessage());
        }
    }

    @Transactional
    public DocumentResponse upload(
            Employee employee,
            String typeCode,
            String name,
            String description,
            DocumentVisibility visibility,
            LocalDate expiresAt,
            MultipartFile file,
            User uploader) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo es obligatorio");
        }

        Long employeeId = employee.getId();
        employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", employeeId));

        DocumentType type = typeRepository.findByCode(CodeNormalizer.normalize(typeCode))
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de documento", typeCode));

        long maxBytes = (long) type.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException("El archivo excede el tamaño máximo de "
                    + type.getMaxSizeMb() + " MB para este tipo de documento");
        }
        if (type.isRequiresExpiry() && expiresAt == null) {
            throw new BusinessException("La fecha de vencimiento es obligatoria para este tipo de documento");
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String mimeType = resolveMimeType(file, fileName, type.getAllowedMime());

        String documentName = name == null || name.isBlank() ? fileName : name.trim();
        if (documentName.length() > 200) {
            throw new BusinessException("El nombre del documento no puede exceder 200 caracteres");
        }
        String documentDescription = CodeNormalizer.trimToNull(description);
        if (documentDescription != null && documentDescription.length() > 500) {
            throw new BusinessException("La descripción no puede exceder 500 caracteres");
        }

        StoredFile stored;
        try {
            stored = storageService.store(file.getInputStream(), fileName, mimeType);
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el archivo: " + ex.getMessage());
        }

        try {
            EmployeeDocument document = new EmployeeDocument();
            document.setCompany(employee.getCompany());
            document.setEmployee(employee);
            document.setType(type);
            document.setName(documentName);
            document.setDescription(documentDescription);
            document.setFileName(fileName);
            document.setMimeType(mimeType);
            document.setSizeBytes(stored.sizeBytes());
            document.setSha256(stored.sha256());
            document.setStorageProvider(stored.provider());
            document.setStorageKey(stored.storageKey());
            document.setVisibility(visibility == null ? DocumentVisibility.EMPLOYEE : visibility);
            document.setExpiresAt(expiresAt);
            document.setUploadedBy(uploader);
            document.setEnabled(true);

            EmployeeDocument saved = documentRepository.save(document);
            DocumentResponse response = DocumentMapper.toResponse(saved);
            auditService.record("DOCUMENT_UPLOAD", "EmployeeDocument",
                    saved.getUuid().toString(), response);
            return response;
        } catch (RuntimeException ex) {
            storageService.delete(stored.storageKey());
            throw ex;
        }
    }

    @Transactional
    public void delete(UUID uuid, User actor, Company company) {
        EmployeeDocument document = findDocument(uuid);
        if (company != null && !document.getCompany().getId().equals(company.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "No puedes eliminar documentos de otra empresa");
        }

        DocumentResponse previous = DocumentMapper.toResponse(document);
        document.setDeletedAt(java.time.Instant.now());
        document.setEnabled(false);
        documentRepository.save(document);
        auditService.record("DOCUMENT_DELETE", "EmployeeDocument",
                uuid.toString(), previous, null);
    }

    private void authorize(
            EmployeeDocument document,
            User actor,
            Company company,
            boolean canViewAll) {

        if (company != null && !document.getCompany().getId().equals(company.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "No puedes consultar documentos de otra empresa");
        }

        if (document.getEmployee() != null
                && authorizationService.canViewDocument(actor, document.getEmployee())) {
            if (canViewAll || authorizationService.hasPermission("DOCUMENT_MANAGE")) {
                return;
            }
            if (document.getVisibility() == DocumentVisibility.EMPLOYEE
                    && document.getType().isEmployeeVisible()) {
                return;
            }
        }

        throw new BusinessException(ErrorCode.FORBIDDEN,
                "No puedes consultar este documento");
    }

    private EmployeeDocument findDocument(UUID uuid) {
        return documentRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Documento", uuid));
    }

    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "documento";
        }
        try {
            String fileName = Paths.get(original).getFileName().toString();
            return fileName.length() > 255 ? fileName.substring(0, 255) : fileName;
        } catch (RuntimeException ex) {
            return "documento";
        }
    }

    private String resolveMimeType(MultipartFile file, String fileName, String allowedMime) {
        String contentType = file.getContentType();
        String normalized = contentType == null
                ? null
                : contentType.trim().toLowerCase(Locale.ROOT);

        if (normalized == null || normalized.isBlank() || "application/octet-stream".equals(normalized)) {
            normalized = inferMimeType(fileName);
        }

        List<String> allowed = List.of(allowedMime.split(",")).stream()
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();

        if (normalized == null || !allowed.contains(normalized)) {
            throw new BusinessException("Tipo de archivo no permitido. Permitidos: " + allowedMime);
        }
        return normalized;
    }

    private String inferMimeType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return null;
    }

    public record Download(byte[] content, String fileName, String mimeType, long sizeBytes) {
    }
}
