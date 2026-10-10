package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.presentation.FxSnapshot;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public final class ParticipantOnlineLessonUiSmoke {
    private ParticipantOnlineLessonUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> {
                var root = new VBox();
                ParticipantSessionTimeline.render(root, List.of(session(Optional.of(URI.create("https://example.test/lesson")))),
                        List.of(), List.of(), true, Clock.systemUTC(), (item, status) -> {}, message -> {});
                var scene = new Scene(root, 1000, 350);
                scene.getStylesheets().add(ParticipantOnlineLessonUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                var stage = new Stage(); stage.setScene(scene); stage.show();
                check(root.lookupAll(".button").stream().map(Button.class::cast)
                        .anyMatch(button -> "Apri lezione".equals(button.getText())),
                        "the authorized online program must provide Apri lezione");
                var copy = root.lookupAll(".button").stream().map(Button.class::cast)
                        .filter(button -> "Copia link".equals(button.getText())).findFirst().orElseThrow();
                check(copy.getStyleClass().contains("button-secondary"), "copy remains a secondary action");
                FxSnapshot.save(root, "target/ui-polish/participant-online-lesson.png");
                for (var url : List.of(Optional.<URI>empty(), Optional.of(URI.create("http://example.test/lesson")),
                        Optional.of(URI.create("https://user:pass@example.test/lesson")),
                        Optional.of(URI.create("javascript:alert(1)")))) {
                    ParticipantSessionTimeline.render(root, List.of(session(url)), List.of(), List.of(), true,
                            Clock.systemUTC(), (item, status) -> {}, message -> {});
                    check(root.lookupAll(".participant-session-row").stream()
                                    .allMatch(row -> row.lookupAll(".button").isEmpty()),
                            "missing or unsafe link must expose no online action within its session");
                }
                stage.close(); return null;
            });
            verifyAsyncOpening();
            System.out.println("PASS: authorized online lesson, secondary copy, invalid links, opening guard and contextual browser failures.");
        } finally { Platform.exit(); }
    }

    private static void verifyAsyncOpening() throws Exception {
        var pending = new AtomicReference<CompletableFuture<Void>>(new CompletableFuture<>());
        var error = new AtomicReference<String>("");
        var opened = new AtomicReference<URI>();
        var calls = new AtomicInteger();
        var lesson = new AtomicReference<ParticipantOnlineLesson>();
        var stage = fx(() -> {
            var action = new ParticipantOnlineLesson(session(Optional.of(URI.create("https://example.test/lesson"))),
                    error::set, uri -> { calls.incrementAndGet(); opened.set(uri); return pending.get(); });
            lesson.set(action);
            var shown = new Stage(); shown.setScene(new Scene(new VBox(action.button()), 600, 200)); shown.show();
            action.button().fire(); action.button().fire();
            check(calls.get() == 1 && action.button().isDisabled(), "browser opening must prevent repeated actions");
            check(opened.get().equals(URI.create("https://example.test/lesson")), "open the exact authorized lesson URI");
            pending.get().completeExceptionally(new IllegalStateException("Browser unavailable"));
            return shown;
        });
        fx(() -> {
            check(!lesson.get().button().isDisabled() && error.get().contains("copiare il link"),
                    "opening failures must restore the action and give usable local feedback");
            error.set(""); pending.set(new CompletableFuture<>()); lesson.get().button().fire();
            lesson.get().button().getParent().setVisible(false);
            pending.get().completeExceptionally(new IllegalStateException("Delayed failure"));
            return null;
        });
        fx(() -> {
            check(error.get().isEmpty(), "failure after navigation must not appear in another view");
            stage.close(); return null;
        });
    }

    private static PublishedCourseSessionSummary session(Optional<URI> uri) {
        return new PublishedCourseSessionSummary(UUID.randomUUID(), 1, LocalDateTime.of(2026, 10, 2, 18, 0),
                SessionMode.ONLINE, 90, uri, Optional.empty(), Optional.empty());
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action); Platform.runLater(task); return task.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
