package it.uninafoodlab.domain.course;

import java.math.BigDecimal;
import java.util.Objects;

public record IngredientMargin(BigDecimal value) {

    public IngredientMargin {
        Objects.requireNonNull(value, "value");
        if (value.signum() != 0) {
            throw new IllegalArgumentException(
                    "Ingredient margin must be zero");
        }
        value = value.stripTrailingZeros();
    }

    public static IngredientMargin of(BigDecimal value) {
        return new IngredientMargin(value);
    }

    public static IngredientMargin zero() {
        return new IngredientMargin(BigDecimal.ZERO);
    }

}
