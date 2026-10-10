package it.uninafoodlab.presentation.course;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CourseWorkspaceResourcesTest {

    @Test
    void coursePagesUseTaskLabelsAndOneContinuousCreationScroll() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");

        assertThat(fxml)
                .doesNotContain("ORGANIZZA IL TUO INSEGNAMENTO",
                        "Dal programma al laboratorio, ogni corso in un unico posto.",
                        "course-create-preview-scroll")
                .contains("fx:id=\"createCourseView\" fitToWidth=\"true\"",
                        "fx:id=\"sessionModeEditor\"",
                        "visible=\"${createCourseView.visible}\" managed=\"${createCourseView.visible}\"");
    }

    @Test
    void chefCatalogOffersActivePastAndAllCourseFilters() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");
        var css = resource("/it/uninafoodlab/presentation/styles/application.css");

        assertThat(fxml)
                .contains(
                        "fx:id=\"activeCoursesTab\" minWidth=\"100\" prefWidth=\"100\" text=\"Attivi\" selected=\"true\"",
                        "onAction=\"#showActiveCourses\"",
                        "fx:id=\"archiveCoursesTab\" minWidth=\"100\" prefWidth=\"100\" text=\"Passati\"",
                        "onAction=\"#showArchivedCourses\"",
                        "Mostra i corsi attivi",
                        "Mostra i corsi passati e annullati")
                .contains("course-catalog-tabs", "course-catalog-tab");
        assertThat(css)
                .contains(".course-catalog-tabs", ".course-catalog-tab:selected");
    }

    @Test
    void workspaceContainsTheCompleteChefFlowWithoutTechnicalIdentifiers() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");

        assertThat(fxml)
                .contains(
                        "Nuovo corso",
                        "Categoria",
                        "Nuova categoria",
                        "Data di inizio",
                        "Frequenza",
                        "Durata",
                        "Sessioni",
                        "Crea corso",
                        "Filtra i corsi per categoria",
                        "Ricette disponibili",
                        "Aggiungi dal ricettario")
                .contains("course-index-page", "fx:id=\"courseCards\"")
                .contains("fx:id=\"courseDetailView\"", "lab-workbench")
                .contains("course-catalog-layout", "course-index-page", "course-detail-panel")
                .contains("fx:id=\"createCoursePanel\"", "onAction=\"#toggleCourseForm\"")
                .contains("fx:id=\"courseSectionStack\"", "onAction=\"#openAddSessionDialog\"")
                .contains("fx:id=\"recipePane\"", "onAction=\"#showRecipes\"")
                .contains("fx:id=\"recipeAvailabilityLabel\"")
                .contains("recipeTaskDescription")
                .contains("minHeight=\"76.0\"")
                .contains("Il percorso")
                .contains("Adesioni", "Iscritti al corso")
                .doesNotContain("Segna svolta", "Segna come svolta", "Correggi svolgimento", "completeSessionButton", "#openLifecycle",
                        "onAction=\"#completeSession\"",
                        "Storico", "sessionHistoryTable", "historyPane", "historyTab")
                .contains("accessibleText=")
                .contains("onAction=\"#createCourse\"")
                .contains("onAction=\"#createCategory\"")
                .contains("onAction=\"#filterCourses\"")
                .contains("fx:id=\"recipeCatalogRows\"", "fx:id=\"recipeSearchField\"")
                .doesNotContain("UUID", "course_id", "session_id");
    }

    @Test
    void courseDetailOffersReadOnlyParticipantsWithoutChefAttendanceManagement()
            throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");
        var shell = java.nio.file.Files.readString(
                java.nio.file.Path.of(
                        "src/main/java/it/uninafoodlab/presentation/shell/AppShellController.java"),
                UTF_8);

        assertThat(fxml)
                .contains(
                        "fx:id=\"courseSectionStack\"",
                        "fx:id=\"programTab\" text=\"Calendario\"",
                        "fx:id=\"recipeTab\" text=\"Ricette\"",
                        "onAction=\"#showProgram\"",
                        "onAction=\"#showRecipes\"",
                        "fx:id=\"programPane\"",
                        "fx:id=\"recipePane\"")
                .doesNotContain(
                        "fx:id=\"participantsTab\"",
                        "fx:id=\"participantsPane\"",
                        "fx:id=\"participantSessionField\"",
                        "fx:id=\"participantTable\"",
                        "participantNameColumn",
                        "participantEnrollmentColumn",
                        "participantAttendanceColumn",
                        "fx:id=\"historyTab\"",
                        "onAction=\"#showHistory\"",
                        "fx:id=\"historyPane\"",
                        "text=\"Dettaglio modifica\"",
                        "<Label text=\"Programma\" styleClass=\"detail-tab-active\"/>",
                        "<Label text=\"Partecipanti\" styleClass=\"detail-tab\"/>");
        assertThat(shell)
                .contains("manageCourseSchedule);")
                .doesNotContain("manageCourseSchedule,\n                    browseChefPlanning");
    }

    @Test
    void createCourseSeparatesDescriptionScheduleAndSessionPreparation()
            throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");
        var css = resource("/it/uninafoodlab/presentation/styles/application.css");

        assertThat(fxml)
                .contains(
                        "fx:id=\"courseCatalogView\"",
                        "fx:id=\"createCourseView\"",
                        "fx:id=\"createCoursePanel\"",
                        "Torna ai corsi",
                        "Il corso",
                        "Il calendario",
                        "Modalità delle sessioni",
                        "course-create-workspace",
                        "course-create-form-column",
                        "course-create-calendar-column",
                        "course-create-summary",
                        "fx:id=\"courseSummaryLabel\"",
                        "minHeight=\"0.0\"",
                        "text=\"Scarta bozza\"",
                        "onAction=\"#discardCourseDraft\"",
                        "onAction=\"#toggleCourseForm\"")
                .doesNotContain("<ScrollPane fx:id=\"createCoursePanel\"",
                        "course-create-form-column,lab-card", "course-create-preview-column,lab-card");
        assertThat(css)
                .contains(".text-area .content", "-fx-padding: 12px", "-fx-background-color: -studio-surface");
    }

    @Test
    void shellUsesAnInjectedControllerAndHostsFeatureWorkspaces() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/shell/app-shell.fxml");

        assertThat(fxml)
                .doesNotContain("fx:controller=")
                .contains("fx:id=\"contentHost\"");
    }

    @Test
    void stylesheetProvidesVisibleFocusForEveryNewInteractiveFamily() throws IOException {
        var css = resource("/it/uninafoodlab/presentation/styles/application.css");

        assertThat(css)
                .contains(
                        ".text-field:focused",
                        ".combo-box:focused",
                        ".button:focus-visible",
                        ".list-view:focused",
                        ".table-view:focused",
                        "-fx-wrap-text: true");
    }

    @Test
    void compositeCourseControlsKeepTheirTextInsideTheAvailableHeight() throws IOException {
        var css = resource("/it/uninafoodlab/presentation/styles/application.css");

        assertThat(css)
                .contains(".combo-box .list-cell", ".date-picker .text-field", ".spinner .text-field")
                .contains("-fx-min-height: 44px", "-fx-padding: 8px 10px");
    }

    @Test
    void workspaceUsesShortActionLabelsAndSemanticButtonTreatments() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");
        var css = resource("/it/uninafoodlab/presentation/styles/application.css");

        assertThat(fxml)
                .contains("Modifica calendario", "Aggiungi dal ricettario", "Cancella sessione")
                .doesNotContain("Segna svolta", "Aggiungi ricetta alla sessione");
        assertThat(css)
                .contains(".button-primary", ".button-secondary", ".button-danger")
                .contains(".button-primary:hover", ".button-primary:pressed", ".button:disabled");
    }

    @Test
    void courseActionsUseSectionNavigationAndModalEditors() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");
        var controller = javaResource("CourseSessionTable.java") + javaResource("CourseOperationDialogs.java")
                + javaResource("CourseScheduleCommands.java");

        assertThat(fxml)
                .contains("course-section-nav", "course-section-stack")
                .contains("text=\"Calendario\"", "text=\"Ricette\"")
                .contains("text=\"Aggiungi sessione\"", "text=\"Modifica calendario\"")
                .contains("onAction=\"#openAddSessionDialog\"")
                .contains("onAction=\"#openRescheduleSessionDialog\"")
                .doesNotContain("fx:id=\"calendarEditor\"", "fx:id=\"recipeEditor\"",
                        "fx:id=\"sessionCountLabel\"", "session-count-chip");
        assertThat(controller)
                .contains("status-complete.png", "status-pending.png")
                .contains("new Dialog<ButtonType>()", "DialogTheme.apply(dialog,", "dialog.showAndWait()")
                .doesNotContain("session-confirmed.png", "session-pending.png", "sessions.stream().limit(3)");
    }

    @Test
    void sessionStateSymbolsKeepTheirOutlineInsideTransparentEdges() throws IOException {
        assertIconHasTransparentMargin("/it/uninafoodlab/presentation/assets/status-complete.png");
        assertIconHasTransparentMargin("/it/uninafoodlab/presentation/assets/status-pending.png");
        var controller = javaResource("CourseSessionTable.java") + javaResource("CourseOperationDialogs.java");
        assertThat(controller)
                .contains("icon.setFitWidth(22.0)", "icon.setFitHeight(22.0)", "icon.setSmooth(true)");
    }

    @Test
    void chefCommandsUseShortVisibleLabelsWithoutEllipses() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");

        assertThat(fxml)
                .contains("fx:id=\"recipeSearchField\"", "fx:id=\"recipeCatalogRows\"")
                .doesNotContain("fx:id=\"recipeField\"", "fx:id=\"assignButton\"")
                .doesNotContain("text=\"Reimposta\"", "text=\"Filtra\"", "text=\"...\"",
                        "fx:id=\"statusFilter\"", "labelFor=\"$statusFilter\"");
    }

    @Test
    void sessionCalendarsSizeTheirRowsFromActualContent() throws IOException {
        var chefFxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");
        var chefController = javaResource("CourseWorkspaceData.java");

        assertThat(chefFxml)
                .contains("fx:id=\"sessionTable\"");
        assertThat(chefController).contains("SessionTableSizing.apply(fields.sessionTable()")
                .contains("SessionTableSizing.apply(fields.sessionTable(), 0);");
    }

    @Test
    void tablePlaceholderIsDeclaredAsANodeInsteadOfAStringAttribute() throws IOException {
        var fxml = resource("/it/uninafoodlab/presentation/course/course-workspace.fxml");

        assertThat(fxml)
                .contains("<placeholder>", "Nessuna sessione disponibile")
                .doesNotContain("placeholder=\"Nessuna sessione disponibile\"");
    }

    private static String resource(String path) throws IOException {
        try (var resource = CourseWorkspaceResourcesTest.class.getResourceAsStream(path)) {
            assertThat(resource).as(path).isNotNull();
            return new String(resource.readAllBytes(), UTF_8);
        }
    }

    private static String javaResource(String fileName) throws IOException {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/it/uninafoodlab/presentation/course", fileName), UTF_8);
    }

    private static void assertIconHasTransparentMargin(String path) throws IOException {
        try (var resource = CourseWorkspaceResourcesTest.class.getResourceAsStream(path)) {
            assertThat(resource).as(path).isNotNull();
            BufferedImage image = ImageIO.read(resource);
            assertThat(image).as(path).isNotNull();

            for (int x = 0; x < image.getWidth(); x++) {
                assertThat(image.getRGB(x, 0) >>> 24).as(path + " top edge").isZero();
                assertThat(image.getRGB(x, image.getHeight() - 1) >>> 24)
                        .as(path + " bottom edge").isZero();
            }
            for (int y = 0; y < image.getHeight(); y++) {
                assertThat(image.getRGB(0, y) >>> 24).as(path + " left edge").isZero();
                assertThat(image.getRGB(image.getWidth() - 1, y) >>> 24)
                        .as(path + " right edge").isZero();
            }
        }
    }

}
