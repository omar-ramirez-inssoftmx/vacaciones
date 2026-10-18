package com.inssoft.people.employee.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MexicanIdentifiersTest {

    @Test
    void validatesCurp() {
        assertThat(MexicanIdentifiers.isValidCurp("GARC850101HDFRRN09")).isTrue();
        assertThat(MexicanIdentifiers.isValidCurp("ABC123")).isFalse();
        assertThat(MexicanIdentifiers.isValidCurp(null)).isTrue();
    }

    @Test
    void validatesRfc() {
        assertThat(MexicanIdentifiers.isValidRfc("GARC850101AB1")).isTrue();
        assertThat(MexicanIdentifiers.isValidRfc("ABC850101AB1")).isTrue();
        assertThat(MexicanIdentifiers.isValidRfc("123")).isFalse();
    }

    @Test
    void validatesNss() {
        assertThat(MexicanIdentifiers.isValidNss("12345678901")).isTrue();
        assertThat(MexicanIdentifiers.isValidNss("12345")).isFalse();
    }

    @Test
    void normalizesAndMasks() {
        assertThat(MexicanIdentifiers.normalize(" garc850101hdfrrn09 ")).isEqualTo("GARC850101HDFRRN09");
        assertThat(MexicanIdentifiers.normalize("  ")).isNull();
        assertThat(MexicanIdentifiers.mask("GARC850101HDFRRN09")).isEqualTo("****RN09");
        assertThat(MexicanIdentifiers.mask("1234")).isEqualTo("****");
        assertThat(MexicanIdentifiers.mask(null)).isNull();
    }
}
