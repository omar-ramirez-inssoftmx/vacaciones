package com.inssoft.people.common.api;

/**
 * Error de validación a nivel de campo.
 */
public record FieldErrorResponse(String field, String message) {
}
