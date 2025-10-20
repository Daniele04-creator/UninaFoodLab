package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.DateCell;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Dialog editor per la creazione/modifica di un Corso.
 * Costruisce un Corso senza accessi al DB: il salvataggio avviene nel chiamante.
 */
public class CorsoEditorDialogController {

    /* ======== Costanti UI/Dati ======== */
    private static final List<String> DEFAULT_ARGOMENTI = List.of(
            "Cucina Asiatica", "Pasticceria", "Panificazione", "Vegetariano",
            "Street Food", "Dolci al cucchiaio", "Cucina Mediterranea",
            "Finger Food", "Fusion", "Vegan"
    );
    private static final List<String> DEFAULT_FREQUENZE = List.of(
            "settimanale", "ogni 2 giorni", "bisettimanale", "mensile"
    );
    private static final String DEFAULT_FREQ = "settimanale";
    private static final int MIN_SESSIONI = 1;
    private static final int MAX_SESSIONI = 365;
    private static final int DEFAULT_SESSIONI = 5;
    private static final int DEFAULT_OFFSET_GIORNI = 7;
    

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

    /* ========= Lifecycle ========= */
    @FXML
    private void initialize() {
        // Argomenti
        cbArg.setEditable(false);
        cbArg.setItems(FXCollections.observableArrayList(DEFAULT_ARGOMENTI));

        // Frequenze
        cbFreq.setItems(FXCollections.observableArrayList(DEFAULT_FREQUENZE));

        // Spinner n. sessioni
        spNumSess.setValueFactory(new IntegerSpinnerValueFactory(MIN_SESSIONI, MAX_SESSIONI, DEFAULT_SESSIONI));
        spNumSess.setEditable(true);
        // Commit del valore anche quando si esce dal campo
        spNumSess.focusedProperty().addListener(new javafx.beans.value.ChangeListener<Boolean>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> obs, Boolean was, Boolean is) {
                if (!Boolean.TRUE.equals(is)) spNumSess.increment(0);
            }
        });

        // DatePicker: default futuro + blocco date passate
        dpInizio.setShowWeekNumbers(false);
        dpInizio.setEditable(false);
        dpInizio.setValue(LocalDate.now().plusDays(DEFAULT_OFFSET_GIORNI));
        dpInizio.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate day, boolean empty) {
                super.updateItem(day, empty); // <-- importante!
                if (empty || day == null) {
                    setDisable(false);
                    setStyle("");              // reset stile
                    return;
                }
                boolean isPast = day.isBefore(LocalDate.now());
                setDisable(isPast);
                setStyle(isPast ? "-fx-opacity:0.5;" : "");
            }
        });


        // Default frequenza + calcolo data fine
        cbFreq.setValue(DEFAULT_FREQ);
        updateDataFine();

        // Ricalcolo "Fine" su ogni variazione
        cbFreq.valueProperty().addListener(new javafx.beans.value.ChangeListener<String>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends String> o, String a, String b) { updateDataFine(); }
        });
        dpInizio.valueProperty().addListener(new javafx.beans.value.ChangeListener<LocalDate>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends LocalDate> o, LocalDate a, LocalDate b) { updateDataFine(); }
        });
        spNumSess.valueProperty().addListener(new javafx.beans.value.ChangeListener<Integer>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends Integer> o, Integer a, Integer b) { updateDataFine(); }
        });

        // Pulsante per aggiungere un argomento personalizzato
        initAddArgButton();

        // Icone e comportamento dei bottoni del dialog + validazione on-OK
        Platform.runLater(new Runnable() {
            @Override public void run() { setupDialogButtons(); }
        });
    }

    /* ========= API ========= */

    /** Precompila i campi in modalità edit. */
    public void setCorso(Corso corso) {
        this.original = corso;
        this.edit = (corso != null);
        if (!edit) return;

        cbArg.setValue(corso.getArgomento());
        cbFreq.setValue(corso.getFrequenza());
        if (spNumSess.getValueFactory() != null) {
            spNumSess.getValueFactory().setValue(corso.getNumSessioni());
        }
        dpInizio.setValue(corso.getDataInizio());
        lblFine.setText(formatOrDash(corso.getDataFine()));
    }

    /** Ritorna il Corso costruito. Da chiamare solo se il Dialog ha chiuso con OK. */
    public Corso getResult() {
        Corso c = edit ? clone(original) : new Corso();
        c.setArgomento(cbArg.getValue());
        c.setFrequenza(cbFreq.getValue());
        c.setDataInizio(dpInizio.getValue());
        c.setNumSessioni(safeSpinnerValue());
        c.setDataFine(computeDataFine(c.getDataInizio(), c.getFrequenza(), c.getNumSessioni()));
        return c;
    }

    public ButtonType getCreateButtonType() { return createButtonType; }

    /* ========= UI wiring ========= */

    private void initAddArgButton() {
        btnAddArg.setText("");
        btnAddArg.setMnemonicParsing(false);
        btnAddArg.setStyle("-fx-graphic: url('/icons/plus-16.png'); -fx-content-display: graphic-only;");
        btnAddArg.setTooltip(new Tooltip("Aggiungi argomento"));
        btnAddArg.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            @Override public void handle(javafx.event.ActionEvent e) { addNewArgomento(); }
        });
    }

    private void setupDialogButtons() {
        // OK
        final Button okBtn = (Button) dialogPane.lookupButton(createButtonType);
        if (okBtn != null) {
            okBtn.setText("");
            okBtn.setMnemonicParsing(false);
            okBtn.setStyle("-fx-graphic: url('/icons/ok-16.png'); -fx-content-display: graphic-only;");
            okBtn.setTooltip(new Tooltip(edit ? "Salva" : "Crea"));

            // Validazione on-action (nessun binding disabilitante)
            okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, new javafx.event.EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(javafx.event.ActionEvent evt) {
                    if (!isFormValid()) {
                        evt.consume();
                        showValidationMessage();
                    }
                }
            });
        }
        // CANCEL
        final Button cancelBtn = (Button) dialogPane.lookupButton(cancelButtonType);
        if (cancelBtn != null) {
            cancelBtn.setText("");
            cancelBtn.setMnemonicParsing(false);
            cancelBtn.setStyle("-fx-graphic: url('/icons/cancel-16.png'); -fx-content-display: graphic-only;");
            cancelBtn.setTooltip(new Tooltip("Annulla"));
        }

       
    }

    /* ========= Logica/Validazione ========= */

    private void addNewArgomento() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuovo Argomento");
        dialog.setHeaderText("Inserisci il nuovo argomento");
        dialog.setContentText("Argomento:");
       

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        String trimmed = result.get().trim();
        if (trimmed.isEmpty()) return;

        // evita duplicati case-insensitive
        for (String s : cbArg.getItems()) {
            if (s.equalsIgnoreCase(trimmed)) {
                Alert a = new Alert(AlertType.INFORMATION, "L'argomento \"" + trimmed + "\" esiste già.", ButtonType.OK);
               
                a.setHeaderText(null);
                a.showAndWait();
                return;
            }
        }
        cbArg.getItems().add(trimmed);
        FXCollections.sort(cbArg.getItems(), String.CASE_INSENSITIVE_ORDER);
        cbArg.setValue(trimmed);
    }

    private void updateDataFine() {
        LocalDate fine = computeDataFine(dpInizio.getValue(), cbFreq.getValue(), safeSpinnerValue());
        lblFine.setText(formatOrDash(fine));
    }

    private int safeSpinnerValue() {
        Integer v = spNumSess.getValue();
        return v == null ? 0 : v.intValue();
    }

    private static String formatOrDash(LocalDate d) { return (d == null) ? "—" : d.toString(); }

    /** Calcola la data di fine in base a frequenza e numero di sessioni. */
    private static LocalDate computeDataFine(LocalDate inizio, String frequenza, int numSessioni) {
        if (inizio == null || numSessioni <= 0) return null;
        int steps = Math.max(0, numSessioni - 1);
        String f = (frequenza == null) ? "" : frequenza.trim().toLowerCase();

        if ("ogni 2 giorni".equals(f)) {
            return inizio.plusDays(2L * steps);
        } else if ("bisettimanale".equals(f)) {
            return inizio.plusWeeks(2L * steps);
        } else if ("mensile".equals(f)) {
            return inizio.plusMonths(steps);
        } else {
            // default: settimanale
            return inizio.plusWeeks(steps);
        }
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

    private boolean isFormValid() {
        String arg = (cbArg.getValue() == null) ? "" : cbArg.getValue().trim();
        String freq = (cbFreq.getValue() == null) ? "" : cbFreq.getValue().trim();
        LocalDate start = dpInizio.getValue();
        int n = safeSpinnerValue();

        if (arg.isEmpty() || freq.isEmpty() || start == null || n <= 0) return false;
        // opzionale: impedire data troppo nel passato (già bloccato dal day cell factory)
        return true;
    }

    private void showValidationMessage() {
        Alert a = new Alert(AlertType.WARNING, "Compila correttamente tutti i campi.", ButtonType.OK);
       
        a.setHeaderText("Dati mancanti o non validi");
        a.showAndWait();
    }

    /* ========= Helpers ========= */

   
}
