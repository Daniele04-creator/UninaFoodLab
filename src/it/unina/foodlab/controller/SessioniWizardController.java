package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class SessioniWizardController {

    /* ---------- UI dal FXML ---------- */
    @FXML private DialogPane dialogPane;
    @FXML private ButtonType okButtonType;

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

    @FXML private Button btnAdd;
    @FXML private Button btnRemove;
    @FXML private ButtonType cancelButtonType;


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
        String cap = "";
        String aula = "";
        String postiMax = "";    // 0/blank = illimitati
    }

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");

    /* layout */
    private static final double ROW_HEIGHT     = 32;
    private static final double EDITOR_HEIGHT  = 28;
    private static final int    MAX_VISIBLE_ROWS = 6;
    private static final double HEADER_H       = 30;

    @FXML
    private void initialize() {
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        /* === Anti-zoom: riga fissa === */
        table.setFixedCellSize(ROW_HEIGHT);
        table.setRowFactory(tv -> {
            TableRow<SessionDraft> r = new TableRow<>();
            r.setPrefHeight(ROW_HEIGHT);
            r.setMinHeight(ROW_HEIGHT);
            r.setMaxHeight(ROW_HEIGHT);
            return r;
        });

        /* DATA: NON editabile */
        cData.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().data == null ? "" : cd.getValue().data.toString())
        );
        cData.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(null);
            }
        });

        /* TESTO editabile: ora inizio/fine */
        makeEditableTextColumn(cInizio, d -> d.oraInizio, (d,v) -> d.oraInizio = v);
        makeEditableTextColumn(cFine,   d -> d.oraFine,   (d,v) -> d.oraFine   = v);

        /* MODALITÀ ComboBox */
        cTipo.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tipo));
        cTipo.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList("Online", "In presenza"));
            private final HBox wrapper = new HBox(cb);
            private boolean internal = false;
            {
                cb.getStyleClass().addAll("cell-editor", "sessione-cell"); // CSS rosa senza bordi
                cb.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(cb, Priority.ALWAYS);
                cb.setPrefHeight(EDITOR_HEIGHT);
                cb.setMinHeight(Region.USE_PREF_SIZE);
                cb.setMaxHeight(Region.USE_PREF_SIZE);
                wrapper.setFillHeight(true);

                cb.valueProperty().addListener((o, oldV, newV) -> {
                    if (internal) return;
                    int i = getIndex();
                    var tv = getTableView();
                    if (tv == null || i < 0 || i >= tv.getItems().size()) return;
                    SessionDraft row = tv.getItems().get(i);
                    if (Objects.equals(row.tipo, newV)) return;
                    row.tipo = newV;
                    if ("Online".equals(newV)) {
                        row.via = ""; row.num = ""; row.cap = ""; row.aula = ""; row.postiMax = "";
                    } else {
                        row.piattaforma = "";
                    }
                    tv.refresh();
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
        });
        cTipo.setMinWidth(170);
        cTipo.setPrefWidth(190);

        /* Colonne condizionali */
        Predicate<SessionDraft> isPres = d -> "In presenza".equals(d.tipo);
        makeConditionalTextColumn(cPiattaforma, d -> "Online".equals(d.tipo), d -> d.piattaforma, (d,v) -> d.piattaforma = v);
        makeConditionalTextColumn(cVia,   isPres, d -> d.via,      (d,v) -> d.via = v);
        makeConditionalTextColumn(cNum,   isPres, d -> d.num,      (d,v) -> d.num = v);
        makeConditionalTextColumn(cCap,   isPres, d -> d.cap,      (d,v) -> d.cap = v);
        makeConditionalTextColumn(cAula,  isPres, d -> d.aula,     (d,v) -> d.aula = v);
        makeConditionalTextColumn(cPosti, isPres, d -> d.postiMax, (d,v) -> d.postiMax = v);

        /* Resize policy */
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().forEach(c -> {
            c.setReorderable(false);
            c.setMaxWidth(1f * Integer.MAX_VALUE);
        });

        /* Minimi per colonne */
        cData.setMinWidth(160); cInizio.setMinWidth(90); cFine.setMinWidth(90);
        cTipo.setMinWidth(170); cPiattaforma.setMinWidth(160);
        cVia.setMinWidth(140); cNum.setMinWidth(80); cCap.setMinWidth(80);
        cAula.setMinWidth(140); cPosti.setMinWidth(100);

        /* Altezza massima della tabella */
        double tablePrefH = HEADER_H + ROW_HEIGHT * MAX_VISIBLE_ROWS;
        table.setMinHeight(HEADER_H + ROW_HEIGHT * 2);
        table.setPrefHeight(tablePrefH);
        table.setMaxHeight(tablePrefH);

        Platform.runLater(() -> {
            if (table.getParent() instanceof javafx.scene.layout.VBox vb) {
                javafx.scene.layout.VBox.setVgrow(table, Priority.NEVER);
            }
            autosizeColumnsToHeader();
        });

        /* Dimensioni dialog */
        dialogPane.setPrefSize(1300, 760);
        dialogPane.setMinSize(1100, 650);
        dialogPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        /* Nascondi bottoni nel content */
        if (btnAdd != null) { btnAdd.setVisible(false); btnAdd.setManaged(false); }
        if (btnRemove != null) { btnRemove.setVisible(false); btnRemove.setManaged(false); }

        /* Button bar: + e cestino a sinistra */
        setupButtonBar();

        /* Trasforma OK/Cancel in icona-only */
        Platform.runLater(() -> {
            Button okBtn = okButtonType != null ? (Button) dialogPane.lookupButton(okButtonType) : (Button) dialogPane.lookupButton(ButtonType.OK);
            if (okBtn != null) {
                okBtn.setText("");
                okBtn.setMnemonicParsing(false);
                okBtn.setStyle("-fx-graphic: url('/icons/ok-16.png'); -fx-content-display: graphic-only;");
                okBtn.setTooltip(new Tooltip("Conferma"));
            }

            Button cancelBtn = cancelButtonType != null ? (Button) dialogPane.lookupButton(cancelButtonType) : null;
            if (cancelBtn == null) {
                for (ButtonType bt : dialogPane.getButtonTypes()) {
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
        });

        File cssFile = new File("src/app.css");
        dialogPane.getStylesheets().add(cssFile.toURI().toString());
    }

    /** Bottoni nella button bar: “+” e cestino a sinistra, OK/Cancel a destra. */
    private void setupButtonBar() {
        if (dialogPane.getButtonTypes().isEmpty()) {
            dialogPane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        }

        ButtonType ADD_TYPE = new ButtonType("Aggiungi", ButtonBar.ButtonData.LEFT);
        ButtonType REM_TYPE = new ButtonType("Rimuovi",  ButtonBar.ButtonData.LEFT);

        dialogPane.getButtonTypes().add(0, REM_TYPE);
        dialogPane.getButtonTypes().add(0, ADD_TYPE);

        Button addBtn = (Button) dialogPane.lookupButton(ADD_TYPE);
        if (addBtn != null) {
            addBtn.setText("");
            addBtn.setMnemonicParsing(false);
            addBtn.getStyleClass().add("add-button");
            addBtn.setStyle("-fx-graphic: url('/icons/plus-16.png'); -fx-content-display: graphic-only;");
            addBtn.setTooltip(new Tooltip("Aggiungi"));
            addBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                ev.consume();
                addBlankRowAfterLast();
            });
        }

        Button remBtn = (Button) dialogPane.lookupButton(REM_TYPE);
        if (remBtn != null) {
            remBtn.setText("");
            remBtn.setMnemonicParsing(false);
            remBtn.setStyle("-fx-graphic: url('/icons/trash-2-16.png'); -fx-content-display: graphic-only;");
            remBtn.setTooltip(new Tooltip("Rimuovi"));
            remBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                ev.consume();
                removeSelectedRows();
            });
        }
    }

    /* ---------- API pubbliche ---------- */

    public void initWithCorso(Corso corso) {
        this.corso = Objects.requireNonNull(corso, "corso nullo");
        int n = Math.max(1, corso.getNumSessioni());
        table.setItems(FXCollections.observableArrayList(buildDrafts(corso, n)));
    }

    public void initWithCorsoAndExisting(Corso corso, List<Sessione> esistenti) {
        this.corso = Objects.requireNonNull(corso, "corso nullo");
        var drafts = new ArrayList<SessionDraft>();
        if (esistenti != null) {
            for (Sessione s : esistenti) {
                SessionDraft d = new SessionDraft();
                d.data = s.getData();
                d.oraInizio = s.getOraInizio() == null ? "10:00" : s.getOraInizio().toString();
                d.oraFine   = s.getOraFine()   == null ? "12:00" : s.getOraFine().toString();
                if (s instanceof SessioneOnline so) {
                    d.tipo = "Online";
                    d.piattaforma = nz(so.getPiattaforma());
                } else if (s instanceof SessionePresenza sp) {
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
        var items = table.getItems();
        SessionDraft d = new SessionDraft();
        LocalDate base = items.stream()
            .map(s -> s.data)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse((corso != null && corso.getDataInizio() != null) ? corso.getDataInizio() : LocalDate.now());
        d.data = base.plusDays(7);
        d.tipo = "Online";
        items.add(d);
        table.getSelectionModel().clearSelection();
        table.getSelectionModel().select(items.size() - 1);
        table.scrollTo(items.size() - 1);
    }

    public void removeSelectedRows() {
        var selIdx = new ArrayList<>(table.getSelectionModel().getSelectedIndices());
        if (selIdx.isEmpty()) return;
        selIdx.sort(Comparator.reverseOrder());
        for (int i : selIdx) {
            if (i >= 0 && i < table.getItems().size()) {
                table.getItems().remove(i);
            }
        }
    }

    public List<Sessione> buildResult() {
        Button ok = (Button) dialogPane.lookupButton(
            okButtonType != null ? okButtonType : ButtonType.OK
        );
        if (ok != null) ok.requestFocus();

        List<Sessione> result = new ArrayList<>();
        for (SessionDraft d : table.getItems()) {
            if (d.data == null) { error("Data mancante"); return null; }

            final LocalTime t1, t2;
            try {
                t1 = LocalTime.parse(d.oraInizio.trim(), TF);
                t2 = LocalTime.parse(d.oraFine.trim(), TF);
                if (!t2.isAfter(t1)) { error("Ora fine deve essere successiva a inizio"); return null; }
            } catch (Exception ex) {
                error("Formato orario non valido (HH:MM)"); return null;
            }

            if ("Online".equals(d.tipo)) {
                result.add(new SessioneOnline(0, d.data, t1, t2, corso, d.piattaforma));
            } else {
                if (isBlank(d.via) || isBlank(d.aula)) { error("Via e Aula sono obbligatorie"); return null; }
                Integer cap = isBlank(d.cap) ? 0 : parseIntOrNull(d.cap);
                if (cap == null || cap < 0) { error("CAP non valido"); return null; }

                int posti = 0;
                if (!isBlank(d.postiMax)) {
                    Integer p = parseIntOrNull(d.postiMax);
                    if (p == null || p <= 0) { error("Posti deve essere > 0"); return null; }
                    posti = p;
                }
                result.add(new SessionePresenza(0, d.data, t1, t2, corso, d.via, d.num, cap, d.aula, posti));
            }
        }
        return result;
    }

    /* ---------- Helpers colonne ---------- */

    private void makeEditableTextColumn(TableColumn<SessionDraft,String> col,
            Function<SessionDraft,String> getter,
            BiConsumer<SessionDraft,String> setter) {
        col.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        col.setCellFactory(tc -> new TableCell<>() {
            private final TextField tf = new TextField();
            private final HBox wrapper = new HBox(tf);
            {
                tf.getStyleClass().addAll("cell-editor", "sessione-cell");
                tf.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(tf, Priority.ALWAYS);
                tf.setPrefHeight(EDITOR_HEIGHT);
                tf.setMinHeight(Region.USE_PREF_SIZE);
                tf.setMaxHeight(Region.USE_PREF_SIZE);
                wrapper.setFillHeight(true);

                tf.textProperty().addListener((o,a,b) -> {
                    int i = getIndex();
                    var tv = getTableView();
                    if (tv != null && i >= 0 && i < tv.getItems().size()) {
                        setter.accept(tv.getItems().get(i), b);
                    }
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                tf.setText(item);
                setGraphic(wrapper);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
    }

    private void makeConditionalTextColumn(TableColumn<SessionDraft,String> col,
                   Predicate<SessionDraft> visibleWhen,
                   Function<SessionDraft,String> getter,
                   BiConsumer<SessionDraft,String> setter) {
        col.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        col.setCellFactory(tc -> new TableCell<>() {
            private final TextField tf = new TextField();
            private final HBox wrapper = new HBox(tf);
            {
                tf.getStyleClass().addAll("cell-editor", "sessione-cell");
                tf.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(tf, Priority.ALWAYS);
                tf.setPrefHeight(EDITOR_HEIGHT);
                tf.setMinHeight(Region.USE_PREF_SIZE);
                tf.setMaxHeight(Region.USE_PREF_SIZE);
                wrapper.setFillHeight(true);

                tf.textProperty().addListener((o,a,b) -> {
                    int i = getIndex();
                    var tv = getTableView();
                    if (tv != null && i >= 0 && i < tv.getItems().size()) {
                        setter.accept(tv.getItems().get(i), b);
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                int i = getIndex();
                var tv = getTableView();
                if (tv == null || i < 0 || i >= tv.getItems().size()) { setGraphic(null); return; }
                SessionDraft row = tv.getItems().get(i);
                if (row == null || !visibleWhen.test(row)) { setGraphic(null); return; }
                tf.setText(item);
                setGraphic(wrapper);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
    }

    /* ---------- Costruzione righe ---------- */

    private List<SessionDraft> buildDrafts(Corso corso, int n) {
        List<SessionDraft> list = new ArrayList<>();
        int step = frequencyToDays(nz(corso.getFrequenza()));
        LocalDate start = corso.getDataInizio() != null ? corso.getDataInizio() : LocalDate.now().plusDays(7);
        for (int i = 0; i < n; i++) {
            SessionDraft d = new SessionDraft();
            d.data = start.plusDays((long) i * step);
            d.tipo = (i < Math.min(2, n)) ? "Online" : "In presenza";
            list.add(d);
        }
        return list;
    }

    private int frequencyToDays(String f) {
        f = f == null ? "" : f.toLowerCase(Locale.ROOT).trim();
        if (f.contains("2") && f.contains("giorn")) return 2;
        if (f.contains("quindic") || (f.contains("bi") && f.contains("settiman"))) return 14;
        if (f.contains("mensil")) return 30;
        if (f.contains("settim")) return 7;
        return 7;
    }

    /* ---------- util ---------- */

    private boolean isBlank(String s){ return s == null || s.isBlank(); }
    private String nz(String s){ return s == null ? "" : s; }

    private void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Dati non validi");
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
    }

    private Integer parseIntOrNull(String s) {
        try { return Integer.valueOf(s.trim()); }
        catch (Exception ex) { return null; }
    }

    /* ---------- Autosize colonne ---------- */

    private void autosizeColumnsToHeader() {
        double padding = 28; // margine header + icona sort
        for (TableColumn<?,?> col : table.getColumns()) {
            String header = col.getText() == null ? "" : col.getText();
            Text t = new Text(header);
            double w = Math.ceil(t.prefWidth(-1) + padding);
            if (col.getMinWidth() < w) col.setMinWidth(w);
            if (col.getPrefWidth() < w) col.setPrefWidth(w);
        }
        table.layout();
    }
}
