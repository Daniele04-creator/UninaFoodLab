package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class SessioniPreviewController {

    @FXML private DialogPane dialogPane;
    @FXML private Label lblHeader;
    @FXML private ListView<Sessione> lv;
    @FXML private ToggleButton tgAll, tgOnline, tgPresenza;
    @FXML private TextField txtSearch;

    private final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");

    private FilteredList<Sessione> filtered;

    /* ==================== INIT ==================== */

    public void init(Corso corso, List<Sessione> sessions) {
        lblHeader.setText("Sessioni — " + (corso == null ? "" : safe(corso.getArgomento())));

        ObservableList<Sessione> items = FXCollections.observableArrayList();
        if (sessions != null) items.addAll(sessions);
        filtered = new FilteredList<Sessione>(items, new java.util.function.Predicate<Sessione>() {
            @Override public boolean test(Sessione s) { return true; }
        });
        lv.setItems(filtered);

        // Celle “a card”
        lv.setCellFactory(new javafx.util.Callback<ListView<Sessione>, ListCell<Sessione>>() {
            @Override public ListCell<Sessione> call(ListView<Sessione> __) {
                return new ListCell<Sessione>() {
                    @Override protected void updateItem(Sessione s, boolean empty) {
                        super.updateItem(s, empty);
                        if (empty || s == null) {
                            setGraphic(null);
                            setText(null);
                        } else {
                            setGraphic(buildCard(s));
                            setText(null);
                        }
                    }
                };
            }
        });

        // Stato iniziale toggles
        tgAll.setSelected(true);
        tgOnline.setSelected(false);
        tgPresenza.setSelected(false);

        // Filtri toggle: mutua esclusione e applyFilter
        tgAll.selectedProperty().addListener(new javafx.beans.value.ChangeListener<Boolean>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> o, Boolean a, Boolean b) {
                if (Boolean.TRUE.equals(b)) {
                    tgOnline.setSelected(false);
                    tgPresenza.setSelected(false);
                    applyFilter();
                }
            }
        });
        tgOnline.selectedProperty().addListener(new javafx.beans.value.ChangeListener<Boolean>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> o, Boolean a, Boolean b) {
                if (Boolean.TRUE.equals(b)) {
                    tgAll.setSelected(false);
                    tgPresenza.setSelected(false);
                    applyFilter();
                } else if (!tgPresenza.isSelected()) {
                    tgAll.setSelected(true);
                    applyFilter();
                }
            }
        });
        tgPresenza.selectedProperty().addListener(new javafx.beans.value.ChangeListener<Boolean>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> o, Boolean a, Boolean b) {
                if (Boolean.TRUE.equals(b)) {
                    tgAll.setSelected(false);
                    tgOnline.setSelected(false);
                    applyFilter();
                } else if (!tgOnline.isSelected()) {
                    tgAll.setSelected(true);
                    applyFilter();
                }
            }
        });

        // Ricerca
        txtSearch.textProperty().addListener(new javafx.beans.value.ChangeListener<String>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends String> o, String a, String b) {
                applyFilter();
            }
        });

        // Altezza auto (celle variabili)
        lv.setFixedCellSize(-1);
    }

    /* ==================== FILTRI ==================== */

    private void applyFilter() {
        final String q = lower(trim(safe(txtSearch.getText())));
        final boolean onlyOn = tgOnline.isSelected();
        final boolean onlyPr = tgPresenza.isSelected();

        filtered.setPredicate(new java.util.function.Predicate<Sessione>() {
            @Override public boolean test(Sessione s) {
                if (s == null) return false;

                // filtro modalità
                if (onlyOn && !(s instanceof SessioneOnline)) return false;
                if (onlyPr && !(s instanceof SessionePresenza)) return false;

                if (q.isEmpty()) return true;

                StringBuilder sb = new StringBuilder();
                // data e orari
                sb.append(s.getData() == null ? "" : D.format(s.getData())).append(' ');
                sb.append(formatTime(s.getOraInizio())).append(' ');
                sb.append(formatTime(s.getOraFine())).append(' ');

                if (s instanceof SessioneOnline) {
                    SessioneOnline so = (SessioneOnline) s;
                    sb.append(safe(so.getPiattaforma()));
                } else if (s instanceof SessionePresenza) {
                    SessionePresenza sp = (SessionePresenza) s;
                    // via/num, CAP, Aula, Posti
                    sb.append(safe(sp.getVia())).append(' ')
                      .append(safe(sp.getNum())).append(' ')
                      .append(String.valueOf(sp.getCap())).append(' ')
                      .append(safe(sp.getAula())).append(' ')
                      .append(sp.getPostiMax() > 0 ? String.valueOf(sp.getPostiMax()) : "illimitati");
                }

                return lower(sb.toString()).contains(q);
            }
        });
    }

    /* ==================== UI CARD ==================== */

    private Pane buildCard(Sessione s) {
        final boolean online = (s instanceof SessioneOnline);

        // Badge modalità
        Label chip = new Label(online ? "ONLINE" : "IN PRESENZA");
        chip.getStyleClass().add("chip");
        chip.getStyleClass().add(online ? "chip-online" : "chip-presenza");

        // Riga 1: data e orari
        Label lData = new Label(s.getData() == null ? "-" : D.format(s.getData()));
        lData.getStyleClass().add("title");

        String orario = (formatTime(s.getOraInizio()) + " — " + formatTime(s.getOraFine()));
        Label lOra = new Label(orario);
        lOra.getStyleClass().add("muted");

        HBox row1 = new HBox(10.0);
        row1.getChildren().addAll(chip, lData, lOra);

        // Riga 2: dettagli
        Label lDett;
        if (online) {
            SessioneOnline so = (SessioneOnline) s;
            String piattaf = safe(so.getPiattaforma());
            lDett = new Label(piattaf.isEmpty() ? "—" : piattaf);
        } else {
            SessionePresenza sp = (SessionePresenza) s;
            String posti = sp.getPostiMax() > 0 ? String.valueOf(sp.getPostiMax()) : "illimitati";
            String aula = safe(sp.getAula());
            String capS = (sp.getCap() == 0 ? "-" : String.valueOf(sp.getCap()));
            String indirizzo = (safe(sp.getVia()) + " " + safe(sp.getNum())).trim().replaceAll(" +", " ");
            String dett = String.format("%s  •  %s  •  Aula %s  •  Posti %s",
                    indirizzo.isEmpty() ? "—" : indirizzo,
                    capS,
                    aula.isEmpty() ? "—" : aula,
                    posti);
            lDett = new Label(dett);
        }
        lDett.getStyleClass().add("body");

        VBox card = new VBox(6.0);
        card.getChildren().addAll(row1, lDett);
        card.getStyleClass().add("card");
        card.setFillWidth(true);

        HBox wrap = new HBox(card);
        wrap.getStyleClass().add("card-wrap");
        return wrap;
    }

    /* ==================== HELPERS ==================== */

    private static String safe(String s) { return (s == null) ? "" : s.trim(); }

    private static String trim(String s) { return (s == null) ? "" : s.trim(); }

    private static String lower(String s) {
        return (s == null) ? "" : s.toLowerCase(Locale.ROOT);
    }

    private String formatTime(LocalTime t) {
        return (t == null) ? "--:--" : T.format(t);
    }
}
