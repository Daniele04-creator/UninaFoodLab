package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import it.uninafoodlab.presentation.FxSnapshot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;


public final class ParticipantCourseCardUiSmoke {
    private ParticipantCourseCardUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var task = new FutureTask<Void>(() -> { verifyCards(); return null; });
            Platform.runLater(task);
            task.get();
            System.out.println("PASS: participant card states, next appointment, value order and separator-free dates");
        } finally {
            Platform.exit();
        }
    }

    private static void verifyCards() throws java.io.IOException {
        var now = LocalDateTime.of(2026, 10, 1, 12, 0);
        var course = course(now, CourseStatus.PUBLISHED);
        var session = new PublishedCourseSessionSummary(UUID.randomUUID(), 3, now.plusDays(1),
                SessionMode.PRACTICAL, 90, Optional.empty(), Optional.of("Laboratorio di cucina"),
                Optional.of(20), Optional.of(now.plusDays(1).minusMinutes(1)), SessionStatus.SCHEDULED);
        var enrollment = enrollment(course, EnrollmentStatus.ACTIVE);
        var available = ParticipantCourseCards.available(course, List.of(enrollment), now, () -> {});
        var active = ParticipantCourseCards.enrollment(enrollment, Optional.of(course), List.of(session), now, () -> {});
        var futureCourse = course(now.plusDays(1), CourseStatus.PUBLISHED);
        var planned = ParticipantCourseCards.available(futureCourse, List.of(), now, () -> {});
        var online = new PublishedCourseSessionSummary(UUID.randomUUID(), 3, session.scheduledAt(),
                SessionMode.ONLINE, 90, Optional.of(java.net.URI.create("https://example.test/lesson")), Optional.empty(),
                Optional.empty(), Optional.empty(), SessionStatus.SCHEDULED);
        var plannedEnrollment = ParticipantCourseCards.enrollment(enrollment(futureCourse, EnrollmentStatus.ACTIVE),
                Optional.of(futureCourse), List.of(online), now, () -> {});
        var ended = course(now, CourseStatus.COMPLETED);
        var cancelled = course(now, CourseStatus.CANCELLED);
        var past = ParticipantCourseCards.enrollment(enrollment(ended, EnrollmentStatus.ACTIVE),
                Optional.of(ended), List.of(session), now, () -> {});
        var cancelledCard = ParticipantCourseCards.enrollment(enrollment(cancelled, EnrollmentStatus.ACTIVE),
                Optional.of(cancelled), List.of(session), now, () -> {});
        var withdrawn = ParticipantCourseCards.enrollment(enrollment(course, EnrollmentStatus.WITHDRAWN),
                Optional.of(course), List.of(session), now, () -> {});
        var withdrawnAvailable = ParticipantCourseCards.available(course,
                List.of(enrollment(course, EnrollmentStatus.WITHDRAWN)), now, () -> {});
        var grid = new FlowPane(20, 20, available, active, planned, past, cancelledCard, withdrawn, withdrawnAvailable, plannedEnrollment);
        grid.setPrefWrapLength(1100);
        for (var card : List.of(available, active, planned, past, cancelledCard, withdrawn, withdrawnAvailable, plannedEnrollment)) card.setPrefWidth(350);
        var root = new VBox(grid); root.getStyleClass().add("participant-workspace");
        var scene = new Scene(root, 1150, 1000);
        scene.getStylesheets().add(ParticipantCourseCardUiSmoke.class.getResource(
                "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
        scene.getStylesheets().add(ParticipantCourseCardUiSmoke.class.getResource("participant.css").toExternalForm());
        var stage = new Stage(); stage.setScene(scene); stage.show(); root.applyCss(); root.layout();
        try {
            state(available, "In corso"); state(active, "In corso"); state(past, "Concluso");
            state(cancelledCard, "Annullato"); state(withdrawn, "In corso");
            state(planned, "In programma"); state(plannedEnrollment, "In programma");
            state(ParticipantCourseCards.available(futureCourse, List.of(), now.plusDays(1).minusNanos(1), () -> {}),
                    "In programma");
            state(ParticipantCourseCards.available(futureCourse, List.of(), now.plusDays(1), () -> {}), "In corso");
            var activeColor = stateColor(active);
            var plannedColor = stateColor(planned);
            var pastColor = stateColor(past);
            check(activeColor.getGreen() > activeColor.getRed() && activeColor.getGreen() > activeColor.getBlue(),
                    "An active course uses a recognizable green state");
            check(plannedColor.getBlue() > plannedColor.getGreen() && plannedColor.getBlue() > plannedColor.getRed(),
                    "A planned course uses a recognizable blue state");
            check(!pastColor.equals(activeColor) && !pastColor.equals(plannedColor),
                    "A concluded course has a distinct visible state");
            check(withdrawn.lookupAll(".label").stream().map(Label.class::cast)
                            .anyMatch(value -> "Ritirato".equals(value.getText())
                                    && !value.getStyleClass().contains("participant-enrollment-state")),
                    "A withdrawal is shown separately from the still-active course state");
            state(withdrawnAvailable, "In corso");
            check(withdrawnAvailable.lookupAll(".label").stream().map(Label.class::cast)
                            .anyMatch(value -> "Ritirato".equals(value.getText())
                                    && !value.getStyleClass().contains("participant-enrollment-state")),
                    "The available catalog retains a withdrawn enrollment separately from the course state");
            for (var card : List.of(past, cancelledCard, withdrawn)) {
                check(card.lookup(".participant-appointment") == null,
                        "Past, cancelled and withdrawn courses do not advertise a next session");
            }
            var band = (VBox) active.lookup(".participant-appointment");
            check(band != null && (band.getBackground() == null || band.getBackground().getFills().stream()
                            .allMatch(fill -> ((Color) fill.getFill()).getOpacity() == 0)),
                    "The upcoming appointment is displayed directly on the course without a colored surface");
            check("Prossima sessione".equals(((Label) band.getChildren().getFirst()).getText())
                            && band.getChildren().getLast() instanceof FlowPane
                            && band.lookupAll(".label").stream().map(Label.class::cast)
                                    .noneMatch(value -> value.getText().startsWith("Sessione ")),
                    "The appointment orders its heading, date and mode without adding a session title");
            var date = label(band, ".participant-appointment-date");
            var mode = label(band, ".participant-mode");
            check(date.getParent() == mode.getParent() && date.getParent() instanceof FlowPane,
                    "Session mode belongs to its next appointment date");
            var dateBounds = date.localToScene(date.getBoundsInLocal());
            var modeBounds = mode.localToScene(mode.getBoundsInLocal());
            if (Math.abs(dateBounds.getMinY() - modeBounds.getMinY()) < 10) {
                check(modeBounds.getMinX() - dateBounds.getMaxX() <= 16,
                        "Session mode sits beside its date rather than at the card edge");
            }
            check(date.getText().contains("alle 12:00") && date.getGraphic() != null,
                    "The next appointment exposes a readable date and time with its calendar icon");
            check(date.getFont().getStyle().toLowerCase(java.util.Locale.ROOT).contains("bold"),
                    "The next appointment date uses a clear bold emphasis");
            var facts = (FlowPane) available.lookup(".participant-course-facts");
            check(facts != null && ((Label) facts.getChildren().getFirst()).getText().startsWith("Dal ")
                            && ((Label) facts.getChildren().get(1)).getText().equals("4 sessioni")
                            && ((Label) facts.getChildren().getLast()).getText().equals("Marta Rossi"),
                    "Available cards order the start date, session count and chef as in the mockup");
            check(root.lookupAll(".label").stream().map(Label.class::cast)
                            .noneMatch(value -> value.getText().contains("·") || value.getText().contains("•")),
                    "Course cards expose no decorative dot separators");
            FxSnapshot.save(root, "target/ui-polish/participant-card-states.png");
        } finally {
            stage.close();
        }
    }

    private static PublishedCourseSummary course(LocalDateTime now, CourseStatus status) {
        return new PublishedCourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Pasta fresca", "Marta Rossi",
                new CourseTitle("Pasta fresca di stagione"), "Un programma dedicato alla pasta fresca.",
                now.toLocalDate(), now.toLocalTime(), 4, now.plusDays(21), status);
    }

    private static EnrollmentSummary enrollment(PublishedCourseSummary course, EnrollmentStatus status) {
        return new EnrollmentSummary(course.id(), course.title(), course.categoryName(), course.chefName(),
                course.startDate(), status, Instant.parse("2026-10-01T09:00:00Z"));
    }

    private static void state(VBox card, String expected) {
        var state = label(card, ".participant-enrollment-state");
        check(expected.equals(state.getText()) && !state.isFocusTraversable(),
                "The course state is informative and reads " + expected);
    }

    private static Label label(javafx.scene.Parent root, String selector) {
        var value = (Label) root.lookup(selector);
        check(value != null, "Expected visible course information " + selector);
        return value;
    }

    private static Color stateColor(VBox card) {
        var badge = label(card, ".participant-enrollment-state");
        var fill = (Color) badge.getBackground().getFills().getFirst().getFill();
        var text = (Color) badge.getTextFill();
        var first = luminance(fill);
        var second = luminance(text);
        check(fill.getSaturation() >= 0.45 && (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05) >= 4.5,
                "Course state " + badge.getText() + " is saturated and its text has contrast of at least 4.5:1");
        return fill;
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
