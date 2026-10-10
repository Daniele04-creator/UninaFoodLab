package it.uninafoodlab.application.catalog;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import java.util.UUID;

public record IngredientSummary(UUID id, String name, MeasurementUnit unit, boolean locked,
        boolean unitLocked) {
    @Override
    public String toString() {
        return name + " (" + unit.symbol() + ")";
    }
}
