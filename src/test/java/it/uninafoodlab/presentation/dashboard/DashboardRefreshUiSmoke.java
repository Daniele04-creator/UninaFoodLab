package it.uninafoodlab.presentation.dashboard;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseRepository;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import it.uninafoodlab.presentation.FxAwait;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class DashboardRefreshUiSmoke {
    private DashboardRefreshUiSmoke() {}

    public static void main(String[] arguments) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            verifyLatestRefresh(false);
            verifyLatestRefresh(true);
            verifyLatestFailure();
            verifyDisposedRead();
            System.out.println("PASS: dashboard ignores obsolete responses, preserves current errors and stops after disposal.");
        } finally {
            Platform.exit();
        }
    }

    private static void verifyLatestFailure() throws Exception {
        try (var fixture = new Fixture(false)) {
            fx(() -> { fixture.controller.activate(); return null; });
            check(fixture.firstEntered.await(10, TimeUnit.SECONDS), "first reading must be blocked");
            fixture.failUpdated = true;
            fx(() -> { fixture.controller.activate(); return null; });
            FxAwait.until(() -> fixture.root.lookup("#feedbackLabel").isVisible() && !fixture.loading(), 10);
            fixture.releaseFirst.countDown();
            check(fixture.firstFinished.await(10, TimeUnit.SECONDS), "first reading must finish");
            fixture.settleCallbacks();
            fx(() -> {
                check(fixture.root.lookup("#feedbackLabel").isVisible()
                                && fixture.root.lookup("#refreshButton").isVisible(),
                        "obsolete success must preserve the latest error and retry");
                check(!fixture.hasTitle("Corso precedente"), "old data cannot replace the failed current reading");
                return null;
            });
        }
    }

    private static void verifyDisposedRead() throws Exception {
        try (var fixture = new Fixture(false)) {
            fx(() -> { fixture.controller.activate(); return null; });
            check(fixture.firstEntered.await(10, TimeUnit.SECONDS), "first reading must be blocked");
            fx(() -> { fixture.controller.dispose(); fixture.controller.activate(); return null; });
            fixture.releaseFirst.countDown();
            check(fixture.firstFinished.await(10, TimeUnit.SECONDS), "first reading must finish");
            fixture.settleCallbacks();
            fx(() -> {
                check(!fixture.hasTitle("Corso precedente") && !fixture.controller.isBusy(),
                        "disposed workspace must ignore pending responses");
                check(fixture.courseReads.get() == 1, "disposed workspace must not start new reads");
                return null;
            });
        }
    }

    private static void verifyLatestRefresh(boolean failFirst) throws Exception {
        try (var fixture = new Fixture(failFirst)) {
            fx(() -> { fixture.controller.activate(); return null; });
            check(fixture.firstEntered.await(10, TimeUnit.SECONDS), "first reading must be blocked");
            fx(() -> { fixture.controller.activate(); return null; });
            FxAwait.until(() -> fixture.hasTitle("Corso aggiornato") && !fixture.loading(), 10);
            fixture.releaseFirst.countDown();
            check(fixture.firstFinished.await(10, TimeUnit.SECONDS), "first reading must finish");
            fixture.settleCallbacks();
            fx(() -> {
                check(fixture.hasTitle("Corso aggiornato") && !fixture.hasTitle("Corso precedente"),
                        "obsolete success must not replace the latest agenda");
                check(!fixture.root.lookup("#feedbackLabel").isVisible(),
                        "obsolete error must not expose retry over the successful agenda");
                check(!fixture.loading(), "obsolete response must preserve the latest busy state");
                return null;
            });
        }
    }

    private static final class Fixture implements AutoCloseable {
        final CountDownLatch firstEntered = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final CountDownLatch firstFinished = new CountDownLatch(1);
        final AtomicInteger courseReads = new AtomicInteger();
        final CourseSummary previous = course("Corso precedente");
        final CourseSummary updated = course("Corso aggiornato");
        final DashboardWorkspaceController controller;
        final Parent root;
        final Stage stage;
        volatile boolean failUpdated;

        Fixture(boolean failFirst) throws Exception {
            var repository = (CourseRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{CourseRepository.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("findByChef")) {
                            return List.of(courseReads.getAndIncrement() == 0 ? previous : updated);
                        }
                        if (method.getName().equals("sessions")) {
                            var ids = (List<?>) arguments[1];
                            var id = (UUID) ids.getFirst();
                            if (id.equals(previous.id())) {
                                firstEntered.countDown();
                                try {
                                    check(releaseFirst.await(10, TimeUnit.SECONDS), "blocked reading timed out");
                                    if (failFirst) throw new IllegalStateException("Synthetic obsolete read failure");
                                } finally {
                                    firstFinished.countDown();
                                }
                            }
                            if (id.equals(updated.id()) && failUpdated) {
                                throw new IllegalStateException("Synthetic current read failure");
                            }
                            return Map.of(id, List.of(session()));
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("refresh.chef"), "Chef sintetico", Role.CHEF);
            controller = new DashboardWorkspaceController(user, new BrowseCourses(repository),
                    () -> {}, (course, session) -> {}, (course, session) -> {});
            root = fx(() -> {
                var loader = new FXMLLoader(getClass().getResource("dashboard-workspace.fxml"));
                loader.setController(controller);
                return loader.load();
            });
            stage = fx(() -> {
                var window = new Stage();
                window.setScene(new Scene(root, 1142, 800));
                window.getScene().getStylesheets().add(getClass().getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                window.show();
                return window;
            });
        }

        boolean hasTitle(String title) {
            return root.lookupAll(".agenda-session-title").stream().map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals(title));
        }

        boolean loading() { return ((ProgressIndicator) root.lookup("#progressIndicator")).isVisible(); }

        void settleCallbacks() throws Exception {
            var settled = new CountDownLatch(1);
            fx(() -> {
                var pause = new PauseTransition(Duration.millis(200));
                pause.setOnFinished(event -> settled.countDown());
                pause.play();
                return null;
            });
            check(settled.await(10, TimeUnit.SECONDS), "callbacks must settle");
        }

        @Override public void close() throws Exception {
            releaseFirst.countDown();
            fx(() -> { controller.dispose(); stage.close(); return null; });
        }
    }

    private static CourseSummary course(String title) {
        var today = LocalDate.now(ZoneId.of("Europe/Rome"));
        return new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Cucina", CourseTitle.of(title),
                "Descrizione sintetica", today, java.time.LocalTime.NOON, 1, 1, Optional.empty(),
                CourseStatus.PUBLISHED, IngredientMargin.zero());
    }

    private static CourseSessionSummary session() {
        var scheduled = LocalDateTime.now(ZoneId.of("Europe/Rome")).withHour(18).withMinute(0);
        return new CourseSessionSummary(UUID.randomUUID(), 1, scheduled, SessionMode.PRACTICAL,
                SessionStatus.SCHEDULED, 60, Optional.empty(), Optional.of("Sede sintetica"), Optional.of(8),
                Optional.of(scheduled.minusDays(1)), Optional.empty(), 1);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
