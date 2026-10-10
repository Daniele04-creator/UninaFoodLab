package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;

import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.schedule.ManageCourseSchedule;
import it.uninafoodlab.application.schedule.NewSessionInput;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.UserErrorMessages;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class CourseScheduleCommands {
    private static final String ADD_SESSION_TITLE = "Aggiungi sessione";
    private static final String RESCHEDULE_SESSION_TITLE = "Modifica sessione";
    private static final String CANCEL_SESSION_TITLE = "Annulla sessione";

    private final AuthenticatedUser user;
    private final ManageCourseSchedule manageCourseSchedule;
    private final CourseScheduleEditor scheduleEditor;
    private final CourseOperationDialogs operationDialogs;
    private final CourseSessionNavigation navigation;
    private final CourseWorkspaceActions actions;
    private final Supplier<CourseSummary> selectedCourse;
    private final Supplier<CourseSessionSummary> selectedSession;
    private final BooleanSupplier archive;

    CourseScheduleCommands(AuthenticatedUser user, ManageCourseSchedule manageCourseSchedule,
            CourseScheduleEditor scheduleEditor, CourseOperationDialogs operationDialogs,
            CourseSessionNavigation navigation, CourseWorkspaceActions actions,
            Supplier<CourseSummary> selectedCourse, Supplier<CourseSessionSummary> selectedSession,
            BooleanSupplier archive) {
        this.user = user;
        this.manageCourseSchedule = manageCourseSchedule;
        this.scheduleEditor = scheduleEditor;
        this.operationDialogs = operationDialogs;
        this.navigation = navigation;
        this.actions = actions;
        this.selectedCourse = selectedCourse;
        this.selectedSession = selectedSession;
        this.archive = archive;
    }

    void openAddSessionDialog() {
        if (selectedPublishedCourse() == null) {
            return;
        }
        scheduleEditor.resetScheduleEditorForNewSession();
        showScheduleDialog(false);
    }

    void openRescheduleSessionDialog() {
        var selected = selectedScheduledSession();
        if (selected == null) {
            return;
        }
        scheduleEditor.populateScheduleEditor(selected);
        scheduleEditor.scheduleNotificationTitleField.setText("Sessione aggiornata");
        scheduleEditor.rescheduleOriginalDate = selected.scheduledAt();
        scheduleEditor.suggestedScheduleMessage = null;
        scheduleEditor.updateSuggestedScheduleMessage();
        showScheduleDialog(true);
    }

    private void showScheduleDialog(boolean reschedule) {
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle(reschedule ? RESCHEDULE_SESSION_TITLE : ADD_SESSION_TITLE);
        dialog.setHeaderText(reschedule ? RESCHEDULE_SESSION_TITLE : ADD_SESSION_TITLE);
        operationDialogs.configure(dialog);

        if (scheduleEditor.scheduleDialogContent instanceof javafx.scene.layout.GridPane previous) {
            previous.getChildren().clear();
        }
        scheduleEditor.scheduleDialogContent = scheduleEditor.createScheduleDialogContent(reschedule);
        var scheduleFeedbackLabel = new Label();
        scheduleFeedbackLabel.setWrapText(true);
        scheduleFeedbackLabel.getStyleClass().add("course-feedback-error");
        var course = selectedCourse.get();
        var content = new VBox(12, readOnlyValue(course.title().value()));
        content.getChildren().add(readOnlyValue((reschedule ? "Aggiorna data, ora o luogo della sessione. " : "Definisci il nuovo appuntamento. ")
                + "Gli iscritti troveranno un avviso nella sezione Comunicazioni."));
        content.getChildren().add(scheduleEditor.scheduleDialogContent);
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(520);
        scroll.setMinHeight(0);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        scroll.getStyleClass().add("workspace-scroll");
        dialog.getDialogPane().setContent(new VBox(12, scroll, scheduleFeedbackLabel));
        var save = new ButtonType(reschedule ? RESCHEDULE_SESSION_TITLE : ADD_SESSION_TITLE,
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        operationDialogs.buttons(dialog, save, "button-primary", () -> {
            if (reschedule) rescheduleSession(); else addSession();
        });
        operationDialogs.show(dialog, scheduleFeedbackLabel);
        dialog.getDialogPane().setContent(null);
    }

    private void rescheduleSession() {
        var selected = selectedScheduledSession();
        if (selected == null) {
            return;
        }
        try {
            var scheduledAt = scheduleEditor.scheduleDateTime();
            var cutoff = scheduleEditor.scheduleAttendanceCutoff(scheduleEditor.scheduleModeField.getValue(), scheduledAt);
            var location = scheduleEditor.scheduleLocationField.getText().strip();
            if (selected.mode() == SessionMode.ONLINE) {
                it.uninafoodlab.domain.course.SessionDetails.online(selected.durationMinutes(), java.net.URI.create(location));
            } else {
                it.uninafoodlab.domain.course.SessionDetails.practical(selected.durationMinutes(), location, selected.capacity().orElseThrow());
                if (location.length() > 180) throw new IllegalArgumentException("La sede può contenere al massimo 180 caratteri.");
            }
            var draft = scheduleEditor.scheduleNotification();
            actions.runAsync(
                    "session-reschedule",
                    () -> {
                        manageCourseSchedule.reschedule(user, selected.id(), scheduledAt, cutoff, location, draft);
                        return selected.id();
                    },
                    ignored -> { operationDialogs.close(); navigation.refreshSelectedCourse("Sessione aggiornata e comunicazione registrata."); },
                    "Non è stato possibile aggiornare la sessione.");
        } catch (IllegalArgumentException exception) {
            actions.showFeedback(UserErrorMessages.validation(exception, "Controlla data, ora e dettagli della sessione."), true);
        }
    }

    private void addSession() {
        var course = selectedPublishedCourse();
        if (course == null) {
            return;
        }
        try {
            var mode = Objects.requireNonNull(scheduleEditor.scheduleModeField.getValue(), "mode");
            var location = scheduleEditor.scheduleLocationField.getText().strip();
            var scheduledAt = scheduleEditor.scheduleDateTime();
            var cutoff = scheduleEditor.scheduleAttendanceCutoff(mode, scheduledAt);
            var input = mode == SessionMode.ONLINE
                    ? new NewSessionInput(
                            scheduledAt, mode, scheduleEditor.scheduleDurationField.getValue(),
                            Optional.of(java.net.URI.create(location)), Optional.empty(), Optional.empty(), cutoff)
                    : new NewSessionInput(
                            scheduledAt, mode, scheduleEditor.scheduleDurationField.getValue(),
                            Optional.empty(), Optional.of(location),
                            Optional.of(scheduleEditor.scheduleCapacityField.getValue()), cutoff);
            var draft = scheduleEditor.scheduleNotification();
            actions.runAsync(
                    "session-add",
                    () -> manageCourseSchedule.add(user, course.id(), input, draft),
                    ignored -> { operationDialogs.close(); navigation.refreshSelectedCourse("Sessione aggiunta e comunicazione registrata."); },
                    "Non è stato possibile aggiungere la sessione.");
        } catch (IllegalArgumentException exception) {
            actions.showFeedback(UserErrorMessages.validation(exception, "Controlla data, ora e dettagli della nuova sessione."), true);
        } catch (RuntimeException _) {
            actions.showFeedback("Controlla data, ora e dettagli della nuova sessione.", true);
        }
    }

    void cancelSession() {
        var selected = selectedScheduledSession();
        if (selected == null) return;
        var course = selectedCourse.get();
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle(CANCEL_SESSION_TITLE);
        dialog.setHeaderText(CANCEL_SESSION_TITLE);
        operationDialogs.configure(dialog);
        var title = new TextField("Sessione annullata");
        var message = new TextArea("La sessione del corso «" + course.title().value() + "» del "
                + DATE_TIME_FORMAT.format(selected.scheduledAt()) + " è stata annullata.");
        it.uninafoodlab.presentation.common.TextAreaNavigation.install(message);
        title.getStyleClass().add("course-field");
        message.getStyleClass().add("course-field");
        message.setWrapText(true);
        message.setPrefRowCount(3);
        var scheduleFeedbackLabel = new Label();
        scheduleFeedbackLabel.setWrapText(true);
        scheduleFeedbackLabel.getStyleClass().add("course-feedback-error");
        var consequence = readOnlyValue("La sessione non sarà più disponibile per le adesioni. "
                + "Gli iscritti troveranno un avviso nella sezione Comunicazioni.");
        var titleLabel = new Label("Titolo comunicazione");
        titleLabel.setLabelFor(title);
        titleLabel.getStyleClass().add("field-label");
        var messageLabel = new Label("Messaggio agli iscritti");
        messageLabel.setLabelFor(message);
        messageLabel.getStyleClass().add("field-label");
        var notificationHeading = new Label("Comunicazione agli iscritti");
        notificationHeading.getStyleClass().add("recipe-dialog-heading");
        dialog.getDialogPane().setContent(new VBox(12,
                readOnlyValue(course.title().value() + ", " + DATE_TIME_FORMAT.format(selected.scheduledAt())), consequence,
                notificationHeading, titleLabel, title, messageLabel, message,
                scheduleFeedbackLabel));
        var confirm = new ButtonType(CANCEL_SESSION_TITLE, javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        operationDialogs.buttons(dialog, confirm, "button-danger",
                () -> confirmCancellation(selected, title.getText(), message.getText()));
        operationDialogs.show(dialog, scheduleFeedbackLabel);
    }

    private void confirmCancellation(CourseSessionSummary selected, String title, String message) {
        try {
            var draft = validatedNotification(title, message);
            actions.runAsync("session-cancel", () -> {
                manageCourseSchedule.cancel(user, selected.id(), draft);
                return selected.id();
            }, ignored -> {
                operationDialogs.close();
                navigation.refreshSelectedCourse("Sessione annullata e comunicazione registrata.");
            }, "Non è stato possibile annullare la sessione. Riprova.");
        } catch (IllegalArgumentException exception) {
            actions.showFeedback(UserErrorMessages.validation(exception, "Controlla il titolo e il messaggio della comunicazione."), true);
        }
    }

    private CourseSummary selectedPublishedCourse() {
        var course = selectedCourse.get();
        if (archive.getAsBoolean()) {
            actions.showFeedback("I corsi passati sono disponibili in sola lettura.", true);
            return null;
        }
        if (course == null) {
            actions.showFeedback("Seleziona un corso attivo.", true);
            return null;
        }
        return course;
    }

    private CourseSessionSummary selectedScheduledSession() {
        if (selectedPublishedCourse() == null) {
            return null;
        }
        var session = selectedSession.get();
        if (session == null || session.status() != SessionStatus.SCHEDULED) {
            actions.showFeedback("Seleziona una sessione programmata.", true);
            return null;
        }
        return session;
    }

}
