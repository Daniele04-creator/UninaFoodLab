package it.unina.foodlab.controller;

import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.dao.RicettaDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;

import it.unina.foodlab.controller.AssociaRicetteController;

import javafx.scene.control.Tooltip;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

public class CorsiPanelController {

    public static final String APP_CSS = "/app.css"; //lop
    private static final String ALL_OPTION = "Tutte";
    private static final int FILTERS_BADGE_MAX_CHARS = 32;
    private static final String LABEL_ARG  = "Argomento";
    private static final String LABEL_FREQ = "Frequenza";
    private static final String LABEL_CHEF = "Chef";
    private static final String LABEL_ID   = "ID";
    private static final String LABEL_PERIODO = "Periodo";
 // in cima alla classe
    private RicettaDao ricettaDao;

    private String formatFilterLabel(String base, String value) {
        return base + ": " + (isBlank(value) ? "(tutte)" : value);
    }

    private String formatDateRangeLabel(LocalDate from, LocalDate to) {
        if (from == null && to == null) return LABEL_PERIODO + ": (nessuno)";
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM");
        String l = (from != null) ? df.format(from) : "…";
        String r = (to   != null) ? df.format(to)   : "…";
        return LABEL_PERIODO + ": " + l + "–" + r;
    }

    // --- TOP BAR ---
    @FXML private MenuButton btnFilters;
    @FXML private MenuItem miFiltroArg;
    @FXML private MenuItem miFiltroFreq;
    @FXML private MenuItem miFiltroChef;
    @FXML private MenuItem miFiltroId;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Button btnApplyDate;

    // pulisci filtri INSIDE menu
    @FXML private Button btnClearInMenu;
    @FXML private Button btnRefresh, btnReport;

    // --- TABELLA ---
    @FXML private TableView<Corso> table;
    @FXML private TableColumn<Corso, Long> colId;
    @FXML private TableColumn<Corso, String> colArg;
    @FXML private TableColumn<Corso, String> colFreq;
    @FXML private TableColumn<Corso, LocalDate> colInizio;
    @FXML private TableColumn<Corso, LocalDate> colFine;
    @FXML private TableColumn<Corso, String> colChef;

    // --- BOTTOM BAR ---
    @FXML private Button btnNew, btnEdit, btnDelete, btnAssocRicette;

    // --- DATI ---
    private final ObservableList<Corso> backing  = FXCollections.observableArrayList();
    private final FilteredList<Corso> filtered   = new FilteredList<>(backing, c -> true);
    private final SortedList<Corso>   sorted     = new SortedList<>(filtered);

    private CorsoDao corsoDao;
    private SessioneDao sessioneDao;

    // --- STATO FILTRI ---
    private String filtroArg   = null;
    private String filtroFreq  = null;
    private String filtroChef  = null;  // etichetta completa
    private String filtroId    = null;  // stringa id ESATTA
    private LocalDate dateFrom = null;
    private LocalDate dateTo   = null;

    @FXML
    private void initialize() {
        initTableColumns();

        table.setItems(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());

        // --- MENU FILTRI (tendine dinamiche) ---
        miFiltroArg.setOnAction(e -> {
            String scelto = askChoice("Filtro Argomento", "Seleziona argomento", distinctArgomenti(), filtroArg);
            filtroArg = normalizeAllToNull(scelto);
            refilter();
            updateFiltersUI();
        });

        miFiltroFreq.setOnAction(e -> {
            String scelto = askChoice("Filtro Frequenza", "Seleziona frequenza", distinctFrequenze(), filtroFreq);
            filtroFreq = normalizeAllToNull(scelto);
            refilter();
            updateFiltersUI();
        });

        miFiltroChef.setOnAction(e -> {
            String scelto = askChoice("Filtro Chef", "Seleziona Chef", distinctChefLabels(), filtroChef);
            filtroChef = normalizeAllToNull(scelto);
            refilter();
            updateFiltersUI();
        });

        miFiltroId.setOnAction(e -> {
            String scelto = askChoice("Filtro ID", "Seleziona ID corso", distinctIdLabels(), filtroId);
            filtroId = normalizeAllToNull(scelto);
            refilter();
            updateFiltersUI();
        });

        // Range date
        btnApplyDate.setOnAction(e -> {
            dateFrom = dpFrom.getValue();
            dateTo   = dpTo.getValue();
            refilter();
            updateFiltersUI();
        });

        // Pulisci tutti i filtri (BOTTONE DENTRO IL MENU)
        btnClearInMenu.setOnAction(e -> {
            filtroArg = filtroFreq = filtroChef = filtroId = null;
            dateFrom = dateTo = null;
            dpFrom.setValue(null);
            dpTo.setValue(null);
            refilter();
            updateFiltersUI();
        });

        btnRefresh.setOnAction(e -> reload());
        btnReport.setOnAction(e -> openReportMode());

        // Abilitazione bottoni CRUD in base alla selezione
        table.getSelectionModel().selectedItemProperty().addListener((obs, ov, sel) -> {
            boolean has = sel != null;
            boolean own = isOwnedByLoggedChef(sel);
            btnEdit.setDisable(!has || !own);
            btnDelete.setDisable(!has || !own);
            btnAssocRicette.setDisable(!has || !own);
        });

        // Doppio click: viewer (read-only)
        table.setRowFactory(tv -> {
            TableRow<Corso> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()
                        && e.getButton() == MouseButton.PRIMARY
                        && e.getClickCount() == 2) {
                    Corso corso = row.getItem();
                    showSessioniDialog(corso);
                }
            });
            return row;
        });

        // Bottoni CRUD
        btnNew.setOnAction(e -> onNew());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnAssocRicette.setOnAction(e -> onAssociateRecipes());
        
        table.setRowFactory(tv -> {
    TableRow<Corso> row = new TableRow<>();
    row.setOnMouseClicked(ev -> {
        if (ev.getClickCount() == 2 && !row.isEmpty()) {
            openSessioniPreview(row.getItem());
        }
    });
    return row;
});


        // Stato iniziale UI filtri
        updateFiltersUI();
    }

    public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao) {
        this.corsoDao = corsoDao;
        this.sessioneDao = sessioneDao;
        if (this.ricettaDao == null) this.ricettaDao = new RicettaDao();
        reload();
    }
    
    public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao, RicettaDao ricettaDao) {
        this.corsoDao = corsoDao;
        this.sessioneDao = sessioneDao;
        this.ricettaDao = ricettaDao;
        reload();
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

        colChef.setCellValueFactory(cd -> Bindings.createStringBinding(() -> {
            Corso c = cd.getValue();
            if (c == null || c.getChef() == null) return "";
            Chef ch = c.getChef();
            String full = ((nz(ch.getNome())) + " " + (nz(ch.getCognome()))).trim();
            return full.isEmpty() ? nz(ch.getCF_Chef()) : full;
        }));

        table.getSortOrder().add(colInizio);
        colInizio.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void reload() {
        if (corsoDao == null) return;
        try {
            List<Corso> list = corsoDao.findAll();
            if (list == null) list = Collections.emptyList();
            backing.setAll(list);
            refilter();
            updateFiltersUI();
        } catch (Exception ex) {
            showError("Errore caricamento corsi: " + ex.getMessage());
        }
    }

    /** Applica tutti i filtri correnti allo stream dei corsi. */
    private void refilter() {
        Predicate<Corso> p = c -> {
            if (c == null) return false;

            if (!matchesEqIgnoreCase(c.getArgomento(), filtroArg)) return false;
            if (!matchesContainsIgnoreCase(c.getFrequenza(), filtroFreq)) return false;

            if (!isBlank(filtroChef)) {
                String chefLabel = chefLabelOf(c.getChef());
                if (!matchesEqIgnoreCase(chefLabel, filtroChef)) return false;
            }

            if (!isBlank(filtroId)) {
                String idS = String.valueOf(c.getIdCorso());
                if (!idS.equals(filtroId)) return false;
            }

            LocalDate din = c.getDataInizio();
            LocalDate dfi = c.getDataFine();
            if (dateFrom != null && din != null && din.isBefore(dateFrom) && (dfi == null || dfi.isBefore(dateFrom)))
                return false;
            if (dateTo != null && din != null && din.isAfter(dateTo) && (dfi == null || dfi.isAfter(dateTo)))
                return false;

            return true;
        };

        filtered.setPredicate(p);
    }

    private void openReportMode() {
        if (corsoDao == null) {
            showError("DAO non inizializzato. Effettua il login.");
            return;
        }
        String cf = corsoDao.getOwnerCfChef();
        if (isBlank(cf)) {
            showError("Chef non identificato. Effettua il login.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/Report.fxml"));
            loader.setControllerFactory(type -> {
                if (type == ReportController.class) return new ReportController(cf);
                try {
                    return type.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Impossibile creare controller: " + type, e);
                }
            });

            Parent reportRoot = loader.load();
            ReportController reportCtrl = loader.getController();
            reportCtrl.setPreviousRoot(table.getScene().getRoot());

            Stage stage = (Stage) table.getScene().getWindow();
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(reportRoot, 1000, 600);
                URL css = getClass().getResource(APP_CSS);
                if (css != null) scene.getStylesheets().add(css.toExternalForm());
                stage.setScene(scene);
            } else {
                scene.setRoot(reportRoot);
            }
            stage.setTitle("Report mensile - Chef " + cf);

        } catch (Exception ex) {
            showError("Errore apertura report: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Modifica: apre wizard sessioni già popolato; al salvataggio fa replace. */
    private void onEdit() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        if (!isOwnedByLoggedChef(sel)) {
            new Alert(Alert.AlertType.WARNING,
                    "Puoi modificare solo i corsi che appartengono al tuo profilo.")
                .showAndWait();
            return;
        }

        javafx.concurrent.Task<List<Sessione>> loadTask = new javafx.concurrent.Task<>() {
            @Override protected List<Sessione> call() throws Exception {
                return sessioneDao.findByCorso(sel.getIdCorso());
            }
        };

        loadTask.setOnSucceeded(ev -> {
            List<Sessione> esistenti = loadTask.getValue();
            try {
                FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
                DialogPane pane = fx.load();
                SessioniWizardController ctrl = fx.getController();
                ctrl.initWithCorsoAndExisting(sel, esistenti != null ? esistenti : java.util.Collections.emptyList());

                if (pane.getButtonTypes().isEmpty()) {
                    pane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                }

                URL css = getClass().getResource(APP_CSS);
                if (css != null) pane.getStylesheets().add(css.toExternalForm());

                // Avvolgi in ScrollPane per contenuti grandi
                Node currentContent = pane.getContent();
                if (currentContent instanceof Region regionContent) {
                    ScrollPane sc = new ScrollPane(regionContent);
                    sc.setFitToWidth(true);
                    sc.setFitToHeight(true);
                    sc.setPannable(true);
                    sc.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    sc.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                    pane.setContent(sc);
                }

                Dialog<List<Sessione>> dlg = new Dialog<>();
                dlg.setTitle("Modifica sessioni - " + (sel.getArgomento() == null ? "" : sel.getArgomento()));
                dlg.setDialogPane(pane);

                // Dimensione preferita iniziale
                pane.setPrefSize(1200, 800);

                dlg.setOnShown(e2 -> {
                    Window w = dlg.getDialogPane().getScene().getWindow();
                    if (w instanceof Stage stg) {
                        stg.setResizable(true);
                        stg.setWidth(1600);
                        stg.setHeight(800);
                        stg.setMinWidth(1500);
                        stg.setMinHeight(650);
                        stg.centerOnScreen();
                    }
                });

                dlg.setResultConverter(bt ->
                    (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                        ? ctrl.buildResult()
                        : null
                );

                dlg.showAndWait().ifPresent(result -> {
                    if (result != null) {
                        javafx.concurrent.Task<Void> replaceTask = new javafx.concurrent.Task<>() {
                            @Override protected Void call() throws Exception {
                                sessioneDao.replaceForCorso(sel.getIdCorso(), result);
                                return null;
                            }
                        };
                        replaceTask.setOnSucceeded(ev2 -> showInfo("Sessioni aggiornate: "));
                        replaceTask.setOnFailed(ev2 -> {
                            Throwable ex2 = replaceTask.getException();
                            showError("Errore salvataggio sessioni: " + (ex2 != null ? ex2.getMessage() : "sconosciuto"));
                            if (ex2 != null) ex2.printStackTrace();
                        });
                        new Thread(replaceTask, "replace-sessioni").start();
                    }
                });

            } catch (Exception e) {
                showError("Errore apertura wizard sessioni: " + e.getMessage());
                e.printStackTrace();
            }
        });

        loadTask.setOnFailed(ev -> {
            Throwable ex = loadTask.getException();
            showError("Errore modifica sessioni (caricamento): " + (ex != null ? ex.getMessage() : "sconosciuto"));
            if (ex != null) ex.printStackTrace();
        });

        new Thread(loadTask, "load-sessioni").start();
    }

   public void onNew() {
    try {
        FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/CorsiEditorDialog.fxml"));
        DialogPane pane = fx.load();

        CorsoEditorDialogController ctrl = fx.getController();
        ctrl.setCorso(null);

        Dialog<Corso> dialog = new Dialog<>();
        dialog.setTitle("Nuovo Corso");
        dialog.setDialogPane(pane);
        dialog.setResizable(true);

        dialog.setResultConverter(bt -> {
            if (bt == null) return null;
            // accetta SOLO il bottone "Crea" del controller
            return (bt == ctrl.getCreateButtonType()) ? ctrl.getResult() : null;
        });

        Optional<Corso> res = dialog.showAndWait();
        if (res.isEmpty()) {
            // Utente ha annullato il primo dialog -> NON creare nulla
            return;
        }

        Corso nuovo = res.get();

        // Associa automaticamente l'attuale chef (se presente)
        if (corsoDao != null) {
            String cfChef = corsoDao.getOwnerCfChef();
            if (cfChef != null && !cfChef.isBlank()) {
                Chef chef = new Chef();
                chef.setCF_Chef(cfChef);
                nuovo.setChef(chef);
            }
        }

        // Wizard delle sessioni
        Optional<List<Sessione>> sessOpt = openSessioniWizard(nuovo);
        if (sessOpt.isEmpty()) {
            // Utente ha annullato il wizard -> NON creare nulla
            return;
        }

        List<Sessione> sessions = sessOpt.get();
        if (sessions == null || sessions.isEmpty()) {
            // Nessuna sessione confermata -> NON creare nulla
            return;
        }

        // Salvataggio atomico corso + sessioni
        try {
            long id = corsoDao.insertWithSessions(nuovo, sessions);
            nuovo.setIdCorso(id);

            // Aggiorna UI
            backing.add(nuovo);
            table.getSelectionModel().select(nuovo);
            updateFiltersUI();

        } catch (Exception saveEx) {
            showError("Salvataggio corso/sessioni fallito: " + saveEx.getMessage());
            saveEx.printStackTrace();
        }

    } catch (Exception ex) {
        showError("Errore apertura editor corso: " + ex.getMessage());
        ex.printStackTrace();
    }
}


    private Optional<List<Sessione>> openSessioniWizard(Corso corso) {
        try {
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
            DialogPane pane = fx.load();

            SessioniWizardController ctrl = fx.getController();
            ctrl.initWithCorso(corso);

            // Avvolgi il contenuto in uno ScrollPane (per contenuti grandi)
            Node currentContent = pane.getContent();
            if (currentContent instanceof Region regionContent) {
                ScrollPane sc = new ScrollPane(regionContent);
                sc.setFitToWidth(true);
                sc.setFitToHeight(true);
                sc.setPannable(true);
                sc.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                sc.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                pane.setContent(sc);
            }

            Dialog<List<Sessione>> dlg = new Dialog<>();
            dlg.setTitle("Configura sessioni del corso");
            dlg.setDialogPane(pane);

            // Dimensione iniziale
            pane.setPrefSize(1200, 800);

            dlg.setOnShown(e2 -> {
                Window w = dlg.getDialogPane().getScene().getWindow();
                if (w instanceof Stage stg) {
                    stg.setResizable(true);
                    stg.setWidth(1600);
                    stg.setHeight(800);
                    stg.setMinWidth(1500);
                    stg.setMinHeight(650);
                    stg.centerOnScreen();
                }
            });

            URL css = getClass().getResource(APP_CSS);
            if (css != null) pane.getStylesheets().add(css.toExternalForm());

            dlg.setResultConverter(bt ->
                    (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                            ? ctrl.buildResult()
                            : null
            );

            return dlg.showAndWait();
        } catch (Exception ex) {
            showError("Errore apertura wizard sessioni: " + ex.getMessage());
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    private void onDelete() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Eliminare il corso: " + sel.getArgomento() + " ?");
        URL css = getClass().getResource(APP_CSS);
        if (css != null) {
            a.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        a.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            try {
                corsoDao.delete(sel.getIdCorso());
                backing.remove(sel);
                updateFiltersUI();
            } catch (Exception ex) {
                showError("Impossibile eliminare il corso: " + ex.getMessage());
            }
        });
    }

    /** ====== NUOVO: associa ricette alle sessioni in presenza ====== */
   /** ====== Associa ricette alle sessioni in presenza (con FXMLLoader + ControllerFactory) ====== */
private void onAssociateRecipes() {
    Corso sel = table.getSelectionModel().getSelectedItem();
    if (sel == null) return;

    if (!isOwnedByLoggedChef(sel)) {
        new Alert(Alert.AlertType.WARNING,
                "Puoi modificare solo i corsi che appartengono al tuo profilo.")
            .showAndWait();
        return;
    }

    try {
        // 1) Recupera le sessioni in presenza del corso
        List<SessionePresenza> presenze = new ArrayList<>();
        List<Sessione> tutte = sessioneDao.findByCorso(sel.getIdCorso());
        if (tutte != null) {
            for (Sessione s : tutte) {
                if (s instanceof SessionePresenza sp) presenze.add(sp);
            }
        }

        if (presenze.isEmpty()) {
            showInfo("Il corso non ha sessioni in presenza.");
            return;
        }

        // 2) Se più di una, chiedi quale sessione associare
        SessionePresenza target = (presenze.size() == 1)
                ? presenze.get(0)
                : choosePresenza(presenze).orElse(null);

        if (target == null) return; // annullato

        // 3) Dati per il dialog: tutte le ricette + già collegate
        if (ricettaDao == null) ricettaDao = new RicettaDao(); // fallback
        List<Ricetta> tutteLeRicette = ricettaDao.findAll();
        List<Ricetta> ricetteGiaAssociate =
                sessioneDao.findRicetteBySessionePresenza(target.getId());

        // 4) Carica l'FXML con fx:controller=it.unina.foodlab.controller.AssociaRicetteController
        var url = AssociaRicetteController.class.getResource("/it/unina/foodlab/ui/AssociaRicette.fxml");
        if (url == null) throw new IllegalStateException("FXML non trovato: /it/unina/foodlab/ui/AssociaRicette.fxml");

        FXMLLoader fx = new FXMLLoader(url);
        fx.setControllerFactory(t -> {
            if (t == AssociaRicetteController.class) {
                return new AssociaRicetteController(
                        sessioneDao,
                        target.getId(),
                        (tutteLeRicette != null ? tutteLeRicette : java.util.Collections.emptyList()),
                        (ricetteGiaAssociate != null ? ricetteGiaAssociate : java.util.Collections.emptyList())
                );
            }
            try {
                return t.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Impossibile creare controller: " + t, e);
            }
        });

        // NB: qui non ci serve 'root', ci basta che il controller venga creato e inizializzato
        fx.load();
        AssociaRicetteController dlg = fx.getController();

        Optional<List<Long>> result = dlg.showAndWait();
        dlg.salvaSeConfermato(result);

    } catch (Exception e) {
        showError("Errore associazione ricette: " + e.getMessage());
        e.printStackTrace();
    }
}


    /** Dialog per scegliere una sessione in presenza quando sono > 1. */
    private Optional<SessionePresenza> choosePresenza(List<SessionePresenza> presenze) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

        Map<String, SessionePresenza> map = new LinkedHashMap<>();
        for (SessionePresenza sp : presenze) {
            String data = sp.getData() != null ? df.format(sp.getData()) : "";
            String orari = (sp.getOraInizio() != null ? tf.format(sp.getOraInizio()) : "") +
                           "-" +
                           (sp.getOraFine() != null ? tf.format(sp.getOraFine()) : "");
            String indirizzo = joinNonEmpty(" ",
                    nz(sp.getVia()), nz(sp.getNum()),
                    nz(sp.getCap())
            ).trim();
            String label = String.format("%s  %s  %s", data, orari, indirizzo).trim();
            // Evita chiavi duplicate
            String key = label;
            int suffix = 2;
            while (map.containsKey(key)) key = label + " (" + (suffix++) + ")";
            map.put(key, sp);
        }

        ChoiceDialog<String> d = new ChoiceDialog<>(map.keySet().iterator().next(), map.keySet());
        URL css = getClass().getResource(APP_CSS);
        if (css != null) {
            d.getDialogPane().getStylesheets().add(css.toExternalForm());
        }

        d.setTitle("Seleziona la sessione in presenza");
        d.setHeaderText("Scegli la sessione a cui associare le ricette");
        d.setContentText(null);

        Optional<String> pick = d.showAndWait();
        return pick.map(map::get);
    }

    private void showSessioniDialog(Corso corso) {
        if (corso == null || sessioneDao == null) return;

        try {
            List<Sessione> sessioni = sessioneDao.findByCorso(corso.getIdCorso());
            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("Sessioni - " + (corso.getArgomento() != null ? corso.getArgomento() : ""));

            DialogPane pane = new DialogPane();
            pane.getButtonTypes().add(ButtonType.CLOSE);

            if (sessioni == null || sessioni.isEmpty()) {
                pane.setContent(new Label("Questo corso non ha sessioni."));
            } else {
                ListView<String> list = new ListView<>();
                DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

                for (Sessione s : sessioni) {
                    LocalDate d = s.getData();
                    LocalTime i = s.getOraInizio();
                    LocalTime f = s.getOraFine();

                    String modalita = s.getModalita() != null ? s.getModalita() : "";

                    String sede = "";
                    if (s instanceof SessionePresenza sp) {
                        String via = nz(sp.getVia());
                        String num = nz(sp.getNum());
                        String cap = nz(sp.getCap());
                        String indirizzo = (via + (num.isBlank() ? "" : " " + num)).trim();
                        sede = joinNonEmpty(" - ", indirizzo, cap);
                    } else if (s instanceof SessioneOnline so) {
                        String piattaforma = nz(so.getPiattaforma());
                        sede = piattaforma;
                    }

                    String riga = String.format("[%s] %s  %s-%s  %s",
                            modalita,
                            d != null ? df.format(d) : "",
                            i != null ? tf.format(i) : "",
                            f != null ? tf.format(f) : "",
                            sede);

                    list.getItems().add(riga.trim());
                }

                list.setPrefSize(600, 350);
                pane.setContent(list);
            }

            URL css = getClass().getResource(APP_CSS);
            if (css != null) pane.getStylesheets().add(css.toExternalForm());

            dlg.setDialogPane(pane);
            dlg.initOwner(table.getScene().getWindow());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.showAndWait();

        } catch (Exception ex) {
            showError("Errore apertura sessioni: " + ex.getMessage());
        }
    }
    
   // Campo o dipendenza nel controller // o come lo istanzi di solito

private void openSessioniPreview(Corso corso) {
    try {
        FXMLLoader l = new FXMLLoader(getClass().getResource(
            "/it/unina/foodlab/ui/SessioniPreview.fxml"
        ));
        DialogPane pane = l.load();
        SessioniPreviewController ctrl = l.getController();

        // PRENDI LE SESSIONI DAL DAO usando l'ID del corso
        long id = corso.getIdCorso();
        List<Sessione> sessions = sessioneDao.findByCorso(id); // <--- adattalo al tuo DAO

        ctrl.init(corso, sessions);

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Sessioni — " + corso.getArgomento()); // o getTitolo se ce l’hai
        dlg.setDialogPane(pane);
        dlg.initOwner(table.getScene().getWindow());
        dlg.showAndWait();

    } catch (Exception ex) {
        ex.printStackTrace();
        new Alert(Alert.AlertType.ERROR,
            "Errore apertura anteprima sessioni:\n" + ex.getMessage()).showAndWait();
    }
}


    
    

    /* ---------------- Helpers dataset & UI ---------------- */

    private static String nz(String s) { return s == null ? "" : s; }
    private static String joinNonEmpty(String sep, String... parts) {
        return Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .collect(java.util.stream.Collectors.joining(sep));
    }

    private static String nz(int n) { return n > 0 ? String.valueOf(n) : ""; }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText("Errore");
        a.setContentText(msg);
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        URL css = getClass().getResource(APP_CSS);
        if (css != null) {
            a.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private boolean isOwnedByLoggedChef(Corso c) {
        if (c == null || c.getChef() == null || isBlank(c.getChef().getCF_Chef())) return false;
        String owner = (corsoDao != null) ? corsoDao.getOwnerCfChef() : null;
        return !isBlank(owner) && c.getChef().getCF_Chef().equalsIgnoreCase(owner);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static boolean matchesEqIgnoreCase(String value, String filter) {
        if (isBlank(filter)) return true;
        return value != null && value.equalsIgnoreCase(filter);
    }
    private static boolean matchesContainsIgnoreCase(String value, String filter) {
        if (isBlank(filter)) return true;
        return value != null && value.toLowerCase().contains(filter.toLowerCase());
    }

    /** Aggiorna il testo del MenuButton + tooltip + etichette voci. */
    private void updateFiltersUI() {
        updateFiltersBadge();
        updateFiltersMenuLabels();
    }

    /** Badge compatto e tooltip completo sul bottone Filtri. */
    private void updateFiltersBadge() {
        List<String> parts = new ArrayList<>();

        if (!isBlank(filtroArg))  parts.add("Arg=" + filtroArg);
        if (!isBlank(filtroFreq)) parts.add("Freq=" + filtroFreq);
        if (!isBlank(filtroChef)) parts.add("Chef=" + filtroChef);
        if (!isBlank(filtroId))   parts.add("ID=" + filtroId);

        if (dateFrom != null || dateTo != null) {
            parts.add(formatDateRange(dateFrom, dateTo));
        }

        if (parts.isEmpty()) {
            btnFilters.setText("Filtri");
            btnFilters.setTooltip(null);
            return;
        }

        String fullSummary = String.join(", ", parts);
        String shown = ellipsize(fullSummary, FILTERS_BADGE_MAX_CHARS);

        btnFilters.setText("Filtri (" + shown + ")");
        btnFilters.setTooltip(new Tooltip("Filtri attivi: " + fullSummary));
    }

    /** Aggiorna i testi delle voci del menu Filtri. */
    private void updateFiltersMenuLabels() {
        miFiltroArg.setText(  formatFilterLabel(LABEL_ARG,  filtroArg));
        miFiltroFreq.setText( formatFilterLabel(LABEL_FREQ, filtroFreq));
        miFiltroChef.setText( formatFilterLabel(LABEL_CHEF, filtroChef));
        miFiltroId.setText(   formatFilterLabel(LABEL_ID,   filtroId));
    }

    /** "01/10–31/10" con puntini se un estremo manca. */
    private String formatDateRange(LocalDate from, LocalDate to) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM");
        String left  = (from != null) ? df.format(from) : "…";
        String right = (to   != null) ? df.format(to)   : "…";
        return left + "–" + right;
    }

    /** Taglia a maxLen caratteri aggiungendo "…" se necessario. */
    private String ellipsize(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen - 1)) + "…";
    }

    /* ---------------- Distinte & ChoiceDialog ---------------- */

    private List<String> distinctArgomenti() {
        Set<String> s = new HashSet<>();
        for (Corso c : backing) {
            String a = c.getArgomento();
            if (a != null && !a.isBlank()) s.add(a.trim());
        }
        List<String> out = new ArrayList<>(s);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        out.add(0, ALL_OPTION);
        return out;
    }

    private List<String> distinctFrequenze() {
        Set<String> s = new HashSet<>();
        for (Corso c : backing) {
            String f = c.getFrequenza();
            if (f != null && !f.isBlank()) s.add(f.trim());
        }
        List<String> out = new ArrayList<>(s);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        out.add(0, ALL_OPTION);
        return out;
    }

    private List<String> distinctChefLabels() {
        Set<String> s = new HashSet<>();
        for (Corso c : backing) {
            s.add(chefLabelOf(c.getChef()));
        }
        s.remove("");
        List<String> out = new ArrayList<>(s);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        out.add(0, ALL_OPTION);
        return out;
    }

    private List<String> distinctIdLabels() {
        List<String> ids = new ArrayList<>();
        for (Corso c : backing) {
            ids.add(String.valueOf(c.getIdCorso()));
        }
        ids.sort(Comparator.comparingLong(Long::parseLong));
        ids.add(0, ALL_OPTION);
        return ids;
    }

    private String chefLabelOf(Chef ch) {
        if (ch == null) return "";
        String full = (nz(ch.getNome()) + " " + nz(ch.getCognome())).trim();
        return full.isEmpty() ? nz(ch.getCF_Chef()) : full;
    }

    /** Dialog a tendina riutilizzabile con pre-selezione case-insensitive. */
    private String askChoice(String title, String header, List<String> options, String preselect) {
        if (options == null || options.isEmpty()) {
            showInfo("Nessuna opzione disponibile.");
            return null;
        }
        String def = options.get(0); // "Tutte"
        if (!isBlank(preselect)) {
            for (String opt : options) {
                if (opt.equalsIgnoreCase(preselect)) {
                    def = opt;
                    break;
                }
            }
        }

        ChoiceDialog<String> d = new ChoiceDialog<>(def, options);
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText(null);
        
        Scene scene = d.getDialogPane().getScene();
        if (scene != null) {
            URL css = getClass().getResource(APP_CSS);
                scene.getStylesheets().add(css.toExternalForm());
        }
        
        Optional<String> res = d.showAndWait();
        return res.orElse(null);
    }

    /** Converte l'opzione "Tutte" in null (nessun filtro). */
    private String normalizeAllToNull(String value) {
        if (value == null) return null;
        return ALL_OPTION.equalsIgnoreCase(value.trim()) ? null : value.trim();
    }
    
    
}
