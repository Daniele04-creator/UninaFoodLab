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
import java.util.Objects;

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
    private javafx.collections.ObservableList<String> argomentiShared;

    @FXML
    private void initialize() {
        if (dialogPane != null) {
            dialogPane.getStyleClass().add("corso-editor-dialog");
            dialogPane.getStylesheets().add(
                Objects.requireNonNull(
                    getClass().getResource("/it/unina/foodlab/util/dark-theme.css")).toExternalForm());
        }

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

        if (cbArg != null) cbArg.getStyleClass().add("dark-combobox");
        if (cbFreq != null) cbFreq.getStyleClass().add("dark-choicebox");
        if (dpInizio != null) dpInizio.getStyleClass().add("dark-datepicker");

        if (cbArg != null) cbArg.setOpacity(1.0);
        if (cbFreq != null) cbFreq.setOpacity(1.0);

        hideDatePickerArrow(dpInizio);
        showDatePickerOnClick(dpInizio);

        Platform.runLater(() -> {
            Button okBtn = (Button) dialogPane.lookupButton(createButtonType);
            if (okBtn != null) {
                okBtn.setText("Crea");
                okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
                    if (!isFormValid()) { evt.consume(); showValidationMessage(); }
                });
            }
            Button cancelBtn = (Button) dialogPane.lookupButton(cancelButtonType);
            if (cancelBtn != null) {
                cancelBtn.setText("Annulla");
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
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("text-input-dark-dialog");
        pane.getStylesheets().add(
            Objects.requireNonNull(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css")
            ).toExternalForm()
        );
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

    private void showWarningDark(String titolo, String messaggio) {
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
            Objects.requireNonNull(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css")
            ).toExternalForm()
        );
        pane.setMinWidth(460);
        alert.showAndWait();
    }

    public void bindArgomenti(javafx.collections.ObservableList<String> shared) {
        this.argomentiShared = shared;
        if (cbArg != null) cbArg.setItems(shared);
    }
}
