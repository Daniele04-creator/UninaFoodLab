package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.modeConverter;

import it.uninafoodlab.domain.course.SessionMode;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class CourseCommonSessionSettings {
    private final CourseFormModel formModel;
    private final CourseWorkspaceActions actions;
    private final Runnable renderSessions;
    private final ComboBox<SessionMode> commonMode = new ComboBox<>();
    private final TextField commonUrl = new TextField();
    private final TextField commonVenue = new TextField("Laboratorio UninaFoodLab");
    private final Spinner<Integer> commonCapacity = new Spinner<>(1, 500, 12);

    CourseCommonSessionSettings(CourseFormModel formModel, CourseWorkspaceActions actions, Runnable renderSessions) {
        this.formModel = formModel;
        this.actions = actions;
        this.renderSessions = renderSessions;
        commonMode.setValue(SessionMode.ONLINE);
    }

    void install(VBox host) {
        configureControls();
        var mode = labeled("Modalità", commonMode);
        mode.setPrefWidth(190); mode.setMinWidth(170);
        var title = new Label("Impostazioni comuni");
        title.getStyleClass().add("field-label");
        host.getChildren().setAll(title, new HBox(16, mode, sessionDetails()), applyFooter());
    }

    private void configureControls() {
        commonMode.setId("commonSessionMode");
        commonMode.getItems().setAll(SessionMode.values());
        commonMode.setConverter(modeConverter());
        CourseSessionModeView.configure(commonMode);
        commonMode.setValue(SessionMode.ONLINE);
        commonMode.setMaxWidth(Double.MAX_VALUE);
        commonMode.setAccessibleText("Modalità comune delle sessioni");
        commonMode.getStyleClass().addAll("course-combo", "course-create-control");
        commonUrl.setId("commonSessionUrl");
        commonUrl.setPromptText("https://meet.example.org/lezione");
        commonUrl.setAccessibleText("URL HTTPS comune delle sessioni");
        commonVenue.setId("commonSessionVenue");
        commonVenue.setAccessibleText("Sede comune delle sessioni");
        commonCapacity.setId("commonSessionCapacity");
        commonCapacity.setAccessibleText("Capienza comune delle sessioni in posti");
        commonCapacity.setPrefWidth(100);
        NumericSpinner.configure(commonCapacity);
        for (var control : java.util.List.of(commonUrl, commonVenue, commonCapacity)) {
            control.getStyleClass().addAll("course-field", "course-create-control");
        }
    }

    private VBox sessionDetails() {
        var link = labeled("Link della lezione", commonUrl);
        var venue = labeled("Sede o link alla posizione", commonVenue);
        var capacity = labeled("Capienza (posti)", commonCapacity);
        var practical = new HBox(12, venue, capacity);
        HBox.setHgrow(venue, Priority.ALWAYS);
        var details = new VBox(link, practical);
        HBox.setHgrow(details, Priority.ALWAYS); details.setMinWidth(0);
        commonMode.valueProperty().addListener((observable, previous, selected) -> {
            boolean online = selected == SessionMode.ONLINE;
            link.setVisible(online); link.setManaged(online);
            practical.setVisible(!online); practical.setManaged(!online);
        });
        practical.setVisible(false); practical.setManaged(false);
        return details;
    }

    private HBox applyFooter() {
        var apply = new Button("Applica a tutte");
        apply.setId("applyCommonSessionSettings");
        apply.getStyleClass().addAll("course-button", "button-secondary");
        apply.setAccessibleHelp("Sostituisce modalità e dettagli di tutte le sessioni del calendario attuale.");
        apply.setOnAction(event -> applyCommonSettings(apply));
        var hint = new Label("Applica sostituisce modalità e dettagli di tutte le sessioni attuali. Puoi poi modificare le singole sessioni.");
        hint.setWrapText(true); hint.getStyleClass().add("form-helper");
        var footer = new HBox(16, hint, apply); footer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(hint, Priority.ALWAYS); hint.setMinWidth(0); apply.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return footer;
    }

    private static VBox labeled(String text, javafx.scene.control.Control control) {
        var label = new Label(text); label.setLabelFor(control); label.getStyleClass().add("field-label");
        var box = new VBox(6, label, control); box.setMinWidth(0); return box;
    }

    private void applyCommonSettings(Button apply) {
        try {
            int capacity = commonMode.getValue() == SessionMode.PRACTICAL
                    ? NumericSpinner.requireValue(commonCapacity, "La capienza comune deve essere compresa tra 1 e 500 posti") : 12;
            formModel.applyCommonSessionSettings(new CourseFormModel.SessionDraft(commonMode.getValue(),
                    commonUrl.getText(), commonVenue.getText(), capacity));
            renderSessions.run();
            apply.requestFocus();
            actions.showFeedback("Impostazioni applicate a " + formModel.sessionDrafts().size() + " sessioni.", false);
        } catch (IllegalArgumentException failure) {
            var message = failure.getMessage().replace("per la sessione 1", "comune delle sessioni");
            actions.showFeedback(message, true);
            if (message.contains("HTTPS")) commonUrl.requestFocus();
            else if (message.contains("sede")) commonVenue.requestFocus();
            else commonCapacity.getEditor().requestFocus();
        }
    }

    void reset() {
        commonMode.setValue(SessionMode.ONLINE);
        commonUrl.clear();
        commonVenue.setText("Laboratorio UninaFoodLab");
        commonCapacity.getValueFactory().setValue(12);
        commonCapacity.getEditor().setText("12");
    }

    boolean hasChanges() {
        return commonMode.getValue() != SessionMode.ONLINE || !commonUrl.getText().isEmpty()
                || !commonVenue.getText().equals("Laboratorio UninaFoodLab")
                || !commonCapacity.getEditor().getText().equals("12");
    }
}
