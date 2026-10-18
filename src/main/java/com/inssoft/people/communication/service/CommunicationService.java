package com.inssoft.people.communication.service;

import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.communication.dto.AnnouncementRequest;
import com.inssoft.people.communication.dto.AnnouncementResponse;
import com.inssoft.people.communication.entity.Announcement;
import com.inssoft.people.communication.repository.AnnouncementRepository;
import com.inssoft.people.organization.entity.Company;
import com.mx.vacaciones.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class CommunicationService {

    private final AnnouncementRepository repository;
    private final AuditService auditService;

    public CommunicationService(AnnouncementRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> active(Company company, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        Page<Announcement> result = repository.findActive(
                company.getId(), now,
                PageRequestFactory.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt")));
        return PageResponse.from(result, announcement -> toResponse(announcement, now));
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> search(Company company, String q, int page, int size) {
        Page<Announcement> result = repository.search(
                company.getId(), CodeNormalizer.trimToNull(q),
                PageRequestFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        LocalDateTime now = LocalDateTime.now();
        return PageResponse.from(result, announcement -> toResponse(announcement, now));
    }

    @Transactional
    public AnnouncementResponse create(
            Company company, AnnouncementRequest request, User creator) {
        Announcement announcement = new Announcement();
        announcement.setCompany(company);
        announcement.setTitle(request.title().trim());
        announcement.setBody(request.body().trim());
        announcement.setPriority(normalizePriority(request.priority()));
        announcement.setExpiresAt(request.expiresAt());
        announcement.setPublishedAt(LocalDateTime.now());
        announcement.setCreatedBy(creator);
        announcement.setEnabled(true);

        Announcement saved = repository.save(announcement);
        AnnouncementResponse response = toResponse(saved, LocalDateTime.now());
        auditService.record("COMMUNICATION_PUBLISH", "Announcement",
                saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public void delete(UUID uuid, Company company, User actor) {
        Announcement announcement = repository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Comunicado", uuid));
        if (!announcement.getCompany().getId().equals(company.getId())) {
            throw new com.inssoft.people.common.exception.BusinessException(
                    com.inssoft.people.common.api.ErrorCode.FORBIDDEN,
                    "No puedes eliminar comunicados de otra empresa");
        }
        AnnouncementResponse previous = toResponse(announcement, LocalDateTime.now());
        announcement.setDeletedAt(java.time.Instant.now());
        announcement.setEnabled(false);
        repository.save(announcement);
        auditService.record("COMMUNICATION_DELETE", "Announcement", uuid.toString(), previous, null);
    }

    private AnnouncementResponse toResponse(Announcement announcement, LocalDateTime now) {
        boolean active = announcement.isEnabled()
                && announcement.getDeletedAt() == null
                && announcement.getPublishedAt() != null
                && !announcement.getPublishedAt().isAfter(now)
                && (announcement.getExpiresAt() == null || announcement.getExpiresAt().isAfter(now));

        return new AnnouncementResponse(
                announcement.getUuid(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getPriority(),
                announcement.getPublishedAt(),
                announcement.getExpiresAt(),
                announcement.getCreatedBy() == null ? null : announcement.getCreatedBy().getUsername(),
                active);
    }

    private String normalizePriority(String priority) {
        String value = priority == null ? "NORMAL" : priority.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("NORMAL") && !value.equals("IMPORTANT") && !value.equals("URGENT")) {
            return "NORMAL";
        }
        return value;
    }
}
