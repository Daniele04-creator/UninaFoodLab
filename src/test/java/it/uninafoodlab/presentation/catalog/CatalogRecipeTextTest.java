package it.uninafoodlab.presentation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogRecipeTextTest {
    @Test void previewUsesTheFirstRealSentenceWithoutSpillingThePreparation() {
        assertThat(CatalogRecipeText.preview("Pane morbido alle erbe.\n\n1. Impastare la farina. 2. Cuocere."))
                .isEqualTo("Pane morbido alle erbe.");
        assertThat(CatalogRecipeText.preview("1. Lavare e tagliare le verdure. 2. Cuocere in padella."))
                .isEqualTo("Lavare e tagliare le verdure.");
        assertThat(CatalogRecipeText.preview("Gnocchi di patate gratinati con pomodoro, fiordilatte e basilico. "
                + "Tempo indicativo: 70 minuti. Dosi per una persona. 1. Lessare le patate. 2. Incorporare la farina."))
                .isEqualTo("Gnocchi di patate gratinati con pomodoro, fiordilatte e basilico.");
    }

    @Test void previewKeepsDecimalsAndDoesNotInventMissingMetadata() {
        assertThat(CatalogRecipeText.preview("Aggiungere 0,5 g di sale e 1.5 ml di olio. Mescolare."))
                .isEqualTo("Aggiungere 0,5 g di sale e 1.5 ml di olio.");
        assertThat(CatalogRecipeText.preview("Un impasto rustico\nLasciare riposare"))
                .isEqualTo("Un impasto rustico");
        assertThat(CatalogRecipeText.preview("  ")).isEmpty();
    }

    @Test void readingAddsSpaceOnlyBetweenAnUnambiguousSequenceOfNumberedSteps() {
        assertThat(CatalogRecipeText.readingText("Un pane rustico.\n1. Impastare con 0,5 g di sale. 2. Riposare. 3. Cuocere."))
                .isEqualTo("Un pane rustico.\n\n1. Impastare con 0,5 g di sale.\n\n2. Riposare.\n\n3. Cuocere.");
        assertThat(CatalogRecipeText.readingText("1) Impastare\n2) Cuocere"))
                .isEqualTo("1) Impastare\n\n2) Cuocere");
    }

    @Test void readingPreservesFreeTextParagraphsAndAmbiguousNumbersInFull() {
        String freeText = "Un impasto semplice.\n\nUsare 1.5 ml di olio e il forno a 180 °C.\nServire caldo.";
        assertThat(CatalogRecipeText.readingText(freeText)).isEqualTo(freeText);
        String ambiguous = "Cuocere 2. Mescolare senza numerare i passaggi.";
        assertThat(CatalogRecipeText.readingText(ambiguous)).isEqualTo(ambiguous);
    }

    @Test void longPreviewsEndAtAWordBoundaryAndFullPreparationRemainsAvailable() {
        String preparation = "Impasto rustico ".repeat(250);
        assertThat(CatalogRecipeText.preview(preparation))
                .isEqualTo("Impasto rustico ".repeat(15).stripTrailing() + "…");
        assertThat(CatalogRecipeText.readingText(preparation)).isEqualTo(preparation);
    }

    @Test void numbersInTheIntroductionDoNotPreventRealStepsFromBeingSeparated() {
        assertThat(CatalogRecipeText.readingText("Ricetta 11. Tempo: 30 minuti. 1. Mescolare. 2. Cuocere."))
                .isEqualTo("Ricetta 11. Tempo: 30 minuti.\n\n1. Mescolare.\n\n2. Cuocere.");
    }
}
