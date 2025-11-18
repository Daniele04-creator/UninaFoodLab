package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class CorsoEditorDialogController {

    @FXML private ButtonType createButtonType;
    @FXML private ComboBox<String> cbArg;
    @FXML private ChoiceBox<String> cbFreq;
    @FXML private Spinner<Integer> spNumSess;
    @FXML private DatePicker dpInizio;
    @FXML private Label lblFine;
    @FXML private DialogPane dialogPane;

    private static final DateTimeFormatter UI_DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private ObservableList<String> argomentiShared;

    @FXML
    private void initialize() {
        spNumSess.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 5));
        spNumSess.setEditable(true);

        dpInizio.setValue(LocalDate.now().plusDays(7));

        updateDataFine();

        cbFreq.valueProperty().addListener((o, a, b) -> updateDataFine());
        dpInizio.valueProperty().addListener((o, a, b) -> updateDataFine());
        spNumSess.valueProperty().addListener((o, a, b) -> updateDataFine());

        Button okBtn = (Button) dialogPane.lookupButton(createButtonType);
        okBtn.addEventFilter(ActionEvent.ACTION, evt -> {
            if (!isFormValid()) {
                evt.consume();
                showValidationMessage();
            }
        });

        }

    public Corso getResult() {
        Corso c = new Corso();

        c.setArgomento(cbArg.getValue());
        c.setFrequenza(cbFreq.getValue());
        c.setDataInizio(dpInizio.getValue());

        int numSess = spNumSess.getValue();
        c.setNumSessioni(numSess);

        c.setDataFine(computeDataFine(
                c.getDataInizio(),
                c.getFrequenza(),
                c.getNumSessioni()
        ));

        return c;
    }

    public ButtonType getCreateButtonType() {
        return createButtonType;
    }

    @FXML
    private void addNewArgomento() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuovo argomento");
        dialog.setHeaderText("Inserisci il nuovo argomento");
        dialog.setContentText("Argomento:");

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("text-input-dark-dialog");
        pane.getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/it/unina/foodlab/util/dark-theme.css")
                ).toExternalForm()
        );

        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                showWarning("Avvertenza", "L'argomento non può essere vuoto.");
                return;
            }

            ObservableList<String> target =
                    (argomentiShared != null) ? argomentiShared : cbArg.getItems();

            if (target.contains(trimmed)) {
                showWarning("Informazione", "L'argomento \"" + trimmed + "\" esiste già.");
                return;
            }

            target.add(trimmed);
            FXCollections.sort(target);
            cbArg.setValue(trimmed);
        });
    }

    private void updateDataFine() {
        int n = spNumSess.getValue();
        LocalDate fine = computeDataFine(
                dpInizio.getValue(),
                cbFreq.getValue(),
                n
        );
        lblFine.setText(formatOrDash(fine));
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
        String arg = cbArg.getValue();
        if (arg == null || arg.isBlank()) {
            return false;
        }
        if (cbFreq.getValue() == null) {
            return false;
        }
        if (dpInizio.getValue() == null) {
            return false;
        }

        Integer n = spNumSess.getValue();
        if (n == null || n <= 0) {
            return false;
        }

        return true;
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
