package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.domain.course.SessionMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

final class CourseScheduleEditor {
    private static final String DEFAULT_VENUE = "Laboratorio UninaFoodLab";

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private final Clock clock;
    private final Supplier<CourseSummary> selectedCourse;
    private final Supplier<CourseSessionSummary> selectedSession;
    DatePicker scheduleDateField;
    TimeSelector scheduleTimeField;
    ComboBox<SessionMode> scheduleModeField;
    Spinner<Integer> scheduleDurationField;
    TextField scheduleLocationField;
    Spinner<Integer> scheduleCapacityField;
    DatePicker scheduleAttendanceDateField;
    TimeSelector scheduleAttendanceTimeField;
    GridPane scheduleAttendanceClosesAtField;
    TextField scheduleNotificationTitleField;
    TextArea scheduleNotificationMessageField;
    private boolean suggestedAttendanceCutoff = true;
    private boolean updatingAttendanceCutoff;
    private String scheduleOnlineDraft = "";
    private String scheduleVenueDraft = DEFAULT_VENUE;
    LocalDateTime rescheduleOriginalDate;
    String suggestedScheduleMessage;
    Node scheduleDialogContent;

    CourseScheduleEditor(Clock clock, Supplier<CourseSummary> selectedCourse,
            Supplier<CourseSessionSummary> selectedSession) {
        this.clock = clock;
        this.selectedCourse = selectedCourse;
        this.selectedSession = selectedSession;
        configureScheduleEditor();
    }

    Node createScheduleDialogContent(boolean reschedule) {
        return new CourseScheduleForm(this).create(reschedule, selectedSession.get());
    }

    void configureScheduleEditor() {
        scheduleDateField = new DatePicker();
        scheduleTimeField = new TimeSelector();
        scheduleModeField = new ComboBox<>();
        scheduleDurationField = new Spinner<>();
        scheduleLocationField = new TextField();
        scheduleCapacityField = new Spinner<>();
        configureAttendanceFields();
        configureSessionFields();
        configureScheduleListeners();
        scheduleDialogContent = createScheduleDialogContent(false);
    }

    private void configureAttendanceFields() {
        scheduleAttendanceDateField = new DatePicker();
        scheduleAttendanceDateField.setEditable(false);
        scheduleAttendanceDateField.setShowWeekNumbers(false);
        scheduleAttendanceDateField.setMaxWidth(Double.MAX_VALUE);
        scheduleAttendanceDateField.setDayCellFactory(ignored -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                var sessionDate = scheduleDateField.getValue();
                setDisable(empty || date == null || (sessionDate != null && date.isAfter(sessionDate)));
            }
        });
        scheduleAttendanceTimeField = new TimeSelector();
        scheduleAttendanceDateField.setAccessibleText("Data di chiusura delle adesioni");
        scheduleAttendanceTimeField.setAccessibleText("Ora di chiusura delle adesioni");
        scheduleAttendanceClosesAtField = new javafx.scene.layout.GridPane();
        scheduleAttendanceClosesAtField.setHgap(20);
        for (int column = 0; column < 2; column++) {
            var constraint = new javafx.scene.layout.ColumnConstraints();
            constraint.setPercentWidth(50);
            constraint.setHgrow(Priority.ALWAYS);
            scheduleAttendanceClosesAtField.getColumnConstraints().add(constraint);
        }
        scheduleAttendanceDateField.setMaxWidth(Double.MAX_VALUE);
        scheduleAttendanceClosesAtField.add(scheduleAttendanceDateField, 0, 0);
        scheduleAttendanceClosesAtField.add(scheduleAttendanceTimeField, 1, 0);
    }

    private void configureSessionFields() {
        scheduleNotificationTitleField = new TextField();
        scheduleNotificationMessageField = new TextArea();
        it.uninafoodlab.presentation.common.TextAreaNavigation.install(scheduleNotificationMessageField);
        scheduleNotificationMessageField.setWrapText(true);
        scheduleNotificationMessageField.setPrefRowCount(3);
        scheduleNotificationMessageField.setPrefHeight(86);
        configureDatePicker(scheduleDateField);
        configureScheduleFieldAccessibility();
        for (var field : List.of(scheduleDateField, scheduleDurationField,
                scheduleLocationField, scheduleCapacityField, scheduleAttendanceDateField,
                scheduleNotificationTitleField, scheduleNotificationMessageField)) {
            field.getStyleClass().add("course-field");
        }
        scheduleModeField.getStyleClass().add("course-combo");
        resetScheduleEditorForNewSession();
        scheduleTimeField.setValue(LocalTime.of(18, 30));
        scheduleModeField.getItems().setAll(SessionMode.values());
        scheduleModeField.setConverter(CoursePresentation.modeConverter());
        CourseSessionModeView.configure(scheduleModeField);
        scheduleModeField.setValue(SessionMode.PRACTICAL);
        scheduleDurationField.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 720, 120, 15));
        scheduleCapacityField.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 500, 12));
        NumericSpinner.configure(scheduleDurationField);
        NumericSpinner.configure(scheduleCapacityField);
        scheduleLocationField.setText(DEFAULT_VENUE);
        refreshSuggestedAttendanceCutoff();
        scheduleNotificationTitleField.setText("Calendario aggiornato");
        scheduleNotificationMessageField.setText("Consulta il calendario aggiornato del corso.");
    }

    private void configureScheduleListeners() {
        scheduleModeField.valueProperty().addListener((observable, previous, mode) -> {
            var online = mode == SessionMode.ONLINE;
            scheduleCapacityField.setDisable(online);
            scheduleAttendanceClosesAtField.setDisable(online);
            if (previous == SessionMode.ONLINE) scheduleOnlineDraft = scheduleLocationField.getText();
            else if (previous == SessionMode.PRACTICAL) scheduleVenueDraft = scheduleLocationField.getText();
            scheduleLocationField.setText(online ? scheduleOnlineDraft : scheduleVenueDraft);
            scheduleLocationField.setPromptText(online ? "Es. https://meet.example.org/lezione" : "Indirizzo o link HTTPS");
            if (suggestedAttendanceCutoff) refreshSuggestedAttendanceCutoff();
        });
        scheduleLocationField.textProperty().addListener((observable, previous, value) -> updateSuggestedScheduleMessage());
        scheduleDateField.valueProperty().addListener((observable, previous, value) -> scheduleTimeChanged());
        scheduleTimeField.valueProperty().addListener((observable, previous, value) -> scheduleTimeChanged());
        scheduleAttendanceDateField.valueProperty().addListener((observable, previous, value) -> {
            if (!updatingAttendanceCutoff) suggestedAttendanceCutoff = false;
        });
        scheduleAttendanceTimeField.valueProperty().addListener((observable, previous, value) -> {
            if (!updatingAttendanceCutoff) suggestedAttendanceCutoff = false;
        });
    }

    private void scheduleTimeChanged() {
        updateSuggestedScheduleMessage();
        if (suggestedAttendanceCutoff) refreshSuggestedAttendanceCutoff();
    }

    void configureScheduleFieldAccessibility() {
        scheduleDateField.setAccessibleText("Data della sessione");
        scheduleTimeField.setAccessibleText("Ora della sessione nel formato ore e minuti");
        scheduleModeField.setAccessibleText("Modalità della sessione");
        scheduleDurationField.setAccessibleText("Durata della sessione in minuti");
        scheduleLocationField.setAccessibleText("URL HTTPS o sede della sessione");
        scheduleCapacityField.setAccessibleText("Capienza della sessione pratica");
        scheduleAttendanceClosesAtField.setAccessibleText("Data e ora di chiusura delle adesioni");
        scheduleNotificationTitleField.setAccessibleText("Titolo della comunicazione");
        scheduleNotificationMessageField.setAccessibleText("Messaggio per gli iscritti");
        List.of(scheduleDateField, scheduleModeField, scheduleDurationField,
                        scheduleLocationField, scheduleCapacityField, scheduleAttendanceDateField,
                        scheduleNotificationTitleField, scheduleNotificationMessageField)
                .forEach(control -> control.getStyleClass().add("course-field"));
    }

    void resetScheduleEditorForNewSession() {
        if (scheduleDateField == null) {
            return;
        }
        suggestedAttendanceCutoff = true;
        rescheduleOriginalDate = null;
        suggestedScheduleMessage = null;
        scheduleOnlineDraft = "";
        scheduleVenueDraft = DEFAULT_VENUE;
        scheduleDateField.setValue(today().plusDays(14));
        scheduleTimeField.setValue(LocalTime.of(18, 30));
        scheduleModeField.setValue(SessionMode.PRACTICAL);
        if (scheduleDurationField.getValueFactory() != null) {
            scheduleDurationField.getValueFactory().setValue(120);
        }
        scheduleLocationField.setText(DEFAULT_VENUE);
        scheduleLocationField.setPromptText("Indirizzo o link HTTPS");
        scheduleOnlineDraft = "";
        scheduleVenueDraft = DEFAULT_VENUE;
        if (scheduleCapacityField.getValueFactory() != null) {
            scheduleCapacityField.getValueFactory().setValue(12);
        }
        scheduleNotificationTitleField.setText("Calendario aggiornato");
        updateSuggestedScheduleMessage();
        refreshSuggestedAttendanceCutoff();
    }

    LocalDateTime scheduleDateTime() {
        var date = scheduleDateField.getValue();
        if (date == null) throw new IllegalArgumentException("Seleziona la data della sessione.");
        CourseDatePolicy.requireNotPast(date, today());
        return LocalDateTime.of(date, scheduleTimeField.getValue());
    }

    Optional<LocalDateTime> scheduleAttendanceCutoff(
            SessionMode mode, LocalDateTime scheduledAt) {
        if (mode == SessionMode.ONLINE) {
            return Optional.empty();
        }
        var date = scheduleAttendanceDateField.getValue();
        if (date == null) {
            throw new IllegalArgumentException("Seleziona la data di chiusura delle adesioni.");
        }
        var cutoff = LocalDateTime.of(date, scheduleAttendanceTimeField.getValue());
        SessionInteractionPolicy.requireBefore(cutoff, scheduledAt);
        return Optional.of(cutoff);
    }

    void refreshSuggestedAttendanceCutoff() {
        if (scheduleModeField == null || scheduleModeField.getValue() != SessionMode.PRACTICAL) return;
        if (scheduleDateField.getValue() == null || scheduleTimeField.getValue() == null) return;
        var cutoff = LocalDateTime.of(scheduleDateField.getValue(), scheduleTimeField.getValue()).minusMinutes(1);
        updatingAttendanceCutoff = true;
        try {
            scheduleAttendanceDateField.setValue(cutoff.toLocalDate());
            scheduleAttendanceTimeField.setValue(cutoff.toLocalTime());
        } finally {
            updatingAttendanceCutoff = false;
        }
    }

    NotificationDraft scheduleNotification() {
        return CoursePresentation.validatedNotification(
                scheduleNotificationTitleField.getText(), scheduleNotificationMessageField.getText());
    }

    void populateScheduleEditor(CourseSessionSummary session) {
        suggestedAttendanceCutoff = false;
        rescheduleOriginalDate = null;
        suggestedScheduleMessage = null;
        scheduleOnlineDraft = "";
        scheduleVenueDraft = DEFAULT_VENUE;
        scheduleDateField.setValue(session.scheduledAt().toLocalDate());
        scheduleTimeField.setValue(session.scheduledAt().toLocalTime());
        scheduleModeField.setValue(session.mode());
        scheduleOnlineDraft = session.onlineUrl().map(Object::toString).orElse("");
        scheduleVenueDraft = session.venue().orElse(DEFAULT_VENUE);
        scheduleDurationField.getValueFactory().setValue(session.durationMinutes());
        var cutoff = SessionInteractionPolicy.initialCutoff(session.attendanceClosesAt(), session.scheduledAt());
        scheduleAttendanceDateField.setValue(cutoff.toLocalDate());
        scheduleAttendanceTimeField.setValue(cutoff.toLocalTime());
        if (session.mode() == SessionMode.ONLINE) {
            scheduleLocationField.setText(session.onlineUrl().map(Object::toString).orElse(""));
        } else {
            scheduleLocationField.setText(session.venue().orElse(""));
            session.capacity().ifPresent(value -> scheduleCapacityField.getValueFactory().setValue(value));
        }
    }

    void updateSuggestedScheduleMessage() {
        if (scheduleNotificationMessageField == null || scheduleDateField.getValue() == null
                || scheduleTimeField.getValue() == null) return;
        if (suggestedScheduleMessage != null && !suggestedScheduleMessage.equals(scheduleNotificationMessageField.getText())) return;
        var course = selectedCourse.get();
        var title = course == null ? "Corso" : course.title().value();
        var updated = LocalDateTime.of(scheduleDateField.getValue(), scheduleTimeField.getValue());
        suggestedScheduleMessage = suggestedMessage(title, updated);
        scheduleNotificationMessageField.setText(suggestedScheduleMessage);
    }

    private String suggestedMessage(String courseTitle, LocalDateTime updated) {
        if (rescheduleOriginalDate == null || !rescheduleOriginalDate.equals(updated)) {
            return CoursePresentation.scheduleMessage(courseTitle, rescheduleOriginalDate, updated);
        }
        var locationLabel = scheduleModeField.getValue() == SessionMode.ONLINE ? "Link: " : "Sede: ";
        return "I dettagli della sessione del corso «" + courseTitle + "» sono stati aggiornati. "
                + locationLabel + scheduleLocationField.getText().strip() + ". Data e ora restano invariate.";
    }

    void configureDatePicker(DatePicker datePicker) {
        datePicker.setEditable(false);
        datePicker.setShowWeekNumbers(false);
        datePicker.setConverter(CoursePresentation.courseDateConverter(today()));
        datePicker.setDayCellFactory(ignored -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date == null || date.isBefore(today()));
            }
        });
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(ROME));
    }

}
