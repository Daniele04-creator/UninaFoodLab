package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.dao.RicettaDao;
import it.unina.foodlab.model.Ricetta;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

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
        setResultConverter(new Callback<ButtonType, List<Long>>() {
            @Override
            public List<Long> call(ButtonType bt) {
                if (bt == ButtonType.OK) {
                    return new ArrayList<Long>(selectedIds);
                }
                return null;
            }
        });
    }

    /* ===================== Initialize ===================== */
    @FXML
    private void initialize() {
        // Contenuto del dialog
        getDialogPane().setContent(root);
        getDialogPane().setStyle("-fx-background-color: transparent;");
        styleDialogButtons();

        // Filtro difficoltà
        ObservableList<String> diffItems = FXCollections.observableArrayList("Tutte", "facile", "medio", "difficile");
        chDifficolta.setItems(diffItems);
        chDifficolta.setValue("Tutte");

        // Tabella
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessuna ricetta trovata."));
        table.setTableMenuButtonVisible(false);

        applyTableTheme();
        hookHeaderStylingRelayout();
        fixTableHeaderTheme();

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

        // RowFactory (una sola, niente duplicati)
        table.setRowFactory(new Callback<TableView<Riga>, TableRow<Riga>>() {
            @Override
            public TableRow<Riga> call(TableView<Riga> tv) {
                final TableRow<Riga> row = new TableRow<Riga>() {
                    @Override
                    protected void updateItem(Riga item, boolean empty) {
                        super.updateItem(item, empty);
                        paintRow(this);
                    }
                };

                row.hoverProperty().addListener(new ChangeListener<Boolean>() {
                    @Override
                    public void changed(ObservableValue<? extends Boolean> o, Boolean ov, Boolean nv) {
                        paintRow(row);
                    }
                });
                row.selectedProperty().addListener(new ChangeListener<Boolean>() {
                    @Override
                    public void changed(ObservableValue<? extends Boolean> o, Boolean ov, Boolean nv) {
                        paintRow(row);
                    }
                });
                row.setOnMouseClicked(evt -> {
                    if (evt.getClickCount() == 2 && !row.isEmpty()) {
                        Riga riga = row.getItem();
                        riga.checked.set(!riga.checked.get());
                    }
                });
                return row;
            }
        });

        HBox.setHgrow(topBarSpacer, Priority.ALWAYS);

        // Listener filtri (senza lambda)
        txtSearch.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> o, String oldV, String newV) {
                applyFilter();
            }
        });
        chDifficolta.valueProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> o, String oldV, String newV) {
                applyFilter();
            }
        });

        // Seleziona/Annulla tutti
        if (btnSelAll != null)  btnSelAll.setOnAction(this::selectAll);
        if (btnSelNone != null) btnSelNone.setOnAction(this::selectNone);

        // Dati iniziali
        ObservableList<Riga> righe = FXCollections.observableArrayList();
        for (int i = 0; i < tutteLeRicette.size(); i++) {
            Ricetta rc = tutteLeRicette.get(i);
            if (rc != null) {
                righe.add(new Riga(rc));
            }
        }

        // sync set selezionati
        for (int i = 0; i < righe.size(); i++) {
            final Riga r = righe.get(i);
            r.checked.addListener(new ChangeListener<Boolean>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> obs, Boolean oldVal, Boolean val) {
                    if (val != null && val.booleanValue()) {
                        selectedIds.add(Long.valueOf(r.idRicetta));
                    } else {
                        selectedIds.remove(Long.valueOf(r.idRicetta));
                    }
                }
            });
        }

        filtered = new FilteredList<Riga>(righe, r -> true);
        table.setItems(filtered);

        // Pre-selezione
        for (int i = 0; i < ricetteGiaAssociate.size(); i++) {
            Ricetta r = ricetteGiaAssociate.get(i);
            if (r != null) {
                selectedIds.add(Long.valueOf(r.getIdRicetta()));
            }
        }
        for (int i = 0; i < filtered.size(); i++) {
            Riga r = filtered.get(i);
            r.checked.set(selectedIds.contains(Long.valueOf(r.idRicetta)));
        }

        applyFilter();
    }

    /* ===================== Azioni ===================== */
    private void selectAll(ActionEvent e) {
        if (filtered == null) return;
        for (int i = 0; i < filtered.size(); i++) {
            filtered.get(i).checked.set(true);
        }
    }

    private void selectNone(ActionEvent e) {
        if (filtered == null) return;
        for (int i = 0; i < filtered.size(); i++) {
            filtered.get(i).checked.set(false);
        }
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
            if (q.length() > 0) {
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
    /** Da usare così: controller.salvaSeConfermato(showAndWait()); */
    public void salvaSeConfermato(Optional<List<Long>> resultOpt) {
        if (resultOpt == null || !resultOpt.isPresent()) return;

        List<Long> selectedNow = resultOpt.get();
        try {
            // 1) Stato "prima" dal DB
            List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);

            Set<Long> before = new HashSet<Long>();
            for (int i = 0; i < gia.size(); i++) {
                Ricetta r = gia.get(i);
                if (r != null) before.add(Long.valueOf(r.getIdRicetta()));
            }

            // 2) Stato "dopo" dalla selezione corrente
            Set<Long> after = new HashSet<Long>(selectedNow);

            // 3) Delta: aggiunte
            for (Long idAdd : after) {
                if (!before.contains(idAdd)) {
                    sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd.longValue());
                }
            }

            // 4) Delta: rimozioni
            for (Long idRem : before) {
                if (!after.contains(idRem)) {
                    sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem.longValue());
                }
            }

            // Se per qualsiasi motivo il SessioneDao non fosse implementato,
            // si può scommentare il seguente fallback con RicettaDao:
            /*
            RicettaDao rdao = new RicettaDao();
            rdao.syncSessioneRicette(idSessionePresenza, after);
            */

            showInfo("Associazioni ricette salvate correttamente.");

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
            String zebra = (row.getIndex() % 2 == 0) ? "rgba(255,255,255,0.03)" : "transparent";
            row.setStyle("-fx-background-color:" + zebra + ";" +
                    "-fx-border-width: 0;" +
                    "-fx-border-color: transparent;");
            row.setCursor(Cursor.DEFAULT);
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.getDialogPane().setMinWidth(420);
        alert.showAndWait(); // <<<<< MOSTRA L'ALERT
    }

    private void showError(String header, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        a.setHeaderText(header);
        a.getDialogPane().setMinWidth(520);
        a.showAndWait(); // <<<<< MOSTRA L'ALERT
    }

    /* ===================== Riga tabella ===================== */
    public static class Riga {
        final long idRicetta;
        final javafx.beans.property.SimpleBooleanProperty checked = new javafx.beans.property.SimpleBooleanProperty(false);
        final javafx.beans.property.SimpleStringProperty  nome = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleStringProperty  descrizione = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleStringProperty  difficolta = new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleIntegerProperty tempoPreparazione = new javafx.beans.property.SimpleIntegerProperty();

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
        final String GRID     = "rgba(255,255,255,0.06)";
        final String TXT_HDR  = "#e9f5ec";

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                Node headerBg = table.lookup(".column-header-background");
                if (headerBg != null) {
                    headerBg.setStyle("-fx-background-color:" + BG_HDR + ";");
                }
                Set<Node> headers = table.lookupAll(".column-header");
                for (Node n : headers) {
                    if (n instanceof Region) {
                        Region r = (Region) n;
                        r.setStyle("-fx-background-color:" + BG_HDR + ";" +
                                "-fx-background-insets: 0;" +
                                "-fx-border-color:" + GRID + ";" +
                                "-fx-border-width: 0 0 1 0;");
                    }
                    Node lab = n.lookup(".label");
                    if (lab instanceof Label) {
                        Label lbl = (Label) lab;
                        lbl.setTextFill(javafx.scene.paint.Color.web(TXT_HDR));
                        lbl.setStyle("-fx-font-weight:700;");
                    }
                }
                Node filler = table.lookup(".filler");
                if (filler != null) {
                    filler.setStyle("-fx-background-color:" + BG_HDR + ";");
                }
            }
        });
    }

    /** Riapplica lo stile header quando lo skin rinasce / cambiano le colonne / resize. */
    private void hookHeaderStylingRelayout() {
        table.skinProperty().addListener(new ChangeListener<javafx.scene.control.Skin<?>>(){
            @Override
            public void changed(ObservableValue<? extends javafx.scene.control.Skin<?>> obs,
                                javafx.scene.control.Skin<?> oldSkin,
                                javafx.scene.control.Skin<?> newSkin) {
                fixTableHeaderTheme();
            }
        });
        table.getColumns().addListener((javafx.collections.ListChangeListener<TableColumn<Riga, ?>>) c -> fixTableHeaderTheme());
        table.widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> o, Number a, Number b) {
                fixTableHeaderTheme();
            }
        });
    }
}
