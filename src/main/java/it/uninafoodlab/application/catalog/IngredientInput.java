package it.uninafoodlab.application.catalog;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import java.util.Objects;

public record IngredientInput(String name, MeasurementUnit unit) {
    public IngredientInput {
        name = Objects.requireNonNull(name, "name").strip();
        Objects.requireNonNull(unit, "unit");
        if (name.isEmpty() || name.length() > 100)
            throw new IllegalArgumentException(
                    "Il nome dell'ingrediente deve contenere da 1 a 100 caratteri.");
        if (unit == MeasurementUnit.KILOGRAM || unit == MeasurementUnit.LITER)
            throw new IllegalArgumentException(
                    "Usa grammi o millilitri per indicare dosi per una persona.");
    }
}
