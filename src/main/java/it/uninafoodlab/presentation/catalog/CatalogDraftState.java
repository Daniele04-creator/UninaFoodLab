package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.RecipeIngredientInput;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;


final class CatalogDraftState {
    record RecipeDraft(UUID id, String name, String description, String servings, UUID categoryId,
            List<RecipeIngredientInput> lines, UUID pendingIngredient, String pendingQuantity) {
        RecipeDraft {
            lines = List.copyOf(lines);
        }
    }

    record IngredientDraft(UUID id, String name, MeasurementUnit unit) {}

    private RecipeDraft savedRecipe;
    private IngredientDraft savedIngredient;

    void recipeLoaded(RecipeDraft value) { savedRecipe = Objects.requireNonNull(value); }
    void ingredientLoaded(IngredientDraft value) { savedIngredient = Objects.requireNonNull(value); }
    RecipeDraft savedRecipe() { return savedRecipe; }

    boolean recipeChanged(RecipeDraft current) {
        return savedRecipe != null && !comparableRecipe(savedRecipe).equals(comparableRecipe(current));
    }

    private static RecipeDraft comparableRecipe(RecipeDraft draft) {
        return new RecipeDraft(draft.id(), draft.name(), draft.description(), draft.servings(),
                draft.categoryId(), draft.lines().stream().map(line -> new RecipeIngredientInput(
                        line.ingredientId(), line.quantity().stripTrailingZeros())).toList(),
                draft.pendingIngredient(), draft.pendingQuantity());
    }

    boolean ingredientChanged(IngredientDraft current) {
        return savedIngredient != null && !savedIngredient.equals(current);
    }

    boolean canRefresh(RecipeDraft recipe, IngredientDraft ingredient) {
        return !recipeChanged(recipe) && !ingredientChanged(ingredient);
    }

    boolean hasUnsavedChanges(Supplier<RecipeDraft> recipeSnapshot,
            Supplier<IngredientDraft> ingredientSnapshot,
            Supplier<List<RecipeIngredientInput>> currentLines) {
        try {
            var recipe = recipeSnapshot.get();
            var visibleRecipe = new RecipeDraft(recipe.id(), recipe.name(), recipe.description(),
                    recipe.servings(), recipe.categoryId(), currentLines.get(),
                    recipe.pendingIngredient(), recipe.pendingQuantity());
            return !canRefresh(visibleRecipe, ingredientSnapshot.get());
        } catch (IllegalArgumentException invalidQuantity) {
            return true;
        }
    }
}
