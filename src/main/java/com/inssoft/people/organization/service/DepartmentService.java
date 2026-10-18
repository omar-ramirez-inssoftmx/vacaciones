package com.inssoft.people.organization.service;

import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.dto.DepartmentRequest;
import com.inssoft.people.organization.dto.DepartmentResponse;
import com.inssoft.people.organization.entity.Branch;
import com.inssoft.people.organization.entity.Department;
import com.inssoft.people.organization.mapper.DepartmentMapper;
import com.inssoft.people.organization.repository.BranchRepository;
import com.inssoft.people.organization.repository.DepartmentRepository;
import com.inssoft.people.organization.repository.PositionRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DepartmentService {

    private static final int MAX_HIERARCHY_DEPTH = 100;

    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final PositionRepository positionRepository;
    private final AuditService auditService;

    public DepartmentService(
            DepartmentRepository departmentRepository,
            BranchRepository branchRepository,
            PositionRepository positionRepository,
            AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.branchRepository = branchRepository;
        this.positionRepository = positionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> list(
            UUID branchUuid, String q, Boolean enabled, int page, int size) {

        if (branchUuid != null) {
            findBranch(branchUuid);
        }

        Page<Department> result = departmentRepository.search(
                branchUuid,
                CodeNormalizer.trimToNull(q),
                enabled,
                PageRequestFactory.of(page, size, "name"));

        return PageResponse.from(result, DepartmentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(UUID uuid) {
        return DepartmentMapper.toResponse(find(uuid));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        Branch branch = findBranch(request.branchUuid());
        Department parent = request.parentUuid() == null ? null : find(request.parentUuid());
        String code = CodeNormalizer.normalize(request.code());

        validateParent(null, branch, parent);

        if (departmentRepository.existsByBranchIdAndCodeIgnoreCaseAndDeletedAtIsNull(branch.getId(), code)) {
            throw new ConflictException("Ya existe un departamento con el código " + code + " en la sucursal");
        }

        Department department = new Department();
        department.setBranch(branch);
        department.setParent(parent);
        DepartmentMapper.apply(request, department);
        department.setCode(code);

        Department saved = departmentRepository.save(department);
        DepartmentResponse response = DepartmentMapper.toResponse(saved);
        auditService.record("ORG_DEPARTMENT_CREATE", "Department", saved.getUuid().toString(), response);
        return response;
    }

    @Transactional
    public DepartmentResponse update(UUID uuid, DepartmentRequest request) {
        Department department = find(uuid);
        Branch branch = findBranch(request.branchUuid());
        Department parent = request.parentUuid() == null ? null : find(request.parentUuid());
        DepartmentResponse previous = DepartmentMapper.toResponse(department);

        validateParent(department, branch, parent);

        String code = CodeNormalizer.normalize(request.code());
        if (departmentRepository.existsByBranchIdAndCodeIgnoreCaseAndUuidNotAndDeletedAtIsNull(
                branch.getId(), code, uuid)) {
            throw new ConflictException("Ya existe un departamento con el código " + code + " en la sucursal");
        }

        if (!department.getBranch().getId().equals(branch.getId())
                && departmentRepository.existsByParentIdAndDeletedAtIsNull(department.getId())) {
            throw new BusinessException(
                    "No se puede cambiar de sucursal un departamento con subdepartamentos activos");
        }

        if (Boolean.FALSE.equals(request.enabled())) {
            if (positionRepository.existsByDepartmentIdAndDeletedAtIsNull(department.getId())) {
                throw new BusinessException(
                        "No se puede deshabilitar el departamento porque tiene puestos activos");
            }
            if (departmentRepository.existsByParentIdAndDeletedAtIsNull(department.getId())) {
                throw new BusinessException(
                        "No se puede deshabilitar el departamento porque tiene subdepartamentos activos");
            }
        }

        department.setBranch(branch);
        department.setParent(parent);
        DepartmentMapper.apply(request, department);
        department.setCode(code);

        Department saved = departmentRepository.save(department);
        DepartmentResponse response = DepartmentMapper.toResponse(saved);
        auditService.record("ORG_DEPARTMENT_UPDATE", "Department", uuid.toString(), previous, response);
        return response;
    }

    @Transactional
    public void delete(UUID uuid) {
        Department department = find(uuid);

        if (positionRepository.existsByDepartmentIdAndDeletedAtIsNull(department.getId())) {
            throw new BusinessException(
                    "No se puede deshabilitar el departamento porque tiene puestos activos");
        }
        if (departmentRepository.existsByParentIdAndDeletedAtIsNull(department.getId())) {
            throw new BusinessException(
                    "No se puede deshabilitar el departamento porque tiene subdepartamentos activos");
        }

        DepartmentResponse previous = DepartmentMapper.toResponse(department);
        department.setDeletedAt(Instant.now());
        department.setEnabled(false);
        departmentRepository.save(department);

        auditService.record("ORG_DEPARTMENT_DELETE", "Department", uuid.toString(), previous, null);
    }

    private void validateParent(Department department, Branch branch, Department parent) {
        if (parent == null) {
            return;
        }

        if (department != null && parent.getId().equals(department.getId())) {
            throw new BusinessException("Un departamento no puede ser su propio padre");
        }

        if (!parent.getBranch().getId().equals(branch.getId())) {
            throw new BusinessException("El departamento padre debe pertenecer a la misma sucursal");
        }

        Department current = parent;
        int depth = 0;
        while (current != null && depth++ < MAX_HIERARCHY_DEPTH) {
            if (department != null && current.getId().equals(department.getId())) {
                throw new BusinessException("La jerarquía de departamentos no puede contener ciclos");
            }
            current = current.getParent();
        }
    }

    private Department find(UUID uuid) {
        return departmentRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Departamento", uuid));
    }

    private Branch findBranch(UUID uuid) {
        return branchRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal", uuid));
    }
}
