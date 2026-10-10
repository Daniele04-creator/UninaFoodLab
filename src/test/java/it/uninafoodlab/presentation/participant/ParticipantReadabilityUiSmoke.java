package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.notification.ParticipantNotification;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.application.notification.NotificationReason;
import it.uninafoodlab.presentation.FxSnapshot;
import java.time.*;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;


public final class ParticipantReadabilityUiSmoke {
    private static final String OUTPUT = "target/native-match/participant-readability/";
    private ParticipantReadabilityUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            if (args.length == 0 || args[0].equals("messages")) fx(() -> { verifyMessages(); return null; });
            if (args.length == 0 || args[0].equals("timeline")) {
                verifyTimeline();
                fx(() -> { verifySessionCellStates(); return null; });
            }
            System.out.println("PASS: readable participant communication actions and next-session scroll/focus.");
        } finally { Platform.exit(); }
    }

    private static void verifyMessages() throws Exception {
        var busy = new SimpleBooleanProperty(false);
        var opened = new AtomicReference<ParticipantNotification>();
        var notice = new ParticipantNotification(UUID.randomUUID(),
                "Spostamento del laboratorio di pasta fresca e verdure di stagione",
                "La sessione inizierà alle 18:30 nel laboratorio didattico. "
                        + "Porta il grembiule e consulta il programma aggiornato per prepararti agli impasti e alle cotture.",
                NotificationReason.GENERAL, Optional.of("Pasta fresca con verdure di stagione"),
                Instant.parse("2026-09-30T16:30:00Z"), Optional.empty(), Optional.of(UUID.randomUUID()));
        for (int width : List.of(1142, 1000)) {
            var cards = new VBox(); cards.setId("notificationCards");
            ParticipantMessageRows.render(cards, List.of(notice), busy, opened::set);
            var heading = new Label("Comunicazioni"); heading.getStyleClass().add("page-title");
            var root = new VBox(20, heading, cards); root.setPadding(new Insets(26));
            root.setStyle("-fx-padding:26;");
            var stage = stage(root, width, 500);
            try {
                var row = (HBox) cards.getChildren().getFirst();
                var copy = (VBox) row.getChildren().getFirst();
                var actions = (VBox) row.getChildren().getLast();
                var open = (Button) actions.getChildren().getLast();
                var date = (Label) actions.getChildren().getFirst();
                var preview = (Label) copy.getChildren().getLast();
                check(open.getWidth() + 1 >= open.prefWidth(-1), "Entire communication action fits at " + width);
                check(date.getWidth() + 1 >= date.prefWidth(-1), "Date is complete without month fragmentation at " + width);
                check(preview.isWrapText() && preview.getWidth() <= copy.getWidth() + 1,
                        "Preview wraps within the allocated text column");
                check(open.localToScene(open.getBoundsInLocal()).getMaxX() <= stage.getScene().getWidth(),
                        "Action stays within the viewport");
                busy.set(true); open.fire(); check(open.isDisabled() && opened.get() == null, "Busy prevents opening");
                busy.set(false); open.fire(); check(opened.get() == notice, "Correct communication opens");
                open.requestFocus(); check(ParticipantDetailNavigation.focusAction(root, notice.id()) && open.isFocused(),
                        "Communication identity preserves return focus");
                FxSnapshot.save(root, OUTPUT + "messages-" + width + ".png");
                opened.set(null);
            } finally { stage.close(); }
        }
    }

    private static void verifyTimeline() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
        var nowInRome = LocalDateTime.of(2026, 10, 1, 12, 0);
        var sessions = new ArrayList<PublishedCourseSessionSummary>();
        for (int i = 1; i <= 8; i++) sessions.add(session(i, nowInRome.minusDays(9 - i), SessionStatus.COMPLETED));
        sessions.add(session(9, nowInRome.minusMinutes(10), SessionStatus.SCHEDULED));
        sessions.add(session(10, nowInRome, SessionStatus.SCHEDULED));
        sessions.add(session(11, nowInRome.plusMinutes(1), SessionStatus.CANCELLED));
        var next = session(12, nowInRome.plusMinutes(2), SessionStatus.SCHEDULED);
        sessions.add(next);
        sessions.add(session(13, nowInRome.plusDays(1), SessionStatus.SCHEDULED));
        Collections.reverse(sessions);
        var timeline = new VBox();
        var scroll = new ScrollPane(timeline); scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        var stage = fx(() -> {
            ParticipantSessionTimeline.render(timeline, sessions, List.of(), List.of(), false, clock,
                    (attendance, status) -> { throw new AssertionError("Jump must not write attendance"); },
                    error -> { throw new AssertionError(error); });
            return stage(new VBox(scroll), 1000, 500);
        });
        try {
            fx(() -> {
                var jump = jump(timeline);
                check(jump != null, "Future chronological session exposes the jump command");
                check(next.id().equals(jump.getUserData()), "Jump chooses earliest future non-cancelled session in Rome");
                var rows = timeline.getChildren().stream().filter(node -> node.getStyleClass().contains("participant-session-row")).toList();
                check(rows.size() == sessions.size() && rows.getFirst().getUserData().equals(sessions.getLast().id()),
                        "Historical rows and chronological order stay intact");
                for (var row : rows) verifySessionState((Label) row.lookup(".participant-session-state"));
                FxSnapshot.save(stage.getScene().getRoot(), OUTPUT + "timeline-before-jump.png");
                jump.fire();
                return null;
            });
            fx(() -> {
                stage.getScene().getRoot().layout();
                var row = timeline.getChildren().stream().filter(node -> next.id().equals(node.getUserData())
                        && node instanceof VBox).findFirst().orElseThrow();
                var heading = row.lookup(".course-list-title");
                check(heading.isFocused(), "Jump transfers keyboard focus to the same session heading");
                var bounds = scroll.sceneToLocal(heading.localToScene(heading.getBoundsInLocal()));
                check(scroll.getVvalue() > 0 && bounds.getMinY() >= 0 && bounds.getMaxY() < scroll.getHeight(),
                        "Jump scrolls its heading into the visible viewport");
                FxSnapshot.save(stage.getScene().getRoot(), OUTPUT + "timeline-after-jump.png");
                for (var subset : List.of(List.<PublishedCourseSessionSummary>of(),
                        List.of(sessions.getLast()), List.of(session(1, nowInRome.plusDays(1), SessionStatus.CANCELLED)),
                        List.of(session(1, nowInRome, SessionStatus.SCHEDULED)))) {
                    ParticipantSessionTimeline.render(timeline, subset, List.of(), List.of(), false, clock,
                            (attendance, status) -> {}, error -> {});
                    check(jump(timeline) == null, "No jump without future non-cancelled session");
                }
                return null;
            });
        } finally { fx(() -> { stage.close(); return null; }); }
    }

    private static Button jump(VBox timeline) {
        return timeline.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "Vai alla prossima sessione".equals(button.getText())).findFirst().orElse(null);
    }

    private static void verifySessionCellStates() throws Exception {
        var list = new ListView<PublishedCourseSessionSummary>();
        list.setCellFactory(ignored -> ParticipantWorkspaceCells.sessionCell(error -> {
            throw new AssertionError(error);
        }));
        var stage = stage(list, 700, 240);
        try {
            for (var status : List.of(SessionStatus.CANCELLED, SessionStatus.COMPLETED, SessionStatus.SCHEDULED)) {
                list.getItems().setAll(session(1, LocalDateTime.of(2026, 10, 1, 12, 0), status));
                list.applyCss(); list.layout();
                var badge = (Label) list.lookup(".participant-session-state");
                check(badge != null, "Session list renders its status");
                verifySessionState(badge);
            }
        } finally { stage.close(); }
    }

    private static void verifySessionState(Label badge) {
        var background = badge.getBackground().getFills().getFirst().getFill();
        var completed = "Svolta".equals(badge.getText());
        check(badge.getStyleClass().contains("participant-session-completed") == completed,
                "Completion style follows the current status, including recycled cells");
        if (completed) {
            check(javafx.scene.paint.Color.web("#6650A4").equals(background)
                            && javafx.scene.paint.Color.WHITE.equals(badge.getTextFill()),
                    "Completed sessions use the vivid purple badge with readable white text");
        } else if ("Annullata".equals(badge.getText())) {
            check(javafx.scene.paint.Color.web("#F9EEEA").equals(background)
                            && javafx.scene.paint.Color.web("#A33421").equals(badge.getTextFill()),
                    "Cancelled sessions retain their distinct red style");
        } else {
            check(javafx.scene.paint.Color.web("#EDF0E8").equals(background)
                            && javafx.scene.paint.Color.web("#424C3B").equals(badge.getTextFill()),
                    "Scheduled sessions retain their neutral style");
        }
    }

    private static PublishedCourseSessionSummary session(int number, LocalDateTime date, SessionStatus status) {
        return new PublishedCourseSessionSummary(UUID.randomUUID(), number, date, SessionMode.ONLINE, 60,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), status);
    }

    private static Stage stage(javafx.scene.Parent root, int width, int height) {
        root.getStyleClass().add("participant-workspace");
        var scene = new Scene(root, width, height);
        scene.getStylesheets().add(ParticipantReadabilityUiSmoke.class.getResource(
                "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
        scene.getStylesheets().add(ParticipantReadabilityUiSmoke.class.getResource("participant.css").toExternalForm());
        var stage = new Stage(); stage.setScene(scene); stage.show(); root.applyCss(); root.layout();
        return stage;
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action); Platform.runLater(task); return task.get();
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
