package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;


final class ParticipantOnlineLesson {
    private final Button button = new Button("Apri lezione");
    private final PublishedCourseSessionSummary session;
    private final Function<URI, CompletableFuture<Void>> opener;
    private final Consumer<String> feedback;
    private boolean opening;

    ParticipantOnlineLesson(PublishedCourseSessionSummary session, Consumer<String> feedback,
            Function<URI, CompletableFuture<Void>> opener) {
        this.session = session;
        this.feedback = feedback;
        this.opener = opener;
        button.getStyleClass().add("button-primary");
        button.setMinHeight(44);
        button.setAccessibleText("Apri nel browser la lezione online della sessione " + session.sequenceNumber());
        boolean available = ParticipantWorkspaceRules.authorizedOnlineLink(session).isPresent();
        button.setVisible(available);
        button.setManaged(available);
        button.setOnAction(event -> open());
    }

    Button button() { return button; }

    private void open() {
        if (opening) return;
        ParticipantWorkspaceRules.authorizedOnlineLink(session).ifPresent(link -> {
            opening = true;
            button.setDisable(true);
            button.setText("Apertura in corso…");
            feedback.accept("");
            CompletableFuture<Void> pending;
            try {
                pending = opener.apply(URI.create(link));
            } catch (RuntimeException exception) {
                pending = CompletableFuture.failedFuture(exception);
            }
            pending.whenComplete((ignored, error) -> Platform.runLater(() -> {
                opening = false;
                button.setDisable(false);
                button.setText("Apri lezione");
                if (error != null && visibleOnScreen()) {
                    feedback.accept("Non è stato possibile aprire la lezione nel browser. Puoi copiare il link.");
                }
            }));
        });
    }

    private boolean visibleOnScreen() {
        if (button.getScene() == null || button.getScene().getWindow() == null
                || !button.getScene().getWindow().isShowing()) return false;
        for (Node node = button; node != null; node = node.getParent()) {
            if (!node.isVisible()) return false;
        }
        return true;
    }
}
