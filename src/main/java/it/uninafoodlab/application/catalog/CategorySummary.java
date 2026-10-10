package it.uninafoodlab.application.catalog;

import java.util.Objects;
import java.util.UUID;

public record CategorySummary(UUID id, String name, String description) {

    public CategorySummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
    }
}
