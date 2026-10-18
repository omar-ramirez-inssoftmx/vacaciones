package com.inssoft.people.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actor IS NULL OR LOWER(a.actorUsername) LIKE LOWER(CONCAT('%', :actor, '%')))
              AND (:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%')))
              AND (:entity IS NULL OR LOWER(a.entity) LIKE LOWER(CONCAT('%', :entity, '%')))
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            """)
    Page<AuditLog> search(
            @Param("actor") String actor,
            @Param("action") String action,
            @Param("entity") String entity,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
