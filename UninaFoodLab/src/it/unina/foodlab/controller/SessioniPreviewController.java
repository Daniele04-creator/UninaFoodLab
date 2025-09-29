package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SessioniPreviewController {

    @FXML private DialogPane dialogPane;
    @FXML private Label lblHeader;
    @FXML private ListView<Sessione> lv;
    @FXML private ToggleButton tgAll, tgOnline, tgPresenza;
    @FXML private TextField txtSearch;

    private final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");

    private FilteredList<Sessione> filtered;

    public void init(Corso corso, List<Sessione> sessions) {
        lblHeader.setText("Sessioni — " + (corso == null ? "" : corso.getArgomento()));

        filtered = new FilteredList<>(FXCollections.observableArrayList(sessions), s -> true);
        lv.setItems(filtered);

        
        lv.setCellFactory(__ -> new ListCell<>() {
            @Override protected void updateItem(Sessione s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                setGraphic(buildCard(s));
                setText(null);
            }
        });

        // filtri toggle
        tgAll.selectedProperty().addListener((o, a, b) -> {
            if (b) { tgOnline.setSelected(false); tgPresenza.setSelected(false); applyFilter(); }
        });
        tgOnline.selectedProperty().addListener((o, a, b) -> {
            if (b) { tgAll.setSelected(false); tgPresenza.setSelected(false); applyFilter(); }
            else if (!tgPresenza.isSelected()) tgAll.setSelected(true);
        });
        tgPresenza.selectedProperty().addListener((o, a, b) -> {
            if (b) { tgAll.setSelected(false); tgOnline.setSelected(false); applyFilter(); }
            else if (!tgOnline.isSelected()) tgAll.setSelected(true);
        });

        // ricerca
        txtSearch.textProperty().addListener((o,a,b) -> applyFilter());

        // stile lista: spazio tra le celle
        lv.setFixedCellSize(-1); // altezza auto
    }

    private void applyFilter() {
        String q = txtSearch.getText() == null ? "" : txtSearch.getText().toLowerCase(Locale.ROOT).trim();
        boolean onlyOn = tgOnline.isSelected();
        boolean onlyPr = tgPresenza.isSelected();

        filtered.setPredicate(s -> {
            if (s == null) return false;

            // filtro modalità
            if (onlyOn && !(s instanceof SessioneOnline)) return false;
            if (onlyPr && !(s instanceof SessionePresenza)) return false;

            if (q.isEmpty()) return true;

            StringBuilder sb = new StringBuilder();
            sb.append(s.getData() == null ? "" : D.format(s.getData())).append(' ');
            sb.append(s.getOraInizio() == null ? "" : T.format(s.getOraInizio())).append(' ');
            sb.append(s.getOraFine() == null ? "" : T.format(s.getOraFine())).append(' ');

            if (s instanceof SessioneOnline so) {
                sb.append(so.getPiattaforma() == null ? "" : so.getPiattaforma());
            } else if (s instanceof SessionePresenza sp) {
                sb.append(nullToEmpty(sp.getVia())).append(' ')
                  .append(nullToEmpty(sp.getNum())).append(' ')
                  .append(sp.getCap()).append(' ')
                  .append(nullToEmpty(sp.getAula()));
            }
            return sb.toString().toLowerCase(Locale.ROOT).contains(q);
        });
    }

    private Pane buildCard(Sessione s) {
        boolean online = s instanceof SessioneOnline;

        // badge modalità
        Label chip = new Label(online ? "ONLINE" : "IN PRESENZA");
        chip.getStyleClass().addAll("chip", online ? "chip-online" : "chip-presenza");

        // riga 1: data e orari
        Label lData  = new Label(s.getData() == null ? "-" : D.format(s.getData()));
        lData.getStyleClass().add("title");
        Label lOra   = new Label((s.getOraInizio()==null?"--:--":T.format(s.getOraInizio())) +
                                 " — " +
                                 (s.getOraFine()==null?"--:--":T.format(s.getOraFine())));
        lOra.getStyleClass().add("muted");

        HBox row1 = new HBox(10, chip, lData, lOra);

        // riga 2: dettagli
        Label lDett;
        if (online) {
            String piattaf = ((SessioneOnline) s).getPiattaforma();
            lDett = new Label(nullToEmpty(piattaf).isEmpty() ? "—" : piattaf);
        } else {
            SessionePresenza sp = (SessionePresenza) s;
            String dett = String.format("%s %s  •  %s  •  Aula %s  •  Posti %s",
                    nullToEmpty(sp.getVia()),
                    nullToEmpty(sp.getNum()),
                    sp.getCap() == 0 ? "-" : String.valueOf(sp.getCap()),
                    nullToEmpty(sp.getAula()),
                    sp.getPostiMax() > 0 ? sp.getPostiMax() : "illimitati");
            lDett = new Label(dett.trim().replaceAll(" +", " "));
        }
        lDett.getStyleClass().add("body");

        VBox card = new VBox(6, row1, lDett);
        card.getStyleClass().add("card");
        card.setFillWidth(true);

        // wrap in HBox per margini laterali
        HBox wrap = new HBox(card);
        wrap.getStyleClass().add("card-wrap");
        return wrap;
    }

    private String nullToEmpty(Object o) { return o == null ? "" : String.valueOf(o); }
}
