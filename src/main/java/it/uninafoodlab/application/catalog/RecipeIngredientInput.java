package it.uninafoodlab.application.catalog;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RecipeIngredientInput(UUID ingredientId, BigDecimal quantity) {
    public RecipeIngredientInput {
        Objects.requireNonNull(ingredientId, "ingredientId");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() <= 0
                || quantity.stripTrailingZeros().scale() > 3
                || quantity.compareTo(new BigDecimal("1000000000000")) >= 0)
            throw new IllegalArgumentException(
                    "La quantità per una persona deve essere positiva, con al massimo 3 decimali,"
                        + " e inferiore a 1000000000000.");
    }
}
