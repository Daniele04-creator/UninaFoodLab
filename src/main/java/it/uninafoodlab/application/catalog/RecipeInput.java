package it.uninafoodlab.application.catalog;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RecipeInput(
        UUID categoryId,
        String name,
        String description,
        int baseServings,
        List<RecipeIngredientInput> ingredients) {
    public RecipeInput {
        Objects.requireNonNull(categoryId, "categoryId");
        name = Objects.requireNonNull(name, "name").strip();
        description = Objects.requireNonNull(description, "description").strip();
        ingredients = List.copyOf(ingredients);
        if (name.isEmpty() || name.length() > 120)
            throw new IllegalArgumentException(
                    "Il nome della ricetta deve contenere da 1 a 120 caratteri.");
        if (description.isEmpty() || description.length() > 4000)
            throw new IllegalArgumentException("Inserisci una descrizione fino a 4000 caratteri.");
        if (baseServings != 1)
            throw new IllegalArgumentException("La ricetta deve indicare le dosi per una persona.");
        if (ingredients.isEmpty()
                || ingredients.stream().map(RecipeIngredientInput::ingredientId).distinct().count()
                        != ingredients.size())
            throw new IllegalArgumentException("Inserisci almeno un ingrediente, senza duplicati.");
    }
}
