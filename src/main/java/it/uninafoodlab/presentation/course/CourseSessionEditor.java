package it.uninafoodlab.presentation.course;

import it.uninafoodlab.domain.course.SessionMode;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

final class CourseSessionEditor extends VBox {
    private static final String FIELD_LABEL_STYLE = "field-label";
    private static final String CREATE_CONTROL_STYLE = "course-create-control";
    private static final String COURSE_FIELD_STYLE = "course-field";

    private final int index;
    private final String dateLabel;
    private final CourseFormModel formModel;
    private final StringConverter<SessionMode> modeConverter;
    private final GridPane row = new GridPane();
    private VBox identity;
    private VBox modeEditor;
    private VBox details;

    CourseSessionEditor(int index, String dateLabel, CourseFormModel formModel,
            StringConverter<SessionMode> modeConverter) {
        super(0);
        this.index = index;
        this.dateLabel = dateLabel;
        this.formModel = formModel;
        this.modeConverter = modeConverter;
        setMinWidth(0);
        getStyleClass().addAll("session-mode-row", "studio-session-editor");
        row.setHgap(18); row.setVgap(10); row.setMinWidth(0);
        widthProperty().addListener((observable, previous, width) -> layoutColumns());
        render();
    }

    private void render() {
        var draft = formModel.sessionDrafts().get(index);
        var session = new Label("Sessione " + (index + 1));
        session.getStyleClass().add("session-draft-title");
        var date = new Label(dateLabel);
        date.getStyleClass().add("session-date");
        date.setWrapText(true);

        var modeLabel = new Label("Modalità");
        modeLabel.getStyleClass().add(FIELD_LABEL_STYLE);
        var mode = new ComboBox<SessionMode>();
        mode.getItems().setAll(SessionMode.values());
        mode.setConverter(modeConverter);
        CourseSessionModeView.configure(mode);
        mode.setValue(draft.mode());
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.setAccessibleText("Modalità della sessione " + (index + 1));
        mode.getStyleClass().addAll("course-combo", CREATE_CONTROL_STYLE);
        mode.valueProperty().addListener((observable, previous, selected) -> {
            if (selected != null) {
                formModel.setSessionMode(index, selected);
                render();
                ((ComboBox<?>) getUserData()).requestFocus();
            }
        });
        modeLabel.setLabelFor(mode);
        modeEditor = new VBox(6, modeLabel, mode);
        modeEditor.setMinWidth(0);

        details = draft.mode() == SessionMode.ONLINE
                ? onlineEditor(draft)
                : practicalEditor(draft);
        identity = new VBox(4, session, date);
        identity.setMinWidth(0);
        getChildren().setAll(row);
        layoutColumns();
        setUserData(mode);
    }

    private void layoutColumns() {
        boolean wide = getWidth() >= 780;
        var identityColumn = new ColumnConstraints();
        identityColumn.setMinWidth(140); identityColumn.setPrefWidth(170);
        var modeColumn = new ColumnConstraints();
        modeColumn.setMinWidth(175); modeColumn.setPrefWidth(190);
        row.getColumnConstraints().setAll(identityColumn, modeColumn);
        if (wide) {
            var detailColumn = new ColumnConstraints();
            detailColumn.setMinWidth(0); detailColumn.setHgrow(Priority.ALWAYS);
            row.getColumnConstraints().add(detailColumn);
        } else {
            modeColumn.setHgrow(Priority.ALWAYS);
        }
        row.getChildren().setAll(identity, modeEditor, details);
        GridPane.setConstraints(identity, 0, 0);
        GridPane.setConstraints(modeEditor, 1, 0);
        GridPane.setConstraints(details, wide ? 2 : 0, wide ? 0 : 1, wide ? 1 : 2, 1);
        GridPane.setValignment(identity, javafx.geometry.VPos.CENTER);
        GridPane.setHgrow(details, Priority.ALWAYS);
    }

    private VBox onlineEditor(CourseFormModel.SessionDraft draft) {
        var label = new Label("Link della lezione");
        label.getStyleClass().add(FIELD_LABEL_STYLE);
        var url = new TextField(draft.onlineUrl());
        label.setLabelFor(url);
        url.setPromptText("Es. https://meet.example.org/lezione");
        url.setAccessibleText("URL HTTPS della sessione " + (index + 1));
        url.getStyleClass().addAll(COURSE_FIELD_STYLE, CREATE_CONTROL_STYLE);
        url.setMinWidth(120);
        HBox.setHgrow(url, Priority.ALWAYS);
        url.textProperty().addListener((observable, previous, value) ->
                formModel.setSessionOnlineUrl(index, value));
        var editor = new VBox(6, label, url);
        editor.setMinWidth(0);
        HBox.setHgrow(editor, Priority.ALWAYS);
        return editor;
    }

    private VBox practicalEditor(CourseFormModel.SessionDraft draft) {
        var row = new GridPane();
        row.setHgap(12);
        for (int column = 0; column < 2; column++) {
            var constraint = new ColumnConstraints();
            constraint.setPercentWidth(column == 0 ? 65 : 35);
            constraint.setHgrow(Priority.ALWAYS);
            row.getColumnConstraints().add(constraint);
        }
        row.add(venueEditor(draft), 0, 0);
        row.add(capacityEditor(draft), 1, 0);
        var editor = new VBox(row);
        editor.setMinWidth(0);
        HBox.setHgrow(editor, Priority.ALWAYS);
        return editor;
    }

    private VBox venueEditor(CourseFormModel.SessionDraft draft) {
        var venueLabel = new Label("Sede o link alla posizione");
        venueLabel.getStyleClass().add(FIELD_LABEL_STYLE);
        var venue = new TextField(draft.venue());
        venueLabel.setLabelFor(venue);
        venue.setMinWidth(90);
        venue.setPromptText("Indirizzo o link HTTPS");
        venue.setAccessibleText("Sede o link alla posizione della sessione " + (index + 1));
        venue.getStyleClass().addAll(COURSE_FIELD_STYLE, CREATE_CONTROL_STYLE);
        HBox.setHgrow(venue, Priority.ALWAYS);
        venue.textProperty().addListener((observable, previous, value) ->
                formModel.setSessionVenue(index, value));
        var venueEditor = new VBox(6, venueLabel, venue);
        venueEditor.setMinWidth(0);
        HBox.setHgrow(venueEditor, Priority.ALWAYS);
        return venueEditor;
    }

    private VBox capacityEditor(CourseFormModel.SessionDraft draft) {
        var capacityLabel = new Label("Capienza massima");
        capacityLabel.getStyleClass().add(FIELD_LABEL_STYLE);
        var capacity = new Spinner<Integer>(1, 500, Math.max(1, draft.capacity()));
        capacityLabel.setLabelFor(capacity);
        NumericSpinner.configure(capacity);
        capacity.setPrefWidth(92);
        capacity.setMinWidth(72);
        capacity.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(capacity, Priority.ALWAYS);
        capacity.setAccessibleText("Capienza massima in posti della sessione " + (index + 1));
        capacity.getStyleClass().addAll(COURSE_FIELD_STYLE, CREATE_CONTROL_STYLE);
        capacity.valueProperty().addListener((observable, previous, value) ->
                formModel.setSessionCapacity(index, value));
        var unit = new Label("posti");
        unit.getStyleClass().add("course-capacity-unit");
        var capacityValue = new HBox(8, capacity, unit);
        capacityValue.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, capacityLabel, capacityValue);
    }

}
