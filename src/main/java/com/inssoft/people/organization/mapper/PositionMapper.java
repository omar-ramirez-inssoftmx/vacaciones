package com.inssoft.people.organization.mapper;

import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.organization.dto.PositionRequest;
import com.inssoft.people.organization.dto.PositionResponse;
import com.inssoft.people.organization.entity.Position;

public final class PositionMapper {

    private PositionMapper() {
    }

    public static PositionResponse toResponse(Position position) {
        return new PositionResponse(
                position.getUuid(),
                position.getDepartment().getUuid(),
                position.getDepartment().getName(),
                position.getDepartment().getBranch().getUuid(),
                position.getCode(),
                position.getName(),
                position.getDescription(),
                position.getJobLevel(),
                position.isEnabled(),
                position.getCreatedAt(),
                position.getUpdatedAt()
        );
    }

    public static void apply(PositionRequest request, Position position) {
        position.setCode(CodeNormalizer.normalize(request.code()));
        position.setName(request.name().trim());
        position.setDescription(CodeNormalizer.trimToNull(request.description()));
        position.setJobLevel(CodeNormalizer.trimToNull(request.jobLevel()));
        if (request.enabled() != null) {
            position.setEnabled(request.enabled());
        }
    }
}
