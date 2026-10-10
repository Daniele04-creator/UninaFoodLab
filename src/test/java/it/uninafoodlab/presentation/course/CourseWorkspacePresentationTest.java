package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.planning.SessionLifecycleRepository;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseWorkspacePresentationTest {

    @Test
    void dossierDateRangeUsesTheLastSessionDateReturnedByTheApplicationLayer() {
        var course = new CourseSummary(
                UUID.randomUUID(), UUID.randomUUID(), "Pasticceria", CourseTitle.of("Dolci"),
                "Descrizione", LocalDate.of(2026, 9, 18), LocalTime.of(17, 0), 7, 4,
                Optional.of(LocalDateTime.of(2026, 10, 9, 18, 30)),
                CourseStatus.PUBLISHED, IngredientMargin.zero());

        assertThat(CourseWorkspaceView.formatCourseDateRange(course))
                .isEqualTo("18 settembre – 9 ottobre 2026");
    }

    @Test
    void expandedSessionMenuNamesFollowTheCatalogAndIgnoreUnassignedRecipes() {
        var crostata = recipe("Crostata di limone", "Pasticceria");
        var torta = recipe("Torta caprese", "Pasticceria");
        var unused = recipe("Pane rustico", "Panificazione");
        var session = new CourseSessionSummary(
                UUID.randomUUID(), 1, LocalDateTime.of(2026, 9, 18, 17, 0), SessionMode.PRACTICAL,
                SessionStatus.SCHEDULED, 150, Optional.empty(), Optional.of("Laboratorio Vesuvio"),
                Optional.of(14), Optional.empty(), Optional.empty(),
                List.of(crostata.id(), torta.id()), 2);

        assertThat(CourseWorkspaceView.assignedRecipeNames(session, List.of(crostata, unused, torta)))
                .containsExactly("Crostata di limone", "Torta caprese");
    }

    @Test
    void agendaMenuNamesUseTheSavedSnapshotWhenTheRecipeIsNoLongerInTheCatalog() {
        var archivedRecipe = new SessionLifecycleRepository.SavedRecipe(UUID.randomUUID(),
                "Ricetta storica", "Preparazione originale", "250 g farina");

        assertThat(CourseRecipeMenu.savedRecipeNames(List.of(archivedRecipe)))
                .containsExactly("Ricetta storica");
    }

    @Test
    void explorerRailKeepsTheDesktopProportionAndTheNarrowMinimum() {
        assertThat(CourseWorkspaceViewSetup.explorerRailWidth(1487)).isBetween(420.0, 435.0);
        assertThat(CourseWorkspaceViewSetup.explorerRailWidth(1000)).isEqualTo(320.0);
    }

    @Test
    void categoryFilterButtonKeepsTheSelectedCategoryVisible() {
        assertThat(CourseCatalogPane.categoryButtonLabel(CourseCategoryFilter.all()))
                .isEqualTo("Tutte le categorie");
        assertThat(CourseCatalogPane.categoryButtonLabel(new CourseCategoryFilter(
                Optional.of(UUID.randomUUID()), "Pasticceria"))).isEqualTo("Pasticceria");
    }

    private static RecipeSummary recipe(String name, String category) {
        return new RecipeSummary(UUID.randomUUID(), UUID.randomUUID(), category, name,
                "Preparazione", 1, 2);
    }
}
