package it.uninafoodlab.presentation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class DesktopUiResourcesTest {

    @Test
    void shellDefaultsToTheDesktopViewport() throws Exception {
        var navigator = Class.forName("it.uninafoodlab.presentation.navigation.SceneNavigator");
        var width = navigator.getDeclaredField("SHELL_WIDTH");
        var height = navigator.getDeclaredField("SHELL_HEIGHT");
        width.setAccessible(true);
        height.setAccessible(true);

        assertThat(Modifier.isStatic(width.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(height.getModifiers())).isTrue();
        assertThat(width.getDouble(null)).isEqualTo(1142.0);
        assertThat(height.getDouble(null)).isEqualTo(633.0);
    }

    @Test
    void dashboardContainsOnlyRealOperationalAttentionAndWeekControls() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/dashboard/dashboard-workspace.fxml");

        assertThat(fxml)
                .contains("agenda-week", "fx:id=\"weekSchedule\"")
                .contains("Settimana precedente", "Settimana successiva")
                .contains("fx:id=\"preparationToggle\"", "fx:id=\"preparationSection\"")
                .contains("onAction=\"#togglePreparation\"")
                .doesNotContain("fx:id=\"pendingAttendanceButton\"",
                        "onAction=\"#openPendingAttendances\"", "adesioni in attesa",
                        "fx:id=\"attendanceRegisterButton\"",
                        "onAction=\"#openAttendanceRegister\"",
                        "Presenze da registrare", "presenze da registrare")
                .doesNotContain("2 adesioni in attesa", "1 sessione senza ricetta",
                        "1 registro da completare", "Fermentazioni contemporanee",
                        "Pasta fresca regionale · domani", "Pane e lievitati naturali · ieri")
                .doesNotContain("Scorte", "scorte", "Da ordinare");
        assertThat(fxml.indexOf("fx:id=\"preparationToggle\"")).isLessThan(fxml.indexOf("fx:id=\"preparationSection\""));
        assertThat(fxml.indexOf("fx:id=\"preparationSection\"")).isLessThan(fxml.indexOf("fx:id=\"taskRows\""));
        assertThat(fxml).doesNotContain("Menu da completare", "Completa menu");
    }

    @Test
    void dashboardExposesSessionAndPreparationActionsWithLiveOperationalPanels() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/dashboard/dashboard-workspace.fxml");

        assertThat(fxml)
                .contains("fx:id=\"daySessions\"", "fx:id=\"selectedDayLabel\"", "agenda-tasks")
                .doesNotContain("fx:id=\"calculationDisclosure\"", "Come si ottiene il totale")
                .doesNotContain("fx:id=\"attendanceProgress\"", "fx:id=\"requirementPreview\"",
                        "Preparazione della sessione selezionata")
                .doesNotContain("Calcola i fabbisogni", "fx:id=\"requirementsButton\"")
                .doesNotContain("Stato operativo", "text=\"2 di 3 completati\"",
                        "dashboard-status-summary", "dashboard-status-grid",
                        "dashboard-status-card", "Registro sessione",
                        "Da completare dopo la lezione", "text=\"Apri il corso\"",
                        "onAction=\"#openFeaturedCourse\"",
                        "text=\"Azioni sessione\"", "text=\"Vedi partecipanti\"",
                        "dashboard-preparation-track", "preparation-line",
                        "preparation-track-line", "Registro da completare", "In sospeso");
    }

    @Test
    void chefCourseCatalogUsesLabelledReactiveFilters() throws IOException {
        var chef = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");

        assertThat(chef)
                .contains("promptText=\"Tutte le categorie\"", "accessibleText=\"Filtra i corsi per categoria\"")
                .contains("fx:id=\"categoryFilter\"", "onAction=\"#filterCourses\"")
                .doesNotContain("fx:id=\"statusFilter\"", "labelFor=\"$statusFilter\"",
                        "text=\"Reimposta\"", "text=\"Filtra\"");
    }

    @Test
    void planningUsesOnlyConfirmedAttendanceToExposeRequiredQuantities() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/planning/planning-workspace.fxml");

        assertThat(fxml)
                .contains("fx:id=\"periodField\"", "fx:id=\"reportYearField\"")
                .contains("text=\"Corso\" labelFor=\"$courseField\"")
                .contains("text=\"Sessione pratica\" labelFor=\"$sessionField\"")
                .contains("text=\"Ingrediente\"", "fx:id=\"confirmedParticipantsColumn\" text=\"Confermati\"",
                        "text=\"Quantità necessaria\"")
                .contains("fx:id=\"reportPanel\"", "text=\"Mese\"")
                .doesNotContain("Filtra fabbisogni", "planning-filter-surface",
                        "fx:id=\"planningResetFiltersButton\"", "text=\"Reimposta\"",
                        "onAction=\"#resetPlanningFilters\"")
                .doesNotContain("text=\"Disponibile\"", "text=\"Stato\"",
                        "Scorte", "scorte", "Da ordinare", "editable=\"true\"",
                        "onAction=\"#applyReportPeriod\"",
                        "Quantità calcolate dalla ricetta", "planning-anti-waste");
    }

    @Test
    void courseCalendarExposesRecipeAssignmentInsteadOfHidingItsColumn() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");

        assertThat(fxml)
                .contains("fx:id=\"recipeCountColumn\" text=\"Menu\"")
                .doesNotContain("fx:id=\"recipeCountColumn\" visible=\"false\"");
    }

    @Test
    void disabledControlsHaveACommonVisibleTreatment() throws IOException {
        var css = resource("/it/uninafoodlab/presentation/styles/application.css");

        assertThat(css).containsPattern(
                "(?s)\\.button:disabled,\\s*\\.toggle-button:disabled\\s*\\{"
                        + "[^}]*-fx-opacity: 1;"
                        + "[^}]*-fx-background-color: #E4E8E5;"
                        + "[^}]*-fx-border-color: #CDD5D0;"
                        + "[^}]*-fx-text-fill: #5C6869;"
                        + "[^}]*-fx-cursor: default;");
    }

    private static String resource(String path) throws IOException {
        try (var input = DesktopUiResourcesTest.class.getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), UTF_8);
        }
    }
}
