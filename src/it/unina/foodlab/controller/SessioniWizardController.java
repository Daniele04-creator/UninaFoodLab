package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Wizard di configurazione/edizione delle sessioni di un Corso.
 * Niente Stream/Lambda: codice lineare e chiaro per l'esame.
 */
public class SessioniWizardController {

    /* ---------- UI dal FXML ---------- */
    @FXML private DialogPane dialogPane;
    @FXML private ButtonType okButtonType;
    @FXML private ButtonType cancelButtonType;

    @FXML private TableView<SessionDraft> table;
    @FXML private TableColumn<SessionDraft, String> cData;
    @FXML private TableColumn<SessionDraft, String> cInizio;
    @FXML private TableColumn<SessionDraft, String> cFine;
    @FXML private TableColumn<SessionDraft, String> cTipo;
    @FXML private TableColumn<SessionDraft, String> cPiattaforma;
    @FXML private TableColumn<SessionDraft, String> cVia;
    @FXML private TableColumn<SessionDraft, String> cNum;
    @FXML private TableColumn<SessionDraft, String> cCap;
    @FXML private TableColumn<SessionDraft, String> cAula;
    @FXML private TableColumn<SessionDraft, String> cPosti;

    /* Bottoni “interni” (se presenti nell’FXML, resi invisibili) */
    @FXML private Button btnAdd;
    @FXML private Button btnRemove;

    /* ---------- Stato ---------- */
    private Corso corso;

    /** Modello temporaneo per riga */
    static class SessionDraft {
        LocalDate data;
        String oraInizio = "";
        String oraFine   = "";
        String tipo = "";        // "Online" | "In presenza"
        String piattaforma = ""; // solo Online
        String via = "";         // solo Presenza
        String num = "";
        String cap = "";         // 5 cifre
        String aula = "";
        String postiMax = "";    // 0/blank = illimitati
    }

    private static final DateTimeFormatter TF    = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /* layout */
    private static final double ROW_HEIGHT    = 32;
    private static final double EDITOR_HEIGHT = 28;

    /* ========= Inizializzazione ========= */
    @FXML
    private void initialize() {
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Riga fissa
        table.setFixedCellSize(ROW_HEIGHT);
        table.setRowFactory(new javafx.util.Callback<TableView<SessionDraft>, TableRow<SessionDraft>>() {
            @Override public TableRow<SessionDraft> call(TableView<SessionDraft> tv) {
                TableRow<SessionDraft> r = new TableRow<SessionDraft>();
                r.setPrefHeight(ROW_HEIGHT);
                r.setMinHeight(ROW_HEIGHT);
                r.setMaxHeight(ROW_HEIGHT);
                return r;
            }
        });

        /* DATA: NON editabile (mostra solo ISO yyyy-MM-dd) */
        cData.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue() == null || cd.getValue().data == null
                        ? "" : cd.getValue().data.toString()));
        cData.setCellFactory(new javafx.util.Callback<TableColumn<SessionDraft, String>, TableCell<SessionDraft, String>>() {
            @Override public TableCell<SessionDraft, String> call(TableColumn<SessionDraft, String> col) {
                return new TableCell<SessionDraft, String>() {
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty ? null : item);
                        setGraphic(null);
                    }
                };
            }
        });

        /* ORA inizio/fine con ComboBox (step 15') */
        makeTimePickerColumn(cInizio, "inizio");
        makeTimePickerColumn(cFine,   "fine");

        /* MODALITÀ */
        cTipo.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tipo));
        cTipo.setCellFactory(new javafx.util.Callback<TableColumn<SessionDraft, String>, TableCell<SessionDraft, String>>() {
            @Override public TableCell<SessionDraft, String> call(TableColumn<SessionDraft, String> col) {
                return new TipoCell();
            }
        });

        /* Colonne condizionali (visibili solo quando serve) */
        makeConditionalTextColumn(cPiattaforma, true,  "piattaforma"); // solo se tipo==Online
        makeConditionalTextColumn(cVia,         false, "via");         // solo se tipo==In presenza
        makeConditionalTextColumn(cNum,         false, "num");
        makeConditionalTextColumn(cCap,         false, "cap");
        makeConditionalTextColumn(cAula,        false, "aula");
        makeConditionalTextColumn(cPosti,       false, "postiMax");

        /* Larghezze minime */
        cData.setMinWidth(160);
        cInizio.setMinWidth(140); cInizio.setPrefWidth(140);
        cFine.setMinWidth(140);   cFine.setPrefWidth(140);
        cTipo.setMinWidth(170);
        cPiattaforma.setMinWidth(160);
        cVia.setMinWidth(140); cNum.setMinWidth(80); cCap.setMinWidth(80);
        cAula.setMinWidth(140); cPosti.setMinWidth(100);

        weightColumns();
        Platform.runLater(new Runnable() {
            @Override public void run() {
                if (table.getParent() instanceof javafx.scene.layout.VBox) {
                    javafx.scene.layout.VBox.setVgrow(table, Priority.ALWAYS);
                }
                autosizeColumnsToHeader();
            }
        });

        dialogPane.setPrefSize(1300, 760);
        dialogPane.setMinSize(1100, 650);
        dialogPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        if (btnAdd != null)   { btnAdd.setVisible(false);   btnAdd.setManaged(false); }
        if (btnRemove != null){ btnRemove.setVisible(false);btnRemove.setManaged(false); }

        setupButtonBar();

        Platform.runLater(new Runnable() {
            @Override public void run() {
                setupOkCancelButtons();
            }
        });
    }

    /* ========= API ========= */

    public void initWithCorso(Corso corso) {
        if (corso == null) throw new IllegalArgumentException("corso nullo");
        this.corso = corso;

        int n = Math.max(1, corso.getNumSessioni());
        java.util.List<SessionDraft> list = buildDrafts(corso, n);
        table.setItems(FXCollections.observableArrayList(list));
    }

    public void initWithCorsoAndExisting(Corso corso, java.util.List<Sessione> esistenti) {
        if (corso == null) throw new IllegalArgumentException("corso nullo");
        this.corso = corso;

        java.util.List<SessionDraft> drafts = new java.util.ArrayList<SessionDraft>();
        if (esistenti != null) {
            for (int i = 0; i < esistenti.size(); i++) {
                Sessione s = esistenti.get(i);
                SessionDraft d = new SessionDraft();
                d.data = s.getData();
                d.oraInizio = (s.getOraInizio() == null) ? "10:00" : s.getOraInizio().toString();
                d.oraFine   = (s.getOraFine()   == null) ? "12:00" : s.getOraFine().toString();

                if (s instanceof SessioneOnline) {
                    SessioneOnline so = (SessioneOnline) s;
                    d.tipo = "Online";
                    d.piattaforma = nz(so.getPiattaforma());
                } else if (s instanceof SessionePresenza) {
                    SessionePresenza sp = (SessionePresenza) s;
                    d.tipo = "In presenza";
                    d.via  = nz(sp.getVia());
                    d.num  = nz(sp.getNum());
                    d.cap  = sp.getCap() > 0 ? String.valueOf(sp.getCap()) : "";
                    d.aula = nz(sp.getAula());
                    d.postiMax = sp.getPostiMax() > 0 ? String.valueOf(sp.getPostiMax()) : "";
                } else {
                    d.tipo = "Online";
                }
                drafts.add(d);
            }
        }
        if (drafts.isEmpty()) {
            drafts.addAll(buildDrafts(corso, Math.max(1, corso.getNumSessioni())));
        }
        table.setItems(FXCollections.observableArrayList(drafts));
    }

    public void addBlankRowAfterLast() {
        ObservableList<SessionDraft> items = table.getItems();
        SessionDraft d = new SessionDraft();

        // Base = max data esistente, altrimenti dataInizio corso o oggi
        LocalDate base = null;
        for (int i = 0; i < items.size(); i++) {
            SessionDraft it = items.get(i);
            if (it != null && it.data != null) {
                if (base == null || it.data.isAfter(base)) base = it.data;
            }
        }
        if (base == null) base = (corso != null && corso.getDataInizio() != null) ? corso.getDataInizio() : LocalDate.now();
        d.data = base.plusDays(7);
        d.tipo = "Online";

        items.add(d);
        table.getSelectionModel().clearSelection();
        table.getSelectionModel().select(items.size() - 1);
        table.scrollTo(items.size() - 1);
    }

    public void removeSelectedRows() {
        ObservableList<Integer> idx = table.getSelectionModel().getSelectedIndices();
        if (idx == null || idx.isEmpty()) return;

        java.util.List<Integer> copy = new java.util.ArrayList<Integer>(idx);
        java.util.Collections.sort(copy);
        // rimuovi dal fondo per non shiftare
        for (int i = copy.size() - 1; i >= 0; i--) {
            int at = copy.get(i).intValue();
            if (at >= 0 && at < table.getItems().size()) {
                table.getItems().remove(at);
            }
        }
    }

    /** Costruisce il risultato. La validazione avviene prima dell'OK. */
    public java.util.List<Sessione> buildResult() {
        java.util.List<Sessione> result = new java.util.ArrayList<Sessione>();
        ObservableList<SessionDraft> items = table.getItems();
        for (int i = 0; i < items.size(); i++) {
            SessionDraft d = items.get(i);

            LocalTime t1 = LocalTime.parse(d.oraInizio.trim(), TF);
            LocalTime t2 = LocalTime.parse(d.oraFine.trim(), TF);

            if ("Online".equals(d.tipo)) {
                result.add(new SessioneOnline(0, d.data, t1, t2, corso, d.piattaforma));
            } else {
                int cap = Integer.parseInt(d.cap.trim()); // validato a priori
                int posti = 0;
                if (!isBlank(d.postiMax)) posti = Integer.parseInt(d.postiMax.trim());
                result.add(new SessionePresenza(0, d.data, t1, t2, corso, d.via, d.num, cap, d.aula, posti));
            }
        }
        return result;
    }

    /* ========= Colonne ========= */

    private void makeConditionalTextColumn(final TableColumn<SessionDraft,String> col,
                                           final boolean forOnline,
                                           final String fieldName) {
        col.setCellValueFactory(cd -> new SimpleStringProperty(getField(cd.getValue(), fieldName)));
        col.setCellFactory(new javafx.util.Callback<TableColumn<SessionDraft, String>, TableCell<SessionDraft, String>>() {
            @Override public TableCell<SessionDraft, String> call(TableColumn<SessionDraft, String> tc) {
                return new TableCell<SessionDraft, String>() {
                    private final TextField tf = new TextField();
                    private final HBox wrapper = new HBox(tf);
                    {
                        tf.getStyleClass().addAll("cell-editor", "sessione-cell");
                        tf.setMaxWidth(Double.MAX_VALUE);
                        HBox.setHgrow(tf, Priority.ALWAYS);
                        tf.setPrefHeight(EDITOR_HEIGHT);
                        tf.setMinHeight(Region.USE_PREF_SIZE);
                        tf.setMaxHeight(Region.USE_PREF_SIZE);

                        tf.textProperty().addListener(new javafx.beans.value.ChangeListener<String>() {
                            @Override public void changed(javafx.beans.value.ObservableValue<? extends String> o, String a, String b) {
                                int i = getIndex();
                                TableView<SessionDraft> tv = getTableView();
                                if (tv != null && i >= 0 && i < tv.getItems().size()) {
                                    SessionDraft row = tv.getItems().get(i);
                                    setField(row, fieldName, b);
                                }
                            }
                        });
                    }
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) { setGraphic(null); return; }
                        int i = getIndex();
                        TableView<SessionDraft> tv = getTableView();
                        if (tv == null || i < 0 || i >= tv.getItems().size()) { setGraphic(null); return; }

                        SessionDraft row = tv.getItems().get(i);
                        boolean show = (forOnline && "Online".equals(row.tipo)) || (!forOnline && "In presenza".equals(row.tipo));
                        if (!show) { setGraphic(null); return; }

                        tf.setText(item == null ? "" : item);
                        setGraphic(wrapper);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                };
            }
        });
    }

    private void makeTimePickerColumn(final TableColumn<SessionDraft, String> col,
                                      final String which /* "inizio" | "fine" */) {
        final ObservableList<LocalTime> TIMES = buildTimes(15);
        col.setCellValueFactory(cd -> new SimpleStringProperty("inizio".equals(which) ? cd.getValue().oraInizio : cd.getValue().oraFine));
        col.setCellFactory(new javafx.util.Callback<TableColumn<SessionDraft, String>, TableCell<SessionDraft, String>>() {
            @Override public TableCell<SessionDraft, String> call(TableColumn<SessionDraft, String> tc) {
                return new TableCell<SessionDraft, String>() {
                    private final ComboBox<LocalTime> cb = new ComboBox<LocalTime>(TIMES);
                    private final HBox wrapper = new HBox(cb);
                    {
                        cb.getStyleClass().addAll("cell-editor", "sessione-cell", "time-picker");
                        cb.setVisibleRowCount(10);
                        cb.setPrefHeight(EDITOR_HEIGHT);
                        cb.setMaxWidth(Double.MAX_VALUE);
                        HBox.setHgrow(cb, Priority.ALWAYS);
                        cb.setEditable(true);

                        // Converter "HH:mm" + tolleranza "H:mm"
                        cb.setConverter(new javafx.util.StringConverter<LocalTime>() {
                            @Override public String toString(LocalTime t) {
                                return t == null ? "" : t.format(HH_MM);
                            }
                            @Override public LocalTime fromString(String s) {
                                if (s == null) return null;
                                String x = s.trim();
                                if (x.isEmpty()) return null;
                                try { return LocalTime.parse(x, TF); }
                                catch (Exception e1) {
                                    try { return LocalTime.parse(x, HH_MM); }
                                    catch (Exception e2) { return null; }
                                }
                            }
                        });

                        cb.setCellFactory(new javafx.util.Callback<ListView<LocalTime>, ListCell<LocalTime>>() {
                            @Override public ListCell<LocalTime> call(ListView<LocalTime> lv) {
                                return new ListCell<LocalTime>() {
                                    @Override protected void updateItem(LocalTime it, boolean empty) {
                                        super.updateItem(it, empty);
                                        setText(empty || it == null ? "" : it.format(HH_MM));
                                    }
                                };
                            }
                        });
                        cb.setButtonCell(new ListCell<LocalTime>() {
                            @Override protected void updateItem(LocalTime it, boolean empty) {
                                super.updateItem(it, empty);
                                setText(empty || it == null ? "" : it.format(HH_MM));
                            }
                        });

                        cb.valueProperty().addListener(new javafx.beans.value.ChangeListener<LocalTime>() {
                            @Override public void changed(javafx.beans.value.ObservableValue<? extends LocalTime> o, LocalTime oldV, LocalTime newV) {
                                int i = getIndex();
                                TableView<SessionDraft> tv = getTableView();
                                if (tv != null && i >= 0 && i < tv.getItems().size()) {
                                    SessionDraft row = tv.getItems().get(i);
                                    String val = (newV == null) ? "" : newV.format(HH_MM);
                                    if ("inizio".equals(which)) row.oraInizio = val; else row.oraFine = val;
                                }
                            }
                        });

                        final Runnable commitEditorText = new Runnable() {
                            @Override public void run() {
                                String txt = cb.getEditor().getText();
                                LocalTime parsed = cb.getConverter().fromString(txt);
                                cb.setValue(parsed);
                            }
                        };
                        cb.getEditor().setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
                            @Override public void handle(javafx.event.ActionEvent e) { commitEditorText.run(); }
                        });
                        cb.getEditor().focusedProperty().addListener(new javafx.beans.value.ChangeListener<Boolean>() {
                            @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> obs, Boolean was, Boolean isNow) {
                                if (!Boolean.TRUE.equals(isNow)) commitEditorText.run();
                            }
                        });

                        wrapper.setOnMouseClicked(new javafx.event.EventHandler<javafx.scene.input.MouseEvent>() {
                            @Override public void handle(javafx.scene.input.MouseEvent e) { cb.show(); }
                        });
                    }

                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) { setGraphic(null); return; }

                        LocalTime val = null;
                        if (item != null && !item.trim().isEmpty()) {
                            try { val = LocalTime.parse(item.trim(), TF); }
                            catch (Exception ignore) { /* lascia null */ }
                        }
                        cb.setValue(val);

                        setGraphic(wrapper);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                };
            }
        });
    }

    /* ========= ButtonBar & Validazione ========= */

    private void setupButtonBar() {
        if (dialogPane.getButtonTypes().isEmpty()) {
            dialogPane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        }

        ButtonType ADD_TYPE = new ButtonType("Aggiungi", ButtonBar.ButtonData.LEFT);
        ButtonType REM_TYPE = new ButtonType("Rimuovi",  ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().add(0, REM_TYPE);
        dialogPane.getButtonTypes().add(0, ADD_TYPE);

        final Button addBtn = (Button) dialogPane.lookupButton(ADD_TYPE);
        if (addBtn != null) {
            addBtn.setText("");
            addBtn.setMnemonicParsing(false);
            addBtn.setStyle("-fx-graphic: url('/icons/plus-16.png'); -fx-content-display: graphic-only;");
            addBtn.setTooltip(new Tooltip("Aggiungi"));
            addBtn.addEventFilter(javafx.event.ActionEvent.ACTION, new javafx.event.EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(javafx.event.ActionEvent ev) {
                    ev.consume();
                    addBlankRowAfterLast();
                }
            });
        }

        final Button remBtn = (Button) dialogPane.lookupButton(REM_TYPE);
        if (remBtn != null) {
            remBtn.setText("");
            remBtn.setMnemonicParsing(false);
            remBtn.setStyle("-fx-graphic: url('/icons/trash-2-16.png'); -fx-content-display: graphic-only;");
            remBtn.setTooltip(new Tooltip("Rimuovi"));
            remBtn.addEventFilter(javafx.event.ActionEvent.ACTION, new javafx.event.EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(javafx.event.ActionEvent ev) {
                    ev.consume();
                    removeSelectedRows();
                }
            });
        }
    }

    private void setupOkCancelButtons() {
        Button okBtn = okButtonType != null
                ? (Button) dialogPane.lookupButton(okButtonType)
                : (Button) dialogPane.lookupButton(ButtonType.OK);
        if (okBtn != null) {
            okBtn.setText("");
            okBtn.setMnemonicParsing(false);
            okBtn.setStyle("-fx-graphic: url('/icons/ok-16.png'); -fx-content-display: graphic-only;");
            okBtn.setTooltip(new Tooltip("Conferma"));
            okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, new javafx.event.EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(javafx.event.ActionEvent ev) {
                    Optional<String> err = validateAllAndFocus();
                    if (err.isPresent()) {
                        ev.consume();
                        error(err.get());
                    }
                }
            });
        }

        Button cancelBtn = null;
        if (cancelButtonType != null) cancelBtn = (Button) dialogPane.lookupButton(cancelButtonType);
        if (cancelBtn == null) {
            for (int i = 0; i < dialogPane.getButtonTypes().size(); i++) {
                ButtonType bt = dialogPane.getButtonTypes().get(i);
                if (bt.getButtonData() != null && bt.getButtonData().isCancelButton()) {
                    cancelBtn = (Button) dialogPane.lookupButton(bt);
                    if (cancelBtn != null) break;
                }
            }
        }
        if (cancelBtn == null) cancelBtn = (Button) dialogPane.lookupButton(ButtonType.CANCEL);
        if (cancelBtn == null) cancelBtn = (Button) dialogPane.lookupButton(ButtonType.CLOSE);

        if (cancelBtn != null) {
            cancelBtn.setText("");
            cancelBtn.setMnemonicParsing(false);
            cancelBtn.setStyle("-fx-graphic: url('/icons/cancel-16.png'); -fx-content-display: graphic-only;");
            cancelBtn.setTooltip(new Tooltip("Annulla"));
        }
    }

    /** Ritorna Optional.empty() se tutto ok, altrimenti messaggio e focus sulla riga. */
    private Optional<String> validateAllAndFocus() {
        ObservableList<SessionDraft> items = table.getItems();
        for (int i = 0; i < items.size(); i++) {
            SessionDraft d = items.get(i);

            if (d.data == null) {
                focusRow(i);
                return Optional.of("Data mancante");
            }

            LocalTime t1, t2;
            try {
                t1 = LocalTime.parse(nz(d.oraInizio).trim(), TF);
                t2 = LocalTime.parse(nz(d.oraFine).trim(), TF);
            } catch (Exception e) {
                focusRow(i);
                return Optional.of("Formato orario non valido (HH:MM)");
            }
            if (!t2.isAfter(t1)) {
                focusRow(i);
                return Optional.of("Ora fine deve essere successiva a inizio");
            }

            if ("Online".equals(d.tipo)) {
                // opzionale: richiedere piattaforma
                // if (isBlank(d.piattaforma)) { focusRow(i); return Optional.of("Piattaforma obbligatoria"); }
            } else { // In presenza
                if (isBlank(d.via) || isBlank(d.aula)) {
                    focusRow(i);
                    return Optional.of("Via e Aula sono obbligatorie");
                }
                if (!isValidItalianCAP(d.cap)) {
                    focusRow(i);
                    return Optional.of("CAP non valido: deve essere di 5 cifre (es. 80132)");
                }
                if (!isBlank(d.postiMax)) {
                    Integer p = parseIntOrNull(d.postiMax);
                    if (p == null || p.intValue() <= 0) {
                        focusRow(i);
                        return Optional.of("Posti deve essere > 0");
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void focusRow(int rowIndex) {
        table.getSelectionModel().clearAndSelect(rowIndex);
        table.scrollTo(rowIndex);
    }

    private void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Dati non validi");
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
    }

    /* ========= Helpers generici ========= */

    private String nz(String s) { return s == null ? "" : s; }

    private boolean isBlank(String s){ return s == null || s.trim().isEmpty(); }

    private Integer parseIntOrNull(String s) {
        try { return Integer.valueOf(s.trim()); } catch (Exception ex) { return null; }
    }

    private boolean isValidItalianCAP(String s) {
        return s != null && s.matches("\\d{5}");
    }

    private void autosizeColumnsToHeader() {
        double padding = 28; // header + icona sort
        for (int i = 0; i < table.getColumns().size(); i++) {
            TableColumn<?,?> col = table.getColumns().get(i);
            String header = col.getText() == null ? "" : col.getText();
            Text t = new Text(header);
            double w = Math.ceil(t.prefWidth(-1) + padding);
            if (col.getMinWidth()  < w) col.setMinWidth(w);
            if (col.getPrefWidth() < w) col.setPrefWidth(w);
        }
        table.layout();
    }

    private void weightColumns() {
        setPerc(cData,        0.12);
        setPerc(cInizio,      0.14);
        setPerc(cFine,        0.14);
        setPerc(cTipo,        0.10);
        setPerc(cPiattaforma, 0.12);
        setPerc(cVia,         0.12);
        setPerc(cNum,         0.06);
        setPerc(cCap,         0.06);
        setPerc(cAula,        0.07);
        setPerc(cPosti,       0.07);
    }
    private void setPerc(TableColumn<?,?> c, double p) {
        c.setMaxWidth(1f * Integer.MAX_VALUE * p);
    }

    private ObservableList<LocalTime> buildTimes(int stepMinutes) {
        ObservableList<LocalTime> list = FXCollections.observableArrayList();
        for (int h = 0; h < 24; h++) {
            for (int m = 0; m < 60; m += stepMinutes) {
                list.add(LocalTime.of(h, m));
            }
        }
        return list;
    }

    private java.util.List<SessionDraft> buildDrafts(Corso corso, int n) {
        java.util.List<SessionDraft> list = new java.util.ArrayList<SessionDraft>();
        int step = frequencyToDays(nz(corso.getFrequenza()));
        LocalDate start = (corso.getDataInizio() != null) ? corso.getDataInizio() : LocalDate.now().plusDays(7);
        for (int i = 0; i < n; i++) {
            SessionDraft d = new SessionDraft();
            d.data = start.plusDays((long) i * step);
            d.tipo = (i < Math.min(2, n)) ? "Online" : "In presenza";
            list.add(d);
        }
        return list;
    }

    private int frequencyToDays(String f) {
        String x = (f == null) ? "" : f.toLowerCase(Locale.ROOT).trim();
        if (x.indexOf('2') >= 0 && x.indexOf("giorn") >= 0) return 2;
        if (x.indexOf("quindic") >= 0 || (x.indexOf("bi") >= 0 && x.indexOf("settiman") >= 0)) return 14;
        if (x.indexOf("mensil") >= 0) return 30;
        if (x.indexOf("settim") >= 0) return 7;
        return 7;
    }

    /* ========= Celle specifiche ========= */

    /** Colonna "Tipo" con ComboBox Online/Presenza e pulizia campi dipendenti. */
    private final class TipoCell extends TableCell<SessionDraft, String> {
        private final ComboBox<String> cb = new ComboBox<String>(FXCollections.observableArrayList("Online", "In presenza"));
        private final HBox wrapper = new HBox(cb);
        private boolean internal = false;

        TipoCell() {
            cb.getStyleClass().addAll("cell-editor", "sessione-cell");
            cb.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(cb, Priority.ALWAYS);
            cb.setPrefHeight(EDITOR_HEIGHT);
            cb.setMinHeight(Region.USE_PREF_SIZE);
            cb.setMaxHeight(Region.USE_PREF_SIZE);
            wrapper.setFillHeight(true);

            cb.valueProperty().addListener(new javafx.beans.value.ChangeListener<String>() {
                @Override public void changed(javafx.beans.value.ObservableValue<? extends String> o, String oldV, String newV) {
                    if (internal) return;
                    int i = getIndex();
                    TableView<SessionDraft> tv = getTableView();
                    if (tv == null || i < 0 || i >= tv.getItems().size()) return;
                    SessionDraft row = tv.getItems().get(i);
                    if (row == null) return;

                    if (newV == null || newV.equals(row.tipo)) return;

                    row.tipo = newV;
                    if ("Online".equals(newV)) {
                        row.via = ""; row.num = ""; row.cap = ""; row.aula = ""; row.postiMax = "";
                    } else { // In presenza
                        row.piattaforma = "";
                    }
                    tv.refresh(); // forza ricalcolo colonne condizionali
                }
            });
        }

        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) { setGraphic(null); return; }
            internal = true;
            cb.setValue(item);
            internal = false;
            setGraphic(wrapper);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
    }

    /* ========= Accesso campi per colonne condizionali ========= */

    private String getField(SessionDraft d, String name) {
        if (d == null) return "";
        if ("piattaforma".equals(name)) return d.piattaforma;
        if ("via".equals(name))         return d.via;
        if ("num".equals(name))         return d.num;
        if ("cap".equals(name))         return d.cap;
        if ("aula".equals(name))        return d.aula;
        if ("postiMax".equals(name))    return d.postiMax;
        return "";
    }

    private void setField(SessionDraft d, String name, String value) {
        String v = (value == null) ? "" : value;
        if ("piattaforma".equals(name)) d.piattaforma = v;
        else if ("via".equals(name))     d.via = v;
        else if ("num".equals(name))     d.num = v;
        else if ("cap".equals(name))     d.cap = v;
        else if ("aula".equals(name))    d.aula = v;
        else if ("postiMax".equals(name))d.postiMax = v;
    }
}
