package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;

import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.course.CreateCourse;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.UserErrorMessages;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

final class CourseCreationForm {
    private static final String SESSION_LABEL_PREFIX = "sessione ";

    private final Fields fields;
    private final Clock clock;
    private final CreateCourse createCourse;
    private final AuthenticatedUser user;
    private final CourseWorkspaceActions actions;
    private final CourseFormModel formModel = new CourseFormModel();
    private final CourseCommonSessionSettings commonSettings;
    private LocalDate initialStartDate;

    CourseCreationForm(Fields fields, Clock clock, CreateCourse createCourse,
            AuthenticatedUser user, CourseWorkspaceActions actions) {
        this.fields = fields;
        this.clock = clock;
        this.createCourse = createCourse;
        this.user = user;
        this.actions = actions;
        commonSettings = new CourseCommonSessionSettings(formModel, actions, this::renderSessions);
    }

    record Fields(ComboBox<CategorySummary> categoryField,
            TextField titleField,
            TextArea descriptionField,
            DatePicker startDateField,
            TimeSelector startTimeField,
            Spinner<Integer> recurrenceField,
            Spinner<Integer> sessionCountField,
            Spinner<Integer> durationField,
            TilePane sessionModeEditor,
            Label courseSummaryLabel) {}

    void createCourse() {
        try {
            NumericSpinner.requireValue(fields.sessionCountField(), "Il numero di sessioni deve essere compreso tra 2 e 30");
            int recurrence = NumericSpinner.requireValue(fields.recurrenceField(), "La frequenza deve essere compresa tra 1 e 31 giorni");
            int duration = NumericSpinner.requireValue(fields.durationField(), "La durata deve essere compresa tra 1 e 720 minuti");
            var category = fields.categoryField().getValue();
            if (fields.startDateField().getValue() != null) {
                CourseDatePolicy.requireNotPast(fields.startDateField().getValue(), today());
            }
            var input = formModel.toInput(
                    category == null ? null : category.id(),
                    fields.titleField().getText(),
                    fields.descriptionField().getText(),
                    fields.startDateField().getValue(),
                    fields.startTimeField().getValue().toString(),
                    recurrence, duration);
            actions.runAsync(
                    "course-create",
                    () -> createCourse.execute(user, input),
                    createdId -> {
                        resetForm();
                        actions.setCourseFormVisible(false);
                        actions.showFeedback("Corso attivo creato con tutte le sessioni.", false);
                        actions.loadCourses(createdId);
                    },
                    "Non è stato possibile creare il corso. Controlla i dati e riprova.");
        } catch (IllegalArgumentException exception) {
            var message = UserErrorMessages.validation(exception, "Controlla i dati del corso e delle sessioni.");
            actions.showFeedback(message, true);
            focusInvalidCourseField(message);
        }
    }

    void focusInvalidCourseField(String message) {
        var field = invalidCourseField(message);
        if (field == null) {
            return;
        }
        field.requestFocus();
        javafx.application.Platform.runLater(() -> {
            for (var parent = field.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof ScrollPane scroll) {
                    scrollToField(scroll, field);
                }
            }
        });
    }

    private javafx.scene.Node invalidCourseField(String message) {
        if (message.contains("categoria")) return fields.categoryField();
        if (message.contains("titolo")) return fields.titleField();
        if (message.contains("descrizione")) return fields.descriptionField();
        if (message.contains("data")) return fields.startDateField();
        if (message.contains("orario")) return fields.startTimeField();
        if (message.contains("frequenza")) return fields.recurrenceField().getEditor();
        if (message.contains("durata")) return fields.durationField().getEditor();
        if (message.contains("numero di sessioni")) return fields.sessionCountField().getEditor();
        return invalidSessionField(message);
    }

    private javafx.scene.Node invalidSessionField(String message) {
        if (!message.contains(SESSION_LABEL_PREFIX)) {
            return null;
        }
        var index = message.substring(message.lastIndexOf(SESSION_LABEL_PREFIX) + 9).strip();
        var prefix = invalidSessionFieldPrefix(message);
        return fields.sessionModeEditor().lookupAll(".course-create-control").stream()
                .filter(node -> node.getAccessibleText() != null
                        && node.getAccessibleText().startsWith(prefix)
                        && node.getAccessibleText().endsWith(SESSION_LABEL_PREFIX + index))
                .findFirst().orElse(null);
    }

    private static String invalidSessionFieldPrefix(String message) {
        if (message.contains("URL")) return "URL HTTPS";
        if (message.contains("sede")) return "Sede";
        return "Capienza massima";
    }

    private static void scrollToField(ScrollPane scroll, javafx.scene.Node field) {
        var content = scroll.getContent();
        var bounds = content.sceneToLocal(field.localToScene(field.getBoundsInLocal()));
        var height = content.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
        if (height > 0) {
            scroll.setVvalue(Math.clamp(bounds.getMinY() / height, 0.0, 1.0));
        }
    }

    void configureForm() {
        configureTitleField(fields.titleField(), message -> actions.showFeedback(message, true));
        fields.categoryField().setConverter(categoryConverter());
        configureDatePicker(fields.startDateField());
        initialStartDate = today().plusDays(7);
        fields.startDateField().setValue(initialStartDate);
        fields.startTimeField().setValue(LocalTime.of(18, 30));
        fields.recurrenceField().setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 31, 7));
        fields.sessionCountField().setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 30, 2));
        fields.durationField().setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 720, 120, 15));
        NumericSpinner.configure(fields.recurrenceField(), "La frequenza deve essere compresa tra 1 e 31 giorni", message -> actions.showFeedback(message, true));
        NumericSpinner.configure(fields.sessionCountField(), "Il numero di sessioni deve essere compreso tra 2 e 30", message -> actions.showFeedback(message, true));
        NumericSpinner.configure(fields.durationField(), "La durata deve essere compresa tra 1 e 720 minuti", message -> actions.showFeedback(message, true));
        formModel.setSessionCount(2);
        fields.sessionCountField().valueProperty().addListener((observable, previous, count) -> {
            formModel.setSessionCount(count);
            renderSessions();
        });
        fields.startDateField().valueProperty().addListener((observable, previous, date) -> renderSessions());
        fields.recurrenceField().valueProperty().addListener((observable, previous, days) -> renderSessions());
        fields.durationField().valueProperty().addListener((observable, previous, minutes) -> updateCourseSummary());
        fields.startTimeField().valueProperty().addListener((observable, previous, time) -> renderSessions());
        renderSessions();
    }

    void installCommonSettings(VBox host) {
        commonSettings.install(host);
    }

    static void configureTitleField(TextField title, java.util.function.Consumer<String> onRejected) {
        title.setTextFormatter(new TextFormatter<String>(change -> {
            String candidate = change.getControlNewText();
            if (!CourseTitle.isSingleLine(candidate)) {
                onRejected.accept("Il titolo del corso deve essere su una sola riga.");
                return null;
            }
            if (candidate.length() > CourseTitle.MAX_LENGTH) {
                onRejected.accept("Il titolo del corso deve contenere da 3 a 30 caratteri.");
                return null;
            }
            return change;
        }));
    }

    private void renderSessions() {
        fields.sessionModeEditor().getChildren().clear();
        var drafts = formModel.sessionDrafts();
        for (var index = 0; index < drafts.size(); index++) {
            var editor = new CourseSessionEditor(index, sessionDateLabel(index), formModel, modeConverter());
            editor.prefWidthProperty().bind(fields.sessionModeEditor().prefTileWidthProperty());
            editor.setMaxWidth(Double.MAX_VALUE);
            fields.sessionModeEditor().getChildren().add(editor);
        }
        updateCourseSummary();
    }

    private String sessionDateLabel(int index) {
        var startDate = fields.startDateField().getValue();
        var recurrence = fields.recurrenceField().getValue();
        if (startDate == null || recurrence == null) {
            return "Data da definire";
        }
        return formatCourseStartDate(formModel.scheduledDate(index, startDate, recurrence));
    }

    private void updateCourseSummary() {
        if (fields.courseSummaryLabel() == null
                || fields.sessionCountField().getValue() == null
                || fields.recurrenceField().getValue() == null
                || fields.durationField().getValue() == null) {
            return;
        }
        fields.courseSummaryLabel().setText(formatCourseSummary(
                fields.sessionCountField().getValue(), fields.recurrenceField().getValue(), fields.durationField().getValue()));
    }

    void resetForm() {
        fields.categoryField().getSelectionModel().clearSelection();
        fields.categoryField().setValue(null);
        fields.titleField().clear();
        fields.descriptionField().clear();
        initialStartDate = today().plusDays(7);
        fields.startDateField().setValue(initialStartDate);
        fields.startTimeField().setValue(LocalTime.of(18, 30));
        fields.recurrenceField().getValueFactory().setValue(7);
        fields.sessionCountField().getValueFactory().setValue(2);
        fields.durationField().getValueFactory().setValue(120);

        for (var spinner : java.util.List.of(fields.recurrenceField(), fields.sessionCountField(), fields.durationField())) {
            spinner.getEditor().setText(spinner.getValue().toString());
        }
        commonSettings.reset();
        formModel.resetSessions(2);
        renderSessions();
        fields.titleField().requestFocus();
    }

    boolean hasDraft() {
        return fields.categoryField().getValue() != null || !fields.titleField().getText().isEmpty()
                || !fields.descriptionField().getText().isEmpty()
                || !java.util.Objects.equals(fields.startDateField().getValue(), initialStartDate)
                || !LocalTime.of(18, 30).equals(fields.startTimeField().getValue())
                || !"7".equals(fields.recurrenceField().getEditor().getText())
                || !"2".equals(fields.sessionCountField().getEditor().getText())
                || !"120".equals(fields.durationField().getEditor().getText())
                || formModel.hasSessionChanges() || commonSettings.hasChanges();
    }

    void discardDraft() {
        if (hasDraft() && !confirmDiscard()) return;
        resetForm();
        actions.setCourseFormVisible(false);
    }

    private boolean confirmDiscard() {
        var discard = new ButtonType("Scarta bozza", ButtonBar.ButtonData.OK_DONE);
        var keep = new ButtonType("Mantieni bozza", ButtonBar.ButtonData.CANCEL_CLOSE);
        var dialog = new Alert(Alert.AlertType.CONFIRMATION,
                "I dati del corso e delle sessioni inseriti finora verranno eliminati.", keep, discard);
        dialog.setTitle("Scartare la bozza?"); dialog.setHeaderText("Scartare la bozza del corso?");
        it.uninafoodlab.presentation.common.DialogTheme.apply(dialog,
                fields.titleField().getScene() == null ? null : fields.titleField().getScene().getWindow());
        var discardButton = (Button) dialog.getDialogPane().lookupButton(discard);
        discardButton.getStyleClass().add("button-danger"); discardButton.setDefaultButton(false);
        var keepButton = (Button) dialog.getDialogPane().lookupButton(keep);
        keepButton.getStyleClass().add("button-secondary"); keepButton.setDefaultButton(true);
        return dialog.showAndWait().filter(discard::equals).isPresent();
    }

    private void configureDatePicker(DatePicker datePicker) {
        datePicker.setEditable(false);
        datePicker.setShowWeekNumbers(false);
        datePicker.setConverter(courseDateConverter(today()));
        datePicker.setDayCellFactory(ignored -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date == null || date.isBefore(today()));
            }
        });
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneId.of("Europe/Rome")));
    }
}
