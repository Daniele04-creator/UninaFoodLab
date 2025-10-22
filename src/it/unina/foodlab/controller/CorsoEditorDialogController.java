package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.layout.Region;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public class CorsoEditorDialogController {

    /* ButtonType definiti nel FXML */
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
        // Dati di base
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
        spNumSess.focusedProperty().addListener((obs, was, is) -> { if (!is) spNumSess.increment(0); });

        // DatePicker dark + default
        tintDatePickerDark(dpInizio);
        dpInizio.setValue(LocalDate.now().plusDays(7));
        cbFreq.setValue("settimanale");
        updateDataFine();

        // Recalcolo "Fine"
        cbFreq.valueProperty().addListener((o,a,b) -> updateDataFine());
        dpInizio.valueProperty().addListener((o,a,b) -> updateDataFine());
        spNumSess.valueProperty().addListener((o,a,b) -> updateDataFine());

        // Pulsante + argomento (solo icona)
        btnAddArg.setText("");
        btnAddArg.setMnemonicParsing(false);
        btnAddArg.setTooltip(new Tooltip("Aggiungi argomento"));
        
     // 1) Collega il pulsante "+" alla logica di inserimento nuovo argomento
        btnAddArg.setOnAction(e -> addNewArgomento());

        // 2) Rendi sempre leggibili le scelte di ComboBox/ChoiceBox
        installComboDarkCells(cbArg, "#e9f5ec", "#2b3438");  // testo chiaro + bg scuro popup
        forceChoiceBoxLabelColor(cbFreq, "#e9f5ec");         // forza il label della ChoiceBox

        // opzionale: se il display-area sembrasse ancora slavato, forza l’opacità a 1
        cbArg.setOpacity(1.0);
        cbFreq.setOpacity(1.0);

        // 3) Mostra il calendario cliccando sul campo e NASCONDI il bottone-icona di destra
        hideDatePickerArrow(dpInizio);
        dpInitoShowOnClick(dpInizio); // apre il popup cliccando sul campo

        // 4) Stile scuro per le frecce dello spinner (niente bianco)
        styleSpinnerArrowsDark(spNumSess, "#2b3438" /*bg*/, "#e9f5ec" /*freccia*/);
        
        cbArg.focusedProperty().addListener((o, was, is) ->
        cbArg.setStyle(cbArg.getStyle() + (is ? "; -fx-border-color:#1fb57a; -fx-border-width:1;" : ""))
    );
    cbFreq.focusedProperty().addListener((o, was, is) ->
        cbFreq.setStyle(cbFreq.getStyle() + (is ? "; -fx-border-color:#1fb57a; -fx-border-width:1;" : ""))
    );



        // Stile dei bottoni del Dialog (verde & grigio)
        Platform.runLater(() -> {
            Button okBtn = (Button) dialogPane.lookupButton(createButtonType);
            if (okBtn != null) {
                okBtn.setText("Crea");
                okBtn.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 14;");
                okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
                    if (!isFormValid()) { evt.consume(); showValidationMessage(); }
                });
            }
            Button cancelBtn = (Button) dialogPane.lookupButton(cancelButtonType);
            if (cancelBtn != null) {
                cancelBtn.setText("Annulla");
                cancelBtn.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:8 14;");
            }
        });
    }

    /* ==== API ==== */

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

    public ButtonType getCreateButtonType() { return createButtonType; }

    /* ==== Logica ==== */

    private void addNewArgomento() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nuovo Argomento");
        dialog.setHeaderText("Inserisci il nuovo argomento");
        dialog.setContentText("Argomento:");

        // Tema scuro coerente
        DialogPane pane = dialog.getDialogPane();
        pane.setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b); " +
                      "-fx-border-color: rgba(255,255,255,0.06); -fx-border-width:1; -fx-border-radius:12; -fx-background-radius:12;");
        styleButtonsIn(pane);

        Optional<String> res = dialog.showAndWait();
        if (res.isEmpty()) return;
        String trimmed = res.get().trim();

        // validazione semplice: solo lettere e spazi
        if (trimmed.isEmpty() || !trimmed.matches("[a-zA-ZÀ-ÖØ-öø-ÿ ]+")) {
            Alert a = new Alert(Alert.AlertType.WARNING, "L'argomento deve contenere solo lettere e spazi.", ButtonType.OK);
            styleAlert(a);
            a.showAndWait();
            return;
        }
        if (cbArg.getItems().contains(trimmed)) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "L'argomento \"" + trimmed + "\" esiste già.", ButtonType.OK);
            styleAlert(a);
            a.showAndWait();
            return;
        }
        cbArg.getItems().add(trimmed);
        FXCollections.sort(cbArg.getItems());
        cbArg.setValue(trimmed);
    }

    private void updateDataFine() {
        lblFine.setText(formatOrDash(
                computeDataFine(dpInizio.getValue(), cbFreq.getValue(), safeSpinnerValue())
        ));
    }
    private int safeSpinnerValue() {
        Integer v = spNumSess.getValue(); return v == null ? 0 : v;
    }
    private static String formatOrDash(LocalDate d) { return d == null ? "—" : d.toString(); }

    private static LocalDate computeDataFine(LocalDate inizio, String frequenza, int numSessioni) {
        if (inizio == null || numSessioni <= 0) return null;
        int steps = Math.max(0, numSessioni - 1);
        String f = (frequenza == null) ? "" : frequenza.trim().toLowerCase(Locale.ROOT);
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

    /* ==== Validazione ==== */
    private boolean isFormValid() {
        return cbArg.getValue() != null && !cbArg.getValue().isBlank()
            && cbFreq.getValue() != null
            && dpInizio.getValue() != null
            && safeSpinnerValue() > 0;
    }

    private void showValidationMessage() {
        Alert a = new Alert(Alert.AlertType.WARNING, "Compila correttamente tutti i campi.", ButtonType.OK);
        styleAlert(a);
        a.showAndWait();
    }

    /* ==== Stile helpers (senza CSS esterno) ==== */

    private void tintDatePickerDark(DatePicker dp) {
        if (dp == null) return;
        dp.setShowWeekNumbers(false);
        dp.setEditable(false);
        dp.setStyle("-fx-background-color:#2b3438; -fx-control-inner-background:#2b3438;" +
                    "-fx-text-fill:#e9f5ec; -fx-prompt-text-fill: rgba(233,245,236,0.70); " +
                    "-fx-background-radius:10; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius:10; -fx-padding:4 8;");
        if (dp.getEditor() != null) {
            dp.getEditor().setStyle("-fx-background-color: transparent; -fx-text-fill:#e9f5ec;");
        }
        // popup scuro
        dp.setOnShowing(ev -> Platform.runLater(() -> {
            Node popup = dp.lookup(".date-picker-popup");
            if (popup != null)
                popup.setStyle("-fx-background-color:#20282b; -fx-background-radius:10; " +
                               "-fx-border-color: rgba(255,255,255,0.06); -fx-border-radius:10;");
            Node header = dp.lookup(".month-year-pane");
            if (header != null) header.setStyle("-fx-background-color:#242c2f; -fx-text-fill:#e9f5ec;");
        }));
    }

    private void styleButtonsIn(DialogPane pane) {
        Button ok = (Button) pane.lookupButton(ButtonType.OK);
        if (ok != null) ok.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800; -fx-background-radius:10; -fx-padding:6 12;");
        Button cancel = (Button) pane.lookupButton(ButtonType.CANCEL);
        if (cancel != null) cancel.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:6 12;");
    }

    private void styleAlert(Alert a) {
        DialogPane p = a.getDialogPane();
        p.setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
                   "-fx-border-color: rgba(255,255,255,0.06); -fx-border-width:1; -fx-border-radius:12; -fx-background-radius:12;");
        styleButtonsIn(p);
        p.setMinWidth(420);
    }
    
    /** Forza celle scure e testo chiaro su una ComboBox (display + popup). */
    private static void installComboDarkCells(ComboBox<String> combo, String textColorHex, String popupBgHex) {
        if (combo == null) return;

        // cella del "display area"
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : item);
                setTextFill(javafx.scene.paint.Color.web(textColorHex));
                setStyle("-fx-background-color: transparent; -fx-font-weight: 700;"); // più leggibile
            }
        });

        // celle del popup
        combo.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTextFill(javafx.scene.paint.Color.web(textColorHex));
                setStyle("-fx-background-color: " + popupBgHex + ";");
            }
        });

        // forziamo l'opacità del controllo (evita effetto "disabled")
        combo.setOpacity(1.0);
    }

    /** Le ChoiceBox non hanno buttonCell: forziamo il colore del label interno. */
    private static void forceChoiceBoxLabelColor(ChoiceBox<String> cb, String textColorHex) {
        if (cb == null) return;
        cb.setStyle(cb.getStyle() + "; -fx-opacity: 1.0;"); // evita look disabilitato

        // Applica subito dopo la creazione della skin
        cb.skinProperty().addListener((obs, oldSkin, newSkin) -> tweakChoiceBoxLabel(cb, textColorHex));
        // e ogni volta che cambia il valore, riapplica (alcune skin ricreano il label)
        cb.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> tweakChoiceBoxLabel(cb, textColorHex));

        // prima applicazione post-layout
        javafx.application.Platform.runLater(() -> tweakChoiceBoxLabel(cb, textColorHex));
    }

    private static void tweakChoiceBoxLabel(ChoiceBox<String> cb, String textColorHex) {
        Node lblNode = cb.lookup(".label");
        if (lblNode instanceof Labeled l) {
            l.setTextFill(javafx.scene.paint.Color.web(textColorHex));
            l.setStyle("-fx-text-fill:" + textColorHex + "; -fx-font-weight: 700;"); // più contrasto
        }
    }

    /** Nasconde il pulsante a destra del DatePicker (lasciamo solo l’icona a sinistra nell’HBox). */
    private static void hideDatePickerArrow(DatePicker dp) {
        if (dp == null) return;
        javafx.application.Platform.runLater(() -> {
            Node arrowBtn = dp.lookup(".arrow-button");
            if (arrowBtn instanceof Region r) {
                r.setVisible(false);
                r.setManaged(false);
                r.setPrefWidth(0);
                r.setMinWidth(0);
                r.setMaxWidth(0);
            }
        });
    }

    /** Cliccando sul campo del DatePicker si apre il popup calendario. */
    private static void dpInitoShowOnClick(DatePicker dp) {
        if (dp == null) return;
        dp.setOnMouseClicked(ev -> {
            if (!dp.isShowing()) dp.show();
        });
        // anche con TAB->Enter
        dp.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case ENTER:
                case SPACE:
                    if (!dp.isShowing()) dp.show();
                    break;
                default: break;
            }
        });
    }

    /** Rende scure le frecce di uno Spinner (senza CSS esterno). */
    private static void styleSpinnerArrowsDark(Spinner<?> sp, String btnBgHex, String arrowColorHex) {
        if (sp == null) return;
        javafx.application.Platform.runLater(() -> {
            Node incBtn = sp.lookup(".increment-arrow-button");
            Node decBtn = sp.lookup(".decrement-arrow-button");
            if (incBtn instanceof Region r1) r1.setStyle("-fx-background-color:" + btnBgHex + "; -fx-background-radius:8;");
            if (decBtn instanceof Region r2) r2.setStyle("-fx-background-color:" + btnBgHex + "; -fx-background-radius:8;");

            Node incArrow = sp.lookup(".increment-arrow");
            Node decArrow = sp.lookup(".decrement-arrow");
            if (incArrow instanceof Region a1) a1.setStyle("-fx-background-color:" + arrowColorHex + ";");
            if (decArrow instanceof Region a2) a2.setStyle("-fx-background-color:" + arrowColorHex + ";");
        });
    }
    
    
    

    
}
