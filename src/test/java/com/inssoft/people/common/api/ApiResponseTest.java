package com.inssoft.people.common.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void okWithData() {
        ApiResponse<String> response = ApiResponse.ok("ok", "data");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("ok");
        assertThat(response.getData()).isEqualTo("data");
        assertThat(response.getErrors()).isNull();
    }

    @Test
    void okWithoutData() {
        ApiResponse<Void> response = ApiResponse.ok("ok");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    void errorWithFieldErrors() {
        ApiResponse<Void> response = ApiResponse.error("Error de validación",
                List.of(new FieldErrorResponse("email", "inválido")));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0).field()).isEqualTo("email");
    }
}
