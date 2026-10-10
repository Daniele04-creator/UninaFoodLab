package it.uninafoodlab.presentation.common;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RecipePreparationTextTest {
    @Test void separatesExistingNumberedStepsWithoutChangingSavedWordsAndQuantities() {
        assertThat(RecipePreparationText.readingText("Dosi storiche. 1. Usa 0.125 g di sale. 2. Cuoci a 180 °C."))
                .isEqualTo("Dosi storiche.\n\n1. Usa 0.125 g di sale.\n\n2. Cuoci a 180 °C.");
    }

    @Test void preservesUnnumberedPreparationAndAmbiguousLists() {
        for (var text : new String[] {"Usa 1.5 ml di olio.\nMescola.", "1. Mescola. 3. Cuoci.", "", "2. Mescola."}) {
            assertThat(RecipePreparationText.readingText(text)).isEqualTo(text);
        }
    }
}
