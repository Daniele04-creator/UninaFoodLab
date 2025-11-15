package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssociaRicetteController extends Dialog<List<Long>> {

    @FXML private VBox root;
    @FXML private TextField txtSearch;
    @FXML private ChoiceBox<String> chDifficolta;
    @FXML private javafx.scene.control.Button btnSelAll;
    @FXML private javafx.scene.control.Button btnSelNone;
    @FXML private TableView<Riga> table;
    @FXML private TableColumn<Riga, Boolean> colChk;
    @FXML private TableColumn<Riga, String> colNome;
    @FXML private TableColumn<Riga, String> colDiff;
    @FXML private TableColumn<Riga, Number> colTempo;
    @FXML private TableColumn<Riga, String> colDesc;
    @FXML private Region topBarSpacer;

    private final SessioneDao sessioneDao;
    private final int idSessionePresenza;
    private final List<Ricetta> tutteLeRicette;
    private final List<Ricetta> ricetteGiaAssociate;

    private FilteredList<Riga> filtered;

    public AssociaRicetteController(SessioneDao sessioneDao,
                                    int idSessionePresenza,
                                    List<Ricetta> tutteLeRicette,
                                    List<Ricetta> ricetteGiaAssociate) {
        if (sessioneDao == null) {
            throw new IllegalArgumentException("sessioneDao nullo");
        }
        this.sessioneDao = sessioneDao;
        this.idSessionePresenza = idSessionePresenza;
        this.tutteLeRicette = tutteLeRicette != null ? tutteLeRicette : new ArrayList<>();
        this.ricetteGiaAssociate = ricetteGiaAssociate != null ? ricetteGiaAssociate : new ArrayList<>();

        setTitle("Associa ricette alla sessione (id=" + idSessionePresenza + ")");
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            List<Long> out = new ArrayList<>();
            for (Riga r : table.getItems()) {
                if (r.checkedProperty().get()) {
                    out.add(r.getIdRicetta());
                }
            }
            out.sort(Long::compareTo);
            return out;
        });
    }

    @FXML
    private void initialize() {
        DialogPane pane = getDialogPane();
        pane.setContent(root);
        pane.getStyleClass().add("associa-ricette-dialog");
        String css = getClass()
                .getResource("/it/unina/foodlab/util/dark-theme.css")
                .toExternalForm();
        pane.getStylesheets().add(css);
        pane.setPrefSize(960, 620);
        setResizable(true);

        chDifficolta.setItems(FXCollections.observableArrayList("Tutte", "Facile", "Medio", "Difficile"));
        chDifficolta.getSelectionModel().selectFirst();

        txtSearch.setPromptText("Cerca per nome o descrizione");

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setTableMenuButtonVisible(false);
        table.setPlaceholder(new Label("Nessuna ricetta trovata."));
        table.setEditable(true);
        table.getStyleClass().add("dark-table");

        colChk.setCellValueFactory(c -> c.getValue().checkedProperty());
        colChk.setCellFactory(CheckBoxTableCell.forTableColumn(colChk));
        colChk.setEditable(true);

        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colDiff.setCellValueFactory(new PropertyValueFactory<>("difficolta"));

        colTempo.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().getTempoPreparazione()));
        colTempo.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Number n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) {
                    setText(null);
                } else {
                    setText(n.intValue() + " min");
                }
            }
        });

        colDesc.setCellValueFactory(new PropertyValueFactory<>("descrizione"));

        ObservableList<Riga> righe = FXCollections.observableArrayList();
        for (Ricetta r : tutteLeRicette) {
            if (r != null) {
                righe.add(new Riga(r));
            }
        }

        Set<Long> preSelected = new HashSet<>();
        for (Ricetta r : ricetteGiaAssociate) {
            if (r != null) {
                preSelected.add(r.getIdRicetta());
            }
        }
        for (Riga riga : righe) {
            riga.checkedProperty().set(preSelected.contains(riga.getIdRicetta()));
        }

        filtered = new FilteredList<>(righe, r -> true);
        table.setItems(filtered);

        txtSearch.textProperty().addListener((o, ov, nv) -> applyFilter());
        chDifficolta.valueProperty().addListener((o, ov, nv) -> applyFilter());

        if (btnSelAll != null) btnSelAll.setOnAction(this::selectAll);
        if (btnSelNone != null) btnSelNone.setOnAction(this::selectNone);
    }

    private void applyFilter() {
        if (filtered == null) return;

        String q = txtSearch.getText();
        if (q == null) q = "";
        q = q.trim().toLowerCase();

        String diff = chDifficolta.getValue();
        if (diff == null || diff.isEmpty()) {
            diff = "Tutte";
        }
        String finalQ = q;
        String finalDiff = diff;

        filtered.setPredicate(r -> {
            if (r == null) return false;

            boolean okTxt = finalQ.isEmpty()
                    || containsIgnoreCase(r.getNome(), finalQ)
                    || containsIgnoreCase(r.getDescrizione(), finalQ);

            boolean okDiff = "Tutte".equalsIgnoreCase(finalDiff)
                    || (r.getDifficolta() != null && finalDiff.equalsIgnoreCase(r.getDifficolta()));

            return okTxt && okDiff;
        });
    }

    private void selectAll(ActionEvent e) {
        if (filtered == null) return;
        for (Riga r : filtered) {
            r.checkedProperty().set(true);
        }
    }

    private void selectNone(ActionEvent e) {
        if (filtered == null) return;
        for (Riga r : filtered) {
            r.checkedProperty().set(false);
        }
    }

    public void salvaSeConfermato(List<Long> result) {
        if (result == null || result.isEmpty()) return;

        List<Long> selectedNow = new ArrayList<>(result);

        try {
            List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);
            Set<Long> before = new HashSet<>();
            if (gia != null) {
                for (Ricetta r : gia) {
                    if (r != null) {
                        before.add(r.getIdRicetta());
                    }
                }
            }

            Set<Long> after = new HashSet<>(selectedNow);

            for (Long idAdd : after) {
                if (!before.contains(idAdd)) {
                    sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);
                }
            }

            for (Long idRem : before) {
                if (!after.contains(idRem)) {
                    sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);
                }
            }

            showInfoDark("Operazione completata", "Associazioni ricette salvate correttamente.");
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "Errore sconosciuto.";
            showErrorDark("Errore salvataggio", msg);
        }
    }

    private static boolean containsIgnoreCase(String s, String q) {
        if (s == null || q == null) return false;
        return s.toLowerCase().contains(q);
    }

    public static class Riga {
        final long idRicetta;
        final javafx.beans.property.SimpleBooleanProperty checked =
                new javafx.beans.property.SimpleBooleanProperty(false);
        final javafx.beans.property.SimpleStringProperty nome =
                new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleStringProperty descrizione =
                new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleStringProperty difficolta =
                new javafx.beans.property.SimpleStringProperty();
        final javafx.beans.property.SimpleIntegerProperty tempoPreparazione =
                new javafx.beans.property.SimpleIntegerProperty();

        public Riga(Ricetta r) {
            this.idRicetta = r.getIdRicetta();
            this.nome.set(r.getNome() != null ? r.getNome() : "");
            this.descrizione.set(r.getDescrizione() != null ? r.getDescrizione() : "");
            this.difficolta.set(r.getDifficolta() != null ? r.getDifficolta() : "");
            Integer tp = r.getTempoPreparazione();
            this.tempoPreparazione.set(tp != null && tp >= 0 ? tp : 0);
        }

        public long getIdRicetta() { return idRicetta; }
        public String getNome() { return nome.get(); }
        public String getDescrizione() { return descrizione.get(); }
        public String getDifficolta() { return difficolta.get(); }
        public Integer getTempoPreparazione() { return tempoPreparazione.get(); }
        public javafx.beans.property.BooleanProperty checkedProperty() { return checked; }
    }

    private void showInfoDark(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title == null ? "Messaggio" : title);
        alert.setHeaderText(null);
        alert.setGraphic(null);
        alert.getDialogPane().setContent(new Label(message == null ? "" : message));
        DialogPane dp = alert.getDialogPane();
        dp.getStylesheets().add(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );
        dp.getStyleClass().add("associa-ricette-dialog");
        dp.setMinWidth(460);
        alert.showAndWait();
    }

    private void showErrorDark(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title == null ? "Errore" : title);
        a.setHeaderText(null);
        a.getDialogPane().setContent(new Label(msg == null ? "" : msg));
        DialogPane dp = a.getDialogPane();
        dp.getStylesheets().add(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );
        dp.getStyleClass().add("associa-ricette-dialog");
        dp.setMinWidth(520);
        a.showAndWait();
    }
}
