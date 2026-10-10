package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.application.notification.ParticipantNotification;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class ParticipantWorkspaceCells {

    private ParticipantWorkspaceCells() {}

    static ListCell<PublishedCourseSummary> courseCell() {
        return new ListCell<>() {
            private final VBox card = new VBox(5);
            private final javafx.scene.control.Label title = new javafx.scene.control.Label();
            private final javafx.scene.control.Label meta = new javafx.scene.control.Label();
            private final javafx.scene.control.Label description = new javafx.scene.control.Label();

            {
                card.getStyleClass().add("course-list-item");
                title.getStyleClass().add("course-list-title");
                meta.getStyleClass().add("course-list-meta");
                description.getStyleClass().add("course-list-description");
                description.setWrapText(true);
                card.getChildren().addAll(title, meta, description);
                configureCard(this, card);
            }

            @Override
            protected void updateItem(PublishedCourseSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    title.setText(item.title().value());
                    meta.setText(item.categoryName() + "\n"
                            + item.startDate().format(ParticipantWorkspaceRules.DATE));
                    description.setText(item.description());
                    setGraphic(card);
                }
            }
        };
    }

    static ListCell<EnrollmentSummary> enrollmentCell() {
        return new ListCell<>() {
            private final VBox card = new VBox(5);
            private final javafx.scene.control.Label title = new javafx.scene.control.Label();
            private final javafx.scene.control.Label meta = new javafx.scene.control.Label();

            {
                title.getStyleClass().add("course-list-title");
                meta.getStyleClass().add("course-list-meta");
                card.getChildren().addAll(title, meta);
                configureCard(this, card);
            }

            @Override
            protected void updateItem(EnrollmentSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    title.setText(item.title().value());
                    meta.setText(item.categoryName() + "\n"
                            + ParticipantWorkspaceRules.enrollmentStatus(item.status()));
                    setGraphic(card);
                }
            }
        };
    }

    static ListCell<PublishedCourseSessionSummary> sessionCell(Consumer<String> locationError) {
        return sessionCell(locationError, it.uninafoodlab.presentation.common.SessionLocationLinks::open);
    }

    static ListCell<PublishedCourseSessionSummary> sessionCell(Consumer<String> locationError,
            Function<URI, CompletableFuture<Void>> openLocation) {
        return new ListCell<>() {
            private final VBox card = new VBox(4);
            private final javafx.scene.control.Label title = new javafx.scene.control.Label();
            private final javafx.scene.control.Label meta = new javafx.scene.control.Label();
            private final javafx.scene.control.Label mode = new javafx.scene.control.Label();
            private final javafx.scene.control.Label location = new javafx.scene.control.Label();
            private final javafx.scene.control.Label state = new javafx.scene.control.Label();
            private final Button copyOnlineLink = new Button("Copia link");
            private final ParticipantSessionLocation locationAction =
                    new ParticipantSessionLocation(this::getItem, locationError, openLocation);

            {
                title.getStyleClass().add("course-list-title");
                meta.getStyleClass().add("course-list-meta");
                mode.getStyleClass().add("participant-mode");
                location.getStyleClass().add("course-list-meta");
                state.getStyleClass().add("participant-session-state");
                state.setMaxWidth(Region.USE_PREF_SIZE);
                var heading = new HBox(12, title, state);
                heading.setAlignment(Pos.CENTER_LEFT);
                title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE); title.setWrapText(true);
                HBox.setHgrow(title, Priority.ALWAYS);
                copyOnlineLink.getStyleClass().add("button-secondary");
                copyOnlineLink.setAccessibleText("Copia il collegamento della sessione online");
                copyOnlineLink.setOnAction(event -> {
                    var session = getItem();
                    if (session != null) {
                        ParticipantWorkspaceRules.authorizedOnlineLink(session)
                                .ifPresent(ParticipantWorkspaceCells::copyToClipboard);
                    }
                });
                card.getChildren().addAll(heading, meta, mode, location, locationAction.button(), copyOnlineLink);
                configureCard(this, card);
            }

            @Override
            protected void updateItem(PublishedCourseSessionSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    title.setText("Sessione " + item.sequenceNumber());
                    meta.setText(ParticipantWorkspaceRules.DATE_TIME.format(item.scheduledAt()));
                    meta.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon("calendar", "dark", 16));
                    mode.setText(ParticipantWorkspaceRules.formatMode(item.mode()));
                    mode.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon(
                            item.mode() == it.uninafoodlab.domain.course.SessionMode.ONLINE ? "video" : "cooking-pot", "dark", 16));
                    boolean practical = item.mode() == it.uninafoodlab.domain.course.SessionMode.PRACTICAL;
                    location.setText(ParticipantSessionLocation.label(item));
                    location.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon("map-pin", "dark", 16));
                    location.setVisible(practical); location.setManaged(practical);
                    locationAction.update(item);
                    updateSessionState(state, item.status());
                    var linkAvailable = ParticipantWorkspaceRules.authorizedOnlineLink(item).isPresent();
                    copyOnlineLink.setVisible(linkAvailable);
                    copyOnlineLink.setManaged(linkAvailable);
                    setGraphic(card);
                }
            }
        };
    }

    private static void updateSessionState(Label state, SessionStatus status) {
        state.setText(switch (status) {
            case SCHEDULED -> "Programmata";
            case COMPLETED -> "Svolta";
            case CANCELLED -> "Annullata";
        });
        state.getStyleClass().removeAll("participant-session-completed", "participant-session-cancelled");
        if (status == SessionStatus.COMPLETED) state.getStyleClass().add("participant-session-completed");
        if (status == SessionStatus.CANCELLED) state.getStyleClass().add("participant-session-cancelled");
    }

    private static void configureCard(ListCell<?> cell, VBox card) {
        cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setMinWidth(0);
        card.prefWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(0, cell.getWidth() - cell.getInsets().getLeft() - cell.getInsets().getRight()),
                cell.widthProperty(), cell.insetsProperty()));
        card.maxWidthProperty().bind(card.prefWidthProperty());
        for (var node : card.getChildren()) {
            if (node instanceof javafx.scene.control.Label label) {
                label.setWrapText(true);
                label.setMinWidth(0);
                label.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }

    private static void copyToClipboard(String link) {
        var content = new ClipboardContent();
        content.putString(link);
        Clipboard.getSystemClipboard().setContent(content);
    }

    static ListCell<PublishedSessionRecipeSummary> menuCell(
            Function<UUID, String> sessionSequence) {
        return new ListCell<>() {
            private final VBox card = new VBox(4);
            private final javafx.scene.control.Label title = new javafx.scene.control.Label();
            private final javafx.scene.control.Label description = new javafx.scene.control.Label();
            private final Button details = new Button("Consulta ricetta e ingredienti →");

            {
                title.getStyleClass().add("course-list-title");
                description.getStyleClass().add("course-list-description");
                description.setWrapText(true);
                details.getStyleClass().add("button-quiet");
                details.setOnAction(event -> {
                    if (getItem() != null) ParticipantRecipeDialog.show(getScene().getWindow(), getItem());
                });
                card.getChildren().addAll(title, description, details);
                configureCard(this, card);
            }

            @Override
            protected void updateItem(PublishedSessionRecipeSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    title.setText(item.name() + ", sessione " + sessionSequence.apply(item.sessionId()));
                    description.setText(item.description().isBlank()
                            ? "Ricetta pronta per il laboratorio." : item.description());
                    setGraphic(card);
                }
            }
        };
    }

    static ListCell<PracticalAttendanceSummary> attendanceCell(
            BiConsumer<PracticalAttendanceSummary, AttendanceStatus> changeAttendance,
            BooleanSupplier busy,
            Clock clock) {
        return new ListCell<>() {
            private final VBox card = new VBox(8);
            private final HBox header = new HBox(10);
            private final VBox copy = new VBox(4);
            private final javafx.scene.control.Label title = new javafx.scene.control.Label();
            private final javafx.scene.control.Label meta = new javafx.scene.control.Label();
            private final javafx.scene.control.Label status = new javafx.scene.control.Label();
            private final javafx.scene.control.Label mode = new javafx.scene.control.Label();
            private final Button confirm = new Button("Confermo");
            private final Button decline = new Button("Non partecipo");

            {
                header.setAlignment(Pos.TOP_LEFT);
                mode.getStyleClass().add("participant-mode");
                mode.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon("cooking-pot", "dark", 16));
                copy.getChildren().addAll(title, mode, meta, status);
                copy.setMinWidth(0);
                HBox.setHgrow(copy, Priority.ALWAYS);
                title.setWrapText(true);
                meta.setWrapText(true);
                status.setWrapText(true);
                var spacer = new Region();
                header.getChildren().addAll(copy, spacer);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                var actions = new HBox(8, decline, confirm);
                actions.setAlignment(Pos.CENTER_RIGHT);
                card.getChildren().addAll(header, actions);
                title.getStyleClass().add("course-list-title");
                meta.getStyleClass().add("course-list-meta");
                status.getStyleClass().add("form-helper");
                confirm.getStyleClass().add("button-primary");
                decline.getStyleClass().add("button-secondary");
                configureCard(this, card);
                confirm.setOnAction(event -> changeAttendance.accept(getItem(), AttendanceStatus.CONFIRMED));
                decline.setOnAction(event -> changeAttendance.accept(getItem(), AttendanceStatus.DECLINED));
            }

            @Override
            protected void updateItem(PracticalAttendanceSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                title.setText("Sessione " + item.sequenceNumber() + "\n"
                        + ParticipantWorkspaceRules.DATE_TIME.format(item.scheduledAt()));
                mode.setText("In presenza, " + item.venue());
                meta.setText(item.confirmedCount() + "/" + item.capacity()
                        + " posti occupati\nAdesioni fino al "
                        + ParticipantWorkspaceRules.DATE_TIME.format(item.attendanceClosesAt()));
                status.setText(ParticipantWorkspaceRules.attendanceStatus(item));
                var now = LocalDateTime.now(clock);
                var canConfirm = ParticipantWorkspaceRules.canConfirmAttendance(item, now);
                var canDecline = ParticipantWorkspaceRules.canDeclineAttendance(item, now);
                var confirmed = item.attendanceStatus().orElse(null) == AttendanceStatus.CONFIRMED;
                var declined = item.attendanceStatus().orElse(null) == AttendanceStatus.DECLINED;
                confirm.setDisable(!canConfirm || confirmed || busy.getAsBoolean());
                decline.setDisable(!canDecline || declined || busy.getAsBoolean());
                if (!item.programReady()) {
                    status.setText("Menu in preparazione: attendi la pubblicazione delle ricette.");
                    confirm.setDisable(true);
                    decline.setDisable(true);
                }
                setGraphic(card);
            }
        };
    }

    static ListCell<ParticipantNotification> notificationCell() {
        return new ListCell<>() {
            private final VBox card = new VBox(5);
            private final javafx.scene.control.Label title = new javafx.scene.control.Label();
            private final javafx.scene.control.Label meta = new javafx.scene.control.Label();
            private final javafx.scene.control.Label preview = new javafx.scene.control.Label();

            {
                title.getStyleClass().add("notification-list-title");
                meta.getStyleClass().add("notification-list-meta");
                preview.getStyleClass().add("notification-preview");
                preview.setWrapText(true);
                card.getChildren().addAll(title, meta, preview);
                configureCard(this, card);
            }

            @Override
            protected void updateItem(ParticipantNotification item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                title.setText((item.readAt().isEmpty() ? "Da leggere: " : "") + item.title());
                meta.setText(item.courseTitle().orElse("Tutti i corsi") + "\n"
                        + ParticipantWorkspaceRules.DATE_TIME.format(item.createdAt()
                        .atZone(ParticipantWorkspaceRules.ROME)));
                preview.setText(item.message());
                setGraphic(card);
            }
        };
    }
}
