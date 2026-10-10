package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.application.course.*;
import it.uninafoodlab.application.schedule.*;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.identity.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public final class CourseCreationUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var fixture = fx(CourseCreationUiSmoke::create);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (fx(fixture.controller()::isBusy)) {
                if (System.nanoTime() > deadline) throw new AssertionError("Initial data timeout");
                Thread.sleep(20);
            }
            fx(() -> { verify(fixture); return null; });
            System.out.println("PASS CourseCreationUiSmoke: draft reset and explicit common settings");
        } finally { Platform.exit(); }
    }

    private record Fixture(CourseWorkspaceController controller, Scene scene, Stage stage) {}

    private static Fixture create() throws Exception {
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("smoke.creation"), "Chef sintetico", Role.CHEF);
        var catalog = readOnly(CatalogRepository.class);
        var courses = readOnly(CourseRepository.class);
        var controller = new CourseWorkspaceController(user, new BrowseCatalog(catalog), new CreateCategory(catalog),
                new CreateCourse(courses), new BrowseCourses(courses), new AssignRecipeToSession(courses),
                new ManageCourseSchedule(readOnly(ScheduleRepository.class)),
                Clock.fixed(Instant.parse("2026-10-02T08:00:00Z"), ZoneOffset.UTC));
        var loader = new FXMLLoader(CourseCreationUiSmoke.class.getResource("/it/uninafoodlab/presentation/course/course-workspace.fxml"));
        loader.setController(controller);
        var workspace = loader.<VBox>load();

        var navigationSpace = new Region(); navigationSpace.setMinHeight(110); navigationSpace.setPrefHeight(110);
        var shell = new VBox(navigationSpace, workspace); VBox.setVgrow(workspace, Priority.ALWAYS);
        var scene = new Scene(shell, 1000, 720);
        scene.getStylesheets().add(CourseCreationUiSmoke.class.getResource("/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
        var stage = new Stage(); stage.setScene(scene); stage.show();
        return new Fixture(controller, scene, stage);
    }

    @SuppressWarnings("unchecked")
    private static void verify(Fixture fixture) throws Exception {
        var controller = fixture.controller(); var scene = fixture.scene(); var stage = fixture.stage();
        try {
            controller.showCourseCreation();
            var form = (CourseCreationForm) field(controller, "creationForm");
            var title = (TextField) field(controller, "titleField");
            var category = (ComboBox<CategorySummary>) field(controller, "categoryField");
            var count = (Spinner<Integer>) field(controller, "sessionCountField");
            var recurrence = (Spinner<Integer>) field(controller, "recurrenceField");
            var date = (DatePicker) field(controller, "startDateField");
            var time = (TimeSelector) field(controller, "startTimeField");
            var host = (TilePane) field(controller, "sessionModeEditor");
            check(!controller.hasUnsavedChanges(), "Opening an untouched course has no unsaved changes");
            title.setText("Bozza");
            check(controller.hasUnsavedChanges(), "Typing protects the course draft");
            title.clear();
            check(!controller.hasUnsavedChanges(), "Reverting text clears the exit warning");
            recurrence.getEditor().clear();
            check(controller.hasUnsavedChanges(), "Incomplete numeric input is protected before commit");
            recurrence.getEditor().setText("7");
            check(!controller.hasUnsavedChanges(), "Reverting numeric input restores an untouched draft");
            count.getValueFactory().setValue(3);
            ((TextField) host.getChildren().get(2).lookup(".text-field")).setText("https://example.test/hidden");
            count.getValueFactory().setValue(2);
            check(controller.hasUnsavedChanges(), "Reduced session count still protects retained hidden session edits");
            form.resetForm();
            title.setText("Corso di prova");
            category.setValue(new CategorySummary(UUID.randomUUID(), "Prova", "Sintetica"));
            count.getValueFactory().setValue(12);
            recurrence.getValueFactory().setValue(3);
            date.setValue(LocalDate.of(2026, 10, 20)); time.setValue(LocalTime.of(9, 15));
            form.resetForm();
            check(category.getValue() == null && recurrence.getValue() == 7
                    && date.getValue().equals(LocalDate.of(2026, 10, 9)) && time.getValue().equals(LocalTime.of(18, 30)),
                    "Discard/success must reset category, date, time and frequency as well as the text and sessions");
            check(title.getText().isEmpty() && count.getValue() == 2, "Fresh draft restores two sessions");
            title.setText("Bozza conservata");
            ((Button) field(controller, "createCourseReturnButton")).fire();
            check(controller.hasUnsavedChanges(), "Returning to the index retains protected course edits");
            ((Button) field(controller, "openCreateCourseButton")).fire();
            check(title.getText().equals("Bozza conservata"), "Return/reopen preserves the course draft");
            check(((Button) field(controller, "createCourseReturnButton")).getText().contains("conserva"),
                    "Return communicates draft preservation");
            check(((Button) field(controller, "cancelCreateCourseButton")).getText().equals("Scarta bozza"),
                    "Discard is distinct from return");
            respondToDiscardDialog("Mantieni bozza");
            ((Button) field(controller, "cancelCreateCourseButton")).fire();
            check(title.getText().equals("Bozza conservata"), "Cancelling the discard confirmation preserves all draft data");
            count.getValueFactory().setValue(12);
            var commonMode = (ComboBox<SessionMode>) scene.lookup("#commonSessionMode");
            var commonUrl = (TextField) scene.lookup("#commonSessionUrl");
            var apply = (Button) scene.lookup("#applyCommonSessionSettings");
            ((TextField) host.getChildren().get(1).lookup(".text-field")).setText("https://example.test/exception");
            commonUrl.setText("https://example.test/common");
            check(((TextField) host.getChildren().get(1).lookup(".text-field")).getText().endsWith("exception"),
                    "Typing common settings cannot overwrite a session exception");
            apply.fire();
            check(host.getChildren().stream().allMatch(node -> ((TextField) node.lookup(".text-field")).getText().endsWith("common")),
                    "Explicit Apply updates all twelve current sessions");
            commonUrl.setText("http://example.test/invalid"); apply.fire();
            check(((TextField) host.getChildren().getFirst().lookup(".text-field")).getText().endsWith("common"),
                    "Invalid common link cannot partially replace session details");
            commonMode.setValue(SessionMode.PRACTICAL);
            ((TextField) scene.lookup("#commonSessionVenue")).setText("Laboratorio comune");
            ((Spinner<Integer>) scene.lookup("#commonSessionCapacity")).getValueFactory().setValue(25); apply.fire();
            check(host.getChildren().stream().allMatch(node -> ((TextField) node.lookup(".text-field")).getText().equals("Laboratorio comune")),
                    "Explicit practical settings update venue and mode");
            scene.getRoot().applyCss();
            for (int pass = 0; pass < 3; pass++) scene.getRoot().layout();
            check(host.getPrefColumns() == 1, "Calendar keeps one compact row per session");
            var first = host.getChildren().getFirst();
            check(first.getBoundsInParent().getHeight() < 120, "Session row remains compact at 1000 px: " + first.getBoundsInParent());
            captureCalendar(controller, scene, "target/ui-polish/course-creation-common-1000.png");

            var root = scene.getRoot();
            scene.setRoot(new VBox());
            scene = new Scene(root, 800, 720);
            scene.getStylesheets().add(CourseCreationUiSmoke.class.getResource("/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
            stage.setScene(scene); stage.sizeToScene();
            root.applyCss(); root.resize(800, 720);
            for (int pass = 0; pass < 3; pass++) scene.getRoot().layout();
            check(first.getBoundsInParent().getHeight() < 210, "Narrow session row wraps without becoming a full card");
            var footer = (Button) field(controller, "createButton");
            check(footer.localToScene(footer.getBoundsInLocal()).getMaxY() <= scene.getHeight(),
                    "Fixed course actions stay reachable below Chef navigation");
            captureCalendar(controller, scene, "target/ui-polish/course-creation-common-800.png");
            respondToDiscardDialog("Scarta bozza");
            ((Button) field(controller, "cancelCreateCourseButton")).fire();
            check(!((ScrollPane) field(controller, "createCourseView")).isVisible(), "Confirmed discard returns to the catalog");
            check(!form.hasDraft(), "Confirmed discard restores a genuinely empty draft");
            check(!controller.hasUnsavedChanges(), "Confirmed discard clears shell exit protection");
            check(commonMode.getValue() == SessionMode.ONLINE && commonUrl.getText().isEmpty(), "Discard clears unapplied common settings");
            controller.showCourseCreation();
            ((Button) field(controller, "cancelCreateCourseButton")).fire();
            check(!((ScrollPane) field(controller, "createCourseView")).isVisible(), "Discarding an empty draft requires no dialog");
        } finally { stage.close(); }
    }

    private static void respondToDiscardDialog(String action) {
        Platform.runLater(() -> {
            var pane = javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                    .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                    .map(DialogPane.class::cast).findFirst().orElseThrow(() -> new AssertionError("Discard confirmation missing"));
            var keep = pane.getButtonTypes().stream().filter(type -> type.getText().equals("Mantieni bozza")).findFirst().orElseThrow();
            check(((Button) pane.lookupButton(keep)).isDefaultButton(), "Keep draft is the safe default confirmation action");
            var choice = pane.getButtonTypes().stream().filter(type -> type.getText().equals(action)).findFirst().orElseThrow();
            ((Button) pane.lookupButton(choice)).fire();
        });
    }

    private static void captureCalendar(CourseWorkspaceController controller, Scene scene, String path) throws Exception {
        var scroll = (ScrollPane) field(controller, "createCourseView");
        var common = (VBox) field(controller, "commonSessionSettings");
        var bounds = scroll.getContent().sceneToLocal(common.localToScene(common.getBoundsInLocal()));
        var range = scroll.getContent().getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
        scroll.setVvalue(Math.clamp(bounds.getMinY() / range, 0, 1));
        scene.getRoot().layout();
        it.uninafoodlab.presentation.FxSnapshot.save(scene.getRoot(), path);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readOnly(Class<T> type) {
        return (T) Proxy.newProxyInstance(CourseCreationUiSmoke.class.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getReturnType() == it.uninafoodlab.application.common.Page.class) return new it.uninafoodlab.application.common.Page<>(List.of(), 0);
            if (method.getReturnType() == java.util.Map.class) return java.util.Map.of();
            if (method.getReturnType() == java.util.Optional.class) return java.util.Optional.empty();
            if (List.class.isAssignableFrom(method.getReturnType())) return List.of();
            throw new AssertionError("Unexpected repository write: " + method.getName());
        });
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T> T fx(Callable<T> operation) throws Exception {
        var task = new FutureTask<>(operation); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
}
