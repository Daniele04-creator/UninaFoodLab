package it.uninafoodlab.presentation.operations;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChefWorkspaceResourcesTest {

    @Test
    void dashboardKeepsDayNavigationAndPreparationQueueWithoutDuplicateDetails() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/dashboard/dashboard-workspace.fxml");
        assertThat(fxml)
                .contains("fx:id=\"daySessions\"", "fx:id=\"weekSchedule\"", "fx:id=\"selectedDayLabel\"",
                        "onAction=\"#previousWeek\"", "onAction=\"#nextWeek\"", "onAction=\"#goToToday\"",
                        "fx:id=\"preparationToggle\"", "onAction=\"#togglePreparation\"",
                        "fx:id=\"taskRows\"", "accessibleText=")
                .doesNotContain("fx:id=\"weekRows\"", "fx:id=\"dashboardRail\"",
                        "fx:id=\"menuPreview\"", "fx:id=\"requirementPreview\"", "metric-card");
    }

    @Test
    void dashboardProvidesLoadingRetryAndEmptyDayNavigation() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/dashboard/dashboard-workspace.fxml");
        assertThat(fxml).contains("fx:id=\"progressIndicator\"", "fx:id=\"feedbackLabel\"",
                "onAction=\"#refresh\"", "fx:id=\"emptyDayPanel\"", "onAction=\"#goToNextAvailable\"")
                .doesNotContain("UUID", "service-board", "Preparazione della sessione selezionata");
    }

    @Test
    void notificationWorkspaceContainsOnlyChefCommunicationActions() throws IOException {
        var fxml = resource(
                "/it/uninafoodlab/presentation/notification/notification-workspace.fxml");

        assertThat(fxml)
                .contains("Nuova comunicazione", "Corso destinatario", "text=\"Invia\"",
                        "Messaggio", "Comunicazioni",
                        "accessibleText=")
                .contains("workspace-hero", "notification-composer", "notification-archive",
                        "notification-detail", "detailTitleLabel", "detailMessageLabel")
                .contains("onAction=\"#toggleComposer\"", "onAction=\"#sendNotification\"")
                .contains("fx:id=\"composeCard\"", "managed=\"false\" visible=\"false\"")
                .doesNotContain("Nuova notifica", "Invia notifica", "Segna come letta",
                        "markSelectedRead", "readFilters", "unreadMessagesButton",
                        "text=\"Aggiorna\"", "onAction=\"#refreshNotifications\"",
                        "UUID", "course_id", "notification_id");
    }

    @Test
    void notificationWorkspaceRefreshesAutomaticallyOnlyWhileActive() throws IOException {
        var controller = Files.readString(Path.of(
                "src/main/java/it/uninafoodlab/presentation/notification/NotificationWorkspaceController.java"),
                UTF_8);

        assertThat(controller)
                .contains("Duration.seconds(5)", "public void activate()", "public void deactivate()",
                        "public void dispose()", "notificationRefresh.playFromStart()",
                        "notificationRefresh.stop()")
                .doesNotContain("private void refreshNotifications()");
    }

    @Test
    void reportWorkspaceContainsPlanningTablesAndNativeCharts() throws IOException {
        var fxml = resource(
                "/it/uninafoodlab/presentation/planning/planning-workspace.fxml");

        assertThat(fxml)
                .contains("Modalità delle sessioni", "online", "in presenza", "corsi tenuti",
                        "Ricette per sessione pratica",
                        "Fabbisogni ingredienti", "Partecipanti e adesioni",
                        "BarChart", "Pianificazione", "Mese del report", "Anno del report", "accessibleText=")
                .contains("workspace-hero", "planning-period-actions", "planning-report-surface",
                        "planning-table-surface")
                .doesNotContain("styleClass=\"metric-card\"")
                .doesNotContain("participantsLink", "participantSessionChoices", "#openParticipants")
                .contains("onAction=\"#openRequirements\"",
                        "onAction=\"#loadSelectedCourseSessions\"",
                        "onAction=\"#loadSessionPlanning\"")
                .contains("fx:id=\"requirementPanel\"", "fx:id=\"participantPanel\"",
                        "fx:id=\"reportPanel\"")
                .contains("studio-report-summary", "report-summary-value")
                .doesNotContain("planningButton", "Aggiorna pianificazione",
                        "Filtra fabbisogni", "planning-filter-surface",
                        "sessioni programmate", "ricette assegnate",
                        "text=\"12 confermati · 2 in attesa\"", "UUID", "session_id")
                .contains("fx:id=\"recipeChart\"")
                .doesNotContain("fx:id=\"recipeChart\" title=\"\" legendVisible=\"false\" animated=\"false\"\n"
                        + "                                  managed=\"false\" visible=\"false\"");
    }

    @Test
    void monthlyChartsUseDedicatedReadablePanels() throws IOException {
        var fxml = resource(
                "/it/uninafoodlab/presentation/planning/planning-workspace.fxml");

        assertThat(fxml)
                .contains("ops-chart-panel", "ops-session-chart", "ops-recipe-chart",
                        "fx:id=\"sessionChart\"", "fx:id=\"recipeChart\"",
                        "forceZeroInRange=\"true\"", "animated=\"false\"")
                .doesNotContain("text=\"Aggiorna pianificazione\"");
    }

    @Test
    void monthlyReportUsesConciseCopyAndCoherentAccentBars() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/planning/planning-workspace.fxml");
        var css = resource("/it/uninafoodlab/presentation/planning/planning-workspace.css");

        assertThat(fxml)
                .doesNotContain("pageEyebrow", "Sono incluse solo le sessioni segnate come svolte");
        assertThat(css)
                .contains(".chart-bar.report-recipe-bar { -fx-bar-fill: -studio-primary; -fx-background-color: -studio-primary;")
                .contains(".chart-bar.report-practical-bar { -fx-bar-fill: -studio-primary; -fx-background-color: -studio-primary;")
                .contains(".chart-bar.report-online-bar { -fx-bar-fill: -studio-primary; -fx-background-color: -studio-primary;")
                .doesNotContain(".ops-recipe-chart .data1", ".ops-session-chart .chart-bar");
    }

    private static String resource(String path) throws IOException {
        try (var resource = ChefWorkspaceResourcesTest.class.getResourceAsStream(path)) {
            assertThat(resource).as(path).isNotNull();
            return new String(resource.readAllBytes(), UTF_8);
        }
    }

}
