package com.inssoft.people.communication.repository;

import com.inssoft.people.communication.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @EntityGraph(attributePaths = {"company", "createdBy"})
    Optional<Announcement> findByUuidAndDeletedAtIsNull(UUID uuid);

    @Query("""
            SELECT a FROM Announcement a
            WHERE a.deletedAt IS NULL
              AND a.enabled = true
              AND a.company.id = :companyId
              AND a.publishedAt IS NOT NULL
              AND a.publishedAt <= :now
              AND (a.expiresAt IS NULL OR a.expiresAt > :now)
            """)
    Page<Announcement> findActive(
            @Param("companyId") Long companyId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            SELECT a FROM Announcement a
            WHERE a.deletedAt IS NULL
              AND a.company.id = :companyId
              AND (:q IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Announcement> search(
            @Param("companyId") Long companyId,
            @Param("q") String q,
            Pageable pageable);
}
