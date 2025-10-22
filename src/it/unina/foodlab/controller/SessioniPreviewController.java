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
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Preview delle sessioni di un corso.
 * - Filtri e ricerca
 * - Doppio click su una sessione in presenza → viewer delle ricette associate
 */
public class SessioniPreviewController {

    @FXML private DialogPane dialogPane;
    @FXML private Label lblHeader;
    @FXML private ListView<Sessione> lv;
    @FXML private ToggleButton tgAll, tgOnline, tgPresenza;
    @FXML private TextField txtSearch;

    private final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");

    private FilteredList<Sessione> filtered;
    /** CF dello chef (owner) richiesto da SessioneDao(String). */
    private String ownerCfChef;

    /* ======= Palette coerente ======= */
    private static final String BG_CARD     = "#20282b";
    private static final String BG_HDR      = "#242c2f";
    private static final String TXT_MAIN    = "#e9f5ec";
    private static final String BORDER_SOFT = "rgba(255,255,255,0.06)";
    private static final String ACCENT      = "#1fb57a";
    private static final String HOVER_BG    = "rgba(31,181,122,0.22)";
    private static final String ZEBRA_BG    = "rgba(255,255,255,0.03)";

    /* ==================== API INIT ==================== */

    /** Backward-compatible: puoi continuare a chiamare ctrl.init(corso, sessions). */
    public void init(Corso corso, java.util.List<Sessione> sessions) {
        initCommon(corso, sessions);
    }

    /** Variante consigliata: passi anche il CF dell'owner per abilitare il viewer ricette. */
    public void init(Corso corso, java.util.List<Sessione> sessions, String cfChef) {
        this.ownerCfChef = (cfChef == null) ? null : cfChef.trim();
        initCommon(corso, sessions);
    }

    /** Imposta/aggiorna il CF dell’owner (opzionale). */
    public void setOwnerCfChef(String cf) {
        this.ownerCfChef = (cf == null) ? null : cf.trim();
    }

    /* ==================== INIT comune ==================== */
    private void initCommon(Corso corso, java.util.List<Sessione> sessions) {
        lblHeader.setText("Sessioni — " + (corso == null ? "" : safe(corso.getArgomento())));

        ObservableList<Sessione> items = FXCollections.observableArrayList();
        if (sessions != null) items.addAll(sessions);
        filtered = new FilteredList<>(items, s -> true);
        lv.setItems(filtered);

        /* Celle card + doppio click */
        lv.setCellFactory(new Callback<>() {
            @Override public ListCell<Sessione> call(ListView<Sessione> __) {
                return new ListCell<>() {
                    private Pane wrapper;

                    {   // doppio click
                        setOnMouseClicked(evt -> {
                            if (evt.getClickCount() == 2 && !isEmpty() && getItem() != null) {
                                apriRicette(getItem());
                            }
                        });
                    }

                    @Override protected void updateItem(Sessione s, boolean empty) {
                        super.updateItem(s, empty);
                        if (empty || s == null) { setGraphic(null); setText(null); return; }
                        wrapper = buildCard(s);
                        setGraphic(wrapper);
                        setText(null);
                        paintRow(this, wrapper);
                    }

                    @Override public void updateSelected(boolean sel) {
                        super.updateSelected(sel);
                        paintRow(this, wrapper);
                    }
                };
            }
        });

        // toggle esclusivi
        tgAll.setSelected(true);
        tgOnline.setSelected(false);
        tgPresenza.setSelected(false);

        tgAll.selectedProperty().addListener((o, w, is) -> { if (is) { tgOnline.setSelected(false); tgPresenza.setSelected(false); applyFilter(); }});
        tgOnline.selectedProperty().addListener((o, w, is) -> { if (is){ tgAll.setSelected(false); tgPresenza.setSelected(false);} else if (!tgPresenza.isSelected()) tgAll.setSelected(true); applyFilter();});
        tgPresenza.selectedProperty().addListener((o, w, is) -> { if (is){ tgAll.setSelected(false); tgOnline.setSelected(false);} else if (!tgOnline.isSelected()) tgAll.setSelected(true); applyFilter();});

        txtSearch.textProperty().addListener((o,a,b)->applyFilter());
    }

    /* ==================== FILTRI ==================== */
    private void applyFilter() {
        final String q = lower(trim(safe(txtSearch.getText())));
        final boolean onlyOn = tgOnline.isSelected();
        final boolean onlyPr = tgPresenza.isSelected();

        filtered.setPredicate(s -> {
            if (s == null) return false;
            if (onlyOn && !(s instanceof SessioneOnline))  return false;
            if (onlyPr && !(s instanceof SessionePresenza))return false;
            if (q.isEmpty()) return true;

            StringBuilder sb = new StringBuilder();
            sb.append(s.getData()==null ? "" : D.format(s.getData())).append(' ')
              .append(formatTime(s.getOraInizio())).append(' ')
              .append(formatTime(s.getOraFine())).append(' ');

            if (s instanceof SessioneOnline so)
                sb.append(safe(so.getPiattaforma()));
            else if (s instanceof SessionePresenza sp)
                sb.append(safe(sp.getVia())).append(' ').append(safe(sp.getNum()))
                  .append(' ').append(sp.getCap()).append(' ').append(safe(sp.getAula()));

            return lower(sb.toString()).contains(q);
        });
    }

    /* ==================== UI: card e row style ==================== */
    private Pane buildCard(Sessione s) {
        boolean online = s instanceof SessioneOnline;

        Label chip = new Label(online ? "ONLINE" : "IN PRESENZA");
        chip.setStyle("-fx-background-color:" + (online ? "#2d6cdf" : "#3a7c6a") + ";" +
                      "-fx-text-fill:white; -fx-font-weight:800; -fx-font-size:11px;" +
                      "-fx-background-radius:999; -fx-padding:2 8;");

        Label lData = new Label(s.getData()==null ? "-" : D.format(s.getData()));
        lData.setStyle("-fx-text-fill:" + TXT_MAIN + "; -fx-font-weight:700;");

        Label lOra = new Label(formatTime(s.getOraInizio()) + " — " + formatTime(s.getOraFine()));
        lOra.setStyle("-fx-text-fill:" + TXT_MAIN + "; -fx-opacity:0.8;");

        HBox row1 = new HBox(10, chip, lData, lOra);

        Label lDett;
        if (online) {
            SessioneOnline so = (SessioneOnline) s;
            lDett = new Label(safe(so.getPiattaforma()));
        } else {
            SessionePresenza sp = (SessionePresenza) s;
            String dett = String.format("%s %s • CAP %s • Aula %s • Posti %s",
                    safe(sp.getVia()), safe(sp.getNum()),
                    sp.getCap()==0?"—":sp.getCap(),
                    safe(sp.getAula()),
                    sp.getPostiMax()==0?"—":sp.getPostiMax());
            lDett = new Label(dett);
        }
        lDett.setStyle("-fx-text-fill:" + TXT_MAIN + "; -fx-opacity:0.85;");

        VBox card = new VBox(6, row1, lDett);
        card.setStyle("-fx-background-color: linear-gradient(to bottom," + BG_HDR + "," + BG_CARD + ");" +
                      "-fx-background-radius:10; -fx-border-radius:10; -fx-border-color:" + BORDER_SOFT + "; -fx-border-width:1; -fx-padding:10;");
        HBox wrap = new HBox(card);
        wrap.setStyle("-fx-background-color: transparent; -fx-padding:4;");
        return wrap;
    }

    private void paintRow(ListCell<Sessione> cell, Pane wrapper) {
        if (wrapper == null) return;
        boolean highlight = !cell.isEmpty() && (cell.isHover() || cell.isSelected());
        if (highlight) {
            wrapper.setStyle("-fx-background-color: linear-gradient(to right,"+ACCENT+" 0, "+ACCENT+" 3px, transparent 3px), "+HOVER_BG+";");
            cell.setCursor(Cursor.HAND);
        } else {
            String base = (cell.getIndex()%2==0)?ZEBRA_BG:"transparent";
            wrapper.setStyle("-fx-background-color:"+base+";");
            cell.setCursor(Cursor.DEFAULT);
        }
    }

    /* ==================== Doppio click: viewer ricette ==================== */
    private void apriRicette(Sessione s) {
        if (!(s instanceof SessionePresenza sp)) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Le ricette sono disponibili solo per le sessioni in presenza.");
            a.setHeaderText(null); scurisciDialog(a.getDialogPane()); a.showAndWait(); return;
        }
        if (ownerCfChef == null || ownerCfChef.isBlank()) {
            Alert w = new Alert(Alert.AlertType.WARNING, "Impossibile caricare le ricette: CF Chef non impostato.");
            w.setHeaderText(null); scurisciDialog(w.getDialogPane()); w.showAndWait(); return;
        }
        try {
            SessioneDao dao = new SessioneDao(ownerCfChef);
            // id normalmente è sp.getId(); se nel modello hai altro nome, adegua qui
            java.util.List<Ricetta> ricette = dao.findRicetteBySessionePresenza(sp.getId());

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("Ricette — " + (sp.getData()==null ? "" : D.format(sp.getData())) + " • " + formatTime(sp.getOraInizio()));
            DialogPane dp = dlg.getDialogPane();
            dp.getButtonTypes().add(ButtonType.CLOSE);
            scurisciDialog(dp);

            TableView<Ricetta> tv = new TableView<>();
            tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            tv.setItems(FXCollections.observableArrayList(ricette));
            tv.setPlaceholder(new Label("Nessuna ricetta associata."));

            TableColumn<Ricetta,String> cNome  = new TableColumn<>("Nome");
            cNome.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getNome()));

            TableColumn<Ricetta,String> cDiff  = new TableColumn<>("Difficoltà");
            cDiff.setPrefWidth(120);
            cDiff.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(safe(cd.getValue().getDifficolta())));

            TableColumn<Ricetta,String> cTempo = new TableColumn<>("Tempo (min)");
            cTempo.setPrefWidth(100);
            cTempo.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(String.valueOf(cd.getValue().getTempoPreparazione())));

            TableColumn<Ricetta,String> cDesc  = new TableColumn<>("Descrizione");
            cDesc.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(safe(cd.getValue().getDescrizione())));

            tv.getColumns().addAll(cNome, cDiff, cTempo, cDesc);
            applicaTemaTabella(tv);

            dp.setContent(tv);
            dlg.showAndWait();
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Errore nel caricamento ricette:\n" + ex.getMessage());
            err.setHeaderText(null); scurisciDialog(err.getDialogPane()); err.showAndWait();
        }
    }

    /* ==================== Helpers UI tema ==================== */
    private static void scurisciDialog(DialogPane dp){
        dp.setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);" +
                    "-fx-border-color:" + BORDER_SOFT + "; -fx-border-radius:12; -fx-background-radius:12;");
        Button bt = (Button) dp.lookupButton(ButtonType.CLOSE);
        if(bt!=null)
            bt.setStyle("-fx-background-color:#2b3438; -fx-text-fill:"+TXT_MAIN+"; -fx-font-weight:700; -fx-background-radius:10; -fx-padding:8 14;");
    }

    private static void applicaTemaTabella(TableView<?> tv){
        tv.setStyle("-fx-background-color:#20282b; -fx-control-inner-background:#20282b; -fx-text-background-color:#e9f5ec;" +
                    "-fx-table-cell-border-color:" + BORDER_SOFT + "; -fx-table-header-border-color:" + BORDER_SOFT + ";");
        Platform.runLater(() -> {
            Node headerBg = tv.lookup(".column-header-background");
            if(headerBg instanceof Region r) r.setStyle("-fx-background-color:"+BG_HDR+";");
            for(Node n: tv.lookupAll(".column-header")){
                if(n instanceof Region rr)
                    rr.setStyle("-fx-background-color:"+BG_HDR+"; -fx-border-color:"+BORDER_SOFT+"; -fx-border-width:0 0 1 0;");
                Node lab=n.lookup(".label");
                if(lab instanceof Labeled l) l.setTextFill(javafx.scene.paint.Color.web(TXT_MAIN));
            }
        });
    }

    /* ==================== Helpers base ==================== */
    private static String safe(String s){return s==null?"":s.trim();}
    private static String trim(String s){return s==null?"":s.trim();}
    private static String lower(String s){return s==null?"":s.toLowerCase(Locale.ROOT);}
    private String formatTime(LocalTime t){return (t==null)?"--:--":T.format(t);}
}
