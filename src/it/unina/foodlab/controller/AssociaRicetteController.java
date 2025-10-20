package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * Dialog per associare ricette a una sessione in presenza.
 * Restituisce la lista di id ricetta selezionati quando l'utente conferma (OK).
 */
public class AssociaRicetteController extends Dialog<List<Long>> {

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
        setResultConverter(new CallbackResult());
    }

    private static class CallbackResult implements javafx.util.Callback<ButtonType, List<Long>> {
        private final ObservableSet<Long> selectedIdsRef;
        CallbackResult() { this.selectedIdsRef = null; }
        CallbackResult(ObservableSet<Long> ref) { this.selectedIdsRef = ref; }
        @Override public List<Long> call(ButtonType bt) {
            if (selectedIdsRef == null) return null;
            return (bt == ButtonType.OK) ? new ArrayList<Long>(selectedIdsRef) : null;
        }
    }

    /* ===================== Initialize ===================== */

    @FXML
    private void initialize() {
        // Il contenuto del dialog viene dal root FXML
        getDialogPane().setContent(root);
        getDialogPane().setStyle("-fx-background-color: #2E3440;");

        // Applica stylesheet se presente

        // Popola filtro difficoltà
        ObservableList<String> diffItems = FXCollections.observableArrayList("Tutte", "facile", "medio", "difficile");
        chDifficolta.setItems(diffItems);
        chDifficolta.setValue("Tutte");

        // Tabella
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessuna ricetta trovata."));

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

        // Doppio click = toggle del check
        table.setRowFactory(new javafx.util.Callback<TableView<Riga>, TableRow<Riga>>() {
            @Override public TableRow<Riga> call(TableView<Riga> tv) {
                final TableRow<Riga> row = new TableRow<Riga>();
                row.setOnMouseClicked(evt -> {
                    if (evt.getClickCount() == 2 && !row.isEmpty()) {
                        Riga r = row.getItem();
                        r.checked.set(!r.checked.get());
                    }
                });
                return row;
            }
        });

        HBox.setHgrow(topBarSpacer, Priority.ALWAYS);

        // Listener filtri (senza usare features avanzate)
        txtSearch.textProperty().addListener((o, a, b) -> applyFilter());
        chDifficolta.valueProperty().addListener((o, a, b) -> applyFilter());

        // Seleziona/Annulla tutti
        if (btnSelAll != null) {
            btnSelAll.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) {
                    if (filtered != null) {
                        for (Riga r : filtered) r.checked.set(true);
                    }
                }
            });
        }
        if (btnSelNone != null) {
            btnSelNone.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) {
                    if (filtered != null) {
                        for (Riga r : filtered) r.checked.set(false);
                    }
                }
            });
        }

        // Dati iniziali (niente stream)
        ObservableList<Riga> righe = FXCollections.observableArrayList();
        for (int i = 0; i < tutteLeRicette.size(); i++) {
            Ricetta rc = tutteLeRicette.get(i);
            if (rc != null) righe.add(new Riga(rc));
        }

        // Selezione: mantieni set id selezionati sincronizzato
        for (int i = 0; i < righe.size(); i++) {
            final Riga r = righe.get(i);
            r.checked.addListener((obs, oldVal, val) -> {
                if (val) selectedIds.add(r.idRicetta); else selectedIds.remove(r.idRicetta);
            });
        }

        filtered = new FilteredList<Riga>(righe, r -> true);
        table.setItems(filtered);

        // Pre-selezione ricette già associate
        for (int i = 0; i < ricetteGiaAssociate.size(); i++) {
            Ricetta r = ricetteGiaAssociate.get(i);
            if (r != null) selectedIds.add(r.getIdRicetta());
        }
        for (Riga r : filtered) {
            r.checked.set(selectedIds.contains(Long.valueOf(r.idRicetta)));
        }

        // Applica filtro iniziale
        applyFilter();

        // Personalizza bottoni OK/Cancel con icone (se disponibili)
        styleDialogButtons();

        // Imposta il converter del risultato (passo il riferimento corretto al set selezionati)
        setResultConverter(new CallbackResult(selectedIds));
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
            if (!q.isEmpty()) {
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

    private static String valueOrEmpty(String s) {
        return (s == null) ? "" : s;
    }

    /* ===================== Salvataggio ===================== */

    /**
     * Chiama questo metodo passando il risultato del dialog:
     *   controller.salvaSeConfermato(showAndWait());
     */
    public void salvaSeConfermato(Optional<List<Long>> resultOpt) {
        if (resultOpt == null || !resultOpt.isPresent()) return;

        List<Long> selectedNow = resultOpt.get();
        try {
            List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);

            // before = set di id già presenti
            Set<Long> before = new HashSet<Long>();
            for (int i = 0; i < gia.size(); i++) {
                Ricetta r = gia.get(i);
                if (r != null) before.add(r.getIdRicetta());
            }

            // after = set di id selezionati ora
            Set<Long> after = new HashSet<Long>(selectedNow);

            // Aggiunte
            for (Long idAdd : after) {
                if (!before.contains(idAdd)) {
                    sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);
                }
            }
            // Rimozioni
            for (Long idRem : before) {
                if (!after.contains(idRem)) {
                    sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);
                }
            }

            showInfo("Associazioni ricette salvate.");

        } catch (Exception ex) {
            showError("Errore salvataggio ricette", ex.getMessage());
        }
    }

    /* ===================== UI Helpers ===================== */

    private void styleDialogButtons() {
        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        if (okBtn != null) {
            okBtn.setStyle("-fx-background-color: #5E81AC; -fx-text-fill: #ECEFF4;");
            ImageView okIcon = loadIcon("/icons/ok-128.png");
            if (okIcon != null) {
                okIcon.setFitWidth(16);
                okIcon.setFitHeight(16);
                okBtn.setGraphic(okIcon);
                okBtn.setText("");
                okBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        }

        Button cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) {
            cancelBtn.setStyle("-fx-background-color: #4C566A; -fx-text-fill: #ECEFF4;");
            ImageView cancelIcon = loadIcon("/icons/cancel-128.png");
            if (cancelIcon != null) {
                cancelIcon.setFitWidth(16);
                cancelIcon.setFitHeight(16);
                cancelBtn.setGraphic(cancelIcon);
                cancelBtn.setText("");
                cancelBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        }
    }

    private ImageView loadIcon(String path) {
        try {
            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) return null;
            Image img = new Image(is);
            return new ImageView(img);
        } catch (Exception ex) {
            // se manca l'icona non è bloccante
            return null;
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.getDialogPane().setMinWidth(420);
    }

    private void showError(String header, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        a.setHeaderText(header);
        a.getDialogPane().setMinWidth(520);
    }

    /* ===================== Riga tabella ===================== */

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
