package com.inssoft.people.common.audit;

import com.inssoft.people.common.api.ApiResponse;
import com.inssoft.people.common.api.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("@accessGuard.has('AUDIT_VIEW')")
public class AuditApiController {

    private final AuditLogRepository repository;

    public AuditApiController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = repository.search(actor, action, entity, from, to,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return ApiResponse.ok("Auditoría obtenida", PageResponse.from(result, audit ->
                new AuditLogResponse(
                        audit.getUuid(),
                        audit.getActorUsername(),
                        audit.getAction(),
                        audit.getEntity(),
                        audit.getEntityId(),
                        audit.getOldValue(),
                        audit.getNewValue(),
                        audit.getIp(),
                        audit.getUserAgent(),
                        audit.getCorrelationId(),
                        audit.getResult(),
                        audit.getCreatedAt())));
    }
}
