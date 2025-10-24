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

    @FXML
    private void initialize() {
        
    	if (cbArg != null && (cbArg.getItems() == null || cbArg.getItems().isEmpty())) {
    	    cbArg.setItems(FXCollections.observableArrayList(
    	        "Cucina Asiatica","Pasticceria","Panificazione","Vegetariano",
    	        "Street Food","Dolci al cucchiaio","Cucina Mediterranea",
    	        "Finger Food","Fusion","Vegan"
    	    ));
    	}

        if (cbFreq != null) {
            cbFreq.setItems(FXCollections.observableArrayList(
                    "settimanale","ogni 2 giorni","bisettimanale","mensile"
            ));
        }
        if (spNumSess != null) {
            spNumSess.setValueFactory(new IntegerSpinnerValueFactory(1, 365, 5));
            spNumSess.setEditable(true);
           
            spNumSess.focusedProperty().addListener((obs, was, is) -> { if (!is) spNumSess.increment(0); });
        }

       
        tintDatePickerDark(dpInizio);
        if (dpInizio != null) dpInizio.setValue(LocalDate.now().plusDays(7));
        if (cbFreq != null) cbFreq.setValue("settimanale");
        updateDataFine(); 

      
        if (cbFreq != null) cbFreq.valueProperty().addListener((o,a,b) -> updateDataFine());
        if (dpInizio != null) dpInizio.valueProperty().addListener((o,a,b) -> updateDataFine());
        if (spNumSess != null) spNumSess.valueProperty().addListener((o,a,b) -> updateDataFine());

       
        if (btnAddArg != null) {
            btnAddArg.setText("");
            btnAddArg.setMnemonicParsing(false);
            btnAddArg.setTooltip(new Tooltip("Aggiungi argomento"));
            btnAddArg.setOnAction(e -> addNewArgomento());
        }

   
        installComboDarkCells(cbArg, "#e9f5ec", "#2b3438");
        forceChoiceBoxLabelColor(cbFreq, "#e9f5ec");
        if (cbArg != null) cbArg.setOpacity(1.0);
        if (cbFreq != null) cbFreq.setOpacity(1.0);

       
        hideDatePickerArrow(dpInizio);
        showDatePickerOnClick(dpInizio);

       
        styleSpinnerArrowsDark(spNumSess, "#2b3438", "#e9f5ec");

       
        installFocusBorder(cbArg);
        installFocusBorder(cbFreq);

      
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

   

    public void setCorso(Corso corso) {
        this.original = corso;
        this.edit = (corso != null);
        if (edit && corso != null) {
            if (cbArg != null) cbArg.setValue(corso.getArgomento());
            if (cbFreq != null) cbFreq.setValue(corso.getFrequenza());
            if (spNumSess != null && spNumSess.getValueFactory() != null)
                spNumSess.getValueFactory().setValue(corso.getNumSessioni());
            if (dpInizio != null) dpInizio.setValue(corso.getDataInizio());
            if (lblFine != null) lblFine.setText(formatOrDash(corso.getDataFine()));
        }
    }

    public Corso getResult() {
        Corso c = edit ? clone(original) : new Corso();
        c.setArgomento(cbArg != null ? cbArg.getValue() : null);
        c.setFrequenza(cbFreq != null ? cbFreq.getValue() : null);
        c.setDataInizio(dpInizio != null ? dpInizio.getValue() : null);
        c.setNumSessioni(safeSpinnerValue());
        c.setDataFine(computeDataFine(c.getDataInizio(), c.getFrequenza(), c.getNumSessioni()));
        return c;
    }

    public ButtonType getCreateButtonType() { return createButtonType; }

   

  private void addNewArgomento() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Nuovo Argomento");
    dialog.setHeaderText("Inserisci il nuovo argomento");
    dialog.setContentText("Argomento:");

   
    styleTextInputDark(dialog);
    styleDarkTextField(dialog.getEditor(), "Es. Pasticceria avanzata");

    Optional<String> res = dialog.showAndWait();
    if (res.isEmpty()) return;
    String trimmed = res.get().trim();

    
    if (trimmed.isEmpty() || !trimmed.matches("[a-zA-ZÀ-ÖØ-öø-ÿ ]+")) {
        showWarningDark("Avvertenza", "L'argomento deve contenere solo lettere e spazi.");
        return;
    }

    
    javafx.collections.ObservableList<String> target =
            (argomentiShared != null ? argomentiShared : cbArg.getItems());
    if (target.contains(trimmed)) {
        showWarningDark("Informazione", "L'argomento \"" + trimmed + "\" esiste già.");
        return;
    }

  
    target.add(trimmed);
    FXCollections.sort(target);
    cbArg.setItems(target);
    cbArg.setValue(trimmed);
}



    private void updateDataFine() {
        if (lblFine == null) return;
        LocalDate fine = computeDataFine(
                (dpInizio != null) ? dpInizio.getValue() : null,
                (cbFreq != null) ? cbFreq.getValue() : null,
                safeSpinnerValue()
        );
        lblFine.setText(formatOrDash(fine));
    }

    private int safeSpinnerValue() {
        if (spNumSess == null) return 0;
        Integer v = spNumSess.getValue();
        return (v == null) ? 0 : v;
    }

    private String formatOrDash(LocalDate d) {
        return (d == null) ? "—" : UI_DF.format(d);
    }

    private static LocalDate computeDataFine(LocalDate inizio, String frequenza, int numSessioni) {
        if (inizio == null || numSessioni <= 0) return null;
        int steps = Math.max(0, numSessioni - 1);
        String f = (frequenza == null) ? "" : frequenza.trim().toLowerCase(Locale.ROOT);
        return switch (f) {
            case "ogni 2 giorni" -> inizio.plusDays(2L * steps);
            case "bisettimanale" -> inizio.plusWeeks(2L * steps);
            case "mensile"       -> inizio.plusMonths(steps);
            default              -> inizio.plusWeeks(steps);  
        };
    }

    private static Corso clone(Corso src) {
        Corso c = new Corso();
        if (src == null) return c;
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
        return cbArg != null && cbArg.getValue() != null && !cbArg.getValue().isBlank()
            && cbFreq != null && cbFreq.getValue() != null
            && dpInizio != null && dpInizio.getValue() != null
            && safeSpinnerValue() > 0;
    }

    private void showValidationMessage() {
        showWarningDark("Avvertenza", "Compila correttamente tutti i campi.");
    }


   
    private void tintDatePickerDark(DatePicker dp) {
        if (dp == null) return;
        dp.setShowWeekNumbers(false);
        dp.setEditable(false);
        dp.setStyle("-fx-background-color:#2b3438; -fx-control-inner-background:#2b3438;" +
                    "-fx-text-fill:#e9f5ec; -fx-prompt-text-fill: rgba(233,245,236,0.70); " +
                    "-fx-background-radius:10; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius:10; -fx-padding:4 8;" +
                    "-fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-accent: transparent;");
        if (dp.getEditor() != null) {
            dp.getEditor().setStyle("-fx-background-color: transparent; -fx-text-fill:#e9f5ec;" +
                                    "-fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-accent: transparent;");
        }
       
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

   
    private static void installComboDarkCells(ComboBox<String> combo, String textColorHex, String popupBgHex) {
        if (combo == null) return;

        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : item);
                setTextFill(javafx.scene.paint.Color.web(textColorHex));
                setStyle("-fx-background-color: transparent; -fx-font-weight: 700;");
            }
        });

        combo.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTextFill(javafx.scene.paint.Color.web(textColorHex));
                setStyle("-fx-background-color: " + popupBgHex + ";");
            }
        });

        combo.setOpacity(1.0);
    }

    
    private static void forceChoiceBoxLabelColor(ChoiceBox<String> cb, String textColorHex) {
        if (cb == null) return;
        cb.setStyle("-fx-opacity: 1.0;");

        cb.skinProperty().addListener((obs, oldSkin, newSkin) -> tweakChoiceBoxLabel(cb, textColorHex));
        cb.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> tweakChoiceBoxLabel(cb, textColorHex));

        Platform.runLater(() -> tweakChoiceBoxLabel(cb, textColorHex));
    }

    private static void tweakChoiceBoxLabel(ChoiceBox<String> cb, String textColorHex) {
        Node lblNode = cb.lookup(".label");
        if (lblNode instanceof Labeled l) {
            l.setTextFill(javafx.scene.paint.Color.web(textColorHex));
            l.setStyle("-fx-text-fill:" + textColorHex + "; -fx-font-weight: 700;");
        }
    }

  
    private static void hideDatePickerArrow(DatePicker dp) {
        if (dp == null) return;
        Platform.runLater(() -> {
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

   
    private static void showDatePickerOnClick(DatePicker dp) {
        if (dp == null) return;
        dp.setOnMouseClicked(ev -> { if (!dp.isShowing()) dp.show(); });
        dp.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case ENTER: case SPACE:
                    if (!dp.isShowing()) dp.show();
                    break;
                default: break;
            }
        });
    }

    
    private static void styleSpinnerArrowsDark(Spinner<?> sp, String btnBgHex, String arrowColorHex) {
        if (sp == null) return;
        Platform.runLater(() -> {
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

    
    private static void installFocusBorder(Control c) {
        if (c == null) return;
        final String base = "-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-background-radius:10; -fx-border-color: rgba(255,255,255,0.06); -fx-border-radius:10;";
        c.setStyle(base);
        c.focusedProperty().addListener((o, was, is) -> {
            c.setStyle(is
                    ? base + " -fx-border-color:#1fb57a; -fx-border-width:1;"
                    : base);
        });
    }
    
   
    private void showWarningDark(String titolo, String messaggio) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titolo);
        alert.setHeaderText(null);        
        alert.setGraphic(null);           

       
        Label content = new Label(messaggio);
        content.setWrapText(true);
        content.setStyle("-fx-text-fill:#e9f5ec; -fx-font-size:13px; -fx-font-weight:700;");
        alert.getDialogPane().setContent(content);

       
        DialogPane pane = alert.getDialogPane();
        pane.setStyle(
            "-fx-background-color: linear-gradient(to bottom,#1b2427,#152022);" +
            "-fx-background-radius:12;" +
            "-fx-border-color: rgba(255,255,255,0.08);" +
            "-fx-border-radius:12;" +
            "-fx-border-width:1;" +
            "-fx-padding:14;" +
            "-fx-focus-color: transparent;" +
            "-fx-faint-focus-color: transparent;" +
            "-fx-accent: transparent;"
        );

       
        Node header = pane.lookup(".header-panel");
        if (header != null) header.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        Node graphicContainer = pane.lookup(".graphic-container");
        if (graphicContainer != null) graphicContainer.setStyle("-fx-background-color: transparent;");

      
        for (ButtonType bt : alert.getButtonTypes()) {
            Button b = (Button) pane.lookupButton(bt);
            if (b != null) {
                b.setStyle(
                    "-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;" +
                    "-fx-background-radius:10; -fx-padding:8 16;"
                );
                b.setOnMouseEntered(e -> b.setStyle(
                    "-fx-background-color:#16a56e; -fx-text-fill:#0a1410; -fx-font-weight:800;" +
                    "-fx-background-radius:10; -fx-padding:8 16;"
                ));
                b.setOnMouseExited(e -> b.setStyle(
                    "-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;" +
                    "-fx-background-radius:10; -fx-padding:8 16;"
                ));
            }
        }

       
        pane.setMinWidth(460);

        alert.showAndWait();
    }

   
    private void styleTextInputDark(TextInputDialog dlg) {
        dlg.setHeaderText(null);   
        dlg.setGraphic(null);      

        DialogPane pane = dlg.getDialogPane();
        pane.setStyle(
            "-fx-background-color: linear-gradient(to bottom,#1b2427,#152022);" +
            "-fx-background-radius:12;" +
            "-fx-border-color: rgba(255,255,255,0.08);" +
            "-fx-border-radius:12;" +
            "-fx-border-width:1;" +
            "-fx-padding:14;" +
            "-fx-focus-color: transparent;" +
            "-fx-faint-focus-color: transparent;" +
            "-fx-accent: transparent;"
        );
        pane.setMinWidth(460);

     
        Node header = pane.lookup(".header-panel");
        if (header != null) header.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        Node graphic = pane.lookup(".graphic-container");
        if (graphic != null) graphic.setStyle("-fx-background-color: transparent;");

        Node contentLbl = pane.lookup(".content.label");
        if (contentLbl instanceof Label l) {
            l.setStyle("-fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-font-size:13px;");
        }


        for (ButtonType bt : pane.getButtonTypes()) {
            Button b = (Button) pane.lookupButton(bt);
            if (b == null) continue;
            if (bt.getButtonData().isDefaultButton()) {
                b.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 16;");
                b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:#16a56e; -fx-text-fill:#0a1410; -fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 16;"));
                b.setOnMouseExited (e -> b.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 16;"));
            } else {
                b.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:8 16;");
                b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:#374047; -fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:8 16;"));
                b.setOnMouseExited (e -> b.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:8 16;"));
            }
        }
    }

    
    private void styleDarkTextField(TextField tf, String prompt) {
        if (tf == null) return;
        tf.setPromptText(prompt);
        tf.setStyle(
            "-fx-background-color:#2e3845;" +
            "-fx-control-inner-background:#2e3845;" +
            "-fx-text-fill:#e9f5ec;" +
            "-fx-prompt-text-fill: rgba(255,255,255,0.65);" +
            "-fx-background-radius:8;" +
            "-fx-border-color:#3a4657;" +
            "-fx-border-radius:8;" +
            "-fx-padding:6 10;" +
            "-fx-focus-color: transparent;" +
            "-fx-faint-focus-color: transparent;" +
            "-fx-accent: transparent;"
        );
    }
    
    private javafx.collections.ObservableList<String> argomentiShared;

    public void bindArgomenti(javafx.collections.ObservableList<String> shared) {
        this.argomentiShared = shared;
        if (cbArg != null) cbArg.setItems(shared);
    }


}