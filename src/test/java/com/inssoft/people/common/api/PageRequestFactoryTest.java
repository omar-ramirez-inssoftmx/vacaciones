package com.inssoft.people.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestFactoryTest {

    @Test
    void normalizesPageAndSize() {
        assertThat(PageRequestFactory.of(-5, 0, "name").getPageNumber()).isZero();
        assertThat(PageRequestFactory.of(-5, 0, "name").getPageSize()).isEqualTo(1);
        assertThat(PageRequestFactory.of(0, 10_000, "name").getPageSize())
                .isEqualTo(PageRequestFactory.MAX_PAGE_SIZE);
    }

    @Test
    void appliesAscendingSortByDefault() {
        assertThat(PageRequestFactory.of(0, 20, "legalName").getSort().getOrderFor("legalName"))
                .isNotNull()
                .satisfies(order -> assertThat(order.isAscending()).isTrue());
    }
}
