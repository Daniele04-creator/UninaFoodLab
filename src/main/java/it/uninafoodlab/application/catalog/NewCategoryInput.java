package it.uninafoodlab.application.catalog;

import java.util.Objects;

public record NewCategoryInput(String name, String description) {

    public NewCategoryInput {
        name = normalized(name, "name");
        description = description == null ? "" : normalized(description, "description");
        if (name.length() < 3 || name.length() > 80) {
            throw new IllegalArgumentException("Il nome della categoria deve contenere da 3 a 80 caratteri.");
        }
        if (description.length() > 500) {
            throw new IllegalArgumentException("La descrizione della categoria non può superare 500 caratteri.");
        }
    }

    private static String normalized(String value, String field) {
        return Objects.requireNonNull(value, field).strip();
    }
}
