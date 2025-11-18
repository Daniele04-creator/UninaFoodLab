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
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SessioniPreviewController {

    @FXML private Label lblHeader;
    @FXML private ListView<Sessione> lv;
    @FXML private ToggleButton tgAll;
    @FXML private ToggleButton tgOnline;
    @FXML private ToggleButton tgPresenza;
    @FXML private TextField txtSearch;

    private final ObservableList<Sessione> backing = FXCollections.observableArrayList();
    private final FilteredList<Sessione> filtered = new FilteredList<>(backing, s -> true);
    private SessioneDao sessioneDao;

    public void init(Corso corso, List<Sessione> sessioni, SessioneDao sessioneDao) {
        this.sessioneDao = sessioneDao;

        String titolo = corso != null && corso.getArgomento() != null
                ? corso.getArgomento()
                : "Corso";
        lblHeader.setText("Sessioni — " + titolo);

        lv.setItems(filtered);
        lv.setCellFactory(list -> new CardCell());
        lv.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                Sessione sel = lv.getSelectionModel().getSelectedItem();
                if (sel instanceof SessionePresenza sp) {
                    openRicetteDialog(sp);
                }
            }
        });

        txtSearch.textProperty().addListener((o, oldV, newV) -> refilter());

        backing.clear();
        if (sessioni != null) {
            backing.addAll(sessioni);
        }
        refilter();
    }

    @FXML
    private void onFiltroAll() {
        tgAll.setSelected(true);
        tgOnline.setSelected(false);
        tgPresenza.setSelected(false);
        refilter();
    }

    @FXML
    private void onFiltroOnline() {
        tgAll.setSelected(false);
        tgOnline.setSelected(true);
        tgPresenza.setSelected(false);
        refilter();
    }

    @FXML
    private void onFiltroPresenza() {
        tgAll.setSelected(false);
        tgOnline.setSelected(false);
        tgPresenza.setSelected(true);
        refilter();
    }

    private void refilter() {
        boolean onlyOnline = tgOnline.isSelected();
        boolean onlyPresenza = tgPresenza.isSelected();
        String q = txtSearch.getText() == null
                ? ""
                : txtSearch.getText().trim().toLowerCase(Locale.ROOT);

        filtered.setPredicate(s -> {
            if (s == null) {
                return false;
            }

            if (onlyOnline && !(s instanceof SessioneOnline)) {
                return false;
            }
            if (onlyPresenza && !(s instanceof SessionePresenza)) {
                return false;
            }

            if (q.isEmpty()) {
                return true;
            }

            String dataStr = s.getData() != null ? s.getData().toString() : "";
            String timeStr = "";
            if (s.getOraInizio() != null) {
                timeStr += s.getOraInizio().toString();
            }
            if (s.getOraFine() != null) {
                if (!timeStr.isEmpty()) {
                    timeStr += " ";
                }
                timeStr += s.getOraFine().toString();
            }

            if (s instanceof SessioneOnline so) {
                return contains(dataStr, q)
                        || contains(timeStr, q)
                        || contains(so.getPiattaforma(), q);
            } else {
                SessionePresenza sp = (SessionePresenza) s;
                String ind = (sp.getVia() + " "
                        + sp.getNum() + " "
                        + (sp.getCap() > 0 ? String.valueOf(sp.getCap()) : "") + " "
                        + sp.getAula()).trim();
                return contains(dataStr, q)
                        || contains(timeStr, q)
                        || contains(ind, q);
            }
        });
    }

    private static boolean contains(String s, String pieceLower) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(pieceLower);
    }

    private void openRicetteDialog(SessionePresenza sp) {
        List<Ricetta> lista;
        try {
            lista = sessioneDao.findRicetteBySessionePresenza(sp.getId());
        } catch (Exception ex) {
            showError("Errore caricamento ricette associate: " + ex.getMessage());
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dataLabel = sp.getData() != null ? df.format(sp.getData()) : "";
        dialog.setTitle("Ricette — sessione del " + dataLabel);

        DialogPane pane = new DialogPane();
        pane.getStyleClass().addAll("sessioni-preview", "dialog-pane");
        pane.getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/it/unina/foodlab/util/dark-theme.css")
                ).toExternalForm()
        );
        pane.getButtonTypes().setAll(ButtonType.CLOSE);

        TableView<Ricetta> tv = buildRicetteTable(lista);
        pane.setContent(tv);
        dialog.setDialogPane(pane);

        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.getStyleClass().add("button-close");
        }

        dialog.setResizable(true);
        dialog.setOnShown(ev -> {
            Node content = pane.getContent();
            if (content instanceof Region region) {
                region.setPrefSize(900, 520);
            }
        });

        dialog.showAndWait();
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
            @Override
            protected void updateItem(String s, boolean empty) {
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
            @Override
            protected void updateItem(Integer n, boolean empty) {
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
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                    return;
                }
                lbl.setText(s);
                setGraphic(lbl);
            }
        });

        tv.getColumns().add(colNome);
        tv.getColumns().add(colDiff);
        tv.getColumns().add(colTempo);
        tv.getColumns().add(colDesc);
        tv.getItems().setAll(data);

        return tv;
    }

    private final class CardCell extends ListCell<Sessione> {

        private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm");

        private final VBox card = new VBox();
        private final Label labTipo = new Label();
        private final Label labRiga2 = new Label();
        private final Label labRiga3 = new Label();

        CardCell() {
            card.getStyleClass().add("card-cell");
            labTipo.getStyleClass().add("label-tipo");
            labRiga2.getStyleClass().add("label-riga2");
            labRiga3.getStyleClass().add("label-riga3");

            card.getChildren().addAll(labTipo, labRiga2, labRiga3);
            card.setSpacing(3);
            card.setPadding(new Insets(10));
            card.setAlignment(Pos.CENTER_LEFT);

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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
            setCursor(Cursor.HAND);
        }

        private void render(Sessione s) {
            String data = s.getData() != null ? DF.format(s.getData()) : "";
            String orari = "";
            if (s.getOraInizio() != null) {
                orari += TF.format(s.getOraInizio());
            }
            orari += "–";
            if (s.getOraFine() != null) {
                orari += TF.format(s.getOraFine());
            }

            labRiga2.setText(data + "   " + orari);

            if (s instanceof SessioneOnline so) {
                labTipo.setText("ONLINE");
                labTipo.getStyleClass().removeAll("tipo-presenza");
                if (!labTipo.getStyleClass().contains("tipo-online")) {
                    labTipo.getStyleClass().add("tipo-online");
                }
                labRiga3.setText(so.getPiattaforma());
            } else if (s instanceof SessionePresenza sp) {
                labTipo.setText("PRESENZA");
                labTipo.getStyleClass().removeAll("tipo-online");
                if (!labTipo.getStyleClass().contains("tipo-presenza")) {
                    labTipo.getStyleClass().add("tipo-presenza");
                }
                String ind = (sp.getVia() + " "
                        + sp.getNum() + " "
                        + (sp.getCap() > 0 ? String.valueOf(sp.getCap()) : "") + " "
                        + sp.getAula()).trim();
                labRiga3.setText(ind);
            }
        }
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Errore");
        a.getDialogPane().setMinWidth(480);
        a.showAndWait();
    }
}
