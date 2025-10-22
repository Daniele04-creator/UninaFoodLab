package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.util.*;

/**
 * Dialog per associare ricette a una sessione in presenza.
 * Restituisce la lista di id ricetta selezionati quando l'utente conferma (OK).
 */
public class AssociaRicetteController extends Dialog<List<Long>> {

    /* ====== Palette coerente con Login/Corsi ====== */
    private static final String BG_CARD   = "#20282b";
    private static final String BG_HDR    = "#242c2f";
    private static final String TXT_MAIN  = "#e9f5ec";
    private static final String BORDER_SOFT = "rgba(255,255,255,0.06)";
    private static final String ACCENT    = "#1fb57a";
    private static final String HOVER_BG  = "rgba(31,181,122,0.22)";

    /* ===================== FXML ===================== */
    @FXML private VBox root;
    @FXML private TextField txtSearch;
    @FXML private ChoiceBox<String> chDifficolta;
    @FXML private Button btnSelAll, btnSelNone;
    @FXML private TableView<Riga> table;
    @FXML private TableColumn<Riga, Boolean> colChk;
    @FXML private TableColumn<Riga, String>  colNome, colDiff, colDesc;
    @FXML private TableColumn<Riga, Number>  colTempo;
    @FXML private Region topBarSpacer;

    /* ===================== Stato/dep ===================== */
    private final SessioneDao sessioneDao;
    private final int idSessionePresenza;
    private final List<Ricetta> tutteLeRicette;
    private final List<Ricetta> ricetteGiaAssociate;

    private final ObservableSet<Long> selectedIds = FXCollections.observableSet();
    private FilteredList<Riga> filtered;

    /* ===================== Costruttore ===================== */
    public AssociaRicetteController(SessioneDao sessioneDao,
                                    int idSessionePresenza,
                                    List<Ricetta> tutteLeRicette,
                                    List<Ricetta> ricetteGiaAssociate) {
        this.sessioneDao = sessioneDao;
        this.idSessionePresenza = idSessionePresenza;
        this.tutteLeRicette = (tutteLeRicette != null) ? tutteLeRicette : Collections.<Ricetta>emptyList();
        this.ricetteGiaAssociate = (ricetteGiaAssociate != null) ? ricetteGiaAssociate : Collections.<Ricetta>emptyList();

        setTitle("Associa ricette alla sessione (id=" + idSessionePresenza + ")");
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        setResultConverter(bt -> (bt == ButtonType.OK) ? new ArrayList<Long>(selectedIds) : null);
    }

    /* ===================== Initialize ===================== */
    @FXML
    private void initialize() {
        // Contenuto del dialog
        getDialogPane().setContent(root);
        getDialogPane().setStyle("-fx-background-color: transparent;");

        // Bottoni dialog coerenti col tema
        styleDialogButtons();

        // Filtro difficoltà
        ObservableList<String> diffItems = FXCollections.observableArrayList("Tutte", "facile", "medio", "difficile");
        chDifficolta.setItems(diffItems);
        chDifficolta.setValue("Tutte");

        // Tabella
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessuna ricetta trovata."));
     // Header scuro e niente fascia bianca a destra
        table.setTableMenuButtonVisible(false); // nasconde il pulsante colonne (spesso lascia alone chiaro)
        hookHeaderStylingRelayout();            // aggancia i listener per riapplicare lo stile
        fixTableHeaderTheme();                  // prima applicazione

        applyTableTheme();

        // Colonne
        colChk.setPrefWidth(60);
        colChk.setStyle("-fx-alignment: CENTER;");
        colChk.setCellValueFactory(cd -> cd.getValue().checked);
        colChk.setCellFactory(CheckBoxTableCell.forTableColumn(colChk));

        colNome.setCellValueFactory(cd -> cd.getValue().nome);

        colDiff.setPrefWidth(120);
        colDiff.setCellValueFactory(cd -> cd.getValue().difficolta);

        colTempo.setPrefWidth(150);
        colTempo.setStyle("-fx-alignment: CENTER-RIGHT;");
        colTempo.setCellValueFactory(cd -> cd.getValue().tempoPreparazione);

        colDesc.setCellValueFactory(cd -> cd.getValue().descrizione);

        // RowFactory: hover/selected evidenti (barra verde + bg)
        table.setRowFactory(tv -> {
            TableRow<Riga> row = new TableRow<Riga>() {
                @Override protected void updateItem(Riga item, boolean empty) {
                    super.updateItem(item, empty);
                    paintRow(this);
                }
            };
            row.hoverProperty().addListener((o,w,h) -> paintRow(row));
            row.selectedProperty().addListener((o,w,s) -> paintRow(row));
            row.setOnMouseEntered(e -> paintRow(row));
            row.setOnMouseExited(e  -> paintRow(row));
            // doppio click = toggle
            row.setOnMouseClicked(evt -> {
                if (evt.getClickCount() == 2 && !row.isEmpty()) {
                    Riga r = row.getItem();
                    r.checked.set(!r.checked.get());
                }
            });
            return row;
        });

        HBox.setHgrow(topBarSpacer, Priority.ALWAYS);

        // Listener filtri
        txtSearch.textProperty().addListener((o, a, b) -> applyFilter());
        chDifficolta.valueProperty().addListener((o, a, b) -> applyFilter());

        // Seleziona/Annulla tutti
        if (btnSelAll != null) btnSelAll.setOnAction(this::selectAll);
        if (btnSelNone != null) btnSelNone.setOnAction(this::selectNone);

        // Dati iniziali
        ObservableList<Riga> righe = FXCollections.observableArrayList();
        for (int i = 0; i < tutteLeRicette.size(); i++) {
            Ricetta rc = tutteLeRicette.get(i);
            if (rc != null) righe.add(new Riga(rc));
        }

        // sync set selezionati
        for (int i = 0; i < righe.size(); i++) {
            final Riga r = righe.get(i);
            r.checked.addListener((obs, oldVal, val) -> {
                if (val) selectedIds.add(r.idRicetta); else selectedIds.remove(r.idRicetta);
            });
        }

        filtered = new FilteredList<Riga>(righe, r -> true);
        table.setItems(filtered);

        // Pre-selezione
        for (int i = 0; i < ricetteGiaAssociate.size(); i++) {
            Ricetta r = ricetteGiaAssociate.get(i);
            if (r != null) selectedIds.add(r.getIdRicetta());
        }
        for (Riga r : filtered) {
            r.checked.set(selectedIds.contains(Long.valueOf(r.idRicetta)));
        }

        applyFilter();
    }

    /* ===================== Azioni ===================== */
    private void selectAll(ActionEvent e) {
        if (filtered == null) return;
        for (Riga r : filtered) r.checked.set(true);
    }
    private void selectNone(ActionEvent e) {
        if (filtered == null) return;
        for (Riga r : filtered) r.checked.set(false);
    }

    /* ===================== Filtro ===================== */
    private void applyFilter() {
        if (filtered == null) return;

        final String q = (txtSearch != null && txtSearch.getText() != null)
                ? txtSearch.getText().trim().toLowerCase(Locale.ROOT) : "";
        final String diff = (chDifficolta != null && chDifficolta.getValue() != null)
                ? chDifficolta.getValue() : "Tutte";

        filtered.setPredicate(riga -> {
            if (riga == null) return false;

            boolean okTxt = true;
            if (!q.isEmpty()) {
                String n = valueOrEmpty(riga.nome.get());
                String d = valueOrEmpty(riga.descrizione.get());
                okTxt = containsIgnoreCase(n, q) || containsIgnoreCase(d, q);
            }

            boolean okDiff = "Tutte".equalsIgnoreCase(diff) ||
                    (riga.difficolta.get() != null && diff.equalsIgnoreCase(riga.difficolta.get()));

            return okTxt && okDiff;
        });
    }

    private static boolean containsIgnoreCase(String text, String pieceLower) {
        if (text == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(pieceLower);
    }
    private static String valueOrEmpty(String s) { return (s == null) ? "" : s; }

    /* ===================== Salvataggio ===================== */
    /** controller.salvaSeConfermato(showAndWait()); */
    public void salvaSeConfermato(Optional<List<Long>> resultOpt) {
        if (resultOpt == null || !resultOpt.isPresent()) return;

        List<Long> selectedNow = resultOpt.get();
        try {
            List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);

            Set<Long> before = new HashSet<Long>();
            for (int i = 0; i < gia.size(); i++) {
                Ricetta r = gia.get(i);
                if (r != null) before.add(r.getIdRicetta());
            }

            Set<Long> after = new HashSet<Long>(selectedNow);

            // Aggiunte
            for (Long idAdd : after) if (!before.contains(idAdd))
                sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);

            // Rimozioni
            for (Long idRem : before) if (!after.contains(idRem))
                sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);

            showInfo("Associazioni ricette salvate.");

        } catch (Exception ex) {
            showError("Errore salvataggio ricette", ex.getMessage());
        }
    }

    /* ===================== UI Helpers ===================== */
    private void styleDialogButtons() {
        DialogPane dp = getDialogPane();
        dp.setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
                    "-fx-border-color:" + BORDER_SOFT + "; -fx-border-width:1; -fx-border-radius:12; -fx-background-radius:12;");

        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        if (okBtn != null) {
            okBtn.setText("Conferma");
            okBtn.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;" +
                           "-fx-background-radius:10; -fx-padding:8 14;");
        }
        Button cancelBtn = (Button) dp.lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) {
            cancelBtn.setText("Annulla");
            cancelBtn.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700;" +
                               "-fx-background-radius:10; -fx-padding:8 14;");
        }
    }

    private void applyTableTheme() {
        table.setStyle(
            "-fx-background-color:" + BG_CARD + ";" +
            "-fx-control-inner-background:" + BG_CARD + ";" +
            "-fx-text-background-color:" + TXT_MAIN + ";" +
            "-fx-table-cell-border-color:" + BORDER_SOFT + ";" +
            "-fx-table-header-border-color:" + BORDER_SOFT + ";"
        );
        table.setRowFactory(tv -> {
            TableRow<Riga> r = new TableRow<Riga>() {
                @Override protected void updateItem(Riga item, boolean empty) {
                    super.updateItem(item, empty);
                    paintRow(this);
                }
            };
            r.hoverProperty().addListener((o,w,h) -> paintRow(r));
            r.selectedProperty().addListener((o,w,s) -> paintRow(r));
            return r;
        });
    }

    private void paintRow(TableRow<Riga> row) {
        if (row == null || row.isEmpty() || row.getItem() == null) {
            row.setStyle("");
            row.setCursor(Cursor.DEFAULT);
            return;
        }
        boolean accented = row.isHover() || row.isSelected();
        if (accented) {
            row.setStyle("-fx-background-color:" + HOVER_BG + ";" +
                         "-fx-border-color: " + ACCENT + ";" +
                         "-fx-border-width: 0 0 0 3;");
            row.setCursor(Cursor.HAND);
        } else {
            // zebra leggerissima
            String zebra = (row.getIndex() % 2 == 0) ? "rgba(255,255,255,0.03)" : "transparent";
            row.setStyle("-fx-background-color:" + zebra + ";" +
                         "-fx-border-width: 0;" +
                         "-fx-border-color: transparent;");
            row.setCursor(Cursor.DEFAULT);
        }
    }

    private ImageView loadIcon(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) return null;
            Image img = new Image(is);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(16); iv.setFitHeight(16); iv.setPreserveRatio(true);
            return iv;
        } catch (Exception ex) {
            return null;
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.getDialogPane().setMinWidth(420);
    }
    private void showError(String header, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        a.setHeaderText(header);
        a.getDialogPane().setMinWidth(520);
    }

    /* ===================== Riga tabella ===================== */
    public static class Riga {
        final long idRicetta;
        final SimpleBooleanProperty checked = new SimpleBooleanProperty(false);
        final SimpleStringProperty  nome = new SimpleStringProperty();
        final SimpleStringProperty  descrizione = new SimpleStringProperty();
        final SimpleStringProperty  difficolta = new SimpleStringProperty();
        final SimpleIntegerProperty tempoPreparazione = new SimpleIntegerProperty();

        Riga(Ricetta r) {
            this.idRicetta = r.getIdRicetta();
            this.nome.set(r.getNome());
            this.descrizione.set(r.getDescrizione());
            this.difficolta.set(r.getDifficolta());
            this.tempoPreparazione.set(r.getTempoPreparazione());
        }
    }
    /** Ri-colora header e "filler" della TableView (no CSS esterno). */
    private void fixTableHeaderTheme() {
        final String BG_HDR   = "#242c2f";                // come card header
        final String GRID     = "rgba(255,255,255,0.06)"; // bordi soft
        final String TXT_HDR  = "#e9f5ec";

        Platform.runLater(() -> {
            // Sfondo continuo dell'header
            Node headerBg = table.lookup(".column-header-background");
            if (headerBg != null) {
                headerBg.setStyle("-fx-background-color:" + BG_HDR + ";");
            }

            // Ogni colonna: sfondo + bordo sotto; label testo chiaro/bold
            for (Node n : table.lookupAll(".column-header")) {
                if (n instanceof Region r) {
                    r.setStyle("-fx-background-color:" + BG_HDR + ";" +
                               "-fx-background-insets: 0;" +
                               "-fx-border-color:" + GRID + ";" +
                               "-fx-border-width: 0 0 1 0;");
                }
                Node lab = n.lookup(".label");
                if (lab instanceof Label lbl) {
                    lbl.setTextFill(javafx.scene.paint.Color.web(TXT_HDR));
                    lbl.setStyle("-fx-font-weight:700;");
                }
            }

            // Filler (zona a destra dell'ultima colonna): togli la fascia bianca
            Node filler = table.lookup(".filler");
            if (filler != null) {
                filler.setStyle("-fx-background-color:" + BG_HDR + ";");
            }
        });
    }

    /** Riapplca lo stile header quando lo skin rinasce / cambiano le colonne / resize. */
    private void hookHeaderStylingRelayout() {
        table.skinProperty().addListener((obs, oldSkin, newSkin) -> fixTableHeaderTheme());
        table.getColumns().addListener((javafx.collections.ListChangeListener<? super TableColumn<Riga, ?>>) c -> fixTableHeaderTheme());
        table.widthProperty().addListener((o, a, b) -> fixTableHeaderTheme());
    }

}
