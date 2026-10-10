package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.application.course.*;
import it.uninafoodlab.application.schedule.*;
import it.uninafoodlab.domain.course.*;
import it.uninafoodlab.domain.identity.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;

public final class CourseUiSmoke {
    private static volatile List<CourseSummary> catalogCourses = List.of();
    private static volatile List<RecipeSummary> catalogRecipes = List.of();
    private static volatile Map<UUID, List<CourseSessionSummary>> catalogSessions = Map.of();
    private static volatile String lastCourseSearch = "";
    private static volatile String lastRecipeSearch = "";
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var chef = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("smoke.chef"), "Chef sintetico", Role.CHEF);
            var courses = readOnly(CourseRepository.class);
            var catalog = readOnly(CatalogRepository.class);
            var controller = new CourseWorkspaceController(chef, new BrowseCatalog(catalog), new CreateCategory(catalog),
                    new CreateCourse(courses), new BrowseCourses(courses), new AssignRecipeToSession(courses),
                    new ManageCourseSchedule(readOnly(ScheduleRepository.class)),
                    Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneOffset.UTC));
            fx(() -> {
                var loader = new FXMLLoader(CourseUiSmoke.class.getResource("/it/uninafoodlab/presentation/course/course-workspace.fxml"));
                loader.setController(controller);

                var scene = new javafx.scene.Scene(loader.<javafx.scene.Parent>load(), 800, 760);
                scene.getStylesheets().add(CourseUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                var initialStage = new javafx.stage.Stage();
                initialStage.setScene(scene);
                initialStage.show();
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                return null;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
            if (args.length > 0 && args[0].equals("paging")) {
                verifyCatalogPagination(controller);
                System.out.println("COURSE_PAGING_OK");
                return;
            }
            var recipes = List.of(new RecipeSummary(UUID.randomUUID(), UUID.randomUUID(), "Italiana", "Pizza", "Preparazione", 4, 2),
                    new RecipeSummary(UUID.randomUUID(), UUID.randomUUID(), "Orientale", "Bao", "Preparazione", 6, 3));
            catalogRecipes = recipes;
            fx(() -> {
                verifyCardNavigation();
                verifyCourseStateLabels();
                verifyCatalogHeading(controller);
                verifyBackAffordances(controller);
                check(node(controller, "courseSelectorRail").isVisible()
                                && !node(controller, "courseDetailView").isVisible(),
                        "Initial entry must show only the course catalog");
                invoke(controller, "toggleCourseForm");
                var editorHost = (javafx.scene.layout.TilePane) field(controller, "sessionModeEditor");
                var firstEditor = (javafx.scene.layout.VBox) editorHost.getChildren().getFirst();
                var draftMode = (ComboBox<SessionMode>) firstEditor.getUserData();
                draftMode.setValue(SessionMode.ONLINE);
                firstEditor = (javafx.scene.layout.VBox) editorHost.getChildren().getFirst();
                var onlineField = (TextField) firstEditor.lookup(".text-field");
                onlineField.setText("https://example.test/draft-preserved");
                draftMode = (ComboBox<SessionMode>) firstEditor.getUserData();
                draftMode.setValue(SessionMode.PRACTICAL);
                firstEditor = (javafx.scene.layout.VBox) editorHost.getChildren().getFirst();
                draftMode = (ComboBox<SessionMode>) firstEditor.getUserData();
                check(draftMode.getScene().getFocusOwner() == draftMode,
                        "Changing draft mode must retain focus on the replacement mode selector");
                draftMode.setValue(SessionMode.ONLINE);
                firstEditor = (javafx.scene.layout.VBox) editorHost.getChildren().getFirst();
                onlineField = (TextField) firstEditor.lookup(".text-field");
                check(onlineField.getText().equals("https://example.test/draft-preserved"),
                        "Recomposing the session editor must preserve the online draft");
                var root = firstEditor.getScene().getRoot();
                root.applyCss();
                root.layout();
                check(onlineField.getWidth() >= 200,
                        "Session link must keep a readable input width in the compact workspace");
                verifySessionDraftControls(controller);
                verifyCourseValidationFocus(controller);
                invoke(controller, "toggleCourseForm");
                check(node(controller, "openCreateCourseButton").getScene().getFocusOwner()
                        == node(controller, "openCreateCourseButton"), "Closing course form restores New course focus");
                controller.setSessionReturnAction("Torna all'agenda", () -> {});
                controller.showCourseIndex();
                check(field(field(controller, "detailPane"), "sessionReturnAction") == null && node(controller, "courseSelectorRail").isVisible(),
                        "Global Courses must restore index and clear contextual return");
                invoke(controller, "showCourseParticipants");
                check(node(controller, "sessionReturnButton").getScene().getFocusOwner()
                        == node(controller, "sessionReturnButton"),
                        "Opening course enrollments moves focus to the visible contextual return");
                var enrollmentAction = new Button("Azione iscritti sintetica");
                ((javafx.scene.layout.StackPane) field(controller, "sessionPlanningHost")).getChildren().add(enrollmentAction);
                enrollmentAction.requestFocus();
                invoke(controller, "showCourseParticipants");
                check(enrollmentAction.getScene().getFocusOwner() == enrollmentAction,
                        "Refreshing the same section must preserve the current user focus");
                invoke(controller, "showProgram");
                check(node(controller, "courseReturnButton").getScene().getFocusOwner()
                        == node(controller, "courseReturnButton"),
                        "Returning to the program moves focus to the course controls");
                schedule(controller).resetScheduleEditorForNewSession();
                var location = (TextField) field(schedule(controller), "scheduleLocationField");
                var mode = (ComboBox<SessionMode>) field(schedule(controller), "scheduleModeField");
                location.setText("Sede personalizzata");
                mode.setValue(SessionMode.ONLINE);
                location.setText("https://example.test/lesson");
                mode.setValue(SessionMode.PRACTICAL);
                check(location.getText().equals("Sede personalizzata"), "Practical venue survives mode roundtrip");
                mode.setValue(SessionMode.ONLINE);
                check(location.getText().equals("https://example.test/lesson"), "Online link survives mode roundtrip");
                var message = (TextArea) field(schedule(controller), "scheduleNotificationMessageField");
                message.setText("Messaggio personalizzato dello chef");
                ((DatePicker) field(schedule(controller), "scheduleDateField")).setValue(LocalDate.of(2026, 10, 10));
                check(message.getText().equals("Messaggio personalizzato dello chef"), "Date changes must preserve custom notification text");
                menu(controller).setRecipeCatalogItems(new it.uninafoodlab.application.common.Page<>(recipes, recipes.size()), 0);
                var results = (javafx.scene.layout.VBox) field(controller, "recipeCatalogRows");
                check(results.getChildren().size() == 2, "Catalog recipes must be immediately visible without opening a selector");
                ((TextField) field(controller, "recipeSearchField")).setText("pizza");
                return null;
            });
            awaitRecipeResults(controller, 1);
            fx(() -> {
                var results = (javafx.scene.layout.VBox) field(controller, "recipeCatalogRows");
                check(results.getChildren().size() == 1 && results.getChildren().getFirst().getUserData().equals(recipes.getFirst().id()),
                        "Typing must directly filter visible recipe rows");
                ((TextField) field(controller, "recipeSearchField")).setText("non presente");
                return null;
            });
            awaitRecipeResults(controller, 0);
            fx(() -> {
                var results = (javafx.scene.layout.VBox) field(controller, "recipeCatalogRows");
                check(results.getChildren().isEmpty() && ((Label) field(controller, "recipeResultsLabel")).getText().contains("Nessuna ricetta"),
                        "Empty search must give explicit feedback");
                ((TextField) field(controller, "recipeSearchField")).clear();
                return null;
            });
            awaitRecipeResults(controller, 2);
            fx(() -> {
                var results = (javafx.scene.layout.VBox) field(controller, "recipeCatalogRows");
                var returned = new java.util.concurrent.atomic.AtomicBoolean();
                controller.setSessionReturnAction("Torna all'agenda", () -> returned.set(true));
                ((Button) field(controller, "sessionReturnButton")).fire();
                check(returned.get(), "Session back action must return to its entry point");
                controller.setSessionReturnAction(null, null);
                check(((Button) field(controller, "sessionReturnButton")).getText().equals("Torna al programma"),
                        "Standard course entry must restore program return");
                var start = LocalDateTime.parse("2026-09-14T18:00");
                var custom = start.minusHours(24);
                var longVenue = ("Laboratorio didattico principale · ala nord, banco dimostrativo "
                        + "per le esercitazioni di pasticceria avanzata. ").repeat(6);
                var practical = new CourseSessionSummary(UUID.randomUUID(), 1, start, SessionMode.PRACTICAL,
                        SessionStatus.SCHEDULED, 90, Optional.empty(), Optional.of(longVenue),
                        Optional.of(12), Optional.of(custom), Optional.empty(), List.of(recipes.getFirst().id()), 1);
                select(controller, practical);
                availability(controller).update();
                invoke(controller, "showProgram");
                var workspaceRoot = node(controller, "courseSearchField").getScene().getRoot();
                workspaceRoot.applyCss();
                workspaceRoot.layout();
                workspaceRoot.layout();
                var agenda = (javafx.scene.layout.VBox) field(controller, "sessionAgenda");
                var track = (javafx.scene.layout.VBox) agenda.getChildren().getFirst();
                var agendaRow = (ToggleButton) track.getChildren().getFirst();
                var locationLabel = (Label) agendaRow.lookup(".course-session-location");
                var agendaGrid = (javafx.scene.layout.GridPane) agendaRow.getGraphic();
                var rowBounds = agendaRow.localToScene(agendaRow.getBoundsInLocal());
                var locationBounds = locationLabel.localToScene(locationLabel.getBoundsInLocal());
                check(agendaRow.getHeight() > 95 && locationBounds.getMaxY() <= rowBounds.getMaxY() + 1,
                        "Long practical venues must grow the timeline row without clipping: row="
                                + agendaRow.getHeight() + ", locationBottom=" + locationBounds.getMaxY()
                                + ", rowBottom=" + rowBounds.getMaxY() + ", locationWidth="
                                + locationLabel.getWidth() + ", locationHeight=" + locationLabel.getHeight()
                                + ", locationPrefHeight=" + locationLabel.prefHeight(locationLabel.getWidth())
                                + ", textWidth=" + locationLabel.prefWidth(-1) + ", gridWidth=" + agendaGrid.getWidth()
                                + ", gridPrefHeight=" + agendaGrid.prefHeight(agendaGrid.getWidth())
                                + ", rowPrefHeight=" + agendaRow.getPrefHeight() + ", rowPrefBound="
                                + agendaRow.prefHeightProperty().isBound() + ", rowMaxHeight=" + agendaRow.getMaxHeight()
                                + ", trackHeight=" + track.getHeight() + ", agendaHeight=" + agenda.getHeight());
                ((ComboBox<CourseSessionSummary>) field(controller, "recipeSessionField")).setValue(practical);
                invoke(controller, "showRecipes");
                verifySessionDetailMetadata(controller, practical);
                menu(controller).renderRecipeCatalog();
                var firstRecipeRow = (javafx.scene.layout.VBox) results.getChildren().getFirst();
                var firstActions = (javafx.scene.layout.FlowPane) firstRecipeRow.getChildren().getLast();
                var alreadyAdded = (Button) firstActions.getChildren().getLast();
                check(alreadyAdded.getText().equals("Nel menu") && alreadyAdded.isDisabled(),
                        "Assigned recipes must remain identifiable and cannot be added twice");
                menu(controller).renderAssignedRecipes(practical, true);
                var assignedRows = (javafx.scene.layout.VBox) field(controller, "assignedRecipeRows");
                check(assignedRows.getChildren().size() == 1,
                        "Session menu must contain only assigned recipes, not the entire catalog");
                var assignedActions = (javafx.scene.layout.FlowPane)
                        ((javafx.scene.Parent) assignedRows.getChildren().getFirst()).getChildrenUnmodifiable().getLast();
                var assignedRow = (javafx.scene.layout.Region) assignedRows.getChildren().getFirst();
                assignedRow.applyCss();
                assignedRow.resize(620, assignedRow.prefHeight(620));
                assignedRow.layout();
                var assignedCopy = ((javafx.scene.Parent) assignedRow).getChildrenUnmodifiable().getFirst();
                check(assignedActions.getBoundsInParent().getMinX() >= assignedCopy.getBoundsInParent().getMaxX(),
                        "A wide menu row places opening actions beside the recipe to save vertical space");
                assignedRow.resize(320, assignedRow.prefHeight(320));
                assignedRow.resize(320, assignedRow.prefHeight(320));
                assignedRow.layout();
                check(assignedActions.getBoundsInParent().getMinY() >= assignedCopy.getBoundsInParent().getMaxY(),
                        "A narrow menu row wraps actions below the recipe without losing its title or controls");
                check(((Button) assignedActions.getChildren().getFirst()).getAccessibleText().contains("Pizza")
                                && assignedActions.getChildren().getLast().isVisible(),
                        "An editable assigned recipe must offer named details and removal");
                menu(controller).renderAssignedRecipes(practical, false);
                assignedActions = (javafx.scene.layout.FlowPane)
                        ((javafx.scene.Parent) assignedRows.getChildren().getFirst()).getChildrenUnmodifiable().getLast();
                check(!assignedActions.getChildren().getLast().isVisible()
                                && !assignedActions.getChildren().getLast().isManaged(),
                        "Closed session menus must remove the removal action from both interaction and layout");
                var coordinator = (CourseWorkspaceCoordinator) field(controller, "coordinator");
                var detailsButton = (Button) firstActions.getChildren().getFirst();
                detailsButton.requestFocus();
                check(detailsButton.getScene().getFocusOwner() == detailsButton, "Recipe action must receive focus");
                coordinator.setBusy(true);
                availability(controller).update();
                check(detailsButton.getScene() == null && ((TextField) field(controller, "recipeSearchField")).getScene().getFocusOwner()
                        == field(controller, "recipeSearchField"), "Rebuilding focused results must move focus to search");
                check(((Label) field(controller, "recipeResultsLabel")).getText().contains("Aggiornamento"),
                        "Loading must be explicit in the recipe block");
                for (var rowNode : results.getChildren()) {
                    var actions = (javafx.scene.layout.FlowPane) ((javafx.scene.layout.VBox) rowNode).getChildren().getLast();
                    check(actions.getChildren().stream().allMatch(Node::isDisabled), "Busy state must disable every recipe action");
                }
                coordinator.setBusy(false);
                menu(controller).markCatalogUnavailable();
                availability(controller).update();
                check(node(controller, "recipeRetryButton").isVisible(), "Failed catalog refresh must expose retry beside the results");
                menu(controller).setRecipeCatalogItems(new it.uninafoodlab.application.common.Page<>(recipes, recipes.size()), 0);
                var sectionField = CourseCatalogPane.class.getDeclaredField("catalogSection");
                sectionField.setAccessible(true);
                sectionField.set(field(controller, "catalogPane"), CourseOperationalView.CatalogSection.ARCHIVE);
                availability(controller).update();
                check(!node(controller, "recipeAddPanel").isVisible(), "Archived session must hide menu editing");
                sectionField.set(field(controller, "catalogPane"), CourseOperationalView.CatalogSection.ACTIVE);
                availability(controller).update();
                var time = (TimeSelector) field(schedule(controller), "scheduleTimeField");
                time.setValue(LocalTime.of(19, 0));
                ((DatePicker) field(schedule(controller), "scheduleDateField")).setValue(start.toLocalDate().plusDays(1));
                check(((DatePicker) field(schedule(controller), "scheduleAttendanceDateField")).getValue().equals(custom.toLocalDate())
                        && ((TimeSelector) field(schedule(controller), "scheduleAttendanceTimeField")).getValue().equals(custom.toLocalTime()),
                        "Changing real date/time controls must preserve custom cutoff");
                check(!node(controller, "programLessonActions").isVisible(), "Practical session must hide online actions");
                check(agendaRowGraphic(controller).lookup(".course-session-menu-count") != null,
                        "Practical agenda rows must retain their recipe-count badge");
                verifyPositionLinkActions(controller, start);
                select(controller, online(Optional.of(URI.create("https://meet.example.org/lesson"))));
                verifySessionDetailMetadata(controller,
                        ((TableView<CourseSessionSummary>) field(controller, "sessionTable")).getSelectionModel().getSelectedItem());
                check(node(controller, "programLessonActions").isVisible(), "Online actions must be visible in program");
                check(agendaRowGraphic(controller).lookup(".course-session-menu-count") == null,
                        "Online agenda rows must omit the ambiguous recipe-count badge");
                check(agendaRowGraphic(controller).lookup(".course-session-state") != null
                                && agendaRowGraphic(controller).lookup(".course-session-mode") != null,
                        "Online agenda rows must retain their status and mode labels");
                var onlineMode = (Label) agendaRowGraphic(controller).lookup(".course-session-mode");
                check(onlineMode.getGraphic() instanceof javafx.scene.image.ImageView,
                        "Online session mode must have its video icon beside the text");
                for (String id : List.of("openProgramLessonButton", "copyProgramLessonButton", "openDetailLessonButton", "copyDetailLessonButton")) {
                    var button = (Button) field(controller, id);
                    check(!button.isDisabled() && button.isFocusTraversable() && button.getOnAction() != null
                            && !button.getAccessibleText().isBlank(), "Online actions must be enabled, keyboard focusable and named: " + id);
                }
                invoke(controller, "showRecipes");
                check(node(controller, "detailLessonActions").isVisible(), "Online detail must expose lesson actions");
                check(((Label) field(controller, "assignedRecipesLabel")).getText()
                                .equals("Questa sessione online non prevede un menu."),
                        "Online menu uses one plain message without repeated session metadata");
                check(!node(controller, "recipeTaskDescription").isManaged()
                                && !node(controller, "recipeAvailabilityLabel").isManaged()
                                && ((javafx.scene.layout.VBox) field(controller, "assignedRecipeRows")).getChildren().isEmpty(),
                        "Online menu omits practical instructions and empty recipe notices");
                it.uninafoodlab.presentation.FxSnapshot.save(node(controller, "recipePane").getScene().getRoot(),
                        "target/native-match/session-online-menu.png");
                select(controller, practical);
                invoke(controller, "showRecipes");
                check(node(controller, "recipeTaskDescription").isManaged()
                                && node(controller, "recipeAvailabilityLabel").isManaged()
                                && ((Label) field(controller, "assignedRecipesLabel")).getText().equals("Ricette nel menu"),
                        "Returning to a practical session restores its menu and instructions");
                for (var link : List.of(Optional.<URI>empty(), Optional.of(URI.create("file:///C:/synthetic")))) {
                    select(controller, online(link));
                    check(((Button) field(controller, "openProgramLessonButton")).isDisabled()
                            && ((Button) field(controller, "copyDetailLessonButton")).isDisabled(),
                            "Missing or non-web link must disable actions");
                }
                return null;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> "https://maps.example.test/position?room=teaching"
                    .equals(javafx.scene.input.Clipboard.getSystemClipboard().getString()), 3);
            fx(() -> { verifySuggestedAttendanceDeadline(); verifyScheduleCommandInputs(chef); return null; });
            verifyCompactOperationDialog(controller);
            captureApprovedLayouts(controller);
            verifyCatalogPagination(controller);
            System.out.println("PASS: compact native operation dialog keeps title and actions on screen; visible recipe search, empty results, duplicate prevention, origin return; real course FXML preserves custom cutoff after date/time changes; online actions visible, keyboard accessible and safely disabled for invalid links");
        } finally { Platform.exit(); }
    }

    @SuppressWarnings("unchecked")
    private static void captureApprovedLayouts(CourseWorkspaceController controller) throws Exception {
        fx(() -> {
            controller.showCourseIndex();
            var course = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca",
                    CourseTitle.of("Pasta fresca della tradizione"), "Impara impasti, ripieni e formati della tradizione.",
                    LocalDate.of(2026, 9, 24), LocalTime.of(18, 0), 7, 4, Optional.of(LocalDateTime.of(2026, 10, 15, 20, 0)),
                    CourseStatus.PUBLISHED, IngredientMargin.zero());
            var list = (ListView<CourseSummary>) field(controller, "courseList");
            var second = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasticceria",
                    CourseTitle.of("Dolci di stagione"), "Tecniche e ricette di pasticceria.",
                    LocalDate.of(2026, 10, 1), LocalTime.of(18, 0), 7, 3,
                    Optional.of(LocalDateTime.of(2026, 10, 15, 20, 0)),
                    CourseStatus.PUBLISHED, IngredientMargin.zero());
            list.getItems().setAll(course, second);
            list.getSelectionModel().select(course);
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        var root = fx(() -> (javafx.scene.layout.Region) node(controller, "courseSearchField").getScene().getRoot());
        var stylesheet = CourseUiSmoke.class.getResource(
                "/it/uninafoodlab/presentation/styles/application.css").toExternalForm();
        fx(() -> {
            var practical = new CourseSessionSummary(UUID.randomUUID(), 1, LocalDateTime.of(2026, 9, 24, 18, 0),
                    SessionMode.PRACTICAL, SessionStatus.SCHEDULED, 120, Optional.empty(), Optional.of("Laboratorio A"),
                    Optional.of(12), Optional.of(LocalDateTime.of(2026, 9, 24, 17, 59)), Optional.empty(), List.of(), 0);
            select(controller, practical);

            if (!root.getStyleClass().contains("root")) root.getStyleClass().add("root");
            if (!root.getStylesheets().contains(stylesheet)) root.getStylesheets().add(stylesheet);
            return null;
        });

        for (int width : List.of(1000, 1229, 1440, 1536)) {
            var creationStage = fx(() -> {
                var previousScene = root.getScene();
                if (previousScene != null) previousScene.setRoot(new javafx.scene.layout.Pane());
                var scene = new javafx.scene.Scene(root, width, 900);
                scene.getStylesheets().add(stylesheet);
                root.resize(width, 900);
                controller.showCourseIndex();
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/courses-" + width + ".png");
                var cards = (javafx.scene.layout.GridPane) field(controller, "courseCards");
                var card = (javafx.scene.layout.VBox) cards.getChildren().getFirst();
                var last = cards.getChildren().getLast();
                check(last.getBoundsInParent().getMinY() == card.getBoundsInParent().getMinY()
                                && last.getBoundsInParent().getMaxX() >= cards.getWidth() - 12
                                && last.getBoundsInParent().getMaxX() <= cards.getWidth(),
                        "Two visible courses must use the full catalog row at " + width
                                + " px; card=" + card.getPrefWidth() + ", available=" + cards.getWidth()
                                + ", second=" + last.getBoundsInParent());
                if (width >= 1229) {
                    var selection = (ListView<CourseSummary>) field(controller, "courseList");
                    var third = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Panificazione",
                            CourseTitle.of("W".repeat(30)),
                            "Preparazioni e cotture del pane.",
                            LocalDate.of(2026, 10, 8), LocalTime.of(18, 0), 7, 3,
                            Optional.empty(), CourseStatus.PUBLISHED, IngredientMargin.zero());
                    selection.getItems().add(third);
                    root.applyCss();
                    root.layout();
                    check(cards.getChildren().size() == 3
                                    && cards.getChildren().getLast().getBoundsInParent().getMaxX() <= cards.getWidth()
                                    && cards.getChildren().getLast().getBoundsInParent().getMinY()
                                            == cards.getChildren().getFirst().getBoundsInParent().getMinY(),
                            "Wide catalog must fit three courses on one row without overflow at " + width
                                    + " px; card=" + card.getPrefWidth() + ", available=" + cards.getWidth()
                                    + ", third=" + cards.getChildren().getLast().getBoundsInParent());
                    verifyCardTitleGeometry((javafx.scene.layout.VBox) cards.getChildren().getLast(), width);
                    selection.getItems().remove(third);
                    root.layout();
                }
                verifyCardTitleGeometry(card, width);
                ((Button) card.getChildren().getLast()).fire();
                check(!node(controller, "courseSelectorRail").isVisible() && node(controller, "courseDetailView").isVisible(),
                        "Open course must replace the full-width catalog with the course page");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/course-" + width + ".png");
                invoke(controller, "showRecipes");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/session-" + width + ".png");
                controller.showCourseCreation();
                ((TextField) field(controller, "titleField")).setText("Pasta fresca della tradizione");
                ((TextArea) field(controller, "descriptionField")).setText("Impasti, ripieni e formati della tradizione.");
                var editor = (javafx.scene.layout.TilePane) field(controller, "sessionModeEditor");
                for (var draft : new java.util.ArrayList<>(editor.getChildren())) {
                    ((ComboBox<SessionMode>) draft.getUserData()).setValue(SessionMode.PRACTICAL);
                }
                root.applyCss();
                root.layout();
                var shownStage = new javafx.stage.Stage();
                shownStage.setScene(scene);
                shownStage.show();
                root.applyCss();
                root.layout();
                check(shownStage.isShowing(), "Creation layout must be checked in a shown native window");
                ((ScrollPane) field(controller, "createCourseView")).setVvalue(0);
                return shownStage;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> !root.isNeedsLayout(), 3);
            fx(() -> {
                verifyCreationLayout(controller, width);
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/new-course-" + width + ".png");
                ((ScrollPane) field(controller, "createCourseView")).setVvalue(1);
                return null;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> !root.isNeedsLayout()
                    && ((ScrollPane) field(controller, "createCourseView")).getVvalue() == 1, 3);
            fx(() -> {
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/new-course-sessions-" + width + ".png");
                var viewport = node(controller, "createCourseView").lookup(".viewport");
                var viewportBounds = viewport.localToScene(viewport.getBoundsInLocal());
                var lastEditor = ((javafx.scene.layout.TilePane) field(controller, "sessionModeEditor")).getChildren().getLast();
                var editorBounds = lastEditor.localToScene(lastEditor.getBoundsInLocal());
                check(editorBounds.getMinY() >= viewportBounds.getMinY() && editorBounds.getMaxY() <= viewportBounds.getMaxY() + 1,
                        "The scrolled capture must actually show the complete session editor in the native viewport");
                creationStage.hide();
                controller.showCourseIndex();
                check(node(controller, "courseSelectorRail").isVisible() && !node(controller, "courseDetailView").isVisible(),
                        "Back to courses must restore only the catalog");
                return null;
            });
        }
    }

    private static void verifyCatalogPagination(CourseWorkspaceController controller) throws Exception {
        var courses = java.util.stream.IntStream.rangeClosed(1, 25).mapToObj(index -> new CourseSummary(
                UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca", CourseTitle.of("Corso " + index),
                "Preparazione", LocalDate.of(2030, 10, 2), LocalTime.of(17, 0), 7, 3,
                Optional.of(LocalDateTime.of(2030, 10, 22, 18, 30)), CourseStatus.PUBLISHED,
                IngredientMargin.zero())).toList();
        catalogCourses = courses;
        var actualAppointment = new CourseSessionSummary(UUID.randomUUID(), 2,
                LocalDateTime.of(2030, 11, 6, 18, 45), SessionMode.PRACTICAL, SessionStatus.SCHEDULED,
                90, Optional.empty(), Optional.of("Laboratorio"), Optional.of(12), 0);
        catalogSessions = Map.of(courses.get(12).id(), List.of(actualAppointment));
        fx(() -> {
            controller.showCourseIndex();
            ((CourseWorkspaceCoordinator) field(controller, "coordinator")).loadCourses(null);
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> {
            verifyCatalogHeading(controller);
            var cards = (javafx.scene.layout.GridPane) field(controller, "courseCards");
            check(cards.getChildren().size() == 12, "Course catalog must show twelve actual results per page");
            var total = (Label) field(controller, "courseCountLabel");
            check(total.isManaged() && total.isVisible() && "25 corsi".equals(total.getText()),
                    "The filtered course total must stay visible when only the bottom pager is present");
            var bottom = node(controller, "coursePaginationBottom");
            check(bottom.isManaged() && node(controller, "courseCatalogScroll").lookupAll(".result-pager").stream()
                    .filter(Node::isManaged).count() == 1,
                    "Course results must have one pagination control at the end of the list");
            pagerButton(bottom, "Pagina successiva").fire();
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> {
            var cards = (javafx.scene.layout.GridPane) field(controller, "courseCards");
            check(cards.getChildren().size() == 12 && cards.getChildren().getFirst().getUserData().equals(courses.get(12).id()),
                    "Second page must start with course thirteen");
            var card = (javafx.scene.layout.VBox) cards.getChildren().getFirst();
            card.getScene().getRoot().applyCss();
            card.getScene().getRoot().layout();
            check(((Label) card.lookup(".course-card-appointment-date")).getText().equals("6 novembre, 18:45"),
                    "Course card must show the read appointment instead of projecting the recurrence");
            var practicalMode = (Label) card.lookup(".course-card-mode");
            var badge = (Label) card.lookup(".course-card-badge");
            check("In programma".equals(badge.getText()) && badge.getWidth() < card.getWidth() / 2,
                    "A future course with a next appointment must retain a compact In programma badge");
            check(((Label) card.lookup(".course-card-appointment-caption")).getText().equals("Prossima sessione:"),
                    "The actual appointment must retain the approved concise caption");
            var band = (javafx.scene.layout.Region) card.lookup(".course-card-appointment");
            var fill = (javafx.scene.paint.Color) band.getBackground().getFills().getFirst().getFill();
            check(fill.getOpacity() == 0, "The actual appointment must remain a flat row without a colored inner card");
            var badgeFill = (javafx.scene.paint.Color) badge.getBackground().getFills().getFirst().getFill();
            var badgeText = (javafx.scene.paint.Color) badge.getTextFill();
            check(badgeFill.getSaturation() >= 0.45 && badgeFill.getBlue() > badgeFill.getGreen()
                            && contrastRatio(badgeText, badgeFill) >= 4.5,
                    "Planned badge uses a saturated blue surface with readable text contrast");
            check(practicalMode.getGraphic() instanceof javafx.scene.image.ImageView icon
                            && icon.getImage().getUrl().endsWith("cooking-pot-dark.png"),
                    "Course presence mode must use the cooking pot icon from the approved mockup");
            it.uninafoodlab.presentation.FxSnapshot.save(card.getScene().getRoot(), "target/native-match/courses-paged.png");
            ((Button) card.getChildren().getLast()).fire();
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> {
            controller.showCourseIndex();
            var cards = (javafx.scene.layout.GridPane) field(controller, "courseCards");
            check(cards.getChildren().getFirst().getUserData().equals(courses.get(12).id()),
                    "Returning from course detail must preserve the current page");
            ((TextField) field(controller, "courseSearchField")).setText("Corso 25");
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy()
                && ((javafx.scene.layout.GridPane) field(controller, "courseCards")).getChildren().size() == 1
                && ((javafx.scene.layout.GridPane) field(controller, "courseCards")).getChildren().getFirst()
                        .getUserData().equals(courses.getLast().id()), 10);
        fx(() -> {
            var cards = (javafx.scene.layout.GridPane) field(controller, "courseCards");
            check(cards.getChildren().size() == 1 && cards.getChildren().getFirst().getUserData().equals(courses.getLast().id()),
                    "New course search must reset the page before displaying its matches");
            check(!node(controller, "coursePaginationBottom").isManaged(), "Short result lists must not leave pagination space");
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        var recipes = java.util.stream.IntStream.rangeClosed(1, 25).mapToObj(index -> new RecipeSummary(
                    UUID.randomUUID(), UUID.randomUUID(), "Pasta", "Ricetta " + index, "Preparazione", 1, 2)).toList();
        catalogRecipes = recipes;
        fx(() -> { controller.refreshCatalog(); return null; });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> {
            var rows = (javafx.scene.layout.VBox) field(controller, "recipeCatalogRows");
            check(rows.getChildren().size() == 12, "Menu chooser must show twelve recipes per page");
            check(node(controller, "recipeAddPanel").lookupAll(".result-pager").stream()
                    .filter(Node::isManaged).count() == 1,
                    "Menu recipe results must have one pagination control at the end of the list");
            pagerButton(node(controller, "recipePaginationBottom"), "Pagina successiva").fire();
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> {
            var rows = (javafx.scene.layout.VBox) field(controller, "recipeCatalogRows");
            check(rows.getChildren().getFirst().getUserData().equals(recipes.get(12).id()), "Menu page two must show recipe thirteen");
            menu(controller).renderRecipeCatalog();
            check(rows.getChildren().getFirst().getUserData().equals(recipes.get(12).id()), "Menu refresh must preserve the recipe page");
            ((TextField) field(controller, "recipeSearchField")).setText("Ricetta 25");
            return null;
        });
        awaitRecipeResults(controller, 1);
        fx(() -> {
            var rows = (javafx.scene.layout.VBox) field(controller, "recipeCatalogRows");
            check(rows.getChildren().size() == 1 && rows.getChildren().getFirst().getUserData().equals(recipes.getLast().id()),
                    "New recipe search must reset the chooser page");
            return null;
        });
        verifySearchDuringRead(controller, false);
        verifySearchDuringRead(controller, true);
        fx(() -> {
            controller.showCourseIndex();
            ((CourseCatalogPane) field(controller, "catalogPane")).selectCatalogSection(CourseOperationalView.CatalogSection.ARCHIVE);
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        var selectedCategory = new CourseCategoryFilter(Optional.of(courses.getFirst().categoryId()), "Pasta");
        fx(() -> {
            var categoryFilter = (ComboBox<CourseCategoryFilter>) field(controller, "categoryFilter");
            ((ReactiveFilterGuard) field(controller, "filterGuard")).update(() -> categoryFilter.setValue(selectedCategory));
            controller.showCourse(courses.getFirst().id());
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> {
            var catalog = (CourseCatalogPane) field(controller, "catalogPane");
            check(catalog.section() == CourseOperationalView.CatalogSection.ARCHIVE && !catalog.isArchive(),
                    "An active destination outside the archived page must remain editable without changing the return filter");
            check(((Label) field(controller, "selectedCourseLabel")).getText().equals(courses.getFirst().title().value()),
                    "Direct navigation must load the authorized destination outside the current page");
            controller.showCourseIndex();
            check(catalog.section() == CourseOperationalView.CatalogSection.ARCHIVE
                            && "Corso 25".equals(((TextField) field(controller, "courseSearchField")).getText())
                            && selectedCategory.equals(((ComboBox<?>) field(controller, "categoryFilter")).getValue()),
                    "Returning from a direct destination must preserve archived scope and search");
            return null;
        });
        fx(() -> {
            var coordinator = (CourseWorkspaceCoordinator) field(controller, "coordinator");
            coordinator.showFeedback("Corso creato.", false);
            coordinator.loadCourses(courses.getFirst().id());
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> {
            check(node(controller, "feedbackLabel").isVisible()
                            && ((Label) field(controller, "feedbackLabel")).getText().equals("Corso creato."),
                    "Opening the newly created course must preserve its success feedback");
            return null;
        });
        verifyCatalogRefreshAfterMutation(controller, courses);
        catalogCourses = List.of();
        catalogRecipes = List.of();
        catalogSessions = Map.of();
    }

    private static void verifySearchDuringRead(CourseWorkspaceController controller, boolean failRead) throws Exception {
        var releaseRead = new CountDownLatch(1);
        var debounceElapsed = new CountDownLatch(1);
        lastCourseSearch = "pending";
        lastRecipeSearch = "pending";
        fx(() -> {
            var coordinator = (CourseWorkspaceCoordinator) field(controller, "coordinator");
            coordinator.runAsync("search-smoke-block", () -> {
                if (!releaseRead.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Read was not released");
                if (failRead) throw new IllegalStateException("Synthetic previous read failure");
                return true;
            }, ignored -> {}, "Read failed");
            var courseSearch = (TextField) field(controller, "courseSearchField");
            check(!courseSearch.isDisabled(), "Course search must remain editable while its previous read is running");
            courseSearch.setText("Corso 2");
            courseSearch.setText("Corso 25");
            var recipeSearch = (TextField) field(controller, "recipeSearchField");
            recipeSearch.setText("Ricetta 2");
            recipeSearch.setText("Ricetta 25");
            var delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(350));
            delay.setOnFinished(event -> debounceElapsed.countDown());
            delay.play();
            return null;
        });
        try {
            check(debounceElapsed.await(10, TimeUnit.SECONDS), "Debounce must elapse without blocking JavaFX");
        } finally {
            releaseRead.countDown();
        }
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy()
                && lastCourseSearch.equals("Corso 25") && lastRecipeSearch.equals("Ricetta 25"), 10);
        fx(() -> {
            check(((javafx.scene.layout.GridPane) field(controller, "courseCards")).getChildren().size() == 1,
                    "The last course query typed during a read must be applied");
            check(((javafx.scene.layout.VBox) field(controller, "recipeCatalogRows")).getChildren().size() == 1,
                    "The last recipe query typed during a read must be applied");
            return null;
        });
    }

    private static void verifyCatalogRefreshAfterMutation(CourseWorkspaceController controller,
            List<CourseSummary> courses) throws Exception {
        fx(() -> {
            controller.showCourseIndex();
            ((CourseCatalogPane) field(controller, "catalogPane")).selectCatalogSection(CourseOperationalView.CatalogSection.ACTIVE);
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> { ((CourseCatalogPane) field(controller, "catalogPane")).resetFilters(); return null; });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> { pagerButton(node(controller, "coursePaginationBottom"), "Pagina successiva").fire(); return null; });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        var created = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca", CourseTitle.of("Corso 26"),
                "Preparazione", LocalDate.of(2030, 10, 2), LocalTime.of(17, 0), 7, 3,
                Optional.of(LocalDateTime.of(2030, 10, 22, 18, 30)), CourseStatus.PUBLISHED, IngredientMargin.zero());
        var updated = new java.util.ArrayList<>(courses);
        updated.add(created);
        catalogCourses = List.copyOf(updated);
        fx(() -> {
            ((CourseWorkspaceCoordinator) field(controller, "coordinator")).loadCourses(created.id());
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy(), 10);
        fx(() -> {
            controller.showCourseIndex();
            check("26 corsi".equals(((Label) field(controller, "courseCountLabel")).getText()),
                    "Returning after creation must refresh the paged catalog total");
            check(((CourseCardGrid) field(controller, "cardGrid")).page() == 1,
                    "Refreshing the catalog after a mutation must retain the page");
            var cards = (javafx.scene.layout.GridPane) field(controller, "courseCards");
            check(cards.getChildren().getFirst().getUserData().equals(courses.get(12).id()),
                    "The refreshed catalog must retain page ordering while its created target is outside the page");
            return null;
        });
    }

    private static void awaitRecipeResults(CourseWorkspaceController controller, int expectedCount) throws Exception {
        it.uninafoodlab.presentation.FxAwait.until(() -> !controller.isBusy()
                && ((javafx.scene.layout.VBox) field(controller, "recipeCatalogRows")).getChildren().size() == expectedCount, 10);
    }

    private static double contrastRatio(javafx.scene.paint.Color first, javafx.scene.paint.Color second) {
        double firstLuminance = luminance(first);
        double secondLuminance = luminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05)
                / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private static double luminance(javafx.scene.paint.Color color) {
        return 0.2126 * linearChannel(color.getRed()) + 0.7152 * linearChannel(color.getGreen())
                + 0.0722 * linearChannel(color.getBlue());
    }

    private static double linearChannel(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static Button pagerButton(Node host, String accessibleText) {
        return host.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> accessibleText.equals(button.getAccessibleText())).findFirst().orElseThrow();
    }

    private static void verifyCreationLayout(CourseWorkspaceController controller, int width) {
        var categoryText = (javafx.scene.text.Text) node(controller, "createCategoryButton").lookup(".text");
        check(categoryText != null && categoryText.getText().equals("Nuova categoria"),
                "New category action must remain fully readable at every tested native width");
        var category = node(controller, "categoryField").localToScene(node(controller, "categoryField").getBoundsInLocal());
        var start = node(controller, "startDateField").localToScene(node(controller, "startDateField").getBoundsInLocal());
        if (width > 1050) {
            check(category.getMaxX() < start.getMinX(),
                    "Course and calendar must be separate adjacent form cards at wide widths");
        }
        var host = (javafx.scene.layout.TilePane) field(controller, "sessionModeEditor");
        var editors = host.getChildren();
        check(host.getPrefColumns() == 1
                        && editors.get(1).getBoundsInParent().getMinY() >= editors.getFirst().getBoundsInParent().getMaxY() - 1,
                "Session editors must form complete chronological rows without overlapping");
        for (var editor : editors) {
            var bounds = editor.getBoundsInParent();
            check(bounds.getMinX() >= -1 && bounds.getMaxX() <= host.getLayoutBounds().getWidth() + 1
                            && Math.abs(bounds.getWidth() - host.getLayoutBounds().getWidth()) <= 3,
                    "Each session row must use the available native width without horizontal overflow");
            check(bounds.getHeight() <= (bounds.getWidth() >= 780 ? 120 : 210),
                    "Session rows must stay compact, adapting to one or two lines at " + width);
            var editorBounds = editor.localToScene(editor.getBoundsInLocal());
            var controls = editor.lookupAll(".course-create-control");
            check(controls.size() == 3, "A practical row must retain mode, venue and capacity controls");
            for (var control : controls) {
                var controlBounds = control.localToScene(control.getBoundsInLocal());
                check(controlBounds.getMinX() >= editorBounds.getMinX() - 1
                                && controlBounds.getMaxX() <= editorBounds.getMaxX() + 1
                                && controlBounds.getMinY() >= editorBounds.getMinY() - 1
                                && controlBounds.getMaxY() <= editorBounds.getMaxY() + 1,
                        "Every native session control must fit completely inside its row at " + width);
                check(control.isFocusTraversable() && control.getAccessibleText() != null,
                        "Compact rows preserve named controls reachable from the keyboard");
            }
        }
    }

    private static void verifyCatalogHeading(CourseWorkspaceController controller) {
        var headings = node(controller, "courseSelectorRail").lookupAll(".page-title").stream()
                .filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> label.isManaged() && label.isVisible()).toList();
        check(headings.size() == 1 && "I miei corsi".equals(headings.getFirst().getText()),
                "Course catalog must show exactly one visible heading named I miei corsi");
    }

    private static void verifyBackAffordances(CourseWorkspaceController controller) {
        var creationBack = node(controller, "createCoursePanel").lookup(".course-create-back");
        for (var button : List.of((Button) node(controller, "courseReturnButton"),
                (Button) node(controller, "sessionReturnButton"), (Button) creationBack)) {
            check(button.getGraphic() instanceof javafx.scene.image.ImageView arrow && arrow.getRotate() == 180
                            && button.getContentDisplay() == ContentDisplay.LEFT,
                    "Every course return action must show a left arrow before its label");
        }
    }

    private static void verifySessionDetailMetadata(CourseWorkspaceController controller, CourseSessionSummary session) {
        var header = node(controller, "sessionDetailHeader");
        var mode = (Label) header.lookup(".session-detail-mode");
        var status = (Label) header.lookup(".session-detail-status");
        var location = (Label) header.lookup(".session-detail-location");
        check(mode != null && status != null && location != null,
                "Session detail must show mode, status and location as separate information");
        var practical = session.mode() == SessionMode.PRACTICAL;
        check(mode.getText().equals(practical ? "In presenza" : "Online")
                        && mode.getGraphic() instanceof javafx.scene.image.ImageView icon
                        && icon.getImage().getUrl().endsWith(practical ? "cooking-pot-dark.png" : "video-dark.png"),
                "Session detail mode must use the approved wording and its own mode icon");
        check(status.getText().equals(CoursePresentation.sessionStatusLabel(session)) && !status.isFocusTraversable(),
                "The real session status must remain informational");
        if (practical) {
            check(location.getText().equals(session.venue().orElseThrow())
                            && location.getGraphic() instanceof javafx.scene.image.ImageView icon
                            && icon.getImage().getUrl().endsWith("map-pin-dark.png"),
                    "The session venue must retain its full stored text and its separate location icon");
        } else {
            check(!location.isManaged(), "Online sessions must retain lesson actions without a duplicate venue row");
        }
        var context = (Label) field(controller, "sessionDetailContext");
        var time = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        check(context.getText().contains(CoursePresentation.COURSE_DATE_FORMAT.format(session.scheduledAt()))
                        && context.getText().contains(time.format(session.scheduledAt()) + "–"
                                + time.format(session.scheduledAt().atZone(CoursePresentation.ROME).plusMinutes(session.durationMinutes())))
                        && context.getText().contains("Sessione " + session.sequenceNumber())
                        && !context.getText().contains(mode.getText()) && !context.getText().contains(status.getText()),
                "Date context must keep the actual schedule without repeating mode and status");
    }

    private static Node agendaRowGraphic(CourseWorkspaceController controller) {
        var agenda = (javafx.scene.layout.VBox) field(controller, "sessionAgenda");
        var track = (javafx.scene.layout.VBox) agenda.getChildren().getFirst();
        return ((ToggleButton) track.getChildren().getFirst()).getGraphic();
    }

    private static void verifyCardTitleGeometry(javafx.scene.layout.VBox card, int viewportWidth) {
        var title = (Label) card.lookup(".course-card-title");
        var expectedText = new javafx.scene.text.Text(title.getText());
        expectedText.setFont(title.getFont());
        double requiredHeight = expectedText.getLayoutBounds().getHeight();
        check(!title.isWrapText() && title.getHeight() <= requiredHeight + 2,
                "Course card title must remain on one line at " + viewportWidth + "px");
        var rendered = (javafx.scene.text.Text) title.lookup(".text");
        check(rendered != null && title.getTooltip() != null && title.getTooltip().getText().equals(title.getText()),
                "Course card must retain its complete title in its tooltip at " + viewportWidth + "px");
        var next = card.getChildren().get(2);
        check(title.getBoundsInParent().getMaxY() <= next.getBoundsInParent().getMinY(),
                "Single-line course title must not overlap the next course fact");
    }

    private static void verifySuggestedAttendanceDeadline() {
        var clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneId.of("Europe/Rome"));
        var editor = new CourseScheduleEditor(clock, () -> null, () -> null);
        editor.resetScheduleEditorForNewSession();
        editor.scheduleDateField.setValue(LocalDate.of(2026, 9, 23));
        editor.scheduleTimeField.setValue(LocalTime.of(10, 0));
        check(editor.scheduleAttendanceCutoff(SessionMode.PRACTICAL, editor.scheduleDateTime())
                        .orElseThrow().equals(LocalDateTime.of(2026, 9, 23, 9, 59)),
                "New practical deadline must follow the selected date and time, including tomorrow");
        editor.scheduleAttendanceTimeField.setValue(LocalTime.of(8, 30));
        editor.scheduleDateField.setValue(LocalDate.of(2026, 9, 24));
        editor.scheduleTimeField.setValue(LocalTime.of(11, 0));
        check(editor.scheduleAttendanceCutoff(SessionMode.PRACTICAL, editor.scheduleDateTime())
                        .orElseThrow().equals(LocalDateTime.of(2026, 9, 23, 8, 30)),
                "A manually selected deadline must survive later date and time changes");
        editor.resetScheduleEditorForNewSession();
        editor.scheduleDateField.setValue(LocalDate.of(2026, 9, 23));
        check(editor.scheduleAttendanceCutoff(SessionMode.PRACTICAL, editor.scheduleDateTime())
                        .orElseThrow().equals(LocalDateTime.of(2026, 9, 23, 18, 29)),
                "Starting another new session must restore the suggested deadline");
    }

    private static void verifyCardNavigation() throws java.io.IOException {
        var selection = new ListView<CourseSummary>();
        var cards = new javafx.scene.layout.GridPane();
        cards.setHgap(20);
        cards.setVgap(20);
        var opened = new java.util.concurrent.atomic.AtomicBoolean();
        var clock = Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneOffset.UTC);
        var grid = new CourseCardGrid(cards, selection, clock, () -> opened.set(true));
        var course = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca",
                CourseTitle.of("Impasti della tradizione"), "Un percorso pratico", LocalDate.of(2026, 10, 1),
                LocalTime.of(18, 0), 7, 4, Optional.of(LocalDateTime.of(2026, 10, 22, 19, 30)),
                CourseStatus.PUBLISHED, IngredientMargin.zero());
        selection.getItems().setAll(course);
        check(!opened.get(), "Loading or filtering the catalog must not navigate to a detail");
        var card = (javafx.scene.layout.VBox) cards.getChildren().getFirst();
        var cardLabels = card.lookupAll(".label").stream().map(Label.class::cast)
                .map(Label::getText).toList();
        check(!cardLabels.contains(course.description()),
                "Catalog cards keep the full course description in the detail instead of repeating it");
        check(cardLabels.contains(course.title().value())
                        && cardLabels.stream().anyMatch(text -> text.contains("sessioni")
                                && text.contains(Integer.toString(course.sessionCount())))
                        && cardLabels.stream().anyMatch(text -> text.contains("giorni")
                                && text.contains(Integer.toString(course.recurrenceDays()))),
                "Compact cards retain the full title and actionable course facts");
        ((Button) card.getChildren().getLast()).fire();
        check(opened.get() && selection.getSelectionModel().getSelectedItem().equals(course),
                "Open course must navigate using the exact real course selection");
        var longer = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca",
                CourseTitle.of("W".repeat(30)),
                "Un percorso pratico", course.startDate(), course.startTime(), 7, 4,
                course.lastSessionEndsAt(), CourseStatus.PUBLISHED, IngredientMargin.zero());
        var shorter = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca",
                CourseTitle.of("Pane rustico"), "Un percorso pratico", course.startDate(), course.startTime(),
                7, 4, course.lastSessionEndsAt(), CourseStatus.PUBLISHED, IngredientMargin.zero());
        selection.getItems().setAll(course, longer, shorter);
        var next = new CourseSessionSummary(UUID.randomUUID(), 1, LocalDateTime.of(2026, 10, 3, 18, 0),
                SessionMode.PRACTICAL, SessionStatus.SCHEDULED, 90, Optional.empty(),
                Optional.of("Laboratorio A"), Optional.of(12), 0);
        var online = new CourseSessionSummary(UUID.randomUUID(), 1, next.scheduledAt(),
                SessionMode.ONLINE, SessionStatus.SCHEDULED, 90, Optional.of(URI.create("https://example.test/lesson")),
                Optional.empty(), Optional.empty(), 0);
        grid.setSessions(Map.of(course.id(), List.of(next), longer.id(), List.of(next), shorter.id(), List.of(online)));
        var host = new javafx.scene.layout.VBox(cards);
        host.getStyleClass().add("course-chef-workspace");
        var cardScene = new javafx.scene.Scene(host, 1040, 600);
        cardScene.getStylesheets().add(CourseUiSmoke.class.getResource(
                "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
        cardScene.getStylesheets().add(CourseUiSmoke.class.getResource(
                "/it/uninafoodlab/presentation/course/course-workspace.css").toExternalForm());
        var cardStage = new javafx.stage.Stage();
        cardStage.setScene(cardScene);
        cardStage.show();
        host.applyCss();
        host.layout();
        for (var candidate : cards.getChildren()) {
            verifyCardTitleGeometry((javafx.scene.layout.VBox) candidate, 1040);
        }
        for (var candidate : cards.getChildren()) {
            var facts = candidate.lookupAll(".course-card-facts").stream()
                    .map(node -> node.localToScene(node.getBoundsInLocal()).getMaxY())
                    .mapToDouble(Double::doubleValue).max().orElseThrow();
            var appointment = candidate.lookup(".course-card-appointment");
            check(appointment.localToScene(appointment.getBoundsInLocal()).getMinY() - facts <= 20,
                    "Next appointment must remain beside course facts even with different title lengths");
            var date = (Label) appointment.lookup(".course-card-appointment-date");
            var mode = (Label) appointment.lookup(".course-card-mode");
            check(date.getParent() == mode.getParent()
                            && date.getParent() instanceof javafx.scene.layout.FlowPane,
                    "Session mode belongs to the same compact facts group as the next session date");
            var dateBounds = date.localToScene(date.getBoundsInLocal());
            var modeBounds = mode.localToScene(mode.getBoundsInLocal());
            if (Math.abs(dateBounds.getMinY() - modeBounds.getMinY()) < 10) {
                check(modeBounds.getMinX() - dateBounds.getMaxX() <= 16,
                        "Session mode sits beside its appointment rather than at the course card edge");
            }
        }
        it.uninafoodlab.presentation.FxSnapshot.save(host, "target/ui-polish/chef-next-session-cards.png");
        cardStage.close();
        selection.getItems().clear();
        check(cards.getChildren().getFirst() instanceof Label, "Empty filters must replace old course cards");
    }

    private static void verifyCourseStateLabels() {
        var selection = new ListView<CourseSummary>();
        var cards = new javafx.scene.layout.GridPane();
        cards.setHgap(20);
        cards.setVgap(20);
        var clock = Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneOffset.UTC);
        new CourseCardGrid(cards, selection, clock, () -> {});
        var host = new javafx.scene.layout.VBox(cards);
        host.getStyleClass().add("course-chef-workspace");
        var scene = new javafx.scene.Scene(host, 800, 400);
        scene.getStylesheets().add(CourseUiSmoke.class.getResource(
                "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
        scene.getStylesheets().add(CourseUiSmoke.class.getResource(
                "/it/uninafoodlab/presentation/course/course-workspace.css").toExternalForm());
        var stage = new javafx.stage.Stage();
        stage.setScene(scene);
        stage.show();
        var colors = new HashSet<javafx.scene.paint.Color>();
        try {
            for (var status : List.of(CourseStatus.PUBLISHED, CourseStatus.COMPLETED, CourseStatus.CANCELLED)) {
                var course = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca",
                        CourseTitle.of("Corso reale"), "Preparazione", LocalDate.of(2026, 9, 1),
                        LocalTime.of(18, 0), 7, 2, Optional.of(LocalDateTime.of(2026, 10, 1, 20, 0)),
                        status, IngredientMargin.zero());
                selection.getItems().setAll(course);
                var badge = (Label) cards.getChildren().getFirst().lookup(".course-card-badge");
                var expected = status == CourseStatus.CANCELLED ? "Annullato"
                        : status == CourseStatus.COMPLETED ? "Concluso" : "In corso";
                check(badge.getText().equals(expected), "Course state must be " + expected + " without changing domain state");
                if (status != CourseStatus.CANCELLED) {
                    host.applyCss(); host.layout();
                    colors.add(verifySaturatedState(badge));
                }
            }
            var future = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca",
                    CourseTitle.of("Corso futuro"), "Preparazione", LocalDate.of(2026, 10, 1),
                    LocalTime.of(18, 0), 7, 2, Optional.of(LocalDateTime.of(2026, 10, 8, 20, 0)),
                    CourseStatus.PUBLISHED, IngredientMargin.zero());
            selection.getItems().setAll(future);
            host.applyCss(); host.layout();
            var planned = (Label) cards.getChildren().getFirst().lookup(".course-card-badge");
            check("In programma".equals(planned.getText()), "A future course retains its planned state");
            colors.add(verifySaturatedState(planned));
            check(colors.size() == 3, "Active, concluded and planned courses use three distinct saturated state colors");
            } finally {
            stage.close();
        }
    }

    private static javafx.scene.paint.Color verifySaturatedState(Label badge) {
        var fill = (javafx.scene.paint.Color) badge.getBackground().getFills().getFirst().getFill();
        var text = (javafx.scene.paint.Color) badge.getTextFill();
        check(fill.getSaturation() >= 0.45 && contrastRatio(text, fill) >= 4.5,
                "Course state " + badge.getText() + " is saturated with at least 4.5:1 text contrast");
        return fill;
    }

    private static void verifyPositionLinkActions(CourseWorkspaceController controller, LocalDateTime start) throws Exception {
        var position = "https://maps.example.test/position?room=teaching";
        var session = new CourseSessionSummary(UUID.randomUUID(), 1, start, SessionMode.PRACTICAL,
                SessionStatus.SCHEDULED, 90, Optional.empty(), Optional.of(position), Optional.of(12), 0);
        select(controller, session);
        invoke(controller, "showRecipes");
        check(node(controller, "programLessonActions").isVisible() && node(controller, "detailLessonActions").isVisible(),
                "A practical HTTPS position must expose actions in the program and session detail");
        var open = (Button) field(controller, "openDetailLessonButton");
        check(open.getText().equals("Apri posizione") && !open.isDisabled(),
                "Practical position action must be usable and named without changing the online lesson actions");
        check(((Label) field(controller, "sessionDetailLocation")).getText().equals("Posizione"),
                "Position link must not overflow the session header with a raw URL");
        var copy = (Button) field(controller, "copyDetailLessonButton");
        copy.fire();
        check(((Label) field(controller, "feedbackLabel")).getText().equals("Link della posizione copiato."),
                "Copy position must preserve the exact stored HTTPS URL and give the right feedback: disabled="
                        + copy.isDisabled() + ", feedback=" + ((Label) field(controller, "feedbackLabel")).getText());
        it.uninafoodlab.presentation.FxSnapshot.save(node(controller, "sessionDetailHeader").getScene().getRoot(),
                "target/native-match/session-position-link.png");
        var plainVenue = new CourseSessionSummary(UUID.randomUUID(), 1, start, SessionMode.PRACTICAL,
                SessionStatus.SCHEDULED, 90, Optional.empty(), Optional.of("Laboratorio A"), Optional.of(12), 0);
        select(controller, plainVenue);
        check(!node(controller, "programLessonActions").isVisible(), "A textual venue must remain plain information");
    }

    private static void verifyScheduleCommandInputs(AuthenticatedUser chef) throws Exception {
        var course = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Categoria prova",
                CourseTitle.of("Corso prova"), "Descrizione", LocalDate.of(2026, 9, 14),
                LocalTime.of(18, 0), 7, 2, Optional.empty(), CourseStatus.PUBLISHED, IngredientMargin.zero());
        var requests = new ArrayList<NewSessionInput>();
        var repository = (ScheduleRepository) Proxy.newProxyInstance(ScheduleRepository.class.getClassLoader(),
                new Class<?>[]{ScheduleRepository.class}, (proxy, method, arguments) -> {
                    check(method.getName().equals("add"), "Only adding a session is expected");
                    check(arguments[0].equals(chef.sessionToken()) && arguments[1].equals(course.id()),
                            "Schedule command must preserve chef authorization and selected course");
                    requests.add((NewSessionInput) arguments[2]);
                    return UUID.randomUUID();
                });
        var actions = new ScheduleInputActions();
        var selected = new java.util.concurrent.atomic.AtomicReference<>(course);
        var archived = new java.util.concurrent.atomic.AtomicBoolean();
        var editor = new CourseScheduleEditor(Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"),
                ZoneOffset.UTC), selected::get, () -> null);
        var commands = new CourseScheduleCommands(chef, new ManageCourseSchedule(repository), editor,
                null, null, actions, selected::get, () -> null, archived::get);
        editor.scheduleDateField.setValue(LocalDate.of(2026, 9, 20));
        editor.scheduleTimeField.setValue(LocalTime.of(18, 0));
        editor.scheduleModeField.setValue(SessionMode.ONLINE);
        editor.scheduleLocationField.setText("not a URI");
        invoke(commands, "addSession");
        check(requests.isEmpty() && !actions.feedback.isBlank(),
                "Malformed online locations must show feedback without writing a session");
        editor.scheduleLocationField.setText("http://example.test/unsecure-lesson");
        invoke(commands, "addSession");
        check(requests.isEmpty() && actions.feedback.equals("Inserisci un link HTTPS valido per la sessione online."),
                "Domain HTTPS validation must be shown in Italian without submitting a session");
        editor.scheduleModeField.setValue(null);
        actions.feedback = "";
        invoke(commands, "addSession");
        check(requests.isEmpty() && actions.feedback.contains("Controlla"),
                "Missing mode must produce actionable feedback without starting an operation");
        editor.scheduleModeField.setValue(SessionMode.ONLINE);
        editor.scheduleLocationField.setText("https://example.test/new-lesson");
        invoke(commands, "addSession");
        check(requests.size() == 1 && requests.getFirst().onlineUrl().orElseThrow()
                        .equals(URI.create("https://example.test/new-lesson"))
                        && requests.getFirst().attendanceClosesAt().isEmpty()
                        && requests.getFirst().venue().isEmpty(),
                "Online creation must submit its link without practical location or attendance deadline");
        editor.scheduleModeField.setValue(SessionMode.PRACTICAL);
        editor.scheduleLocationField.setText("Laboratorio prova");
        editor.scheduleCapacityField.getValueFactory().setValue(24);
        editor.scheduleAttendanceDateField.setValue(LocalDate.of(2026, 9, 19));
        editor.scheduleAttendanceTimeField.setValue(LocalTime.of(12, 0));
        invoke(commands, "addSession");
        check(requests.size() == 2 && requests.getLast().venue().orElseThrow().equals("Laboratorio prova")
                        && requests.getLast().capacity().orElseThrow() == 24
                        && requests.getLast().onlineUrl().isEmpty()
                        && requests.getLast().attendanceClosesAt().orElseThrow()
                                .equals(LocalDateTime.of(2026, 9, 19, 12, 0)),
                "Practical creation must submit the chosen venue, capacity and custom deadline");
        archived.set(true);
        invoke(commands, "addSession");
        check(requests.size() == 2 && actions.feedback.contains("sola lettura"),
                "Archived courses must reject schedule writes");
        archived.set(false);
        selected.set(null);
        invoke(commands, "addSession");
        check(requests.size() == 2 && actions.feedback.contains("Seleziona un corso"),
                "Missing course selection must reject schedule writes");
    }

    private static final class ScheduleInputActions implements CourseWorkspaceActions {
        private String feedback = "";
        @Override public <T> void runAsync(String name, Callable<T> operation,
                java.util.function.Consumer<T> onSuccess, String failureMessage) {
            try { operation.call(); }
            catch (Exception failure) { throw new AssertionError(failureMessage, failure); }
        }
        @Override public boolean isBusy() { return false; }
        @Override public void setBusy(boolean busy) {
            throw new AssertionError("Input validation must not manage asynchronous UI state");
        }
        @Override public void showFeedback(String message, boolean error) { feedback = message; }
        @Override public void setCourseFormVisible(boolean visible) {
            throw new AssertionError("Input validation must not change the course form");
        }
        @Override public void loadCourses(UUID courseId) {
            throw new AssertionError("Course refresh belongs to the asynchronous completion callback");
        }
        @Override public void loadSessions(CourseSummary course) {
            throw new AssertionError("Session refresh belongs to the asynchronous completion callback");
        }
        @Override public void refreshCategories(CategorySummary created) {
            throw new AssertionError("Session commands must not refresh categories");
        }
    }

    private static void verifyCompactOperationDialog(CourseWorkspaceController controller) throws Exception {
        var windows = fx(() -> {
            var owner = new javafx.stage.Stage();
            var scene = node(controller, "selectedCourseLabel").getScene();
            if (scene.getWindow() instanceof javafx.stage.Stage previous) {
                previous.hide();
                previous.setScene(null);
            }
            owner.setScene(scene);
            owner.setWidth(1144);
            owner.setHeight(665);
            owner.show();
            var dialog = new Dialog<ButtonType>();
            dialog.setTitle("Aggiungi sessione");
            dialog.setHeaderText("Aggiungi sessione");
            ((CourseOperationDialogs) field(field(controller, "scheduleCommands"), "operationDialogs")).configure(dialog);
            var form = schedule(controller).createScheduleDialogContent(false);
            var scroll = new ScrollPane(form);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(520);
            scroll.setMinHeight(0);
            javafx.scene.layout.VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
            dialog.getDialogPane().setContent(new javafx.scene.layout.VBox(12, scroll, new Label()));
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
            dialog.show();
            return new Object[]{owner, dialog};
        });
        try {
            fx(() -> {
                var owner = (javafx.stage.Stage) windows[0];
                var dialog = (Dialog<?>) windows[1];
                var pane = dialog.getDialogPane();
                pane.applyCss();
                pane.layout();
                var window = pane.getScene().getWindow();
                check(window.isShowing() && pane.getScene().getWidth() >= 580 && pane.getScene().getHeight() >= 400,
                        "Native dialog and scene must be shown and laid out at readable dimensions");
                var bounds = javafx.stage.Screen.getScreensForRectangle(window.getX(), window.getY(),
                        window.getWidth(), window.getHeight()).getFirst().getVisualBounds();
                check(window.getX() >= bounds.getMinX() && window.getY() >= bounds.getMinY()
                                && window.getX() + window.getWidth() <= bounds.getMaxX() + 1
                                && window.getY() + window.getHeight() <= bounds.getMaxY() + 1,
                        "The entire native operation dialog, including its title, must fit the visual screen");
                check(window.getHeight() <= owner.getHeight() && window.getWidth() <= owner.getWidth(),
                        "Operation dialog must fit a compact owner");
                check(window.getWidth() >= 600 && window.getHeight() >= 450,
                        "Operation dialog must retain readable dimensions when native size is initially unavailable");
                for (var type : pane.getButtonTypes()) {
                    var button = pane.lookupButton(type);
                    var buttonBounds = button.localToScreen(button.getBoundsInLocal());
                    check(button.isVisible() && !button.isDisabled() && buttonBounds != null
                                    && buttonBounds.getMinY() >= window.getY()
                                    && buttonBounds.getMaxY() <= window.getY() + window.getHeight()
                                    && buttonBounds.getMinX() >= window.getX()
                                    && buttonBounds.getMaxX() <= window.getX() + window.getWidth(),
                            "Dialog actions must remain accessible without scrolling the form");
                }
                return null;
            });
        } finally {
            fx(() -> {
                ((Dialog<?>) windows[1]).close();
                ((javafx.stage.Stage) windows[0]).close();
                return null;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifySessionDraftControls(CourseWorkspaceController controller) {
        var date = (DatePicker) field(controller, "startDateField");
        check(!date.isEditable() && !date.isShowWeekNumbers() && date.getDayCellFactory() != null,
                "Course date picker uses its bounded calendar, without editable free text or week numbers");
        var host = (javafx.scene.layout.TilePane) field(controller, "sessionModeEditor");
        ((ComboBox<SessionMode>) host.getChildren().getFirst().getUserData()).setValue(SessionMode.PRACTICAL);
        var row = host.getChildren().getFirst();
        ((TextField) row.lookup(".text-field")).setText("Laboratorio prova");
        var capacity = (Spinner<Integer>) row.lookup(".spinner");
        capacity.getEditor().setText("27");
        capacity.commitValue();
        check(capacity.getValue() == 27, "Typing a valid capacity must update its value");
        capacity.getEditor().setText("501");
        check(capacity.getEditor().getText().equals("27"), "Capacity must reject values above its limit");
        var labels = row.lookupAll(".label").stream().map(node -> ((Label) node).getText()).toList();
        check(labels.contains("Capienza massima") && labels.contains("posti"),
                "Capacity must display its meaning and unit");

        ((ComboBox<SessionMode>) row.getUserData()).setValue(SessionMode.ONLINE);
        ((ComboBox<SessionMode>) host.getChildren().getFirst().getUserData()).setValue(SessionMode.PRACTICAL);
        ((DatePicker) field(controller, "startDateField")).setValue(LocalDate.of(2026, 10, 10));
        ((Spinner<Integer>) field(controller, "recurrenceField")).getValueFactory().setValue(3);
        ((TimeSelector) field(controller, "startTimeField")).setValue(LocalTime.of(19, 45));
        row = host.getChildren().getFirst();
        check(((TextField) row.lookup(".text-field")).getText().equals("Laboratorio prova")
                        && ((Spinner<Integer>) row.lookup(".spinner")).getValue() == 27,
                "Mode and schedule changes must preserve venue and capacity");
        var nextDate = (Label) host.getChildren().get(1).lookup(".session-date");
        check(nextDate.getText().equals("13 ottobre 2026"),
                "Session dates must follow the edited start date, recurrence and time");
    }

    @SuppressWarnings("unchecked")
    private static void verifyCourseValidationFocus(CourseWorkspaceController controller) throws Exception {
        invoke(controller, "createCourse");
        check(node(controller, "categoryField").getScene().getFocusOwner() == node(controller, "categoryField"),
                "Missing category must receive focus before the other invalid fields");
        var category = new CategorySummary(UUID.randomUUID(), "Categoria prova", "Categoria sintetica");
        var categoryField = (ComboBox<CategorySummary>) field(controller, "categoryField");
        categoryField.getItems().add(category);
        categoryField.setValue(category);
        invoke(controller, "createCourse");
        check(node(controller, "titleField").getScene().getFocusOwner() == node(controller, "titleField"),
                "Missing title must receive focus");
        ((TextField) field(controller, "titleField")).setText("Corso prova");
        ((TextArea) field(controller, "descriptionField")).setText("Descrizione prova");
        var host = (javafx.scene.layout.TilePane) field(controller, "sessionModeEditor");
        ((ComboBox<SessionMode>) host.getChildren().getFirst().getUserData()).setValue(SessionMode.ONLINE);
        var url = (TextField) host.getChildren().getFirst().lookup(".text-field");
        url.setText("http://example.test/invalid");
        invoke(controller, "createCourse");
        check(url.getScene().getFocusOwner() == url,
                "An invalid lesson URL must focus that session's input");
    }

    private static CourseSessionSummary online(Optional<URI> link) {
        return new CourseSessionSummary(UUID.randomUUID(), 2, LocalDateTime.parse("2026-09-16T18:00"),
                SessionMode.ONLINE, SessionStatus.SCHEDULED, 60, link, Optional.empty(), Optional.empty(), 0);
    }

    @SuppressWarnings("unchecked")
    private static void select(Object controller, CourseSessionSummary session) {
        var table = (TableView<CourseSessionSummary>) field(controller, "sessionTable");
        table.getItems().setAll(session);
        table.getSelectionModel().select(session);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readOnly(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getReturnType() == it.uninafoodlab.application.common.Page.class) return catalogPage(args);
            if (method.getReturnType() == Map.class) return catalogSessions;
            if (method.getReturnType() == Optional.class) return catalogCourses.stream()
                    .filter(course -> course.id().equals(args[1])).findFirst();
            if (method.getName().equals("sessions") && args[1] instanceof UUID courseId) {
                return catalogSessions.getOrDefault(courseId, List.of());
            }
            if (List.class.isAssignableFrom(method.getReturnType())) return List.of();
            throw new AssertionError("Unexpected write: " + method.getName());
        });
    }

    private static it.uninafoodlab.application.common.Page<?> catalogPage(Object[] arguments) {
        var page = (it.uninafoodlab.application.common.PageRequest) arguments[2];
        List<?> matches;
        if (arguments[1] instanceof CourseFilter filter) {
            lastCourseSearch = filter.search();
            var section = switch (filter.period()) {
                case ACTIVE -> CourseOperationalView.CatalogSection.ACTIVE;
                case PAST -> CourseOperationalView.CatalogSection.ARCHIVE;
                case ALL -> CourseOperationalView.CatalogSection.ALL;
            };
            var clock = Clock.fixed(filter.now().atZone(ZoneId.of("Europe/Rome")).toInstant(), ZoneId.of("Europe/Rome"));
            matches = catalogCourses.stream().filter(course -> section == CourseOperationalView.CatalogSection.ALL
                    || CourseOperationalView.isArchived(course, clock) == (section == CourseOperationalView.CatalogSection.ARCHIVE))
                    .filter(course -> filter.categoryId().isEmpty() || filter.categoryId().orElseThrow().equals(course.categoryId()))
                    .filter(course -> (course.title().value() + " " + course.categoryName()).toLowerCase(Locale.ITALIAN)
                            .contains(filter.search().strip().toLowerCase(Locale.ITALIAN))).toList();
        } else {
            var filter = (RecipeFilter) arguments[1];
            lastRecipeSearch = filter.search();
            var search = filter.search().strip().toLowerCase(Locale.ITALIAN);
            matches = catalogRecipes.stream().filter(recipe -> recipe.name().toLowerCase(Locale.ITALIAN).contains(search)
                    || recipe.categoryName().toLowerCase(Locale.ITALIAN).contains(search)).toList();
        }
        int from = Math.min(matches.size(), page.index() * page.size());
        return new it.uninafoodlab.application.common.Page<>(matches.subList(from, Math.min(matches.size(), from + page.size())), matches.size());
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
    private static CourseScheduleEditor schedule(CourseWorkspaceController controller) {
        return (CourseScheduleEditor) field(field(controller, "scheduleCommands"), "scheduleEditor");
    }
    private static CourseRecipeMenu menu(CourseWorkspaceController controller) {
        return (CourseRecipeMenu) field(controller, "recipeMenu");
    }
    private static CourseWorkspaceAvailability availability(CourseWorkspaceController controller) {
        return (CourseWorkspaceAvailability) field(controller, "availability");
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static Node node(Object target, String name) { return (Node) field(target, name); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
