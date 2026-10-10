package it.uninafoodlab.presentation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.domain.catalog.MeasurementUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogDraftStateTest {
    @Test
    void returningToSavedRecipeClearsDirtyStateButIncompleteIngredientLineIsPreserved() {
        var state = new CatalogDraftState();
        var saved = recipe("Pasta", "", null);
        state.recipeLoaded(saved);
        assertThat(state.recipeChanged(recipe("Pasta nuova", "", null))).isTrue();
        assertThat(state.recipeChanged(saved)).isFalse();
        assertThat(state.recipeChanged(recipe("Pasta", "1,5", UUID.randomUUID()))).isTrue();
    }

    @Test
    void ingredientAndRecipeDraftsIndependentlyPreventRefresh() {
        var state = new CatalogDraftState();
        var recipe = recipe("Pasta", "", null);
        var ingredient = new CatalogDraftState.IngredientDraft(null, "Farina", MeasurementUnit.GRAM);
        var modifiedIngredient = new CatalogDraftState.IngredientDraft(null, "Farina di ceci", MeasurementUnit.GRAM);
        state.recipeLoaded(recipe);
        state.ingredientLoaded(ingredient);
        assertThat(state.canRefresh(recipe, ingredient)).isTrue();
        assertThat(state.canRefresh(recipe, modifiedIngredient)).isFalse();
        state.recipeLoaded(recipe("Pasta nuova", "", null));
        assertThat(state.ingredientChanged(modifiedIngredient)).isTrue();
        state.ingredientLoaded(modifiedIngredient);
        assertThat(state.canRefresh(recipe("Pasta nuova", "", null), modifiedIngredient)).isTrue();
    }

    @Test
    void changedRecipeRemainsUnsavedUntilExplicitlyAccepted() {
        var state = new CatalogDraftState();
        var saved = recipe("Pasta", "", null);
        state.recipeLoaded(saved);
        var modified = new CatalogDraftState.RecipeDraft(saved.id(), "Pasta aggiornata", "Preparazione", "1",
                saved.categoryId(), List.of(), null, "");
        assertThat(state.recipeChanged(modified)).isTrue();
        assertThat(state.savedRecipe()).isEqualTo(saved);
    }

    @Test
    void changingTheEditorIngredientListCannotMutateTheSavedRecipeBaseline() {
        var lines = new java.util.ArrayList<it.uninafoodlab.application.catalog.RecipeIngredientInput>();
        var line = new it.uninafoodlab.application.catalog.RecipeIngredientInput(UUID.randomUUID(), java.math.BigDecimal.ONE);
        lines.add(line);
        var snapshot = new CatalogDraftState.RecipeDraft(null, "Pasta", "Preparazione", "1", null, lines, null, "");
        lines.clear();
        assertThat(snapshot.lines()).containsExactly(line);
    }

    @Test
    void equivalentDecimalQuantitiesDoNotCountAsUnsavedChanges() {
        var state = new CatalogDraftState();
        var ingredient = UUID.randomUUID();
        var saved = new CatalogDraftState.RecipeDraft(null, "Pasta", "Preparazione", "1", null,
                List.of(new it.uninafoodlab.application.catalog.RecipeIngredientInput(ingredient, new java.math.BigDecimal("100"))), null, "");
        state.recipeLoaded(saved);
        var equivalent = new CatalogDraftState.RecipeDraft(null, "Pasta", "Preparazione", "1", null,
                List.of(new it.uninafoodlab.application.catalog.RecipeIngredientInput(ingredient, new java.math.BigDecimal("100.0"))), null, "");
        assertThat(state.recipeChanged(equivalent)).isFalse();
    }

    private static CatalogDraftState.RecipeDraft recipe(String name, String quantity, UUID pendingIngredient) {
        return new CatalogDraftState.RecipeDraft(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), name, "Preparazione", "1",
                UUID.fromString("00000000-0000-0000-0000-000000000002"), List.of(), pendingIngredient, quantity);
    }
}
