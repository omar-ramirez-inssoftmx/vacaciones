package com.inssoft.people.employee.dto;

import java.time.Instant;
import java.util.UUID;

public record EmployeeSummaryResponse(
        UUID uuid,
        String employeeNumber,
        String fullName,
        String corporateEmail,
        UUID companyUuid,
        String companyName,
        UUID branchUuid,
        String branchName,
        UUID departmentUuid,
        String departmentName,
        UUID positionUuid,
        String positionName,
        String statusCode,
        String statusName,
        boolean enabled,
        Instant createdAt
) {
}
