package it.uninafoodlab.application.catalog;

import java.util.Objects;
import java.util.UUID;

public record RecipeSummary(
        UUID id,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        int baseServings,
        int ingredientCount,
        boolean locked) {

    public RecipeSummary(UUID id, UUID categoryId, String categoryName, String name,
            String description, int baseServings, int ingredientCount) {
        this(id, categoryId, categoryName, name, description, baseServings, ingredientCount, false);
    }

    public RecipeSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(categoryName, "categoryName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (baseServings <= 0 || ingredientCount < 0) {
            throw new IllegalArgumentException("Recipe counts are invalid");
        }
    }
}
