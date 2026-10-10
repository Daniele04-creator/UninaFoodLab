package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoryDialogModelTest {

    @Test
    void acceptsOnlyNormalizedCategoryInputWithinTheApplicationBounds() {
        assertThat(CategoryDialogModel.isValid("  Pasticceria  ", "  Dolci  ")).isTrue();
        assertThat(CategoryDialogModel.isValid("   ", "Descrizione")).isFalse();
        assertThat(CategoryDialogModel.isValid("ab", "Descrizione")).isFalse();
        assertThat(CategoryDialogModel.isValid("x".repeat(81), "Descrizione")).isFalse();
        assertThat(CategoryDialogModel.isValid("Pane", "x".repeat(501))).isFalse();
    }

    @Test
    void convertsInvalidInputToAnEmptyResultInsteadOfLettingValidationEscapeTheDialogCallback() {
        assertThat(CategoryDialogModel.toInput("   ", "Descrizione")).isEmpty();
        assertThat(CategoryDialogModel.toInput(" Pane ", null))
                .hasValueSatisfying(input -> {
                    assertThat(input.name()).isEqualTo("Pane");
                    assertThat(input.description()).isEmpty();
                });
    }
}
