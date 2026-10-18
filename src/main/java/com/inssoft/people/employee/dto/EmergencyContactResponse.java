package com.inssoft.people.employee.dto;

import java.util.UUID;

public record EmergencyContactResponse(
        UUID uuid,
        String name,
        String relationship,
        String phone,
        String email,
        boolean primary
) {
}
