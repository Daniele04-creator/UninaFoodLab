package it.uninafoodlab.presentation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CatalogRecipeModeTest {
    @Test void existingRecipesOpenInConsultationAndAllowExplicitEditing() {
        var mode = new CatalogRecipeMode();
        mode.open();
        assertThat(mode.editing()).isFalse();
        mode.edit();
        assertThat(mode.editing()).isTrue();
    }

    @Test void editingRequiresExplicitActionAndCancellationReturnsToConsultation() {
        var mode = new CatalogRecipeMode();
        mode.open();
        mode.edit();
        assertThat(mode.editing()).isTrue();
        mode.open();
        assertThat(mode.editing()).isFalse();
    }

    @Test void newRecipeCanBeEditedEvenAfterViewingAnExistingRecipe() {
        var mode = new CatalogRecipeMode();
        mode.open();
        mode.create();
        assertThat(mode.editing()).isTrue();
    }
}
