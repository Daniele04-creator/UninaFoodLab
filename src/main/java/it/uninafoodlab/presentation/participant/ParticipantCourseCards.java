package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javafx.scene.control.*;
import javafx.scene.layout.*;


final class ParticipantCourseCards {
    private ParticipantCourseCards() {}

    static VBox available(PublishedCourseSummary item, List<EnrollmentSummary> enrollments,
            LocalDateTime now, Runnable open) {
        String state = courseState(item, now);
        var card = card(item.categoryName(), item.title().value(), state,
                List.of(fact("Dal " + item.startDate().format(ParticipantWorkspaceRules.DATE), "calendar"),
                        fact(item.sessionCount() + (item.sessionCount() == 1 ? " sessione" : " sessioni"), "book-open"),
                        fact(item.chefName(), "user")), "Apri corso →", item.id(), open);
        if (ParticipantWorkspaceRules.isEnrolled(enrollments, item.id())) {
            card.getChildren().add(3, label("Già iscritto", "participant-card-meta"));
        } else if (enrollments.stream().anyMatch(enrollment -> enrollment.courseId().equals(item.id())
                && enrollment.status() == EnrollmentStatus.WITHDRAWN)) {
            card.getChildren().add(3, label("Ritirato", "participant-card-meta"));
        }
        return card;
    }

    static VBox enrollment(EnrollmentSummary item, Optional<PublishedCourseSummary> summary,
            List<PublishedCourseSessionSummary> sessions, LocalDateTime now, Runnable open) {
        var card = card(item.categoryName(), item.title().value(), enrollmentState(summary, now),
                List.of(fact("Dal " + item.startDate().format(ParticipantWorkspaceRules.DATE), "calendar"),
                        fact(item.chefName(), "user")), "Apri corso →", item.courseId(), open);
        if (item.status() == EnrollmentStatus.WITHDRAWN) {
            card.getChildren().add(3, label("Ritirato", "participant-card-meta"));
        }
        if (item.status() == EnrollmentStatus.ACTIVE && summary.filter(course -> !course.hasEnded(now)).isPresent()) {
            ParticipantWorkspaceRules.nextAppointment(sessions, now)
                    .ifPresent(session -> card.getChildren().add(card.getChildren().size() - 2, appointment(session)));
        }
        return card;
    }

    private static String enrollmentState(Optional<PublishedCourseSummary> summary, LocalDateTime now) {
        return summary.map(course -> courseState(course, now)).orElse("In corso");
    }

    private static String courseState(PublishedCourseSummary course, LocalDateTime now) {
        if (course.status() == CourseStatus.CANCELLED) return "Annullato";
        if (course.hasEnded(now)) return "Concluso";
        return LocalDateTime.of(course.startDate(), course.startTime()).isAfter(now)
                ? "In programma" : "In corso";
    }

    private static VBox appointment(PublishedCourseSessionSummary session) {
        var band = new VBox(6); band.getStyleClass().add("participant-appointment");
        var mode = label(ParticipantWorkspaceRules.formatMode(session.mode()), "participant-mode");
        mode.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon(
                session.mode() == it.uninafoodlab.domain.course.SessionMode.ONLINE ? "video" : "cooking-pot", "dark", 16));
        var date = label(ParticipantWorkspaceRules.DATE_TIME.format(session.scheduledAt()), "participant-appointment-date");
        date.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon("calendar", "dark", 16));
        date.setMaxWidth(Region.USE_PREF_SIZE);
        mode.setMaxWidth(Region.USE_PREF_SIZE);
        var facts = new FlowPane(12, 6, date, mode);
        facts.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        band.getChildren().addAll(label("Prossima sessione", "participant-appointment-label"), facts);
        return band;
    }

    static VBox card(String category, String title, String supportingText,
            List<Label> metadata, String action, UUID id, Runnable open) {
        var card = new VBox(14); card.getStyleClass().add("participant-course-card");
        var button = new Button(action); button.setUserData(id); button.setMinHeight(44);
        button.getStyleClass().add("button-quiet"); button.setOnAction(event -> open.run());
        button.setAccessibleText(action + " " + title);
        var spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        var titleLabel = label(title, "participant-card-title"); titleLabel.setMinHeight(Region.USE_PREF_SIZE);
        titleLabel.setWrapText(false);
        titleLabel.setMinWidth(0);
        titleLabel.setTooltip(new Tooltip(title));
        var top = heading(category, supportingText);
        var facts = new FlowPane(14, 8); facts.getStyleClass().add("participant-course-facts");
        facts.getChildren().addAll(metadata);
        var actions = new HBox(button); actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        card.getChildren().addAll(top, titleLabel, facts, spacer, actions);
        return card;
    }
    private static HBox heading(String category, String supportingText) {
        var top = new HBox(12); top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var categoryLabel = label(category, "participant-category"); HBox.setHgrow(categoryLabel, Priority.ALWAYS);
        categoryLabel.setMaxWidth(Double.MAX_VALUE); top.getChildren().add(categoryLabel);
        if (supportingText != null) {
            var state = label(supportingText, "participant-enrollment-state"); state.setMaxWidth(Region.USE_PREF_SIZE);
            if (supportingText.equals("In corso")) state.getStyleClass().add("participant-enrollment-active");
            if (supportingText.equals("In programma")) state.getStyleClass().add("participant-enrollment-planned");
            if (supportingText.equals("Concluso")) state.getStyleClass().add("participant-enrollment-concluded");
            if (supportingText.equals("Annullato")) state.getStyleClass().add("participant-enrollment-withdrawn");
            top.getChildren().add(state);
        }
        return top;
    }

    static Label fact(String text, String icon) {
        var fact = label(text, "participant-card-meta");
        fact.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon(icon, "dark", 16));
        return fact;
    }
    static Button action(VBox card) { return (Button) ((HBox) card.getChildren().getLast()).getChildren().getLast(); }
    static void size(VBox card, FlowPane grid, int visibleCount) {
        card.prefWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            double width = grid.getWidth();
            int widthColumns = width >= 1050 ? 3 : width >= 650 ? 2 : 1;
            int columns = Math.max(1, Math.min(Math.max(1, visibleCount), widthColumns));
            return Math.max(260, (width - (columns - 1) * grid.getHgap() - (columns > 1 ? columns : 0)) / columns);
        }, grid.widthProperty()));
    }
    static Label label(String text, String style) {
        var label = new Label(text); label.setWrapText(true);
        label.setMaxWidth(style.equals("participant-category") ? Region.USE_PREF_SIZE : Double.MAX_VALUE);
        label.getStyleClass().add(style); return label;
    }
}
