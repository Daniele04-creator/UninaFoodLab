package it.uninafoodlab.presentation.participant;

import static it.uninafoodlab.presentation.participant.ParticipantCourseCards.label;
import it.uninafoodlab.application.notification.ParticipantNotification;
import javafx.scene.control.*;
import javafx.scene.layout.*;


final class ParticipantMessageRows {
    private ParticipantMessageRows() {}

    static void render(VBox messageCards, java.util.List<ParticipantNotification> messages,
            javafx.beans.value.ObservableBooleanValue busy, java.util.function.Consumer<ParticipantNotification> openMessage) {
        messageCards.getChildren().clear();
        for (var item : messages) {
            var row = new HBox(24);
            row.getStyleClass().add("participant-message-row");
            var copy = new VBox(7);
            copy.getStyleClass().add("participant-message-copy");
            HBox.setHgrow(copy, Priority.ALWAYS); copy.setMinWidth(0);
            String preview = item.message().replaceAll("\\s+", " ").strip();
            if (preview.length() > 180) preview = preview.substring(0, 177) + "…";
            copy.getChildren().addAll(label(item.courseTitle().orElse("Tutti i corsi"), "participant-card-meta"),
                    label(item.title(), "participant-message-title"), label(preview, "participant-card-copy"));
            copy.getChildren().forEach(node -> ((Region) node).setMinHeight(Region.USE_PREF_SIZE));
            var actions = new VBox(10);
            actions.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
            actions.setMinWidth(Region.USE_PREF_SIZE);
            var date = label(ParticipantWorkspaceRules.DATE_TIME.format(
                    item.createdAt().atZone(ParticipantWorkspaceRules.ROME)), "participant-card-meta");
            date.setWrapText(false);
            actions.getChildren().add(date);
            var state = label(item.readAt().isEmpty() ? "Da leggere" : "Letta", "participant-message-state");
            actions.getChildren().add(state);
            var open = new Button("Apri comunicazione →");
            open.setMinWidth(Region.USE_PREF_SIZE);
            open.setUserData(item.id());
            open.setAccessibleText("Apri comunicazione: " + item.title());
            open.getStyleClass().add("button-quiet");
            open.disableProperty().bind(busy);
            open.setOnAction(event -> openMessage.accept(item));
            actions.getChildren().add(open); row.getChildren().addAll(copy, actions);
            messageCards.getChildren().add(row);
        }
        if (messageCards.getChildren().isEmpty()) messageCards.getChildren().add(label(
                "Nessuna comunicazione ricevuta.", "participant-card-copy"));
    }

}
