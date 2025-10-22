package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;

import java.time.LocalDate;
import java.util.Optional;

public class CorsoEditorDialogController {

    /* ButtonType definiti nel FXML del dialog */
    @FXML private ButtonType createButtonType;
    @FXML private ButtonType cancelButtonType;

    /* UI */
    @FXML private ComboBox<String> cbArg;
    @FXML private Button btnAddArg;
    @FXML private ChoiceBox<String> cbFreq;
    @FXML private Spinner<Integer> spNumSess;
    @FXML private DatePicker dpInizio;
    @FXML private Label lblFine;
    @FXML private DialogPane dialogPane;

    /* Stato */
    private boolean edit;
    private Corso original;

    @FXML
    private void initialize() {
        // --- dati e default ---
        cbArg.setEditable(false);
        cbArg.setItems(FXCollections.observableArrayList(
                "Cucina Asiatica","Pasticceria","Panificazione","Vegetariano",
                "Street Food","Dolci al cucchiaio","Cucina Mediterranea",
                "Finger Food","Fusion","Vegan"
        ));
        cbFreq.setItems(FXCollections.observableArrayList(
                "settimanale","ogni 2 giorni","bisettimanale","mensile"
        ));
        spNumSess.setValueFactory(new IntegerSpinnerValueFactory(1, 365, 5));
        spNumSess.setEditable(true);
        // commit allo spostamento focus (evita valori non applicati)
        spNumSess.focusedProperty().addListener((obs, was, is) -> {
            if (!is) spNumSess.increment(0);
        });

        dpInizio.setShowWeekNumbers(false);
        dpInizio.setValue(LocalDate.now().plusDays(7));
        cbFreq.setValue("settimanale");
        updateDataFine();

        // ricalcolo "Fine"
        cbFreq.valueProperty().addListener((o, a, b) -> updateDataFine());
        dpInizio.valueProperty().addListener((o, a, b) -> updateDataFine());
        spNumSess.valueProperty().addListener((o, a, b) -> updateDataFine());

        // "+" argomento (graphic-only)
        btnAddArg.setText("");
        btnAddArg.setMnemonicParsing(false);
        btnAddArg.setStyle("-fx-graphic: url('/icons/plus-16.png'); -fx-content-display: graphic-only;");
        btnAddArg.setTooltip(new Tooltip("Aggiungi argomento"));
        btnAddArg.setOnAction(e -> addNewArgomento());

        // Bottoni del dialog (icone) + validazione SOLO via event filter (niente :disabled)
        Platform.runLater(() -> {
            Button okBtn = (Button) dialogPane.lookupButton(createButtonType);
            if (okBtn != null) {
                okBtn.setText("");
                okBtn.setMnemonicParsing(false);
                okBtn.setStyle("-fx-graphic: url('/icons/ok-16.png'); -fx-content-display: graphic-only;");
                okBtn.setTooltip(new Tooltip("Crea"));

                // Blocca l'OK se i campi non sono validi (evita binding su disable -> niente :disabled nel CSS)
                okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
                    if (!isFormValid()) {
                        evt.consume();
                        showValidationMessage();
                    }
                });
            }

            Button cancelBtn = (Button) dialogPane.lookupButton(cancelButtonType);
            if (cancelBtn != null) {
                cancelBtn.setText("");
                cancelBtn.setMnemonicParsing(false);
                cancelBtn.setStyle("-fx-graphic: url('/icons/cancel-16.png'); -fx-content-display: graphic-only;");
                cancelBtn.setTooltip(new Tooltip("Annulla"));
            }
        });
    }

    /* Precompilazione in edit */
    public void setCorso(Corso corso) {
        this.original = corso;
        this.edit = (corso != null);
        if (edit) {
            cbArg.setValue(corso.getArgomento());
            cbFreq.setValue(corso.getFrequenza());
            spNumSess.getValueFactory().setValue(corso.getNumSessioni());
            dpInizio.setValue(corso.getDataInizio());
            lblFine.setText(formatOrDash(corso.getDataFine()));
        }
    }

    /* Costruisce il risultato (nessun accesso DB qui) */
    public Corso getResult() {
        // chiamare SOLO se è stato premuto OK nel Dialog chiamante
        Corso c = edit ? clone(original) : new Corso();
        c.setArgomento(cbArg.getValue());
        c.setFrequenza(cbFreq.getValue());
        c.setDataInizio(dpInizio.getValue());
        c.setNumSessioni(spNumSess.getValue());
        c.setDataFine(computeDataFine(c.getDataInizio(), c.getFrequenza(), c.getNumSessioni()));
        return c;
    }

    public ButtonType getCreateButtonType() {
        return createButtonType;
    }

    /* ---------- Utility ---------- */

    private void addNewArgomento() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuovo Argomento");
        dialog.setHeaderText("Inserisci il nuovo argomento");
        dialog.setContentText("Argomento:");

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("corsi-dialog");
        pane.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(arg -> {
            String trimmed = arg.trim();

            // ✅ Validazione: solo lettere e spazi
            if (trimmed.isEmpty() || !trimmed.matches("[a-zA-ZÀ-ÖØ-öø-ÿ ]+")) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Input non valido");
                alert.setHeaderText(null);
                alert.setContentText("L'argomento deve contenere solo lettere e spazi.");
                alert.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
                alert.showAndWait();
                return;
            }

            if (cbArg.getItems().contains(trimmed)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Argomento già esistente");
                alert.setHeaderText(null);
                alert.setContentText("L'argomento \"" + trimmed + "\" esiste già.");
                alert.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
                alert.showAndWait();
            } else {
                cbArg.getItems().add(trimmed);
                FXCollections.sort(cbArg.getItems());
                cbArg.setValue(trimmed);
            }
        });
    }


    private void updateDataFine() {
        lblFine.setText(formatOrDash(
                computeDataFine(dpInizio.getValue(), cbFreq.getValue(), safeSpinnerValue())
        ));
    }

    private int safeSpinnerValue() {
        Integer v = spNumSess.getValue();
        return v == null ? 0 : v;
    }

    private static String formatOrDash(LocalDate d) {
        return d == null ? "—" : d.toString();
    }

    private static LocalDate computeDataFine(LocalDate inizio, String frequenza, int numSessioni) {
        if (inizio == null || numSessioni <= 0) return null;
        int steps = Math.max(0, numSessioni - 1);
        String f = frequenza == null ? "" : frequenza.trim().toLowerCase();
        return switch (f) {
            case "ogni 2 giorni" -> inizio.plusDays(2L * steps);
            case "bisettimanale" -> inizio.plusWeeks(2L * steps);
            case "mensile"       -> inizio.plusMonths(steps);
            default              -> inizio.plusWeeks(steps); // settimanale
        };
    }

    private static Corso clone(Corso src) {
        Corso c = new Corso();
        c.setIdCorso(src.getIdCorso());
        c.setArgomento(src.getArgomento());
        c.setFrequenza(src.getFrequenza());
        c.setDataInizio(src.getDataInizio());
        c.setDataFine(src.getDataFine());
        c.setNumSessioni(src.getNumSessioni());
        c.setChef(src.getChef());
        return c;
    }

    /* ---- Validazione ---- */

    private boolean isFormValid() {
        return cbArg.getValue() != null && !cbArg.getValue().isBlank()
                && cbFreq.getValue() != null
                && dpInizio.getValue() != null
                && safeSpinnerValue() > 0;
    }

    private void showValidationMessage() {
        // se vuoi feedback visivo, puoi aprire un Alert:
         Alert a = new Alert(Alert.AlertType.WARNING, "Compila correttamente tutti i campi.", ButtonType.OK);
         a.initOwner(dialogPane.getScene().getWindow());
         a.showAndWait();
    }
}