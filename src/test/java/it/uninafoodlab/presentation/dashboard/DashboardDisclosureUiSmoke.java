package it.uninafoodlab.presentation.dashboard;

import it.uninafoodlab.application.course.*;
import it.uninafoodlab.application.notification.*;
import it.uninafoodlab.domain.course.*;
import it.uninafoodlab.domain.identity.*;
import it.uninafoodlab.presentation.dashboard.DashboardWorkspaceController.DashboardSession;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public final class DashboardDisclosureUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var shownStage = new AtomicReference<Stage>();
        try {
            var opened = new AtomicReference<List<UUID>>();
            var menuOpened = new AtomicReference<List<UUID>>();
            var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("smoke.chef"), "Chef sintetico", Role.CHEF);
            var controller = new DashboardWorkspaceController(actor, new BrowseCourses(empty(CourseRepository.class)),
                    () -> {},
                    (course, session) -> opened.set(List.of(course, session)),
                    (course, session) -> menuOpened.set(List.of(course, session)));
            var today = LocalDate.now(ZoneId.of("Europe/Rome"));
            var first = session("Pasta fresca fatta a mano", today, 18, SessionMode.PRACTICAL, SessionStatus.SCHEDULED);
            var second = session("Laboratorio di cucina campana con preparazioni tradizionali e varianti stagionali", today, 20,
                    SessionMode.ONLINE, SessionStatus.COMPLETED);
            var third = session("Pane e lievitati", today, 22, SessionMode.PRACTICAL, SessionStatus.CANCELLED);
            var future = session("Cucina del territorio", today.plusDays(2), 18, SessionMode.PRACTICAL, SessionStatus.SCHEDULED);
            var sessions = List.of(first, second, third, future);
            Parent root = fx(() -> {
                var loader = new FXMLLoader(DashboardDisclosureUiSmoke.class.getResource("dashboard-workspace.fxml"));
                loader.setController(controller);
                Parent loaded = loader.load();
                var stage = new Stage();
                stage.setScene(new Scene(loaded, 1000, 1000));
                stage.getScene().getStylesheets().add(DashboardDisclosureUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                stage.show(); stage.requestFocus(); shownStage.set(stage);
                show(controller, List.of(first), List.of());
                loaded.applyCss(); loaded.layout();
                check(rows(loaded).getChildren().size() == 1, "One session has one row");
                var ended = session("Sessione terminata", today.minusDays(1), 10,
                        SessionMode.PRACTICAL, SessionStatus.SCHEDULED);
                show(controller, List.of(ended), List.of());
                var tasks = (VBox) loaded.lookup("#taskRows");
                check(tasks.getChildren().size() == 1 && tasks.getChildren().getFirst() instanceof Label label
                                && label.getText().equals("Nessun menu da preparare."),
                        "Ended sessions never request manual completion");
                show(controller, sessions, List.of(future));
                loaded.applyCss(); loaded.layout();
                check(rows(loaded).getChildren().size() == 3, "All three same-day sessions are visible once");
                for (var item : sessions.subList(0, 3)) {
                    check(rows(loaded).lookupAll(".agenda-session-title").stream().filter(Label.class::isInstance)
                                    .map(Label.class::cast).filter(label -> label.getText().equals(item.courseTitle())).count() == 1,
                            "Full course title appears once in the daily list: " + item.courseTitle());
                }
                var firstRow = (GridPane) rows(loaded).getChildren().getFirst();
                check(firstRow.lookup(".agenda-session-start") instanceof Label start && start.getText().equals("18:00")
                                && firstRow.lookup(".agenda-session-end") instanceof Label end && end.getText().equals("19:30"),
                        "Start and end time are separate readable agenda labels");
                check(firstRow.lookup(".agenda-session-sequence") instanceof Label sequence
                                && sequence.getText().equals("Sessione 1"),
                        "Session number identifies the appointment without inventing a domain title");
                check(firstRow.lookup(".agenda-session-mode") instanceof Label mode
                                && mode.getText().equals("In presenza") && mode.getTextFill().equals(Color.web("#22566d")),
                        "Mode is readable blue information, distinct from an action");
                for (int index = 0; index < 2; index++) {
                    var mode = (Label) rows(loaded).getChildren().get(index).lookup(".agenda-session-mode");
                    check(mode.getGraphic() instanceof javafx.scene.image.ImageView icon
                                    && icon.getImage().getUrl().endsWith(index == 0 ? "/cooking-pot-dark.png" : "/video-dark.png"),
                            "Session mode pairs its readable text with the appropriate informational icon");
                }
                check(rowAction(loaded, 0).getAccessibleText().contains("In presenza"), "Practical mode is identified");
                check(rowAction(loaded, 1).getAccessibleText().contains("Online")
                                && rows(loaded).getChildren().get(1).lookup(".agenda-session-state") instanceof Label held
                                && held.getText().equals("Svolta"), "Online completion remains explicit");
                check(rowAction(loaded, 2).getAccessibleText().contains("Annullata")
                                && rows(loaded).getChildren().get(2).lookup(".agenda-session-state") instanceof Label cancelled
                                && cancelled.getText().equals("Annullata"), "Cancellation remains explicit");
                for (int index = 0; index < 3; index++) {
                    rowAction(loaded, index).fire();
                    check(opened.get().equals(List.of(sessions.get(index).courseId(), sessions.get(index).session().id())),
                            "Each session action opens its exact course and session");
                }
                for (var removed : List.of("dashboardRail", "weekRows", "sessionContent", "menuPreview", "requirementPreview"))
                    check(loaded.lookup("#" + removed) == null, "No duplicate detail panel: " + removed);
                var page = (Region) loaded.lookup(".dashboard-workspace");
                check(page.getBackground().getFills().getFirst().getFill().equals(Color.web("#FAFAF8")),
                        "Actual agenda surface is neutral after all stylesheets are applied");
                var preparation = (VBox) loaded.lookup("#preparationSection");
                var toggle = (ToggleButton) loaded.lookup("#preparationToggle");
                check(!preparation.isManaged() && toggle.getText().equals("1 sessione senza ricette"), "Preparation queue starts collapsed with accurate count");
                toggle.fire();
                check(preparation.isManaged() && preparation.isVisible(), "Preparation queue expands");
                check(preparation.lookup(".agenda-preparation-icon") instanceof javafx.scene.image.ImageView,
                        "Preparation row carries the recipe-book icon from the agenda mockup");
                loaded.applyCss(); loaded.layout();
                it.uninafoodlab.presentation.FxSnapshot.save(loaded, "target/ui-polish/agenda-preparation.png");
                ((Button) preparation.lookup(".button")).fire();
                check(menuOpened.get().equals(List.of(future.courseId(), future.session().id())), "Queue opens the correct recipe assignment");
                toggle.fire();
                check(!preparation.isManaged() && !preparation.isVisible(), "Preparation queue closes");
                return loaded;
            });
            fx(() -> {
                show(controller, List.of(second, third, future), List.of(future));
                select(controller, today.minusDays(1));
                var historicalNext = (Button) root.lookup("#nextAvailableButton");
                check(historicalNext.isVisible() && historicalNext.getText().endsWith(today.format(
                                java.time.format.DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN))),
                        "Empty historical day identifies the first non-cancelled session even when it is completed");
                root.applyCss(); root.layout();
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/ui-polish/agenda-next-historical.png");
                historicalNext.fire();
                check(rows(root).getChildren().size() == 2 && rowAction(root, 0).getAccessibleText().contains(second.courseTitle()),
                        "Next-session click selects the same completed appointment advertised by the empty state");
                show(controller, sessions, List.of(future));
                var focused = rowAction(root, 1); focused.requestFocus();
                check(focused.isFocused(), "Session action can receive keyboard focus");
                var updated = new DashboardSession(UUID.randomUUID(), "Titolo aggiornato della sessione", second.session());
                show(controller, List.of(first, updated, third, future), List.of(future));
                check(rowAction(root, 1) == focused && focused.isFocused(), "Refresh with the same IDs preserves the focused action");
                check(focused.getAccessibleText().contains(updated.courseTitle()), "Refresh updates the retained action label");
                focused.fire();
                check(opened.get().equals(List.of(updated.courseId(), updated.session().id())), "Retained action uses refreshed destination");
                show(controller, sessions, List.of(future));
                var preparationToggle = (ToggleButton) root.lookup("#preparationToggle");
                preparationToggle.fire();
                var preparation = (VBox) root.lookup("#preparationSection");
                var menuAction = (Button) preparation.lookup(".button"); menuAction.requestFocus();
                show(controller, sessions, List.of(future));
                check(preparation.lookup(".button") == menuAction && menuAction.isFocused(),
                        "Refreshing the same preparation queue preserves its focused action");
                preparationToggle.fire();
                var strip = (HBox) root.lookup("#weekSchedule");
                var day = (Button) strip.getChildren().get(1); day.requestFocus(); day.fire();
                check(strip.getChildren().get(1) == day && day.isFocused(), "Day selection retains its focused calendar node");
                check(day.getStyleClass().contains("lab-week-day-selected") && day.getAccessibleText().contains("selezionato"),
                        "Calendar selection is visible and accessible");
                select(controller, today.plusDays(1));
                check(root.lookup("#emptyDayPanel").isManaged() && !rows(root).isManaged(), "Empty day replaces the list with useful guidance");
                var next = (Button) root.lookup("#nextAvailableButton");
                check(next.isVisible(), "Empty day offers the next scheduled session"); next.fire();
                check(rows(root).getChildren().size() == 1 && rowAction(root, 0).getAccessibleText().contains(future.courseTitle()),
                        "Next available action selects the correct day");
                select(controller, today.plusDays(3));
                check(!next.isManaged(), "No next-session action is offered when no future session exists");
                select(controller, today);
                return null;
            });
            for (int width : new int[]{850, 1000, 1440}) {
                fx(() -> {
                    var stage = shownStage.get();
                    stage.setWidth(width + stage.getWidth() - stage.getScene().getWidth());
                    stage.setHeight(1000 + stage.getHeight() - stage.getScene().getHeight());
                    return null;
                });
                it.uninafoodlab.presentation.FxAwait.until(() -> Math.abs(shownStage.get().getScene().getWidth() - width) < 1, 5);
                fx(() -> {
                    root.applyCss(); root.layout();
                    it.uninafoodlab.presentation.FxSnapshot.save(root, "target/ui-polish/agenda-mock-" + width + ".png");
                    assertWeekGeometry((HBox) root.lookup("#weekSchedule"), width);
                    double previousBottom = Double.NEGATIVE_INFINITY;
                    var container = rows(root);
                    var panelBounds = container.localToScene(container.getBoundsInLocal());
                    for (var child : container.getChildren()) {
                        var row = (GridPane) child;
                        var bounds = row.localToScene(row.getBoundsInLocal());
                        check(bounds.getMinY() >= previousBottom - 0.5, "Session rows do not overlap at " + width);
                        previousBottom = bounds.getMaxY();
                        check(bounds.getMinX() >= panelBounds.getMinX() - 0.5 && bounds.getMaxX() <= panelBounds.getMaxX() + 0.5,
                                "Session row fits container at " + width);
                        var action = (Button) row.getChildren().get(2);
                        check(action.getWidth() >= action.prefWidth(-1) - 0.5, "Session action is fully readable at " + width);
                        var text = (VBox) row.getChildren().get(1);
                        var title = (Label) text.getChildren().getFirst();
                        check(title.isWrapText() && title.getHeight() >= title.prefHeight(title.getWidth()) - 1,
                                "Long session title remains fully readable at " + width);
                        check(action.localToScene(action.getBoundsInLocal()).getMinX()
                                        >= text.localToScene(text.getBoundsInLocal()).getMaxX() - 0.5,
                                "Text and session action do not collide at " + width);
                    }
                    var tasks = root.lookup(".agenda-tasks").localToScene(root.lookup(".agenda-tasks").getBoundsInLocal());
                    check(tasks.getMinY() >= panelBounds.getMaxY() + 12, "Activities have space below daily sessions at " + width);
                    check(tasks.getMaxX() <= root.getScene().getWidth() + 1, "Activities stay within viewport at " + width);
                    return null;
                });
            }
            fx(() -> { shownStage.get().setWidth(1000); shownStage.get().setHeight(600); return null; });
            it.uninafoodlab.presentation.FxAwait.until(() -> shownStage.get().getScene().getHeight() < 600, 5);
            fx(() -> {
                root.applyCss(); root.layout();
                var toggle = (ToggleButton) root.lookup("#preparationToggle");
                check(toggle.getHeight() >= toggle.prefHeight(toggle.getWidth()) - 1,
                        "Preparation action stays readable in a short window instead of shrinking");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/ui-polish/agenda-short.png");
                return null;
            });
            System.out.println("PASS: daily sessions, exact navigation, refresh focus, calendar, empty days, preparation queue and responsive layout");
        } finally {
            if (shownStage.get() != null) fx(() -> { shownStage.get().hide(); return null; });
            Platform.exit();
        }
    }

    private static DashboardSession session(String title, LocalDate day, int hour, SessionMode mode, SessionStatus status) {
        return new DashboardSession(UUID.randomUUID(), title, new CourseSessionSummary(UUID.randomUUID(), 1,
                day.atTime(hour, 0), mode, status, 90,
                mode == SessionMode.ONLINE ? Optional.of(java.net.URI.create("https://example.test/lezione")) : Optional.empty(),
                mode == SessionMode.PRACTICAL ? Optional.of("Laboratorio 2") : Optional.empty(),
                mode == SessionMode.PRACTICAL ? Optional.of(20) : Optional.empty(), 0));
    }
    private static VBox rows(Parent root) { return (VBox) root.lookup("#daySessions"); }
    private static Button rowAction(Parent root, int index) { return (Button) ((GridPane) rows(root).getChildren().get(index)).getChildren().get(2); }
    private static void show(Object controller, List<DashboardSession> sessions, List<DashboardSession> attention) throws Exception {
        var method = controller.getClass().getDeclaredMethod("showSnapshot", DashboardOverviewLoader.DashboardSnapshot.class);
        method.setAccessible(true);
        method.invoke(controller, new DashboardOverviewLoader.DashboardSnapshot(sessions, attention));
    }
    private static void select(Object controller, LocalDate day) throws Exception {
        var method = controller.getClass().getDeclaredMethod("selectDay", LocalDate.class);
        method.setAccessible(true); method.invoke(controller, day);
    }
    private static void assertWeekGeometry(HBox strip, int width) {
        check(strip.getChildren().size() == 7, "Seven calendar days at " + width);
        check(strip.getHeight() < 95, "Calendar remains compact at " + width + " (height " + strip.getHeight() + ")");
        double previousRight = Double.NEGATIVE_INFINITY;
        for (var node : strip.getChildren()) {
            var button = (Button) node;
            var bounds = button.localToParent(button.getLayoutBounds());
            check(bounds.getMinX() >= previousRight - 0.5, "Calendar buttons do not overlap at " + width);
            previousRight = bounds.getMaxX();
            var column = (VBox) button.getGraphic();
            check(column.getChildren().size() == 3 && column.getChildren().stream().allMatch(Label.class::isInstance),
                    "Calendar shows weekday and date without decorative dots");
            for (var label : column.getChildren()) {
                var textBounds = label.localToScene(label.getBoundsInLocal());
                var buttonBounds = button.localToScene(button.getBoundsInLocal());
                check(textBounds.getMinX() >= buttonBounds.getMinX() - 1 && textBounds.getMaxX() <= buttonBounds.getMaxX() + 1,
                        "Calendar text fits inside its day at " + width);
            }
        }
        check(previousRight <= strip.getWidth() + 0.5, "Calendar fits its container at " + width);
    }
    private static <T> T empty(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> List.of()));
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
