package it.uninafoodlab.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.catalog.MeasurementUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientInputTest {
    @Test
    void acceptsUnitsSuitableForWholeSinglePersonDoses() {
        for (var unit : List.of(MeasurementUnit.GRAM, MeasurementUnit.MILLILITER,
                MeasurementUnit.PIECE, MeasurementUnit.TEASPOON)) {
            assertThat(new IngredientInput(" Ingrediente ", unit).unit()).isEqualTo(unit);
        }
    }

    @Test
    void rejectsLargeUnitsThatWouldRequireFractionalDoses() {
        for (var unit : List.of(MeasurementUnit.KILOGRAM, MeasurementUnit.LITER)) {
            assertThatThrownBy(() -> new IngredientInput("Ingrediente", unit))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
