package com.inssoft.people.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void mapsPageContentAndMetadata() {
        PageImpl<String> page = new PageImpl<>(
                List.of("a", "b"),
                PageRequest.of(0, 2),
                5);

        PageResponse<String> response = PageResponse.from(page, value -> value.toUpperCase());

        assertThat(response.content()).containsExactly("A", "B");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isFalse();
    }
}
