package it.uninafoodlab.presentation.planning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlanningFilterChoiceTest {

    @Test
    void neutralChoiceExplainsSelectionAndContainsNoSelection() {
        var choice = PlanningFilterChoice.<String>none();

        assertThat(choice.label()).isEqualTo("Scegli un corso");
        assertThat(choice.value()).isEmpty();
    }

    @Test
    void selectedChoiceKeepsItsDomainValueAndReadableLabel() {
        var choice = PlanningFilterChoice.of("course-1", "Pasta fresca regionale");

        assertThat(choice.label()).isEqualTo("Pasta fresca regionale");
        assertThat(choice.value()).contains("course-1");
    }

    @Test
    void allSessionsChoiceIsExplicitAndContainsNoSingleSession() {
        var choice = PlanningFilterChoice.<String>all("Tutte le sessioni");

        assertThat(choice.label()).isEqualTo("Tutte le sessioni");
        assertThat(choice.value()).isEmpty();
    }
}
