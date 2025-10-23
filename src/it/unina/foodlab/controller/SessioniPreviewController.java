package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Preview delle sessioni di un corso in stile "card".
 * - Filtri: Tutte / Online / In presenza + ricerca per testo
 * - Doppio click su SessionePresenza: mostra le ricette associate (read-only)
 */
public class SessioniPreviewController {

    /* ====== Palette coerente con il resto ====== */
    private static final String BG_SURFACE = "#20282b";   // fondo dialog
    private static final String BG_CARD    = "#242c2f";   // card
    private static final String TXT_MAIN   = "#e9f5ec";
    private static final String GRID_SOFT  = "rgba(255,255,255,0.08)";
    private static final String ACCENT     = "#1fb57a";
    private static final String HOVER_BG   = "rgba(31,181,122,0.16)";
    private static final String TYPE_ONLINE   = "#3b82f6"; // azzurro (chip/bordo ONLINE)
    private static final String TYPE_PRESENZA = "#10b981"; // verde (chip/bordo PRESENZA)


    /* ====== FXML ====== */
    @FXML private DialogPane dialogPane;
    @FXML private Label lblHeader;
    @FXML private ListView<Sessione> lv;
    @FXML private ToggleButton tgAll, tgOnline, tgPresenza;
    @FXML private TextField txtSearch;

    /* ====== Stato ====== */
    private final ObservableList<Sessione> backing = FXCollections.observableArrayList();
    private final FilteredList<Sessione> filtered = new FilteredList<Sessione>(backing, s -> true);
    private Corso corso;
    private SessioneDao sessioneDao;

    /** Inizializzazione dati esterni. */
    public void init(Corso corso, List<Sessione> sessioni, SessioneDao sessioneDao) {
        this.corso = corso;
        this.sessioneDao = sessioneDao;

        // Header
        if (lblHeader != null) {
            String titolo = (corso != null && corso.getArgomento() != null) ? corso.getArgomento() : "Corso";
            lblHeader.setText("Sessioni — " + titolo);
        }

        // Dialog styling
        if (dialogPane != null) {
            dialogPane.setStyle(
                "-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
                "-fx-border-color:" + GRID_SOFT + ";" +
                "-fx-border-width:1; -fx-border-radius:14; -fx-background-radius:14;"
            );
        }

        // ListView: dati + cell factory "card"
        if (lv != null) {
            lv.setItems(filtered);
            lv.setStyle("-fx-background-color:" + BG_SURFACE + "; -fx-control-inner-background:" + BG_SURFACE + ";");
            lv.setCellFactory(new javafx.util.Callback<ListView<Sessione>, ListCell<Sessione>>() {
                @Override
                public ListCell<Sessione> call(ListView<Sessione> list) {
                    return new CardCell();
                }
            });
            // doppio click
            lv.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    Sessione sel = lv.getSelectionModel().getSelectedItem();
                    if (sel instanceof SessionePresenza) {
                        openRicetteDialog((SessionePresenza) sel);
                    }
                }
            });
        }

        // Bottoni filtro: di default "Tutte"
        if (tgAll != null) {
            tgAll.setSelected(true);
            tgAll.setOnAction(e -> {
                tgAll.setSelected(true);
                if (tgOnline != null) tgOnline.setSelected(false);
                if (tgPresenza != null) tgPresenza.setSelected(false);
                refilter();
            });
        }
        if (tgOnline != null) {
            tgOnline.setOnAction(e -> {
                tgOnline.setSelected(true);
                if (tgAll != null) tgAll.setSelected(false);
                if (tgPresenza != null) tgPresenza.setSelected(false);
                refilter();
            });
        }
        if (tgPresenza != null) {
            tgPresenza.setOnAction(e -> {
                tgPresenza.setSelected(true);
                if (tgAll != null) tgAll.setSelected(false);
                if (tgOnline != null) tgOnline.setSelected(false);
                refilter();
            });
        }

        // Ricerca
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((o,a,b) -> refilter());
            txtSearch.setPromptText("Cerca"); // come screenshot
        }

        // Dati iniziali
        backing.clear();
        if (sessioni != null) backing.addAll(sessioni);
        refilter();
    }

    /* =================== Filtro =================== */
    private void refilter() {
        final boolean onlyOnline   = tgOnline != null && tgOnline.isSelected();
        final boolean onlyPresenza = tgPresenza != null && tgPresenza.isSelected();
        final String q = (txtSearch != null && txtSearch.getText() != null)
                ? txtSearch.getText().trim().toLowerCase(java.util.Locale.ROOT) : "";

        filtered.setPredicate(s -> {
            if (s == null) return false;

            if (onlyOnline && !(s instanceof SessioneOnline))   return false;
            if (onlyPresenza && !(s instanceof SessionePresenza)) return false;

            if (q.length() == 0) return true;

            String dataStr = (s.getData() != null) ? s.getData().toString() : "";
            String timeStr = "";
            if (s.getOraInizio() != null) timeStr += s.getOraInizio().toString();
            if (s.getOraFine() != null)   timeStr += " " + s.getOraFine().toString();

            if (s instanceof SessioneOnline) {
                SessioneOnline so = (SessioneOnline) s;
                String p = nz(so.getPiattaforma());
                return contains(dataStr, q) || contains(timeStr, q) || contains(p, q);
            } else {
                SessionePresenza sp = (SessionePresenza) s;
                String via = nz(sp.getVia());
                String num = nz(sp.getNum());
                String aula = nz(sp.getAula());
                String cap  = (sp.getCap() > 0) ? String.valueOf(sp.getCap()) : "";
                String ind  = join(" ", via, num, cap, aula);
                return contains(dataStr, q) || contains(timeStr, q) || contains(ind, q);
            }
        });
    }

    private static boolean contains(String s, String pieceLower) {
        return s != null && s.toLowerCase(java.util.Locale.ROOT).contains(pieceLower);
    }

    /* ============ Dialog: ricette associate ============ */
    private void openRicetteDialog(SessionePresenza sp) {
        List<Ricetta> lista;
        try {
            lista = (sessioneDao != null)
                    ? sessioneDao.findRicetteBySessionePresenza(sp.getId())
                    : new ArrayList<Ricetta>();
        } catch (Exception ex) {
            showError("Errore caricamento ricette associate: " + ex.getMessage());
            return;
        }

        Dialog<Void> dlg = new Dialog<Void>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        dlg.setTitle("Ricette — sessione del " + (sp.getData() != null ? df.format(sp.getData()) : ""));

        DialogPane pane = new DialogPane();
        pane.getButtonTypes().setAll(ButtonType.CLOSE);
        pane.setStyle(
            "-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
            "-fx-border-color:" + GRID_SOFT + ";" +
            "-fx-border-width:1; -fx-border-radius:14; -fx-background-radius:14;"
        );

        TableView<Ricetta> tv = buildRicetteTable(lista);
        pane.setContent(tv);

        dlg.setDialogPane(pane);
     // Stile dark per il bottone "Chiudi"
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setText("Chiudi");
            closeBtn.setStyle(
                "-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700;" +
                "-fx-background-radius:10; -fx-padding:8 16;"
            );
            closeBtn.setOnMouseEntered(e2 -> closeBtn.setStyle(
                "-fx-background-color:#374151; -fx-text-fill:#e9f5ec; -fx-font-weight:700;" +
                "-fx-background-radius:10; -fx-padding:8 16;"
            ));
            closeBtn.setOnMouseExited(e2 -> closeBtn.setStyle(
                "-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec; -fx-font-weight:700;" +
                "-fx-background-radius:10; -fx-padding:8 16;"
            ));
        }

        dlg.setResizable(true);
        dlg.setOnShown(ev -> {
            Node c = pane.getContent();
            if (c instanceof Region) {
                ((Region) c).setPrefSize(900, 520);
            }
        });
        dlg.showAndWait();
    }

    private TableView<Ricetta> buildRicetteTable(List<Ricetta> data) {
    TableView<Ricetta> tv = new TableView<>();
    tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    tv.setTableMenuButtonVisible(false);
    tv.setFixedCellSize(40);

    // Tema scuro coerente + niente selezione blu
    tv.setStyle(
        "-fx-background-color:" + BG_SURFACE + ";" +
        "-fx-control-inner-background:" + BG_SURFACE + ";" +
        "-fx-text-background-color:" + TXT_MAIN + ";" +
        "-fx-table-cell-border-color:" + GRID_SOFT + ";" +
        "-fx-table-header-border-color:" + GRID_SOFT + ";" +
        "-fx-selection-bar: transparent;" +
        "-fx-selection-bar-non-focused: transparent;" +
        "-fx-focus-color: transparent;" +
        "-fx-faint-focus-color: transparent;" +
        "-fx-background-insets:0; -fx-padding:0;"
    );

    // Colonne
    TableColumn<Ricetta, String> colNome = new TableColumn<>("Nome");
    colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));

    TableColumn<Ricetta, String> colDiff = new TableColumn<>("Difficoltà");
    colDiff.setCellValueFactory(new PropertyValueFactory<>("difficolta"));
    colDiff.setPrefWidth(140);
    // chip colore per difficoltà
    colDiff.setCellFactory(tc -> new TableCell<>() {
        private final Label chip = new Label();
        @Override protected void updateItem(String s, boolean empty) {
            super.updateItem(s, empty);
            if (empty || s == null) { setGraphic(null); return; }
            chip.setText(s);
            String bg = switch (s.trim().toLowerCase()) {
                case "facile"    -> "#10b981";
                case "medio"     -> "#f59e0b";
                case "difficile" -> "#ef4444";
                default          -> "#6b7280";
            };
            chip.setStyle("-fx-background-color:"+bg+"; -fx-text-fill:white; -fx-font-weight:700; -fx-font-size:12px; -fx-background-radius:999; -fx-padding:2 8;");
            setGraphic(chip);
        }
    });

    TableColumn<Ricetta, Integer> colTempo = new TableColumn<>("Minuti");
    colTempo.setCellValueFactory(new PropertyValueFactory<>("tempoPreparazione"));
    colTempo.setPrefWidth(110);
    colTempo.setStyle("-fx-alignment: CENTER;");
    colTempo.setCellFactory(tc -> new TableCell<>() {
        @Override protected void updateItem(Integer n, boolean empty) {
            super.updateItem(n, empty);
            setText(empty || n == null ? null : (n + " min"));
        }
    });

    TableColumn<Ricetta, String> colDesc = new TableColumn<>("Descrizione");
    colDesc.setCellValueFactory(new PropertyValueFactory<>("descrizione"));
    // ellissi su una riga
    colDesc.setCellFactory(tc -> new TableCell<>() {
        private final Label lbl = new Label();
        { lbl.setStyle("-fx-text-fill:#cfe5d9;"); lbl.setEllipsisString("…"); }
        @Override protected void updateItem(String s, boolean empty) {
            super.updateItem(s, empty);
            if (empty || s == null) { setGraphic(null); return; }
            lbl.setText(s);
            setGraphic(lbl);
        }
    });

    tv.getColumns().setAll(colNome, colDiff, colTempo, colDesc);
    if (data != null) tv.getItems().addAll(data);

    // Header scuro
    Platform.runLater(() -> {
        for (Node n : tv.lookupAll(".column-header")) {
            if (n instanceof Region r) {
                r.setStyle("-fx-background-color:" + BG_CARD + "; -fx-border-color:" + GRID_SOFT + "; -fx-border-width:0 0 1 0;");
            }
            Node lab = n.lookup(".label");
            if (lab instanceof Label l) {
                l.setTextFill(javafx.scene.paint.Color.web(TXT_MAIN));
                l.setStyle("-fx-font-weight:700;");
            }
        }
        Node headerBg = tv.lookup(".column-header-background");
        if (headerBg != null) headerBg.setStyle("-fx-background-color:" + BG_CARD + ";");
        Node filler = tv.lookup(".filler");
        if (filler != null) filler.setStyle("-fx-background-color:" + BG_CARD + ";");
    });

    // Placeholder
    Label ph = new Label("Nessuna ricetta associata.");
    ph.setStyle("-fx-text-fill:#b7c5cf; -fx-font-weight:700;");
    tv.setPlaceholder(ph);

    // Riga “card-like” (hover/selected)
    tv.setRowFactory(t -> new TableRow<>() {
        @Override protected void updateItem(Ricetta item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setStyle(""); return; }
            boolean on = isHover() || isSelected();
            if (on) {
                setStyle("-fx-background-color: rgba(31,181,122,0.18); -fx-border-color:#1fb57a; -fx-border-width:0 0 0 4;");
            } else {
                String base = (getIndex()%2==0) ? "rgba(255,255,255,0.03)" : "transparent";
                setStyle("-fx-background-color:"+base+";");
            }
        }
    });

    return tv;
}


    /* =================== Card cell =================== */
    private final class CardCell extends ListCell<Sessione> {
        private final VBox card = new VBox();
        private final Label labTipo = new Label();
        private final Label labRiga2 = new Label();
        private final Label labRiga3 = new Label(); // dettaglio (piattaforma o indirizzo)

        CardCell() {
            super();
            card.getChildren().addAll(labTipo, labRiga2, labRiga3);
            card.setSpacing(3);
            card.setPadding(new Insets(10));
            card.setAlignment(Pos.CENTER_LEFT);
            card.setBackground(new Background(new BackgroundFill(Color.web(BG_CARD), new CornerRadii(12), Insets.EMPTY)));
            card.setBorder(new Border(new BorderStroke(Color.web(GRID_SOFT),
                    BorderStrokeStyle.SOLID, new CornerRadii(12), new BorderWidths(1))));
            // testo
            labTipo.setTextFill(Color.web(TXT_MAIN));
            labTipo.setStyle("-fx-font-weight: 700; -fx-font-size: 13.5px;");
            labRiga2.setTextFill(Color.web(TXT_MAIN));
            labRiga2.setStyle("-fx-font-size: 12.5px;");
            labRiga3.setTextFill(Color.web(TXT_MAIN));
            labRiga3.setStyle("-fx-font-size: 12.5px; -fx-opacity: 0.95;");

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setGraphic(card);

            // hover/selected styling
            hoverProperty().addListener((o, w, h) -> paint());
            selectedProperty().addListener((o, w, s) -> paint());
        }

        @Override
        protected void updateItem(Sessione s, boolean empty) {
            super.updateItem(s, empty);
            if (empty || s == null) {
                setGraphic(null);
                setCursor(Cursor.DEFAULT);
                return;
            }
            setGraphic(card);
            render(s);
            paint();
        }

       private void render(Sessione s) {
    DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

    String data = (s.getData() != null) ? df.format(s.getData()) : "";
    String orari = (s.getOraInizio() != null ? tf.format(s.getOraInizio()) : "") +
            "–" + (s.getOraFine() != null ? tf.format(s.getOraFine()) : "");

    String color = typeColor(s); // <<--- nuovo

    if (s instanceof SessioneOnline so) {
        labTipo.setText("ONLINE");
        labRiga2.setText(data + "   " + orari);
        labRiga3.setText(nz(so.getPiattaforma()));
    } else {
        SessionePresenza sp = (SessionePresenza) s;
        labTipo.setText("PRESENZA");
        labRiga2.setText(data + "   " + orari);
        String ind = join(" ", nz(sp.getVia()), nz(sp.getNum()),
                (sp.getCap() > 0 ? String.valueOf(sp.getCap()) : ""), nz(sp.getAula()));
        labRiga3.setText(ind.trim());
    }

    // chip tipo colorata
    labTipo.setStyle("-fx-text-fill:white; -fx-font-weight:800; -fx-font-size:12.5px; "
            + "-fx-background-color:" + color + "; -fx-background-radius:9999; -fx-padding:2 8;");
}


       private void paint() {
    	    boolean accented = isHover() || isSelected();
    	    String color = getItem() == null ? ACCENT : typeColor(getItem()); // <<--- nuovo
    	    if (accented) {
    	        card.setStyle(
    	            "-fx-background-color: linear-gradient(to right," + color + " 0px," + color + " 3px, transparent 3px), " + HOVER_BG + ";" +
    	            "-fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 14, 0.25, 0, 3);"
    	        );
    	        setCursor(Cursor.HAND);
    	    } else {
    	        String zebra = (getIndex() % 2 == 0) ? "rgba(255,255,255,0.03)" : "transparent";
    	        card.setStyle(
    	            "-fx-background-color: linear-gradient(to right, transparent 0px, transparent 3px), " + zebra + ";" +
    	            "-fx-background-radius: 12;"
    	        );
    	        setCursor(Cursor.DEFAULT);
    	    }
    	}

    }

    /* =================== piccoli helpers =================== */
    private static String nz(String s) { return s == null ? "" : s; }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (p != null && !p.trim().isEmpty()) {
                    if (!first) sb.append(sep);
                    sb.append(p.trim());
                    first = false;
                }
            }
        }
        return sb.toString();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Errore");
        a.getDialogPane().setMinWidth(480);
        a.showAndWait();
    }
    
    /** Ritorna il colore di accento in base al tipo di sessione. */
    private static String typeColor(Sessione s) {
        return (s instanceof SessioneOnline) ? TYPE_ONLINE : TYPE_PRESENZA;
    }

}
