package com.inssoft.people.organization.service;

import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.dto.PositionRequest;
import com.inssoft.people.organization.dto.PositionResponse;
import com.inssoft.people.organization.entity.Department;
import com.inssoft.people.organization.entity.Position;
import com.inssoft.people.organization.mapper.PositionMapper;
import com.inssoft.people.organization.repository.DepartmentRepository;
import com.inssoft.people.organization.repository.PositionRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public PositionService(
            PositionRepository positionRepository,
            DepartmentRepository departmentRepository,
            AuditService auditService) {
        this.positionRepository = positionRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PositionResponse> list(
            UUID departmentUuid, String q, Boolean enabled, int page, int size) {

        if (departmentUuid != null) {
            findDepartment(departmentUuid);
        }

        Page<Position> result = positionRepository.search(
                departmentUuid,
                CodeNormalizer.trimToNull(q),
                enabled,
                PageRequestFactory.of(page, size, "name"));

        return PageResponse.from(result, PositionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PositionResponse get(UUID uuid) {
        return PositionMapper.toResponse(find(uuid));
    }

    @Transactional
    public PositionResponse create(PositionRequest request) {
        Department department = findDepartment(request.departmentUuid());
        String code = CodeNormalizer.normalize(request.code());

        if (positionRepository.existsByDepartmentIdAndCodeIgnoreCaseAndDeletedAtIsNull(
                department.getId(), code)) {
            throw new ConflictException("Ya existe un puesto con el código " + code + " en el departamento");
        }

        Position position = new Position();
        position.setDepartment(department);
        PositionMapper.apply(request, position);
        position.setCode(code);

        Position saved = positionRepository.save(position);
        PositionResponse response = PositionMapper.toResponse(saved);
        auditService.record("ORG_POSITION_CREATE", "Position", saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public PositionResponse update(UUID uuid, PositionRequest request) {
        Position position = find(uuid);
        Department department = findDepartment(request.departmentUuid());
        PositionResponse previous = PositionMapper.toResponse(position);

        String code = CodeNormalizer.normalize(request.code());
        if (positionRepository.existsByDepartmentIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                department.getId(), code, uuid)) {
            throw new ConflictException("Ya existe un puesto con el código " + code + " en el departamento");
        }

        position.setDepartment(department);
        PositionMapper.apply(request, position);
        position.setCode(code);

        Position saved = positionRepository.save(position);
        PositionResponse response = PositionMapper.toResponse(saved);
        auditService.record("ORG_POSITION_UPDATE", "Position", uuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public void delete(UUID uuid) {
        Position position = find(uuid);
        PositionResponse previous = PositionMapper.toResponse(position);

        position.setDeletedAt(Instant.now());
        position.setEnabled(false);
        positionRepository.save(position);

        auditService.record("ORG_POSITION_DELETE", "Position", uuid.toString(), previous, null);
    }

    private Position find(UUID uuid) {
        return positionRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Puesto", uuid));
    }

    private Department findDepartment(UUID uuid) {
        return departmentRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Departamento", uuid));
    }
}
