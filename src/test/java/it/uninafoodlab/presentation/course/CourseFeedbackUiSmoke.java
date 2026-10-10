package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.application.course.*;
import it.uninafoodlab.application.schedule.*;
import it.uninafoodlab.domain.identity.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class CourseFeedbackUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var categoryReads = new AtomicInteger();
        var writes = new AtomicInteger();
        var failedList = new AtomicBoolean();
        var lastCategory = new AtomicReference<Optional<UUID>>();
        var availableCourses = new AtomicReference<List<CourseSummary>>(List.of());
        var availableSessions = new HashMap<UUID, List<CourseSessionSummary>>();
        var failedSessionCourse = new AtomicReference<UUID>();
        var lastSessionCourse = new AtomicReference<UUID>();
        var releaseWrite = new CountDownLatch(1);
        var writeEntered = new CountDownLatch(1);
        var category = new CategorySummary(UUID.randomUUID(), "Cucina", "Categoria sintetica");
        var chef = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("smoke.feedback"), "Chef sintetico", Role.CHEF);
        var catalog = (CatalogRepository) Proxy.newProxyInstance(CourseFeedbackUiSmoke.class.getClassLoader(),
                new Class<?>[]{CatalogRepository.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("categories")) {
                        if (categoryReads.incrementAndGet() == 1) throw new IllegalStateException("jdbc:synthetic-failure");
                        return List.of(category);
                    }
                    if (method.getReturnType() == it.uninafoodlab.application.common.Page.class) return new it.uninafoodlab.application.common.Page<>(List.of(), 0);
                    if (method.getReturnType() == List.class) return List.of();
                    throw new AssertionError("Unexpected catalog write");
                });
        var courses = (CourseRepository) Proxy.newProxyInstance(CourseFeedbackUiSmoke.class.getClassLoader(),
                new Class<?>[]{CourseRepository.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("createAndPublish")) {
                        writes.incrementAndGet(); writeEntered.countDown();
                        if (!releaseWrite.await(10, TimeUnit.SECONDS)) throw new AssertionError("Write latch timeout");
                        throw new IllegalStateException("synthetic uncertain write");
                    }
                    if (method.getName().equals("findByChef")) {
                        lastCategory.set(((it.uninafoodlab.application.course.CourseFilter) arguments[1]).categoryId());
                        if (failedList.getAndSet(false)) throw new IllegalStateException("synthetic list failure");
                        return new it.uninafoodlab.application.common.Page<>(availableCourses.get(), availableCourses.get().size());
                    }
                    if (method.getName().equals("sessions") && arguments[1] instanceof UUID courseId) {
                        lastSessionCourse.set(courseId);
                        if (courseId.equals(failedSessionCourse.get())) throw new IllegalStateException("synthetic session failure");
                        return availableSessions.getOrDefault(courseId, List.of());
                    }
                    if (method.getName().equals("sessions") && arguments[1] instanceof List<?>) return Map.copyOf(availableSessions);
                    if (method.getReturnType() == List.class) return List.of();
                    if (method.getReturnType() == java.util.Map.class) return java.util.Map.of();
                    throw new AssertionError("Unexpected course write");
                });
        var schedule = (ScheduleRepository) Proxy.newProxyInstance(CourseFeedbackUiSmoke.class.getClassLoader(),
                new Class<?>[]{ScheduleRepository.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Unexpected schedule access");
                });
        var controller = new CourseWorkspaceController(chef, new BrowseCatalog(catalog), new CreateCategory(catalog),
                new CreateCourse(courses), new BrowseCourses(courses), new AssignRecipeToSession(courses),
                new ManageCourseSchedule(schedule), Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneOffset.UTC));
        try {
            fx(() -> {
                var loader = new FXMLLoader(CourseFeedbackUiSmoke.class.getResource("/it/uninafoodlab/presentation/course/course-workspace.fxml"));
                loader.setController(controller);
                var scene = new Scene(loader.<VBox>load(), 1000, 640);
                scene.getStylesheets().add(CourseFeedbackUiSmoke.class.getResource("/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                var stage = new Stage(); stage.setScene(scene); stage.show(); return null;
            });
            await(() -> !controller.isBusy());
            fx(() -> {
                var retry = button(controller, "courseRetryButton");
                check(retry.isVisible() && retry.isManaged(), "Initial read failure exposes safe retry");
                check(effectivelyVisible(node(controller, "feedbackLabel")), "Failure is visible in the course index");
                check(!((Label) field(controller, "feedbackLabel")).getText().contains("jdbc:"), "Technical read error remains private");
                retry.fire(); return null;
            });
            await(() -> !controller.isBusy());
            fx(() -> {
                check(categoryReads.get() == 2, "Retry performs the failed initial read once");
                check(!button(controller, "courseRetryButton").isVisible(), "Successful read clears retry");
                failedList.set(true);
                ((CourseWorkspaceCoordinator) field(controller, "coordinator")).loadCourses(null); return null;
            });
            await(() -> !controller.isBusy());
            var otherCategory = new CategorySummary(UUID.randomUUID(), "Pane", "Seconda categoria sintetica");
            fx(() -> {
                check(effectivelyVisible(node(controller, "feedbackLabel")) && button(controller, "courseRetryButton").isVisible(),
                        "List read failure is visible and retryable");
                var guard = (ReactiveFilterGuard) field(controller, "filterGuard");
                guard.update(() -> ((ComboBox<CourseCategoryFilter>) uncheckedField(controller, "categoryFilter"))
                        .setValue(CourseCategoryFilter.category(otherCategory)));
                ((TextField) field(controller, "courseSearchField")).setText("Pane");
                button(controller, "courseRetryButton").fire(); return null;
            });
            await(() -> !controller.isBusy());
            fx(() -> {
                check(lastCategory.get().equals(Optional.of(otherCategory.id())), "List retry uses the current filter rather than stale failure context");
                check(((TextField) field(controller, "courseSearchField")).getText().equals("Pane"), "List retry preserves the current query");
                controller.showCourseCreation();
                ((ComboBox<CategorySummary>) field(controller, "categoryField")).setValue(category);
                ((TextField) field(controller, "titleField")).setText("Cucina italiana");
                ((TextArea) field(controller, "descriptionField")).setText("Un corso sintetico completo");
                var editors = (javafx.scene.layout.TilePane) field(controller, "sessionModeEditor");
                for (var editor : editors.getChildren()) {
                    var url = (TextField) editor.lookup(".text-field"); url.setText("https://example.test/lezione");
                }
                button(controller, "createButton").fire(); return null;
            });
            check(writeEntered.await(10, TimeUnit.SECONDS), "Creation enters synthetic delayed write");
            fx(() -> {
                check(node(controller, "titleField").isDisabled() && node(controller, "descriptionField").isDisabled()
                        && node(controller, "startDateField").isDisabled() && node(controller, "sessionModeEditor").isDisabled(),
                        "Every creation field is locked while values are being saved");
                check(button(controller, "cancelCreateCourseButton").isDisabled()
                        && button(controller, "createCourseReturnButton").isDisabled(), "Creation cannot be left during save");
                check(effectivelyVisible(node(controller, "createProgressIndicator"))
                        && ((Label) field(controller, "createProgressLabel")).getText().equals("Creazione in corso…"),
                        "Creation status and progress are beside the submit action");
                button(controller, "createButton").fire();
                controller.showCourseIndex();
                check(node(controller, "createCourseView").isVisible(), "Programmatic return also respects busy creation");
                snapshot(controller, "target/course-creation-busy.png"); return null;
            });
            releaseWrite.countDown();
            await(() -> !controller.isBusy());
            fx(() -> {
                check(writes.get() == 1, "Repeated submit never duplicates the write");
                check(((TextField) field(controller, "titleField")).getText().equals("Cucina italiana"), "Write error retains draft");
                check(!node(controller, "titleField").isDisabled() && !button(controller, "cancelCreateCourseButton").isDisabled(),
                        "Write error restores all controls");
                check(!button(controller, "courseRetryButton").isVisible(), "Uncertain creation cannot be replayed by safe read retry");
                check(button(controller, "createButton").getScene().getFocusOwner() == button(controller, "createButton"),
                        "Failure returns focus to creation action");
                var coordinator = (CourseWorkspaceCoordinator) field(controller, "coordinator");
                coordinator.setCourseFormVisible(false); coordinator.showFeedback("Corso creato.", false);
                controller.showCourseIndex();
                check(effectivelyVisible(node(controller, "feedbackLabel")), "Success remains visible after returning to the index");
                snapshot(controller, "target/course-index-feedback.png"); return null;
            });
            var courseA = course(category.id(), "Corso A");
            var courseB = course(category.id(), "Corso B");
            var sessionA = session();
            var sessionB = session();
            availableSessions.put(courseA.id(), List.of(sessionA));
            availableSessions.put(courseB.id(), List.of(sessionB));
            availableCourses.set(List.of(courseA, courseB));
            fx(() -> {
                ((CourseWorkspaceCoordinator) field(controller, "coordinator")).loadCourses(null);
                return null;
            });
            await(() -> !controller.isBusy());
            fx(() -> {
                var table = (TableView<CourseSessionSummary>) field(controller, "sessionTable");
                var menu = (ComboBox<CourseSessionSummary>) field(controller, "recipeSessionField");
                check(table.getItems().equals(List.of(sessionA)) && menu.getValue().equals(sessionA),
                        "First course has a loaded program and selected menu session");
                failedSessionCourse.set(courseB.id());
                ((ListView<CourseSummary>) field(controller, "courseList")).getSelectionModel().select(courseB);
                return null;
            });
            await(() -> !controller.isBusy());
            fx(() -> {
                check(((TableView<?>) field(controller, "sessionTable")).getItems().isEmpty(),
                        "Changing courses clears the previous program even when the new read fails");
                var menu = (ComboBox<?>) field(controller, "recipeSessionField");
                check(menu.getItems().isEmpty() && menu.getValue() == null,
                        "Changing courses clears previous menu session selection and choices");
                check(button(controller, "rescheduleButton").isDisabled() && button(controller, "cancelSessionButton").isDisabled(),
                        "Failed course read cannot reenable writes against the previous course session");
                check(button(controller, "courseRetryButton").isVisible(), "Current course read has a safe retry");
                failedSessionCourse.set(null);
                button(controller, "courseRetryButton").fire();
                return null;
            });
            await(() -> !controller.isBusy());
            fx(() -> {
                check(lastSessionCourse.get().equals(courseB.id())
                        && ((TableView<?>) field(controller, "sessionTable")).getItems().equals(List.of(sessionB)),
                        "Retry loads only the selected course program");
                ((ListView<?>) field(controller, "courseList")).getSelectionModel().clearSelection();
                check(((ComboBox<?>) field(controller, "recipeSessionField")).getValue() == null,
                        "Clearing the course also clears its selected menu session");
                return null;
            });
            System.out.println("PASS: visible index feedback, safe read retry, locked delayed creation, guarded return, draft and focus recovery, no write replay");
        } finally { releaseWrite.countDown(); Platform.exit(); }
    }
    private static CourseSummary course(UUID categoryId, String title) {
        return new CourseSummary(UUID.randomUUID(), categoryId, "Cucina", it.uninafoodlab.domain.course.CourseTitle.of(title),
                "Corso sintetico", LocalDate.of(2026, 9, 20), LocalTime.of(18, 30), 7, 2,
                Optional.of(LocalDateTime.of(2026, 10, 1, 20, 30)), it.uninafoodlab.domain.course.CourseStatus.PUBLISHED,
                it.uninafoodlab.domain.course.IngredientMargin.zero());
    }
    private static CourseSessionSummary session() {
        return new CourseSessionSummary(UUID.randomUUID(), 1, LocalDateTime.of(2026, 9, 20, 18, 30),
                it.uninafoodlab.domain.course.SessionMode.PRACTICAL, it.uninafoodlab.domain.course.SessionStatus.SCHEDULED,
                120, Optional.empty(), Optional.of("Laboratorio"), Optional.of(12),
                Optional.of(LocalDateTime.of(2026, 9, 20, 18, 29)), Optional.empty(), List.of(), 0);
    }
    private static boolean effectivelyVisible(Node node) {
        for (var current = node; current != null; current = current.getParent()) if (!current.isVisible()) return false;
        return true;
    }
    private static void snapshot(Object controller, String path) throws Exception {
        var root = node(controller, "titleField").getScene().getRoot(); root.applyCss(); root.layout();
        var image = root.snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", new java.io.File(path));
    }
    private static Object field(Object object, String name) throws Exception { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private static Object uncheckedField(Object object, String name) {
        try { return field(object, name); } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static Node node(Object object, String name) throws Exception { return (Node) field(object, name); }
    private static Button button(Object object, String name) throws Exception { return (Button) field(object, name); }
    private static <T> T fx(Callable<T> action) throws Exception { var task = new FutureTask<T>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS); }
    private static void await(Callable<Boolean> condition) throws Exception { it.uninafoodlab.presentation.FxAwait.until(condition, 10); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
