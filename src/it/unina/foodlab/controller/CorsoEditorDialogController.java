package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class CorsoEditorDialogController {

    @FXML private ButtonType createButtonType;
    @FXML private ButtonType cancelButtonType;

    @FXML private ComboBox<String> cbArg;
    @FXML private Button btnAddArg;
    @FXML private ChoiceBox<String> cbFreq;
    @FXML private Spinner<Integer> spNumSess;
    @FXML private DatePicker dpInizio;
    @FXML private Label lblFine;
    @FXML private DialogPane dialogPane;

    private boolean edit;
    private Corso original;

    private static final DateTimeFormatter UI_DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private ObservableList<String> argomentiShared;

    @FXML
    private void initialize() {
        cbArg.setPromptText("Seleziona argomento");
        cbArg.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-text-fill: white;");
            }
        });
        cbArg.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-text-fill: white;");
            }
        });

        cbFreq.setItems(FXCollections.observableArrayList(
            "settimanale",
            "ogni 2 giorni",
            "bisettimanale",
            "mensile"
        ));
        cbFreq.setValue("settimanale");

        spNumSess.setValueFactory(new IntegerSpinnerValueFactory(1, 365, 5));
        spNumSess.setEditable(true);

        dpInizio.setValue(LocalDate.now().plusDays(7));
        dpInizio.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : UI_DF.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) return null;
                try {
                    return LocalDate.parse(text.trim(), UI_DF);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });

        updateDataFine();

        cbFreq.valueProperty().addListener((o, a, b) -> updateDataFine());
        dpInizio.valueProperty().addListener((o, a, b) -> updateDataFine());
        spNumSess.valueProperty().addListener((o, a, b) -> updateDataFine());

        btnAddArg.setOnAction(e -> addNewArgomento());

        Button okBtn = (Button) dialogPane.lookupButton(createButtonType);
        if (okBtn != null) {
            okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
                if (!isFormValid()) {
                    evt.consume();
                    showValidationMessage();
                }
            });
        }
    }

    public void setCorso(Corso corso) {
        original = corso;
        edit = corso != null;
        if (!edit) return;

        cbArg.setValue(corso.getArgomento());
        cbFreq.setValue(corso.getFrequenza());
        if (spNumSess.getValueFactory() != null) {
            spNumSess.getValueFactory().setValue(corso.getNumSessioni());
        }
        dpInizio.setValue(corso.getDataInizio());
        lblFine.setText(formatOrDash(corso.getDataFine()));
    }

    public Corso getResult() {
        Corso c = edit && original != null ? original : new Corso();
        c.setArgomento(cbArg.getValue());
        c.setFrequenza(cbFreq.getValue());
        c.setDataInizio(dpInizio.getValue());
        c.setNumSessioni(safeSpinnerValue());
        c.setDataFine(computeDataFine(c.getDataInizio(), c.getFrequenza(), c.getNumSessioni()));
        return c;
    }

    public ButtonType getCreateButtonType() {
        return createButtonType;
    }

    private void addNewArgomento() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuovo Argomento");
        dialog.setHeaderText("Inserisci il nuovo argomento");
        dialog.setContentText("Argomento:");

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("text-input-dark-dialog");
        pane.getStylesheets().add(
            getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );

        Optional<String> res = dialog.showAndWait();
        if (res.isEmpty()) return;

        String trimmed = res.get().trim();
        if (trimmed.isEmpty()) {
            showWarning("Avvertenza", "L'argomento non può essere vuoto.");
            return;
        }

        ObservableList<String> target =
            argomentiShared != null ? argomentiShared : cbArg.getItems();

        if (target.contains(trimmed)) {
            showWarning("Informazione", "L'argomento \"" + trimmed + "\" esiste già.");
            return;
        }

        target.add(trimmed);
        FXCollections.sort(target);
        cbArg.setItems(target);
        cbArg.setValue(trimmed);
    }

    private void updateDataFine() {
        LocalDate fine = computeDataFine(
            dpInizio.getValue(),
            cbFreq.getValue(),
            safeSpinnerValue()
        );
        lblFine.setText(formatOrDash(fine));
    }

    private int safeSpinnerValue() {
        Integer v = spNumSess.getValue();
        return v == null ? 0 : v;
    }

    private String formatOrDash(LocalDate d) {
        return d == null ? "—" : UI_DF.format(d);
    }

    private static LocalDate computeDataFine(LocalDate inizio, String frequenza, int numSessioni) {
        if (inizio == null || numSessioni <= 0) return null;

        int steps = Math.max(0, numSessioni - 1);
        String f = frequenza == null ? "" : frequenza.trim().toLowerCase();

        return switch (f) {
            case "ogni 2 giorni" -> inizio.plusDays(2L * steps);
            case "bisettimanale" -> inizio.plusWeeks(2L * steps);
            case "mensile"       -> inizio.plusMonths(steps);
            default              -> inizio.plusWeeks(steps);
        };
    }

    private boolean isFormValid() {
        if (cbArg.getValue() == null || cbArg.getValue().isBlank()) return false;
        if (cbFreq.getValue() == null) return false;
        if (dpInizio.getValue() == null) return false;
        return safeSpinnerValue() > 0;
    }

    private void showValidationMessage() {
        showWarning("Avvertenza", "Compila correttamente tutti i campi.");
    }

    private void showWarning(String titolo, String messaggio) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titolo);
        alert.setHeaderText(null);
        alert.setGraphic(null);

        Label content = new Label(messaggio == null ? "" : messaggio);
        content.setWrapText(true);
        alert.getDialogPane().setContent(content);

        DialogPane pane = alert.getDialogPane();
        pane.getStyleClass().add("dark-dialog");
        pane.getStylesheets().add(
            getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );
        pane.setMinWidth(460);

        alert.showAndWait();
    }

    public void bindArgomenti(ObservableList<String> shared) {
        argomentiShared = shared;
        cbArg.setItems(shared);
    }
}
