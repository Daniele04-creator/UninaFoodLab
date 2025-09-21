package it.unina.foodlab.ui;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.Locale;

/**
 * Dialog per associare una o più ricette (tabella: ricetta)
 * alla sessione in presenza (tabella: sessione_presenza, PK: idSessionePresenza)
 * tramite la join table: sessione_presenza_ricetta(fk_id_sess_pr, fk_id_ricetta).
 */
public class AssociaRicetteSessionePresenzaDialog extends Dialog<List<Long>> {

    private final SessioneDao sessioneDao;
    private final int idSessionePresenza;

    private final TextField txtSearch = new TextField();
    private final ChoiceBox<String> chDifficolta = new ChoiceBox<>(
            FXCollections.observableArrayList("Tutte", "facile", "medio", "difficile")
    );
    private final Button btnSelAll = new Button("Seleziona tutto");
    private final Button btnSelNone = new Button("Nessuna");
    private final TableView<Riga> table = new TableView<>();

    /** id_ricetta selezionati (indipendente dal filtro corrente) */
    private final ObservableSet<Long> selectedIds = FXCollections.observableSet();

    public AssociaRicetteSessionePresenzaDialog(SessioneDao sessioneDao, int idSessionePresenza,
                                                List<Ricetta> tutteLeRicette,
                                                List<Ricetta> ricetteGiaAssociate) {
        this.sessioneDao = sessioneDao;
        this.idSessionePresenza = idSessionePresenza;

        setTitle("Associa ricette alla sessione (id=" + idSessionePresenza + ")");
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        // UI top bar: ricerca + filtro difficoltà + azioni rapide
        txtSearch.setPromptText("Cerca per nome o descrizione…");
        chDifficolta.setValue("Tutte");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(10,
                new Label("Filtro:"), txtSearch,
                new Label("Difficoltà:"), chDifficolta,
                spacer, btnSelAll, btnSelNone
        );
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Table setup
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessuna ricetta trovata."));

        TableColumn<Riga, Boolean> colChk = new TableColumn<>("✓");
        colChk.setPrefWidth(60);
        colChk.setStyle("-fx-alignment: CENTER;");
        colChk.setCellValueFactory(cd -> cd.getValue().checked);
        colChk.setCellFactory(tc -> {
            CheckBoxTableCell<Riga, Boolean> cell = new CheckBoxTableCell<>();
            cell.setAlignment(Pos.CENTER);
            return cell;
        });

        TableColumn<Riga, String> colNome = new TableColumn<>("Nome");
        colNome.setCellValueFactory(cd -> cd.getValue().nome);

        TableColumn<Riga, String> colDiff = new TableColumn<>("Difficoltà");
        colDiff.setPrefWidth(120);
        colDiff.setCellValueFactory(cd -> cd.getValue().difficolta);

        TableColumn<Riga, Number> colTempo = new TableColumn<>("Tempo prep. (min)");
        colTempo.setPrefWidth(150);
        colTempo.setStyle("-fx-alignment: CENTER-RIGHT;");
        colTempo.setCellValueFactory(cd -> cd.getValue().tempoPreparazione);

        TableColumn<Riga, String> colDesc = new TableColumn<>("Descrizione");
        colDesc.setCellValueFactory(cd -> cd.getValue().descrizione);

        table.getColumns().addAll(colChk, colNome, colDiff, colTempo, colDesc);

        // Dati
        var righe = FXCollections.observableArrayList(
                tutteLeRicette.stream().map(Riga::new).toList()
        );

        // preseleziona quelle già collegate (se fornite)
        if (ricetteGiaAssociate != null) {
            for (Ricetta r : ricetteGiaAssociate) {
                if (r != null) selectedIds.add(r.getIdRicetta());
            }
        }
        righe.forEach(r -> r.checked.set(selectedIds.contains(r.idRicetta)));

        // filtro (testo + difficoltà)
        FilteredList<Riga> filtered = new FilteredList<>(righe, r -> true);
        table.setItems(filtered);

        Runnable applyFilter = () -> {
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
        };

        txtSearch.textProperty().addListener((o, a, b) -> applyFilter.run());
        chDifficolta.valueProperty().addListener((o, a, b) -> applyFilter.run());
        applyFilter.run();

        // Selezioni rapide (agiscono sulle righe FILTRATE, non su tutte)
        btnSelAll.setOnAction(e -> filtered.forEach(r -> r.checked.set(true)));
        btnSelNone.setOnAction(e -> filtered.forEach(r -> r.checked.set(false)));

        // Mantieni selectedIds in sync con le checkbox
        righe.forEach(r -> r.checked.addListener((obs, old, val) -> {
            if (val) selectedIds.add(r.idRicetta);
            else selectedIds.remove(r.idRicetta);
        }));

        // Toggle selection su doppio click riga
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

        VBox root = new VBox(12, topBar, table);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        getDialogPane().setContent(root);

        // OK sempre abilitato (nessun vincolo)
        Node okBtn = getDialogPane().lookupButton(ButtonType.OK);
        okBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> false, selectedIds));

        // Result: lista di id_ricetta selezionati
        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            return new ArrayList<>(selectedIds);
        });
    }

    /**
     * Persisti la scelta nel DB: chiama questo metodo dopo showAndWait().
     * Salvataggio a DELTA (ADD/REMOVE) rispetto allo stato reale del DB,
     * così non rischi di "sostituire" per errore.
     */
    public void salvaSeConfermato(Optional<List<Long>> resultOpt) {
        resultOpt.ifPresent(selectedNow -> {
            try {
                // stato attuale dal DB (prima)
                List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);
                Set<Long> before = new HashSet<>();
                for (Ricetta r : gia) {
                    if (r != null) before.add(r.getIdRicetta());
                }

                // stato dopo dalla UI
                Set<Long> after = new HashSet<>(selectedNow);

                // ADD: after \ before
                for (Long idAdd : after) {
                    if (!before.contains(idAdd)) {
                        sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);
                    }
                }
                // REMOVE: before \ after
                for (Long idRem : before) {
                    if (!after.contains(idRem)) {
                        sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);
                    }
                }

                new Alert(Alert.AlertType.INFORMATION, "Associazioni ricette salvate.").showAndWait();
            } catch (Exception ex) {
                Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                a.setHeaderText("Errore salvataggio ricette");
                a.getDialogPane().setMinWidth(520);
                a.showAndWait();
            }
        });
    }

    /* --------- riga table: binding ai nomi ESATTI degli attributi --------- */
    private static class Riga {
        final long idRicetta; // ricetta.id_ricetta
        final SimpleBooleanProperty checked = new SimpleBooleanProperty(false);
        final SimpleStringProperty  nome = new SimpleStringProperty();
        final SimpleStringProperty  descrizione = new SimpleStringProperty();
        final SimpleStringProperty  difficolta = new SimpleStringProperty(); // facile|medio|difficile
        final SimpleIntegerProperty tempoPreparazione = new SimpleIntegerProperty(); // minuti

        Riga(Ricetta r) {
            this.idRicetta = r.getIdRicetta();
            this.nome.set(r.getNome());
            this.descrizione.set(r.getDescrizione());
            this.difficolta.set(r.getDifficolta());
            this.tempoPreparazione.set(r.getTempoPreparazione());
        }
    }
}