package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class SessioniPreviewController {

    @FXML private DialogPane dialogPane;
    @FXML private Label      lblHeader;
    @FXML private ListView<Sessione> lv;
    @FXML private ToggleButton tgAll, tgOnline, tgPresenza;
    @FXML private TextField   txtSearch;

    private final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");

    private FilteredList<Sessione> filtered;

    // Theme
    private static final String BG_CARD   = "#20282b";
    private static final String BG_HDR    = "#242c2f";
    private static final String TXT_MAIN  = "#e9f5ec";
    private static final String TXT_MUTED = "#cfe5d9";
    private static final String BORDER_SOFT = "rgba(255,255,255,0.06)";
    private static final String ACCENT    = "#1fb57a";
    private static final String HOVER_BG  = "rgba(31,181,122,0.22)";
    private static final String ZEBRA_BG  = "rgba(255,255,255,0.03)";

    /* ==================== INIT ==================== */
    public void init(Corso corso, List<Sessione> sessions) {
        lblHeader.setText("Sessioni — " + (corso == null ? "" : safe(corso.getArgomento())));

        // Lista e filtro base
        ObservableList<Sessione> items = FXCollections.observableArrayList();
        if (sessions != null) items.addAll(sessions);
        filtered = new FilteredList<>(items, s -> true);
        lv.setItems(filtered);

        // Celle “a card” con hover/selected in verde
        lv.setCellFactory(new Callback<>() {
            @Override public ListCell<Sessione> call(ListView<Sessione> __) {
                return new ListCell<>() {
                    private Pane wrapper;

                    @Override protected void updateItem(Sessione s, boolean empty) {
                        super.updateItem(s, empty);
                        if (empty || s == null) {
                            setGraphic(null);
                            setText(null);
                            return;
                        }
                        wrapper = buildCard(s);
                        setGraphic(wrapper);
                        setText(null);
                        paintRow(this, wrapper);
                    }

                    @Override public void updateSelected(boolean selected) {
                        super.updateSelected(selected);
                        paintRow(this, wrapper);
                    }
                };
            }
        });

        // toggle state
        tgAll.setSelected(true);
        tgOnline.setSelected(false);
        tgPresenza.setSelected(false);

        // mutuamente esclusivi
        tgAll.selectedProperty().addListener((o, was, is) -> { if (is) { tgOnline.setSelected(false); tgPresenza.setSelected(false); applyFilter(); }});
        tgOnline.selectedProperty().addListener((o, was, is) -> { if (is){ tgAll.setSelected(false); tgPresenza.setSelected(false);} else if (!tgPresenza.isSelected()) tgAll.setSelected(true); applyFilter();});
        tgPresenza.selectedProperty().addListener((o, was, is) -> { if (is){ tgAll.setSelected(false); tgOnline.setSelected(false);} else if (!tgOnline.isSelected()) tgAll.setSelected(true); applyFilter();});

        // ricerca
        txtSearch.textProperty().addListener((o, a, b) -> applyFilter());
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
            sb.append(s.getData()==null ? "" : D.format(s.getData())).append(' ');
            sb.append(formatTime(s.getOraInizio())).append(' ')
              .append(formatTime(s.getOraFine())).append(' ');

            if (s instanceof SessioneOnline so) {
                sb.append(safe(so.getPiattaforma()));
            } else if (s instanceof SessionePresenza sp) {
                String indirizzo = (safe(sp.getVia()) + " " + safe(sp.getNum())).trim().replaceAll(" +"," ");
                sb.append(indirizzo).append(' ')
                  .append(sp.getCap()).append(' ')
                  .append(safe(sp.getAula()));
            }
            return lower(sb.toString()).contains(q);
        });
    }

    /* ==================== UI building ==================== */
    private Pane buildCard(Sessione s) {
        boolean online = s instanceof SessioneOnline;

        Label chip = new Label(online ? "ONLINE" : "IN PRESENZA");
        chip.setStyle("-fx-background-color:" + (online ? "#2d6cdf" : "#3a7c6a") + ";" +
                      "-fx-text-fill:white; -fx-font-weight:800; -fx-font-size:11px;" +
                      "-fx-background-radius:999; -fx-padding:2 8;");

        Label lData = new Label(s.getData() == null ? "-" : D.format(s.getData()));
        lData.setStyle("-fx-text-fill:" + TXT_MAIN + "; -fx-font-weight:700;");

        String orario = (formatTime(s.getOraInizio()) + " — " + formatTime(s.getOraFine()));
        Label lOra = new Label(orario);
        lOra.setStyle("-fx-text-fill:" + TXT_MAIN + "; -fx-opacity:0.8;");

        HBox row1 = new HBox(10, chip, lData, lOra);

        Label lDett;
        if (online) {
            SessioneOnline so = (SessioneOnline) s;
            String piattaf = safe(so.getPiattaforma());
            lDett = new Label(piattaf.isEmpty() ? "—" : piattaf);
        } else {
            SessionePresenza sp = (SessionePresenza) spOrNull(s);
            String posti = sp != null && sp.getPostiMax() > 0 ? String.valueOf(sp.getPostiMax()) : "illimitati";
            String aula = sp != null ? safe(sp.getAula()) : "";
            String capS = (sp != null && sp.getCap() != 0) ? String.valueOf(sp.getCap()) : "—";
            String indirizzo = sp == null ? "" : (safe(sp.getVia()) + " " + safe(sp.getNum())).trim().replaceAll(" +", " ");
            String dett = String.format("%s  •  %s  •  Aula %s  •  Posti %s",
                    indirizzo.isEmpty() ? "—" : indirizzo,
                    capS,
                    aula.isEmpty() ? "—" : aula,
                    posti);
            lDett = new Label(dett);
        }
        lDett.setStyle("-fx-text-fill:" + TXT_MAIN + "; -fx-opacity:0.85;");

        VBox card = new VBox(6, row1, lDett);
        card.setFillWidth(true);
        card.setStyle("-fx-background-color: linear-gradient(to bottom," + BG_HDR + "," + BG_CARD + ");" +
                      "-fx-background-radius:10; -fx-border-radius:10; -fx-border-color:" + BORDER_SOFT + "; -fx-border-width:1;" +
                      "-fx-padding:10;");

        HBox wrap = new HBox(card);
        wrap.setStyle("-fx-background-color: transparent; -fx-background-radius:10; -fx-padding:4;");
        return wrap;
    }

    private void paintRow(ListCell<Sessione> cell, Pane wrapper) {
        if (wrapper == null) return;
        boolean highlight = !cell.isEmpty() && (cell.isHover() || cell.isSelected());
        if (highlight) {
            wrapper.setStyle(
                "-fx-background-color: " +
                    "linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT + " 3px, transparent 3px), " +
                    HOVER_BG + ";" +
                "-fx-background-radius:10;"
            );
            cell.setCursor(Cursor.HAND);
        } else {
            String base = (cell.getIndex()%2==0) ? ZEBRA_BG : "transparent";
            wrapper.setStyle("-fx-background-color:" + base + ";" +
                             "-fx-background-radius:10;");
            cell.setCursor(Cursor.DEFAULT);
        }
    }

    private SessionePresenza spOrNull(Sessione s){
        return (s instanceof SessionePresenza sp) ? sp : null;
    }

    /* ==================== Helpers ==================== */
    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static String lower(String s){ return s == null ? "" : s.toLowerCase(Locale.ROOT); }
    private String formatTime(java.time.LocalTime t) { return (t == null) ? "--:--" : T.format(t); }
}
