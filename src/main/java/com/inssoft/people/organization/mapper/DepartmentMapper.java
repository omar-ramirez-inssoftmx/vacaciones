package com.inssoft.people.organization.mapper;

import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.dto.DepartmentRequest;
import com.inssoft.people.organization.dto.DepartmentResponse;
import com.inssoft.people.organization.entity.Department;

public final class DepartmentMapper {

    private DepartmentMapper() {
    }

    public static DepartmentResponse toResponse(Department department) {
        Department parent = department.getParent();
        return new DepartmentResponse(
                department.getUuid(),
                department.getBranch().getUuid(),
                department.getBranch().getName(),
                department.getBranch().getCompany().getUuid(),
                parent == null ? null : parent.getUuid(),
                parent == null ? null : parent.getName(),
                department.getCode(),
                department.getName(),
                department.getDescription(),
                department.isEnabled(),
                department.getCreatedAt(),
                department.getUpdatedAt()
        );
    }

    public static void apply(DepartmentRequest request, Department department) {
        department.setCode(CodeNormalizer.normalize(request.code()));
        department.setName(request.name().trim());
        department.setDescription(CodeNormalizer.trimToNull(request.description()));
        if (request.enabled() != null) {
            department.setEnabled(request.enabled());
        }
    }
}
