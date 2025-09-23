package it.unina.foodlab.controller;

import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Chef;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class CorsiPanelController {

    public static final String APP_CSS = "/app.css";

    /* ====== FXML ====== */
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cbArgFiltro;
    @FXML private Button btnClear, btnRefresh, btnReport;
    @FXML private Button btnNew, btnEdit, btnDelete, btnAssocRicette;
    @FXML private TableView<Corso> table;
    @FXML private TableColumn<Corso, Long>       colId;
    @FXML private TableColumn<Corso, String>     colArg;
    @FXML private TableColumn<Corso, String>     colFreq;
    @FXML private TableColumn<Corso, LocalDate>  colInizio;
    @FXML private TableColumn<Corso, LocalDate>  colFine;
    @FXML private TableColumn<Corso, String>     colChef;

    /* ====== Stato/Dati ====== */
    private final ObservableList<Corso> backing  = FXCollections.observableArrayList();
    private final FilteredList<Corso>   filtered = new FilteredList<>(backing, c -> true);
    private final SortedList<Corso>     sorted   = new SortedList<>(filtered);

    private CorsoDao corsoDao;
    private SessioneDao sessioneDao;

    /* ====== Init ====== */
    @FXML
    private void initialize() {
        initTableColumns();

        table.setItems(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());

        // Filtri
        txtSearch.textProperty().addListener((obs, ov, nv) -> refilter());
        cbArgFiltro.valueProperty().addListener((obs, ov, nv) -> refilter());

        btnClear.setOnAction(e -> {
            txtSearch.clear();
            cbArgFiltro.getSelectionModel().clearSelection();
        });
        btnRefresh.setOnAction(e -> reload());
        btnReport.setOnAction(e -> openReportMode());

        // Abilitazione bottoni in base alla selezione
        table.getSelectionModel().selectedItemProperty().addListener((obs, ov, sel) -> {
            boolean has = sel != null;
            btnEdit.setDisable(!has);
            btnDelete.setDisable(!has);
            btnAssocRicette.setDisable(!has);
        });

        // CRUD stub (sostituisci con implementazioni reali)
        btnNew.setOnAction(e -> onNew());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnAssocRicette.setOnAction(e -> onAssocRicette());
    }

    private void initTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("idCorso"));

        colArg.setCellValueFactory(new PropertyValueFactory<>("argomento"));

        colFreq.setCellValueFactory(new PropertyValueFactory<>("frequenza"));

        colInizio.setCellValueFactory(new PropertyValueFactory<>("dataInizio"));
        colInizio.setCellFactory(tc -> new TableCell<>() {
            { setAlignment(Pos.CENTER); }
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        colFine.setCellValueFactory(new PropertyValueFactory<>("dataFine"));
        colFine.setCellFactory(tc -> new TableCell<>() {
            { setAlignment(Pos.CENTER); }
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        // Colonna Chef come stringa composta
        colChef.setCellValueFactory(cd -> Bindings.createStringBinding(() -> {
            Corso c = cd.getValue();
            if (c == null || c.getChef() == null) return "";
            Chef ch = c.getChef();
            String nome = ch.getNome() == null ? "" : ch.getNome();
            String cogn = ch.getCognome() == null ? "" : ch.getCognome();
            String full = (nome + " " + cogn).trim();
            return full.isEmpty() ? (ch.getCF_Chef() == null ? "" : ch.getCF_Chef()) : full;
        }));

        // Ordine di default: per data inizio desc
        table.getSortOrder().setAll(colInizio);
        colInizio.setSortType(TableColumn.SortType.DESCENDING);
    }

    /* ====== Wiring DAOs ====== */
    public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao) {
        this.corsoDao = corsoDao;
        this.sessioneDao = sessioneDao;

        reload();

        // Popola filtro argomenti (se non hai il metodo, passa una lista vuota)
        List<String> args = Collections.emptyList();
        try {
            // se hai un metodo nel dao, es: corsoDao.findAllArgomenti()
            // args = corsoDao.findAllArgomenti();
        } catch (Exception ignore) { /* opzionale */ }

        cbArgFiltro.getItems().setAll(args);
        cbArgFiltro.setEditable(false);
        cbArgFiltro.setConverter(new StringConverter<>() {
            @Override public String toString(String s) { return s; }
            @Override public String fromString(String s) { return s; }
        });
    }

    /* ====== Dati & Filtri ====== */
    private void reload() {
        if (corsoDao == null) {
            backing.clear();
            return;
        }
        try {
            List<Corso> list = corsoDao.findAll();
            if (list == null) list = Collections.emptyList();
            backing.setAll(list); // OK: ObservableList<Corso>
            // Ordinamento secondario opzionale
            sorted.setComparator(Comparator.comparing(Corso::getDataInizio, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        } catch (Exception ex) {
            showError("Errore caricando corsi: " + ex.getMessage());
            backing.clear();
        }
    }

    private void refilter() {
        String q = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String arg = cbArgFiltro.getValue();

        Predicate<Corso> p = c -> {
            if (c == null) return false;

            boolean okArg = (arg == null || arg.isBlank()) || arg.equalsIgnoreCase(c.getArgomento());
            if (!okArg) return false;

            if (q.isBlank()) return true;

            String chefStr = "";
            if (c.getChef() != null) {
                String nome = c.getChef().getNome() == null ? "" : c.getChef().getNome();
                String cogn = c.getChef().getCognome() == null ? "" : c.getChef().getCognome();
                chefStr = (nome + " " + cogn).trim();
            }
            return String.valueOf(c.getIdCorso()).contains(q)
                    || (c.getArgomento() != null && c.getArgomento().toLowerCase().contains(q))
                    || (c.getFrequenza() != null && c.getFrequenza().toLowerCase().contains(q))
                    || chefStr.toLowerCase().contains(q);
        };

        filtered.setPredicate(p);
    }

    /* ====== “Report” (placeholder) ====== */
    private void openReportMode() {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        styleDialog(dlg);
        dlg.setHeaderText("Report mensile");
        dlg.setContentText("Qui andrà la vista del report mensile.");
        dlg.showAndWait();
    }

    /* ====== CRUD stubs ====== */
    private void onNew()         { showInfo("Nuovo corso (stub)"); }
    private void onEdit()        { showInfo("Modifica corso (stub)"); }
    private void onDelete()      { showInfo("Elimina corso (stub)"); }
    private void onAssocRicette(){ showInfo("Associa ricette (stub)"); }

    /* ====== Util ====== */
    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        styleDialog(a);
        a.setHeaderText("Errore");
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        styleDialog(a);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    /** Applica lo stesso CSS ai Dialog. */
    public void styleDialog(Dialog<?> dlg) {
        try {
            URL css = getClass().getResource(APP_CSS);
            if (css != null) {
                var list = dlg.getDialogPane().getStylesheets();
                String uri = css.toExternalForm();
                if (!list.contains(uri)) list.add(uri);
            } else {
                System.err.println("[CorsiPanel] CSS non trovato: " + APP_CSS);
            }
        } catch (Exception ex) {
            System.err.println("[CorsiPanel] Errore applicando CSS al dialog: " + ex.getMessage());
        }
    }
}
