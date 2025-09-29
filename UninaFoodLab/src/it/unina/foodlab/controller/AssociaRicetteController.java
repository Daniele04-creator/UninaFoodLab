package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.*;

public class AssociaRicetteController extends Dialog<List<Long>> {

    // Root dell'FXML (fx:id="root")
    @FXML private VBox root;

    // UI
    @FXML private TextField txtSearch;
    @FXML private ChoiceBox<String> chDifficolta;
    @FXML private Button btnSelAll, btnSelNone;
    @FXML private TableView<Riga> table;
    @FXML private TableColumn<Riga, Boolean> colChk;
    @FXML private TableColumn<Riga, String>  colNome, colDiff, colDesc;
    @FXML private TableColumn<Riga, Number>  colTempo;
    @FXML private Region topBarSpacer;

    // Stato/dep
    private final SessioneDao sessioneDao;
    private final int idSessionePresenza;
    private final List<Ricetta> tutteLeRicette;
    private final List<Ricetta> ricetteGiaAssociate;

    private final ObservableSet<Long> selectedIds = FXCollections.observableSet();
    private FilteredList<Riga> filtered;

    // >>> Costruttore con argomenti (usato dalla ControllerFactory)
    public AssociaRicetteController(SessioneDao sessioneDao,
                                    int idSessionePresenza,
                                    List<Ricetta> tutteLeRicette,
                                    List<Ricetta> ricetteGiaAssociate) {
        this.sessioneDao = sessioneDao;
        this.idSessionePresenza = idSessionePresenza;
        this.tutteLeRicette = (tutteLeRicette != null) ? tutteLeRicette : List.of();
        this.ricetteGiaAssociate = (ricetteGiaAssociate != null) ? ricetteGiaAssociate : List.of();

        setTitle("Associa ricette alla sessione (id=" + idSessionePresenza + ")");
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        setResultConverter(bt -> (bt == ButtonType.OK) ? new ArrayList<>(selectedIds) : null);
    }

    // >>> Chiamato dall'FXML Loader dopo l'iniezione dei @FXML
    @FXML
    private void initialize() {
        // Monta il contenuto del Dialog dalla root caricata
    	 getDialogPane().setContent(root);

    	    // 1️⃣ Sfondo scuro del DialogPane
    	    getDialogPane().setStyle("-fx-background-color: #2E3440;");

    	  

        chDifficolta.setItems(FXCollections.observableArrayList("Tutte", "facile", "medio", "difficile"));
        chDifficolta.setValue("Tutte");

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessuna ricetta trovata."));

        colChk.setPrefWidth(60);
        colChk.setStyle("-fx-alignment: CENTER;");
        colChk.setCellValueFactory(cd -> cd.getValue().checked);
        colChk.setCellFactory(CheckBoxTableCell.forTableColumn(colChk));

        colNome.setCellValueFactory(cd -> cd.getValue().nome);
        colDiff.setPrefWidth(120);     colDiff.setCellValueFactory(cd -> cd.getValue().difficolta);
        colTempo.setPrefWidth(150);    colTempo.setStyle("-fx-alignment: CENTER-RIGHT;");
        colTempo.setCellValueFactory(cd -> cd.getValue().tempoPreparazione);
        colDesc.setCellValueFactory(cd -> cd.getValue().descrizione);

        table.setRowFactory(tv -> {
            TableRow<Riga> row = new TableRow<>();
            row.setOnMouseClicked(evt -> {
                if (evt.getClickCount() == 2 && !row.isEmpty()) {
                    Riga r = row.getItem();
                    r.checked.set(!r.checked.get());
                }
            });
            return row;
        });

        HBox.setHgrow(topBarSpacer, Priority.ALWAYS);

        txtSearch.textProperty().addListener((o, a, b) -> applyFilter());
        chDifficolta.valueProperty().addListener((o, a, b) -> applyFilter());

        btnSelAll.setOnAction(e -> { if (filtered != null) filtered.forEach(r -> r.checked.set(true)); });
        btnSelNone.setOnAction(e -> { if (filtered != null) filtered.forEach(r -> r.checked.set(false)); });

        // Dati iniziali
        var righe = FXCollections.observableArrayList(tutteLeRicette.stream().map(Riga::new).toList());
        righe.forEach(r -> r.checked.addListener((obs, old, val) -> {
            if (val) selectedIds.add(r.idRicetta); else selectedIds.remove(r.idRicetta);
        }));
        filtered = new FilteredList<>(righe, r -> true);
        table.setItems(filtered);

        // Preselezione
        ricetteGiaAssociate.forEach(r -> { if (r != null) selectedIds.add(r.getIdRicetta()); });
        filtered.forEach(r -> r.checked.set(selectedIds.contains(r.idRicetta)));

        applyFilter();

        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        if (okBtn != null) okBtn.setStyle("-fx-background-color: #5E81AC; -fx-text-fill: #ECEFF4;");
        Button cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) cancelBtn.setStyle("-fx-background-color: #4C566A; -fx-text-fill: #ECEFF4;");
        
        ImageView okIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/ok-128.png")));
        okIcon.setFitWidth(16);   // dimensione larghezza
        okIcon.setFitHeight(16);  // dimensione altezza
        okBtn.setGraphic(okIcon);
        okBtn.setText("");         // rimuove il testo

        ImageView cancelIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/cancel-128.png")));
        cancelIcon.setFitWidth(16);
        cancelIcon.setFitHeight(16);
        cancelBtn.setGraphic(cancelIcon);
        cancelBtn.setText("");
        okBtn.setDisable(false);
    }

    private void applyFilter() {
        if (filtered == null) return;
        String q = Optional.ofNullable(txtSearch.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        String diff = Optional.ofNullable(chDifficolta.getValue()).orElse("Tutte");

        filtered.setPredicate(r -> {
            if (r == null) return false;
            boolean okTxt = q.isEmpty()
                    || (r.nome.get() != null && r.nome.get().toLowerCase(Locale.ROOT).contains(q))
                    || (r.descrizione.get() != null && r.descrizione.get().toLowerCase(Locale.ROOT).contains(q));
            boolean okDiff = "Tutte".equals(diff) || diff.equalsIgnoreCase(r.difficolta.get());
            return okTxt && okDiff;
        });
    }

    public void salvaSeConfermato(Optional<List<Long>> resultOpt) {
        resultOpt.ifPresent(selectedNow -> {
            try {
                List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);
                Set<Long> before = new HashSet<>();
                for (Ricetta r : gia) if (r != null) before.add(r.getIdRicetta());

                Set<Long> after = new HashSet<>(selectedNow);
                for (Long idAdd : after) if (!before.contains(idAdd))
                    sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);
                for (Long idRem : before) if (!after.contains(idRem))
                    sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);

                new Alert(Alert.AlertType.INFORMATION, "Associazioni ricette salvate.").showAndWait();
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                a.setHeaderText("Errore salvataggio ricette");
                a.getDialogPane().setMinWidth(520);
                a.showAndWait();
            }
        });
    }

    /* Riga tabella */
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
}
