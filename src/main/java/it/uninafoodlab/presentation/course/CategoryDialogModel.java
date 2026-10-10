package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.NewCategoryInput;
import java.util.Optional;

final class CategoryDialogModel {

    private CategoryDialogModel() {}

    static boolean isValid(String name, String description) {
        var normalizedName = normalized(name);
        var normalizedDescription = normalized(description);
        return normalizedName.length() >= 3
                && normalizedName.length() <= 80
                && normalizedDescription.length() <= 500;
    }

    static Optional<NewCategoryInput> toInput(String name, String description) {
        if (!isValid(name, description)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new NewCategoryInput(name, description));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }
}
