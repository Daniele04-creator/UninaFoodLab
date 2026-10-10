package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.presentation.common.SessionLocationLinks;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.Button;


final class ParticipantSessionLocation {
    private final Button button = new Button("Apri posizione");
    private final Supplier<PublishedCourseSessionSummary> currentSession;
    private final Consumer<String> errorFeedback;
    private final Function<URI, CompletableFuture<Void>> opener;

    ParticipantSessionLocation(Supplier<PublishedCourseSessionSummary> currentSession,
            Consumer<String> errorFeedback, Function<URI, CompletableFuture<Void>> opener) {
        this.currentSession = currentSession;
        this.errorFeedback = errorFeedback;
        this.opener = opener;
        button.getStyleClass().add("button-secondary");
        button.setMinHeight(44);
        button.setAccessibleText("Apri la posizione della sessione nel browser");
        button.setOnAction(event -> open());
    }

    Button button() { return button; }

    void update(PublishedCourseSessionSummary session) {
        boolean visible = session.mode() == SessionMode.PRACTICAL
                && SessionLocationLinks.fromVenue(session.venue()).isPresent();
        button.setVisible(visible);
        button.setManaged(visible);
    }

    static String label(PublishedCourseSessionSummary session) {
        return SessionLocationLinks.fromVenue(session.venue()).isPresent()
                ? "Posizione" : session.venue().orElse("Sede da definire");
    }

    private void open() {
        var session = currentSession.get();
        if (session == null || session.mode() != SessionMode.PRACTICAL) return;
        SessionLocationLinks.fromVenue(session.venue()).ifPresent(uri -> openUri(uri, session.id()));
    }

    private void openUri(URI uri, UUID sessionId) {
        opener.apply(uri).whenComplete((ignored, error) -> {
            if (error != null) Platform.runLater(() -> showError(sessionId));
        });
    }

    private void showError(UUID sessionId) {
        var session = currentSession.get();
        if (session != null && sessionId.equals(session.id()) && visibleOnScreen()) {
            errorFeedback.accept("Non è stato possibile aprire la posizione nel browser.");
        }
    }

    private boolean visibleOnScreen() {
        var scene = button.getScene();
        if (scene == null || scene.getWindow() == null || !scene.getWindow().isShowing()) return false;
        for (javafx.scene.Node node = button; node != null; node = node.getParent()) {
            if (!node.isVisible()) return false;
        }
        return true;
    }
}
