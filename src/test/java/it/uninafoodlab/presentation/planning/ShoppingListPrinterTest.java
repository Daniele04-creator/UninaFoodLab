package it.uninafoodlab.presentation.planning;

import static org.assertj.core.api.Assertions.assertThat;
import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShoppingListPrinterTest {
    @Test
    void printKeepsExactDecimalQuantitiesAndWrapsLongIngredientNames() {
        var row = new IngredientRequirement("Farina integrale di grano tenero", MeasurementUnit.GRAM,
                3, new BigDecimal("9876543210.125"));
        var lines = ShoppingListPrinter.printLines("Corso cucina", List.of(row), 20);
        assertThat(lines).allMatch(line -> line.length() <= 20);
        assertThat(String.join("", lines)).contains("Farina integrale di grano tenero — 9876543210.125 g");
        assertThat(String.join("", lines)).contains("Corso cucina");
    }
}
