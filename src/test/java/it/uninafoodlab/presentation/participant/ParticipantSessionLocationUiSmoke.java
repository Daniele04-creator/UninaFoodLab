package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.presentation.FxSnapshot;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public final class ParticipantSessionLocationUiSmoke {
    private ParticipantSessionLocationUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var stageRef = new AtomicReference<Stage>();
        var sessionsRef = new AtomicReference<ListView<PublishedCourseSessionSummary>>();
        var opened = new AtomicReference<URI>();
        var feedback = new AtomicReference<String>();
        var pending = new AtomicReference<CompletableFuture<Void>>();
        var uri = URI.create("https://maps.example.test/locations/laboratorio-1");
        try {
            fx(() -> {
                var sessions = new ListView<PublishedCourseSessionSummary>();
                sessionsRef.set(sessions);
                Function<URI, CompletableFuture<Void>> opener = value -> {
                    opened.set(value);
                    return pending.get() == null
                            ? CompletableFuture.failedFuture(new IllegalStateException("Browser unavailable")) : pending.get();
                };
                sessions.setCellFactory(ignored -> ParticipantWorkspaceCells.sessionCell(message -> {
                    check(Platform.isFxApplicationThread(), "Location failures are presented on the JavaFX thread");
                    feedback.set(message);
                }, opener));
                var root = new VBox(sessions); root.getStyleClass().add("participant-workspace");
                var scene = new Scene(root, 1000, 350);
                scene.getStylesheets().add(ParticipantSessionLocationUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                scene.getStylesheets().add(ParticipantSessionLocationUiSmoke.class.getResource("participant.css").toExternalForm());
                var stage = new Stage(); stageRef.set(stage); stage.setScene(scene); stage.show();
                replace(sessions, SessionMode.PRACTICAL, uri.toString());
                check(sessions.lookupAll(".label").stream().map(Label.class::cast)
                                .anyMatch(label -> "Posizione".equals(label.getText()) && label.getGraphic() != null),
                        "A location link is shown as a short position label with a map icon");
                var action = locationButton(sessions);
                check(action != null && action.getMinHeight() >= 44 && !action.getAccessibleText().isBlank(),
                        "The received HTTPS location exposes an accessible explicit action");
                action.fire();
                check(uri.equals(opened.get()), "The action opens the exact received URI without reconstructing an address");
                FxSnapshot.save(root, "target/ui-polish/participant-session-position-link.png");
            });
            fx(() -> {
                check(feedback.get() != null && feedback.get().contains("posizione"),
                        "A browser failure produces visible location feedback");
                var sessions = sessionsRef.get();
                replace(sessions, SessionMode.PRACTICAL, "Laboratorio 1, via Roma 10");
                check(locationButton(sessions) == null && sessions.lookupAll(".label").stream().map(Label.class::cast)
                                .anyMatch(label -> "Laboratorio 1, via Roma 10".equals(label.getText())),
                        "Textual venues remain readable and do not generate map links");
                replace(sessions, SessionMode.PRACTICAL, "http://maps.example.test/location");
                check(locationButton(sessions) == null, "An unsupported location URL exposes no action");
                replace(sessions, SessionMode.ONLINE, uri.toString());
                check(locationButton(sessions) == null, "An online session never exposes a venue action");
                feedback.set(null);
                pending.set(new CompletableFuture<>());
                replace(sessions, SessionMode.PRACTICAL, uri.toString());
                locationButton(sessions).fire();
                replace(sessions, SessionMode.PRACTICAL, "Laboratorio 2, via Roma 11");
                pending.get().completeExceptionally(new IllegalStateException("Delayed browser failure"));
            });
            fx(() -> {
                check(feedback.get() == null, "A location failure after cell reuse does not affect the new session");
                pending.set(new CompletableFuture<>());
                var sessions = sessionsRef.get();
                replace(sessions, SessionMode.PRACTICAL, uri.toString());
                locationButton(sessions).fire();
                sessions.setVisible(false);
                pending.get().completeExceptionally(new IllegalStateException("Failure after navigation"));
            });
            fx(() -> {
                check(feedback.get() == null, "A location failure after hiding the page does not affect another screen");
                sessionsRef.get().setVisible(true);
            });
            System.out.println("PASS: participant received location URI, textual venue, cell reuse and asynchronous failure feedback");
        } finally {
            fx(() -> { if (stageRef.get() != null) stageRef.get().close(); });
            Platform.exit();
        }
    }

    private static void replace(ListView<PublishedCourseSessionSummary> sessions, SessionMode mode, String venue) {
        sessions.getItems().setAll(new PublishedCourseSessionSummary(UUID.randomUUID(), 1,
                LocalDateTime.of(2026, 10, 2, 18, 0), mode, 90, Optional.empty(), Optional.of(venue),
                Optional.of(20), Optional.empty(), SessionStatus.SCHEDULED));
        sessions.applyCss(); sessions.layout();
    }

    private static Button locationButton(ListView<?> sessions) {
        return sessions.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.isVisible() && button.isManaged() && "Apri posizione".equals(button.getText()))
                .findFirst().orElse(null);
    }

    private static void fx(CheckedAction action) throws Exception {
        var task = new FutureTask<Void>(() -> { action.run(); return null; });
        Platform.runLater(task); task.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface CheckedAction { void run() throws Exception; }
}
