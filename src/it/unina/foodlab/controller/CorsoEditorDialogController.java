package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
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

    private boolean edit;
    private Corso original;

    @FXML
    private void initialize() {
        // --- setup base ---
        cbArg.setEditable(false);
        cbArg.setItems(FXCollections.observableArrayList(
                "Cucina Asiatica","Pasticceria","Panificazione","Vegetariano",
                "Street Food","Dolci al cucchiaio","Cucina Mediterranea",
                "Finger Food","Fusion","Vegan"
        ));

        cbFreq.setItems(FXCollections.observableArrayList(
                "settimanale","ogni 2 giorni","bisettimanale","mensile"
        ));
        spNumSess.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 5));
        spNumSess.setEditable(true);

        dpInizio.setValue(LocalDate.now().plusDays(7));
        cbFreq.setValue("settimanale");
        updateDataFine();

        // --- listeners per aggiornare data fine ---
        Runnable updateFine = this::updateDataFine;
        cbFreq.valueProperty().addListener((o,a,b)->updateFine.run());
        dpInizio.valueProperty().addListener((o,a,b)->updateFine.run());
        spNumSess.valueProperty().addListener((o,a,b)->updateFine.run());

        // --- pulsante "+" per nuovi argomenti ---
        btnAddArg.setOnAction(e -> addNewArgomento());
    }

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

    public Corso getResult() {
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

    // ---------- utility ----------

    private void addNewArgomento() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuovo Argomento");
        dialog.setHeaderText("Inserisci il nuovo argomento");
        dialog.setContentText("Argomento:");

        // --- Applichiamo CSS e classe per lo stile ---
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("corsi-dialog");  // aggiunge la classe CSS
        pane.getStylesheets().add(getClass().getResource("/app.css").toExternalForm()); // aggiunge il CSS

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(arg -> {
            String trimmed = arg.trim();
            if (!trimmed.isEmpty() && !cbArg.getItems().contains(trimmed)) {
                cbArg.getItems().add(trimmed);
                FXCollections.sort(cbArg.getItems());
                cbArg.setValue(trimmed);
            } else if (cbArg.getItems().contains(trimmed)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Argomento già esistente");
                alert.setHeaderText(null);
                alert.setContentText("L'argomento \"" + trimmed + "\" esiste già.");
                alert.showAndWait();
            }
        });
    }


    private void updateDataFine() {
        lblFine.setText(formatOrDash(computeDataFine(dpInizio.getValue(), cbFreq.getValue(), spNumSess.getValue())));
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
            default              -> inizio.plusWeeks(steps);
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
}