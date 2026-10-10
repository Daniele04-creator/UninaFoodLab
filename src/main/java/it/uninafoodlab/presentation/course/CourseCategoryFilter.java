package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.CategorySummary;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

record CourseCategoryFilter(Optional<UUID> categoryId, String label) {
    CourseCategoryFilter {
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(label, "label");
    }

    static CourseCategoryFilter all() {
        return new CourseCategoryFilter(Optional.empty(), "Tutte le categorie");
    }

    static CourseCategoryFilter category(CategorySummary category) {
        return new CourseCategoryFilter(Optional.of(category.id()), category.name());
    }
}
