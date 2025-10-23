package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.*;

/**
 * Dialog: associa una o più ricette a una SessionePresenza.
 * Multi-selezione (checkbox + SHIFT/CTRL + SPACE), stile dark coerente.
 */
public class AssociaRicetteController extends Dialog<List<Long>> {

    /* ===== Theme ===== */
    private static final String BG_SURFACE = "#20282b";
    private static final String BG_CARD    = "#242c2f";
    private static final String TXT_MAIN   = "#e9f5ec";
    private static final String TXT_MUTED  = "#cfe5d9";
    private static final String GRID_SOFT  = "rgba(255,255,255,0.06)";
    private static final String ACCENT     = "#1fb57a";
    private static final String HOVER_BG   = "rgba(31,181,122,0.18)";

    /* ===== FXML ===== */
    @FXML private VBox root;
    @FXML private TextField txtSearch;
    @FXML private ChoiceBox<String>  chDifficolta;
    @FXML private Button btnSelAll, btnSelNone;
    @FXML private TableView<Riga>    table;
    @FXML private TableColumn<Riga, Boolean> colChk;
    @FXML private TableColumn<Riga, String>  colNome, colDiff, colDesc;
    @FXML private TableColumn<Riga, Number>  colTempo;
    @FXML private Region topBarSpacer;

    /* ===== State / deps ===== */
    private final SessioneDao sessioneDao;
    private final int idSessionePresenza;
    private final List<Ricetta> tutteLeRicette;
    private final List<Ricetta> ricetteGiaAssociate;

    private final ObservableSet<Long> selectedIds = FXCRONT(); // shorthand helper below creates an ObservableSet
    private FilteredList<Riga> filtered;

    public AssociaRicetteController(SessioneDao sessioneDao,
                                int idSessionePresenza,
                                List<Ricetta> tutteLeRicette,
                                List<Ricetta> ricetteGiaAssociate) {
    this.sessioneDao = sessioneDao;
    this.idSessionePresenza = idSessionePresenza;
    this.tutteLeRicette = (tutteLeRicette != null) ? tutteLeRicette : Collections.emptyList();
    this.ricetteGiaAssociate = (ricetteGiaAssociate != null) ? ricetteGiaAssociate : Collections.emptyList();

    setTitle("Associa ricette alla sessione (id=" + idSessionePresenza + ")");
    getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
    setResultConverter(bt -> bt == ButtonType.OK ? new ArrayList<>(selectedIds) : null);
}


    @FXML
    private void initialize() {
        // Dialog base
        getDialogPane().setContent(root);
        styleDialogPaneDark(getDialogPane());
        styleDialogButtonsDark(getDialogPane());
        getDialogPane().setPrefSize(960, 620);
        setResizable(true);
        Platform.runLater(() -> {
            // Allarga un po’ la finestra
            Stage st = (Stage) getDialogPane().getScene().getWindow();
            if (st != null) {
                st.sizeToScene();
                st.setMinWidth(880);
                st.setMinHeight(560);
            }
        });

        // Filtro difficoltà
        chDifficolta.setItems(FXCollections.observableArrayList("Tutte","Facile","Medio","Difficile"));
        chDifficolta.getSelectionModel().selectFirst();
        styleChoiceBoxDark(chDifficolta);

        // Search
        styleTextFieldDark(txtSearch, "Cerca per nome o descrizione");

        // Table
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setTableMenuButtonVisible(false);
        table.setPlaceholder(styledPlaceholder("Nessuna ricetta trovata."));
        table.setFixedCellSize(40);
        styleTableDark(table);

        // Columns
        colChk.setPrefWidth(60);
        colChk.setStyle("-fx-alignment: CENTER;");
        colChk.setCellValueFactory(c -> c.getValue().checked);
        colChk.setCellFactory(tc -> {
            CheckBoxTableCell<Riga, Boolean> cell = new CheckBoxTableCell<>();
            cell.setAlignment(Pos.CENTER);
            return cell;
        });

        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colDiff.setPrefWidth(140);
        colDiff.setCellValueFactory(new PropertyValueFactory<>("difficolta"));
        colDiff.setCellFactory(tc -> new TableCell<Riga, String>() {
            private final Label chip = new Label();
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                chip.setText(s);
                String bg = switch (s.trim().toLowerCase(Locale.ROOT)) {
                    case "facile"    -> "#10b981"; // verde
                    case "medio"     -> "#f59e0b"; // ambra
                    case "difficile" -> "#ef4444"; // rosso
                    default          -> "#6b7280"; // grigio
                };
                chip.setStyle("-fx-background-color:"+bg+"; -fx-text-fill:white; -fx-font-weight:700; -fx-font-size:12px; -fx-background-radius:999; -fx-padding:2 8;");
                setGraphic(chip);
            }
        });


        colTempo.setPrefWidth(140);
        colTempo.setStyle("-fx-alignment: CENTER;");
        colTempo.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().tempoPreparazione.get()));
        colTempo.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null : n.intValue() + " min");
            }
        });

        colDesc.setCellValueFactory(new PropertyValueFactory<>("descrizione"));
        colDesc.setCellFactory(tc -> new TableCell<Riga, String>() {
            private final Label lbl = new Label();
            { 
                lbl.setStyle("-fx-text-fill:" + TXT_MUTED + ";");
                lbl.setEllipsisString("…");
            }
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                lbl.setText(s);
                setGraphic(lbl);
            }
        });


        // multi-select + SPACE toggles all selected
        table.setEditable(true);
        colChk.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                var sel = new ArrayList<>(table.getSelectionModel().getSelectedItems());
                if (!sel.isEmpty()) {
                    boolean allChecked = sel.stream().allMatch(r -> r.checked.get());
                    boolean newVal = !allChecked;
                    sel.forEach(r -> r.checked.set(newVal));
                    e.consume();
                }
            }
        });

        // single RowFactory: paint + ctrl-click toggle
        table.setRowFactory(tv -> {
    TableRow<Riga> row = new TableRow<>() {
        @Override
        protected void updateItem(Riga item, boolean empty) {
            super.updateItem(item, empty);
            paintRow(this);
        }
    };
    row.hoverProperty().addListener((o,ov,nv) -> paintRow(row));
    row.selectedProperty().addListener((o,ov,nv) -> paintRow(row));
    row.setOnMouseClicked(evt -> {
        if (row.isEmpty()) return;
        if (evt.getClickCount() == 2 && evt.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
            Riga riga = row.getItem();
            riga.checked.set(!riga.checked.get());
        } else if (evt.getButton() == javafx.scene.input.MouseButton.PRIMARY
                   && (evt.isControlDown() || evt.isShortcutDown())) {
            Riga riga = row.getItem();
            riga.checked.set(!riga.checked.get());
            evt.consume();
        }
    });
    return row;
});


        // Data
        ObservableList<Riga> righe = FXCollections.observableArrayList();
        for (Ricetta r : tutteLeRicette) {
            if (r != null) righe.add(new Riga(r));
        }

        // wire checkbox <-> selectedIds
        righe.forEach(r ->
            r.checked.addListener((obs, oldV, newV) -> {
                if (Boolean.TRUE.equals(newV)) selectedIds.add(r.idRicetta);
                else                           selectedIds.remove(r.idRicetta);
            })
        );

        filtered = new FilteredList<>(righe, r -> true);
        table.setItems(filtered);

        // pre-selezione da DB
        for (Ricetta r : ricetteGiaAssociate) {
            if (r != null) selectedIds.add(r.getIdRicetta());
        }
        filtered.forEach(r -> r.checked.set(selectedIds.contains(r.idRicetta)));

        // filtro testo/difficoltà
        txtSearch.textProperty().addListener((o,ov,nv) -> applyFilter());
        chDifficolta.valueProperty().addListener((o,ov,nv) -> applyFilter());

        // bottoni “Tutte / Nessuna” applicati alle righe visibili
        if (btnSelAll   != null) btnSelAll.setOnAction(this::selectAll);
        if (btnSelNone  != null) btnSelNone.setOnAction(this::selectNone);
    }

    /* ===== Filter ===== */
    private void applyFilter() {
        if (filtered == null) return;
        final String q = (txtSearch.getText() == null) ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        final String diff = Optional.ofNullable(chDifficolta.getValue()).orElse("Tutte");
        filtered.setPredicate(r -> {
            if (r == null) return false;
            boolean okTxt  = q.isEmpty()
                    || containsIgnoreCase(r.nome.get(), q)
                    || containsIgnoreCase(r.descrizione.get(), q);
            boolean okDiff = "Tutte".equalsIgnoreCase(diff)
                    || (r.difficolta.get() != null && diff.equalsIgnoreCase(r.difficolta.get()));
            return okTxt && okDiff;
        });
    }

    private void selectAll(ActionEvent e)   { if (filtered != null) filtered.forEach(r -> r.checked.set(true)); }
    private void selectNone(ActionEvent e)  { if (filtered != null) filtered.forEach(r -> r.checked.set(false)); }

    /* ===== Save ===== */
    /** Da usare così: controller.salvaSeConfermato(showAndWait()); */
    public void salvaSeConfermato(Optional<List<Long>> resultOpt) {
        if (resultOpt == null || resultOpt.isEmpty()) return;
        List<Long> selectedNow = resultOpt.get();
        try {
            List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);
            Set<Long> before = new HashSet<>();
            for (Ricetta r : gia) if (r != null) before.add(r.getIdRicetta());
            Set<Long> after = new HashSet<>(selectedNow);

            for (Long idAdd : after)   if (!before.contains(idAdd)) sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);
            for (Long idRem : before)  if (!after.contains(idRem))  sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);

            showInfoDark("Operazione completata", "Associazioni ricette salvate correttamente.");
        } catch (Exception ex) {
            showErrorDark("Errore salvataggio", ex.getMessage());
        }
    }

    /* ===== Row / Table paint ===== */
    private void paintRow(TableRow<Riga> row) {
        if (row == null || row.isEmpty() || row.getItem() == null) {
            row.setStyle(""); row.setCursor(Cursor.DEFAULT); return;
        }
        boolean on = row.isHover() || row.isSelected();
        if (on) {
            row.setStyle("-fx-background-color:"+HOVER_BG+"; -fx-border-color:"+ACCENT+"; -fx-border-width:0 0 0 4;");
            row.setCursor(Cursor.HAND);
        } else {
            String base = (row.getIndex()%2==0) ? "rgba(255,255,255,0.03)" : "transparent";
            row.setStyle("-fx-background-color:"+base+";");
            row.setCursor(Cursor.DEFAULT);
        }
    }
    private void styleTableDark(TableView<?> tv) {
        tv.setStyle(
            "-fx-background-color:"+BG_SURFACE+";" +
            "-fx-control-inner-background:"+BG_SURFACE+";" +
            "-fx-text-background-color:"+TXT_MAIN+";" +
            "-fx-table-cell-border-color:"+GRID_SOFT+";" +
            "-fx-table-header-border-color:"+GRID_SOFT+";" +
            "-fx-selection-bar: transparent;" +
            "-fx-selection-bar-non-focused: transparent;" +
            "-fx-focus-color: transparent;" +
            "-fx-faint-focus-color: transparent;" +
            "-fx-background-insets:0; -fx-padding:0;"
        );
        Platform.runLater(() -> {
            for (Node n : tv.lookupAll(".column-header")) {
                if (n instanceof Region r) {
                    r.setStyle("-fx-background-color:"+BG_CARD+"; -fx-border-color:"+GRID_SOFT+"; -fx-border-width:0 0 1 0;");
                }
                Node lab = n.lookup(".label");
                if (lab instanceof Label l) {
                    l.setTextFill(javafx.scene.paint.Color.web(TXT_MAIN));
                    l.setStyle("-fx-font-weight:700;");
                }
            }
            Node headerBg = tv.lookup(".column-header-background");
            if (headerBg != null) headerBg.setStyle("-fx-background-color:"+BG_CARD+";");
            Node filler = tv.lookup(".filler");
            if (filler != null) filler.setStyle("-fx-background-color:"+BG_CARD+";");
        });
    }
    private TableCell<Ricetta,String> oneLineEllipsisCell() {
        return new TableCell<>() {
            private final Label lbl = new Label();
            { lbl.setStyle("-fx-text-fill:"+TXT_MUTED+";"); lbl.setEllipsisString("…"); }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                lbl.setText(s); setGraphic(lbl);
            }
        };
    }
    private TableCell<Ricetta,String> difficultyChipCell() {
        return new TableCell<>() {
            private final Label chip = new Label();
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                chip.setText(s);
                String bg = switch (s.trim().toLowerCase()) {
                    case "facile" -> "#10b981";
                    case "medio"  -> "#f59e0b";
                    case "difficile" -> "#ef4444";
                    default -> "#6b7280";
                };
                chip.setStyle("-fx-background-color:"+bg+"; -fx-text-fill:white; -fx-font-weight:700; -fx-font-size:12px; -fx-background-radius:999; -fx-padding:2 8;");
                setGraphic(chip);
            }
        };
    }
    private Label styledPlaceholder(String text) {
        Label ph = new Label(text);
        ph.setStyle("-fx-text-fill:#b7c5cf; -fx-font-weight:700;");
        return ph;
    }

    /* ===== Dark Dialog / Controls ===== */
    private void styleDialogPaneDark(DialogPane pane) {
        pane.setStyle(
            "-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
            "-fx-border-color:"+GRID_SOFT+"; -fx-border-width:1; -fx-border-radius:12; -fx-background-radius:12;" +
            "-fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-accent: transparent;"
        );
    }
    private void styleDialogButtonsDark(DialogPane dp) {
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        if (okBtn != null) {
            okBtn.setText("Conferma");
            okBtn.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 14;");
            okBtn.setOnMouseEntered(e -> okBtn.setStyle("-fx-background-color:#16a56e; -fx-text-fill:#0a1410; -fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 14;"));
            okBtn.setOnMouseExited (e -> okBtn.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 14;"));
        }
        Button cancelBtn = (Button) dp.lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) {
            cancelBtn.setText("Annulla");
            cancelBtn.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:8 14;");
            cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle("-fx-background-color:#374151; -fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:8 14;"));
            cancelBtn.setOnMouseExited (e -> cancelBtn.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:8 14;"));
        }
    }
    private void styleChoiceBoxDark(ChoiceBox<String> cb) {
        if (cb == null) return;
        cb.setStyle("-fx-background-color:#2e3845; -fx-background-radius:8; -fx-border-color:#3a4657; -fx-border-radius:8; -fx-padding:4 10;");
        // forza il colore del label “button” quando la choice è chiusa
        cb.skinProperty().addListener((obs, o, n) -> forceChoiceBoxLabelColor(cb, "#e9f5ec"));
        Platform.runLater(() -> forceChoiceBoxLabelColor(cb, "#e9f5ec"));
        // popup scuro
        cb.showingProperty().addListener((obs, was, is) -> {
            if (is) {
                Scene sc = cb.getScene();
                if (sc != null) {
                    for (Node n : sc.getRoot().lookupAll(".context-menu")) {
                        n.setStyle("-fx-background-color:"+BG_SURFACE+";");
                    }
                    for (Node n : sc.getRoot().lookupAll(".menu-item .label")) {
                        if (n instanceof Label l) l.setTextFill(javafx.scene.paint.Color.web(TXT_MAIN));
                    }
                }
            }
        });
    }
    private void forceChoiceBoxLabelColor(ChoiceBox<?> cb, String color) {
        Node lblNode = cb.lookup(".label");
        if (lblNode instanceof Labeled l) {
            l.setStyle("-fx-text-fill:"+color+"; -fx-font-weight:700;");
        }
    }
    private void styleTextFieldDark(TextField tf, String prompt) {
        if (tf == null) return;
        tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color:#2e3845; -fx-control-inner-background:#2e3845; -fx-text-fill:#e9f5ec; -fx-prompt-text-fill:rgba(255,255,255,0.65); -fx-background-radius:10; -fx-border-color:#3a4657; -fx-border-radius:10; -fx-padding:6 10;");
    }

    /* ===== Helpers ===== */
    private static boolean containsIgnoreCase(String s, String q) {
        return s != null && q != null && s.toLowerCase(Locale.ROOT).contains(q);
    }
    private static ObservableSet<Long> FXCRONT() { return FXCollections.observableSet(); }

    /* ===== Row model ===== */
    public static class Riga {
        final long idRicetta;
        final javafx.beans.property.SimpleBooleanProperty checked = new javafx.beans.property.SimpleBooleanProperty(false);
        final javafx.beans.property.SimpleStringProperty  nome        = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleStringProperty  descrizione = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleStringProperty  difficolta  = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleIntegerProperty tempoPreparazione = new javafx.beans.property.SimpleIntegerProperty();

        public Riga(Ricetta r) {
            this.idRicetta = r.getIdRicetta();
            this.nome.set(r.getNome());
            this.descrizione.set(r.getDescrizione());
            this.difficolta.set(r.getDifficolta());
            this.tempoPreparazione.set(r.getTempoPreparazione());
        }

        public long getIdRicetta() { return idRicetta; }
        public String getNome()    { return nome.get(); }
        public String getDescrizione() { return descrizione.get(); }
        public String getDifficolta()  { return difficolta.get(); }
        public Integer getTempoPreparazione() { return tempoPreparazione.get(); }
        public javafx.beans.property.BooleanProperty checkedProperty() { return checked; }
    }

    /* ===== Dark alerts ===== */
    /** Alert INFO scuro, senza header né icona, testo ben leggibile. */
    private void showInfoDark(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title == null ? "Messaggio" : title);

        // niente header/icona (evita la banda chiara)
        alert.setHeaderText(null);
        alert.setGraphic(null);

        // testo nel contenuto, colore leggibile
        Label content = new Label(message == null ? "" : message);
        content.setWrapText(true);
        content.setStyle("-fx-text-fill:#e9f5ec; -fx-font-size:14px; -fx-font-weight:600;");
        alert.getDialogPane().setContent(content);

        // stile dark coerente
        DialogPane dp = alert.getDialogPane();
        dp.setStyle(
            "-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
            "-fx-border-color: rgba(255,255,255,0.08);" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;" +
            "-fx-padding: 14;" +
            "-fx-focus-color: transparent;" +
            "-fx-faint-focus-color: transparent;" +
            "-fx-accent: transparent;"
        );

        // rimuovi eventuali residui di header/grafica
        Node header = dp.lookup(".header-panel");
        if (header instanceof Region r) r.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        Node graphic = dp.lookup(".graphic-container");
        if (graphic instanceof Region g) g.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

        // bottone OK in stile brand
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        if (okBtn != null) {
            okBtn.setText("Conferma");
            okBtn.setStyle(
                "-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;" +
                "-fx-background-radius:10; -fx-padding:8 16;"
            );
            okBtn.setOnMouseEntered(e -> okBtn.setStyle(
                "-fx-background-color:#16a56e; -fx-text-fill:#0a1410; -fx-font-weight:800;" +
                "-fx-background-radius:10; -fx-padding:8 16;"));
            okBtn.setOnMouseExited(e -> okBtn.setStyle(
                "-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;" +
                "-fx-background-radius:10; -fx-padding:8 16;"));
        }

        dp.setMinWidth(460);
        alert.showAndWait();
    }

    private void showErrorDark(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null);
        a.getDialogPane().setContent(new Label(msg));
        styleDialogPaneDark(a.getDialogPane());
        styleDialogButtonsDark(a.getDialogPane());
        a.getDialogPane().setMinWidth(520);
        a.showAndWait();
    }
}
