package it.uninafoodlab.application.planning;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import java.math.BigDecimal;
import java.util.Objects;

public record IngredientRequirement(
        String ingredientName,
        MeasurementUnit unit,
        int confirmedParticipants,
        BigDecimal requiredQuantity) {

    public IngredientRequirement {
        ingredientName = Objects.requireNonNull(ingredientName, "ingredientName").strip();
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        if (ingredientName.isEmpty() || confirmedParticipants < 0
                || requiredQuantity.signum() < 0) {
            throw new IllegalArgumentException("Ingredient requirement is invalid");
        }
    }
}
