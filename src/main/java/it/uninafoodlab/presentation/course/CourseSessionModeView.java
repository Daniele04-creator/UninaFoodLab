package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.presentation.common.LabIcons;
import it.uninafoodlab.presentation.common.SessionLocationLinks;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;

final class CourseSessionModeView {
    private CourseSessionModeView() {}

    static Label label(SessionMode mode) {
        var label = new Label();
        label.getStyleClass().add("course-session-mode");
        label.setWrapText(true);
        label.setMinWidth(0);
        update(label, mode);
        return label;
    }

    static void update(Label label, SessionMode mode) {
        label.setText(CoursePresentation.modeLabel(mode));
        label.setGraphic(LabIcons.icon(mode == SessionMode.ONLINE ? "video" : "cooking-pot", "dark", 17));
        label.setGraphicTextGap(6);
    }

    static void configure(ComboBox<SessionMode> combo) {
        combo.setMinWidth(190);
        combo.setPrefWidth(210);
        combo.setCellFactory(ignored -> new ModeCell());
        combo.setButtonCell(new ModeCell());
    }

    static String locationLabel(CourseSessionSummary session) {
        if (session.mode() == SessionMode.ONLINE) return "Lezione online";
        if (SessionLocationLinks.fromVenue(session.venue()).isPresent()) return "Posizione";
        return session.venue().filter(value -> !value.isBlank()).orElse("Sede da definire");
    }

    private static final class ModeCell extends ListCell<SessionMode> {
        private ModeCell() {
            getStyleClass().add("course-session-mode");
            setGraphicTextGap(6);
        }

        @Override
        protected void updateItem(SessionMode mode, boolean empty) {
            super.updateItem(mode, empty);
            setText(empty || mode == null ? null : CoursePresentation.modeLabel(mode));
            setGraphic(empty || mode == null ? null
                    : LabIcons.icon(mode == SessionMode.ONLINE ? "video" : "cooking-pot", "dark", 17));
        }
    }
}
