package it.uninafoodlab.application.planning;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionPreparationTest {
    @Test
    void snapshotKeepsOperationalContributionsSeparateFromRoundedTotalsAndCopiesCollections() {
        var ingredients = new ArrayList<IngredientContribution>();
        ingredients.add(new IngredientContribution(UUID.randomUUID(), "Farina", MeasurementUnit.GRAM,
                new BigDecimal("2.25")));
        var recipe = new RecipePreparation(UUID.randomUUID(), "Pane", 4,
                new BigDecimal("1.5"), ingredients);
        var recipes = new ArrayList<>(List.of(recipe));
        var totals = new ArrayList<>(List.of(new IngredientRequirement("Farina", MeasurementUnit.GRAM,
                1, new BigDecimal("3"))));
        var preparation = new SessionPreparation(UUID.randomUUID(), 1, BigDecimal.ZERO, recipes, totals);
        ingredients.clear();
        recipes.clear();
        totals.clear();
        assertThat(preparation.recipes().getFirst().ingredients().getFirst().requiredQuantity())
                .isEqualByComparingTo("2.25");
        assertThat(preparation.totals().getFirst().requiredQuantity()).isEqualByComparingTo("3");
        var returnedRecipes = preparation.recipes();
        assertThatThrownBy(returnedRecipes::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
