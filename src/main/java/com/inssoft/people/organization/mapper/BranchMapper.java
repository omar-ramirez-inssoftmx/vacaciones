package com.inssoft.people.organization.mapper;

import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.dto.BranchRequest;
import com.inssoft.people.organization.dto.BranchResponse;
import com.inssoft.people.organization.entity.Branch;

public final class BranchMapper {

    private BranchMapper() {
    }

    public static BranchResponse toResponse(Branch branch) {
        return new BranchResponse(
                branch.getUuid(),
                branch.getCompany().getUuid(),
                branch.getCompany().getLegalName(),
                branch.getCode(),
                branch.getName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.isEnabled(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }

    public static void apply(BranchRequest request, Branch branch) {
        branch.setCode(CodeNormalizer.normalize(request.code()));
        branch.setName(request.name().trim());
        branch.setAddress(CodeNormalizer.trimToNull(request.address()));
        branch.setPhone(CodeNormalizer.trimToNull(request.phone()));
        if (request.enabled() != null) {
            branch.setEnabled(request.enabled());
        }
    }
}
