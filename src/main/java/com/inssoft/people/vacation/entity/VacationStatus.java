package com.inssoft.people.vacation.entity;

import java.util.Locale;

public enum VacationStatus {
    PENDING_LEADER,
    PENDING_ADMIN,
    APPROVED,
    REJECTED,
    CANCELLED;

    public static VacationStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING_ADMIN;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(normalized)) {
            return PENDING_ADMIN;
        }
        return VacationStatus.valueOf(normalized);
    }

    public boolean isPending() {
        return this == PENDING_LEADER || this == PENDING_ADMIN;
    }
}
