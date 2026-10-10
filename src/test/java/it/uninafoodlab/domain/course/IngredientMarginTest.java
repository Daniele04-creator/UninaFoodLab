package it.uninafoodlab.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class IngredientMarginTest {

    @Test
    void normalizesZeroMargin() {
        var margin = IngredientMargin.of(new BigDecimal("0.00"));
        assertThat(margin.value()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsAnyPositiveMargin() {
        var positive = new BigDecimal("0.01");
        assertThatThrownBy(() -> IngredientMargin.of(positive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ingredient margin must be zero");
    }

    @Test
    void rejectsNegativeValues() {
        var negative = new BigDecimal("-0.01");
        assertThatThrownBy(() -> IngredientMargin.of(negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ingredient margin must be zero");
    }

}
