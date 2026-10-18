package com.inssoft.people.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Estructura estándar de respuesta para toda la API v1.
 *
 * <pre>
 * { "success": true, "message": "...", "data": { ... } }
 * { "success": false, "message": "...", "data": null }
 * { "success": false, "message": "Error de validación", "data": null,
 *   "errors": [ { "field": "email", "message": "..." } ] }
 * </pre>
 */
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<FieldErrorResponse> errors;

    private ApiResponse(boolean success, String message, T data, List<FieldErrorResponse> errors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message, List<FieldErrorResponse> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public List<FieldErrorResponse> getErrors() {
        return errors;
    }
}
