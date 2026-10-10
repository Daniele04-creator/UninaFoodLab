package it.uninafoodlab.application.catalog;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RecipeFilter(Optional<UUID> categoryId, String search, RecipeSearchScope scope) {

    public RecipeFilter {
        Objects.requireNonNull(categoryId, "categoryId");
        search = Objects.requireNonNull(search, "search").strip();
        Objects.requireNonNull(scope, "scope");
    }

    public RecipeFilter(Optional<UUID> categoryId, String search) {
        this(categoryId, search, RecipeSearchScope.CATALOG);
    }
}
