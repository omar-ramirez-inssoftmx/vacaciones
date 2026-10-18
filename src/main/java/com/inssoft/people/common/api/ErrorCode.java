package com.inssoft.people.common.api;

/**
 * Códigos estables de error para clientes web/móvil.
 */
public enum ErrorCode {

    VALIDATION_ERROR("VALIDATION_ERROR"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND"),
    CONFLICT("CONFLICT"),
    BUSINESS_RULE_VIOLATION("BUSINESS_RULE_VIOLATION"),
    UNAUTHORIZED("UNAUTHORIZED"),
    FORBIDDEN("FORBIDDEN"),
    INTERNAL_ERROR("INTERNAL_ERROR");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
