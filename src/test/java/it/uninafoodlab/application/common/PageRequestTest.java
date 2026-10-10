package it.uninafoodlab.application.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    void acceptsTheLastPageWhoseOffsetFitsTheDatabaseInteger() {
        int lastIndex = Integer.MAX_VALUE / 12;

        assertThat(new PageRequest(lastIndex, 12).offset()).isEqualTo(lastIndex * 12);
        assertThat(new PageRequest(Integer.MAX_VALUE, 1).offset()).isEqualTo(Integer.MAX_VALUE);
        assertThatThrownBy(() -> new PageRequest(lastIndex + 1, 12))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsInvalidPageDimensionsBeforeCalculatingAnOffset() {
        assertThatThrownBy(() -> new PageRequest(-1, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageRequest(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageRequest(0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesTheRequestedPageSizeWithoutExceedingTheSupportedLimit() {
        assertThat(new PageRequest(3, 12).offset()).isEqualTo(36);
        assertThat(new PageRequest(1, 100).offset()).isEqualTo(100);
    }
}
