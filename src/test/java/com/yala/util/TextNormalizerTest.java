package com.yala.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void stripsAccentsLowercasesAndCollapsesSpaces() {
        assertThat(TextNormalizer.normalize("  JOSÉ   PÉREZ ")).isEqualTo("jose perez");
    }

    @Test
    void equalsNormalizedIsTolerantToCaseAndAccents() {
        assertThat(TextNormalizer.equalsNormalized("Lóvelace", "LOVELACE")).isTrue();
        assertThat(TextNormalizer.equalsNormalized("Ada María", "ada maria")).isTrue();
        assertThat(TextNormalizer.equalsNormalized("Ada", "Eva")).isFalse();
    }

    @Test
    void nullIsNormalizedToEmpty() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }
}
