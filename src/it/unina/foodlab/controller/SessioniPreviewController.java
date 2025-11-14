package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SessioniPreviewController {

    @FXML private DialogPane dialogPane;
    @FXML private Label lblHeader;
    @FXML private ListView<Sessione> lv;
    @FXML private ToggleButton tgAll, tgOnline, tgPresenza;
    @FXML private TextField txtSearch;

    private final ObservableList<Sessione> backing = FXCollections.observableArrayList();
    private final FilteredList<Sessione> filtered = new FilteredList<>(backing, s -> true);
    private SessioneDao sessioneDao;

    public void init(Corso corso, List<Sessione> sessioni, SessioneDao sessioneDao) {
        this.sessioneDao = sessioneDao;

        if (lblHeader != null) {
            String titolo = (corso != null && corso.getArgomento() != null) ? corso.getArgomento() : "Corso";
            lblHeader.setText("Sessioni — " + titolo);
        }

        if (dialogPane != null) {
            dialogPane.getStyleClass().add("sessioni-preview");
            dialogPane.getStylesheets().add(
                    getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
            );
        }

        if (lv != null) {
            lv.setItems(filtered);
            lv.getStyleClass().add("list-view");
            lv.setCellFactory(list -> new CardCell());

            lv.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    Sessione sel = lv.getSelectionModel().getSelectedItem();
                    if (sel instanceof SessionePresenza sp) {
                        openRicetteDialog(sp);
                    }
                }
            });
        }

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

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((o, a, b) -> refilter());
            txtSearch.setPromptText("Cerca");
            txtSearch.getStyleClass().add("text-field");
        }

        backing.clear();
        if (sessioni != null) backing.addAll(sessioni);
        refilter();
    }

    private void refilter() {
        final boolean onlyOnline = tgOnline != null && tgOnline.isSelected();
        final boolean onlyPresenza = tgPresenza != null && tgPresenza.isSelected();
        final String q = (txtSearch != null && txtSearch.getText() != null)
                ? txtSearch.getText().trim().toLowerCase(java.util.Locale.ROOT)
                : "";

        filtered.setPredicate(s -> {
            if (s == null) return false;

            if (onlyOnline && !(s instanceof SessioneOnline)) return false;
            if (onlyPresenza && !(s instanceof SessionePresenza)) return false;

            if (q.isEmpty()) return true;

            String dataStr = (s.getData() != null) ? s.getData().toString() : "";
            String timeStr = "";
            if (s.getOraInizio() != null) timeStr += s.getOraInizio().toString();
            if (s.getOraFine() != null) timeStr += " " + s.getOraFine().toString();

            if (s instanceof SessioneOnline so) {
                return contains(dataStr, q) || contains(timeStr, q) || contains(nz(so.getPiattaforma()), q);
            } else {
                SessionePresenza sp = (SessionePresenza) s;
                String ind = join(" ", nz(sp.getVia()), nz(sp.getNum()),
                        (sp.getCap() > 0 ? String.valueOf(sp.getCap()) : ""), nz(sp.getAula()));
                return contains(dataStr, q) || contains(timeStr, q) || contains(ind, q);
            }
        });
    }

    private static boolean contains(String s, String pieceLower) {
        return s != null && s.toLowerCase(java.util.Locale.ROOT).contains(pieceLower);
    }

    private void openRicetteDialog(SessionePresenza sp) {
        List<Ricetta> lista;
        try {
            lista = (sessioneDao != null)
                    ? sessioneDao.findRicetteBySessionePresenza(sp.getId())
                    : new ArrayList<>();
        } catch (Exception ex) {
            showError("Errore caricamento ricette associate: " + ex.getMessage());
            return;
        }

        Dialog<Void> dlg = new Dialog<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        dlg.setTitle("Ricette — sessione del " + (sp.getData() != null ? df.format(sp.getData()) : ""));

        DialogPane pane = new DialogPane();
        pane.getStyleClass().addAll("sessioni-preview", "dialog-pane");
        pane.getButtonTypes().setAll(ButtonType.CLOSE);

        TableView<Ricetta> tv = buildRicetteTable(lista);
        pane.setContent(tv);

        dlg.setDialogPane(pane);

        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) closeBtn.getStyleClass().add("button-close");

        dlg.setResizable(true);
        dlg.setOnShown(ev -> {
            Node c = pane.getContent();
            if (c instanceof Region region) {
                region.setPrefSize(900, 520);
            }
        });
        dlg.showAndWait();
    }

    private TableView<Ricetta> buildRicetteTable(List<Ricetta> data) {
        TableView<Ricetta> tv = new TableView<>();
        tv.getStyleClass().add("table-view");
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setTableMenuButtonVisible(false);
        tv.setFixedCellSize(40);

        TableColumn<Ricetta, String> colNome = new TableColumn<>("Nome");
        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));

        TableColumn<Ricetta, String> colDiff = new TableColumn<>("Difficoltà");
        colDiff.setCellValueFactory(new PropertyValueFactory<>("difficolta"));
        colDiff.setPrefWidth(140);
        colDiff.setCellFactory(tc -> new TableCell<>() {
            private final Label chip = new Label();
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(s);
                chip.getStyleClass().setAll("chip", s.trim().toLowerCase());
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
        colDesc.setCellFactory(tc -> new TableCell<>() {
            private final Label lbl = new Label();
            {
                lbl.getStyleClass().add("table-description");
            }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                    return;
                }
                lbl.setText(s);
                setGraphic(lbl);
            }
        });

        if (data != null) tv.getItems().addAll(data);

        Label ph = new Label("Nessuna ricetta associata.");
        ph.getStyleClass().add("table-placeholder");
        tv.setPlaceholder(ph);

        return tv;
    }

    private final class CardCell extends ListCell<Sessione> {
        private final VBox card = new VBox();
        private final Label labTipo = new Label();
        private final Label labRiga2 = new Label();
        private final Label labRiga3 = new Label();

        CardCell() {
            super();
            card.getStyleClass().add("card-cell");
            labTipo.getStyleClass().add("label-tipo");
            labRiga2.getStyleClass().add("label-riga2");
            labRiga3.getStyleClass().add("label-riga3");

            card.getChildren().addAll(labTipo, labRiga2, labRiga3);
            card.setSpacing(3);
            card.setPadding(new Insets(10));
            card.setAlignment(Pos.CENTER_LEFT);

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setGraphic(card);
        }

        @Override
        protected void updateItem(Sessione s, boolean empty) {
            super.updateItem(s, empty);
            if (empty || s == null) {
                setGraphic(null);
                setCursor(Cursor.DEFAULT);
                return;
            }
            render(s);
            setGraphic(card);
        }

        private void render(Sessione s) {
            DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

            String data = (s.getData() != null) ? df.format(s.getData()) : "";
            String orari = (s.getOraInizio() != null ? tf.format(s.getOraInizio()) : "") +
                    "–" + (s.getOraFine() != null ? tf.format(s.getOraFine()) : "");

            if (s instanceof SessioneOnline so) {
                labTipo.setText("ONLINE");
                labTipo.getStyleClass().removeAll("tipo-presenza");
                labTipo.getStyleClass().add("tipo-online");
                labRiga2.setText(data + "   " + orari);
                labRiga3.setText(nz(so.getPiattaforma()));
            } else if (s instanceof SessionePresenza sp) {
                labTipo.setText("PRESENZA");
                labTipo.getStyleClass().removeAll("tipo-online");
                labTipo.getStyleClass().add("tipo-presenza");
                labRiga2.setText(data + "   " + orari);
                String ind = join(" ", nz(sp.getVia()), nz(sp.getNum()),
                        (sp.getCap() > 0 ? String.valueOf(sp.getCap()) : ""), nz(sp.getAula()));
                labRiga3.setText(ind.trim());
            }
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (p != null && !p.trim().isEmpty()) {
                if (!first) sb.append(sep);
                sb.append(p.trim());
                first = false;
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
}
