package it.uninafoodlab.application.catalog;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class RecipeInputTest {
    @Test
    void rejectsEmptyAndDuplicateIngredients() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> new RecipeInput(id, "Pasta", "Descrizione", 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        var line = new RecipeIngredientInput(id, BigDecimal.ONE);
        var duplicates = List.of(line, line);
        assertThatThrownBy(
                        () -> new RecipeInput(id, "Pasta", "Descrizione", 1, duplicates))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsQuantitiesThatDatabaseWouldRoundOrOverflow() {
        for (String value : List.of("0", "-1", "0.0001", "1000000000000")) {
            var id = UUID.randomUUID();
            var quantity = new BigDecimal(value);
            assertThatThrownBy(() -> new RecipeIngredientInput(id, quantity))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void normalizesAndDefensivelyCopiesRecipe() {
        var lines = new ArrayList<RecipeIngredientInput>();
        lines.add(new RecipeIngredientInput(UUID.randomUUID(), BigDecimal.ONE));
        var input = new RecipeInput(UUID.randomUUID(), " Pasta ", " Descrizione ", 1, lines);
        lines.clear();
        assertThat(input.name()).isEqualTo("Pasta");
        assertThat(input.ingredients()).hasSize(1);
    }

    @Test
    void onlyAcceptsRecipesForOnePerson() {
        var line = new RecipeIngredientInput(UUID.randomUUID(), BigDecimal.ONE);
        var ingredients = List.of(line);
        for (int servings : List.of(-1, 0, 2, 4)) {
            var id = UUID.randomUUID();
            assertThatThrownBy(() -> new RecipeInput(id, "Pasta", "Descrizione",
                    servings, ingredients)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsDecimalQuantitiesIncludingInsignificantDecimalZeros() {
        for (String quantity : List.of("0.001", "0.5", "125.001", "125.0000", "999999999999.999", "1E+3")) {
            var line = new RecipeIngredientInput(UUID.randomUUID(), new BigDecimal(quantity));
            assertThat(line.quantity()).isEqualByComparingTo(quantity);
        }
    }

    @Test
    void acceptsLargestLegacyQuantityConvertedFromKilogramsToGrams() {
        var convertedQuantity = new BigDecimal("999999999.999")
                .multiply(new BigDecimal("1000"));

        var line = new RecipeIngredientInput(UUID.randomUUID(), convertedQuantity);

        assertThat(line.quantity()).isEqualByComparingTo("999999999999");
    }
}
