package com.inssoft.people.organization.service;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.organization.dto.PositionRequest;
import com.inssoft.people.organization.dto.PositionResponse;
import com.inssoft.people.organization.entity.Branch;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.organization.entity.Department;
import com.inssoft.people.organization.entity.Position;
import com.inssoft.people.organization.repository.DepartmentRepository;
import com.inssoft.people.organization.repository.PositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
class PositionServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PositionService positionService;

    @Test
    void createsPositionUnderDepartment() {
        Department department = department();
        when(departmentRepository.findByUuidAndDeletedAtIsNull(department.getUuid()))
                .thenReturn(Optional.of(department));
        when(positionRepository.existsByDepartmentIdAndCodeIgnoreCaseAndDeletedAtIsNull(5L, "DEV-SR"))
                .thenReturn(false);
        when(positionRepository.save(any(Position.class))).thenAnswer(invocation -> {
            Position position = invocation.getArgument(0);
            position.setUuid(UUID.randomUUID());
            position.setCreatedAt(Instant.now());
            return position;
        });

        PositionRequest request = new PositionRequest(
                department.getUuid(), "dev-sr", "Desarrollador Senior", null, "SENIOR", true);

        PositionResponse response = positionService.create(request);

        assertThat(response.code()).isEqualTo("DEV-SR");
        assertThat(response.departmentUuid()).isEqualTo(department.getUuid());
        verify(auditService).record(eq("ORG_POSITION_CREATE"), eq("Position"), anyString(), any());
    }

    @Test
    void rejectsDuplicatePositionCode() {
        Department department = department();
        when(departmentRepository.findByUuidAndDeletedAtIsNull(department.getUuid()))
                .thenReturn(Optional.of(department));
        when(positionRepository.existsByDepartmentIdAndCodeIgnoreCaseAndDeletedAtIsNull(5L, "DEV-SR"))
                .thenReturn(true);

        PositionRequest request = new PositionRequest(
                department.getUuid(), "DEV-SR", "Desarrollador Senior", null, "SENIOR", true);

        assertThatThrownBy(() -> positionService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listsPositions() {
        Position position = positionEntity(9L, "DEV-SR", department());
        when(positionRepository.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(position)));

        PageResponse<PositionResponse> response = positionService.list(null, null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).code()).isEqualTo("DEV-SR");
    }

    @Test
    void updatesPosition() {
        Department department = department();
        Position position = positionEntity(9L, "DEV-SR", department);

        when(positionRepository.findByUuidAndDeletedAtIsNull(position.getUuid())).thenReturn(Optional.of(position));
        when(departmentRepository.findByUuidAndDeletedAtIsNull(department.getUuid())).thenReturn(Optional.of(department));
        when(positionRepository.existsByDepartmentIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                5L, "DEV-LEAD", position.getUuid())).thenReturn(false);
        when(positionRepository.save(any(Position.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PositionRequest request = new PositionRequest(
                department.getUuid(), "dev-lead", "Desarrollador Lead", null, "LEAD", true);

        PositionResponse response = positionService.update(position.getUuid(), request);

        assertThat(response.code()).isEqualTo("DEV-LEAD");
        verify(auditService).record(eq("ORG_POSITION_UPDATE"), eq("Position"),
                eq(position.getUuid().toString()), any(), any());
    }

    @Test
    void deletesPositionLogically() {
        Position position = positionEntity(9L, "DEV-SR", department());
        when(positionRepository.findByUuidAndDeletedAtIsNull(position.getUuid())).thenReturn(Optional.of(position));

        positionService.delete(position.getUuid());

        assertThat(position.getDeletedAt()).isNotNull();
        assertThat(position.isEnabled()).isFalse();
        verify(auditService).record(eq("ORG_POSITION_DELETE"), eq("Position"),
                eq(position.getUuid().toString()), any(), any());
    }

    private Position positionEntity(long id, String code, Department department) {
        Position position = new Position();
        position.setId(id);
        position.setUuid(UUID.randomUUID());
        position.setCode(code);
        position.setName(code);
        position.setDepartment(department);
        position.setEnabled(true);
        position.setCreatedAt(Instant.now());
        return position;
    }

    private Department department() {
        Company company = new Company();
        company.setId(1L);
        company.setUuid(UUID.randomUUID());
        company.setCode("ACME");
        company.setLegalName("Acme");
        company.setEnabled(true);

        Branch branch = new Branch();
        branch.setId(1L);
        branch.setUuid(UUID.randomUUID());
        branch.setCode("SUC");
        branch.setName("Sucursal");
        branch.setCompany(company);
        branch.setEnabled(true);

        Department department = new Department();
        department.setId(5L);
        department.setUuid(UUID.randomUUID());
        department.setCode("TECNOLOGIA");
        department.setName("Tecnología");
        department.setBranch(branch);
        department.setEnabled(true);
        return department;
    }
}
