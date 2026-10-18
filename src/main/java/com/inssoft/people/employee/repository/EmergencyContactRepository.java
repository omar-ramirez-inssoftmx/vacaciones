package com.inssoft.people.employee.repository;

import com.inssoft.people.employee.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    @Query("""
            SELECT c FROM EmergencyContact c
            WHERE c.employee.id = :employeeId
              AND c.deletedAt IS NULL
            ORDER BY c.primary DESC, c.name ASC
            """)
    List<EmergencyContact> findActiveByEmployeeId(@Param("employeeId") Long employeeId);

    Optional<EmergencyContact> findByUuidAndEmployee_IdAndDeletedAtIsNull(UUID uuid, Long employeeId);

    boolean existsByEmployee_IdAndDeletedAtIsNull(Long employeeId);

    boolean existsByEmployee_IdAndPrimaryTrueAndDeletedAtIsNull(Long employeeId);

    boolean existsByEmployee_IdAndPrimaryTrueAndUuidNotAndDeletedAtIsNull(Long employeeId, UUID uuid);
}
