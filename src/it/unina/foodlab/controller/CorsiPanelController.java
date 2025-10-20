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
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import javafx.util.Callback;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gestione elenco corsi con filtri, CRUD, wizard sessioni,
 * associazione ricette a sessioni in presenza e anteprima sessioni.
 */
public class CorsiPanelController {

    private static final String ALL_OPTION = "Tutte";
    private static final int FILTERS_BADGE_MAX_CHARS = 32;

    private static final String LABEL_ARG  = "Argomento";
    private static final String LABEL_FREQ = "Frequenza";
    private static final String LABEL_CHEF = "Chef";
    private static final String LABEL_ID   = "ID";
    private static final String LABEL_PERIODO = "Periodo";

    /* ----------- TOP BAR ----------- */
    @FXML private MenuButton btnFilters;
    @FXML private MenuItem miFiltroArg;
    @FXML private MenuItem miFiltroFreq;
    @FXML private MenuItem miFiltroChef;
    @FXML private MenuItem miFiltroId;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Button btnApplyDate;
    @FXML private Button btnClearInMenu;
    @FXML private Button btnRefresh, btnReport;

    /* ----------- TABELLA ----------- */
    @FXML private TableView<Corso> table;
    @FXML private TableColumn<Corso, Long> colId;
    @FXML private TableColumn<Corso, String> colArg;
    @FXML private TableColumn<Corso, String> colFreq;
    @FXML private TableColumn<Corso, LocalDate> colInizio;
    @FXML private TableColumn<Corso, LocalDate> colFine;
    @FXML private TableColumn<Corso, String> colChef;

    /* ----------- BOTTOM BAR ----------- */
    @FXML private Button btnNew, btnEdit, btnDelete, btnAssocRicette;

    /* ----------- DATI/STATE ----------- */
    private final ObservableList<Corso> backing = FXCollections.observableArrayList();
    private final FilteredList<Corso> filtered  = new FilteredList<Corso>(backing, c -> true);
    private final SortedList<Corso>   sorted    = new SortedList<Corso>(filtered);

    private CorsoDao corsoDao;
    private SessioneDao sessioneDao;
    private RicettaDao ricettaDao;

    /* Filtri correnti */
    private String filtroArg   = null;
    private String filtroFreq  = null;
    private String filtroChef  = null;
    private String filtroId    = null;
    private LocalDate dateFrom = null;
    private LocalDate dateTo   = null;

    /* ================== INIT ================== */
    @FXML
    private void initialize() {
        initTableColumns();
        table.setItems(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());

        /* Menu filtri */
        miFiltroArg.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) {
                String scelto = askChoice("Filtro Argomento", "Seleziona argomento", distinctArgomenti(), filtroArg);
                filtroArg = normalizeAllToNull(scelto);
                refilter(); updateFiltersUI();
            }
        });
        miFiltroFreq.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) {
                String scelto = askChoice("Filtro Frequenza", "Seleziona frequenza", distinctFrequenze(), filtroFreq);
                filtroFreq = normalizeAllToNull(scelto);
                refilter(); updateFiltersUI();
            }
        });
        miFiltroChef.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) {
                String scelto = askChoice("Filtro Chef", "Seleziona Chef", distinctChefLabels(), filtroChef);
                filtroChef = normalizeAllToNull(scelto);
                refilter(); updateFiltersUI();
            }
        });
        miFiltroId.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) {
                String scelto = askChoice("Filtro ID", "Seleziona ID corso", distinctIdLabels(), filtroId);
                filtroId = normalizeAllToNull(scelto);
                refilter(); updateFiltersUI();
            }
        });

        btnApplyDate.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) {
                dateFrom = dpFrom.getValue();
                dateTo   = dpTo.getValue();
                refilter(); updateFiltersUI();
            }
        });
        btnClearInMenu.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) {
                clearAllFilters();
            }
        });

        btnRefresh.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) { reload(); }
        });
        btnReport.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) { openReportMode(); }
        });

        /* Abilitazione bottoni in base alla selezione */
        table.getSelectionModel().selectedItemProperty().addListener((obs, ov, sel) -> {
            boolean has = sel != null;
            boolean own = isOwnedByLoggedChef(sel);
            btnEdit.setDisable(!has || !own);
            btnDelete.setDisable(!has || !own);
            btnAssocRicette.setDisable(!has || !own);
        });

        /* Doppio click = anteprima sessioni */
        table.setRowFactory(new Callback<TableView<Corso>, TableRow<Corso>>() {
            @Override public TableRow<Corso> call(TableView<Corso> tv) {
                final TableRow<Corso> row = new TableRow<Corso>();
                row.setOnMouseClicked(e -> {
                    if (!row.isEmpty() && e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                        openSessioniPreview(row.getItem());
                    }
                });
                return row;
            }
        });

        /* CRUD */
        btnNew.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) { onNew(); }
        });
        btnEdit.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) { onEdit(); }
        });
        btnDelete.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) { onDelete(); }
        });
        btnAssocRicette.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent event) { onAssociateRecipes(); }
        });

        updateFiltersUI();

        /* Dimensioni finestra principali iniziali/minime */
        Platform.runLater(new Runnable() {
            @Override public void run() {
                if (table == null || table.getScene() == null) return;
                Stage stage = (Stage) table.getScene().getWindow();
                if (stage != null) {
                    stage.setMinWidth(1000);
                    stage.setMinHeight(600);
                    stage.setWidth(1200);
                    stage.setHeight(800);
                    stage.centerOnScreen();
                }
            }
        });
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

    /* ================== TABELLA ================== */
    private void initTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<Corso, Long>("idCorso"));
        colArg.setCellValueFactory(new PropertyValueFactory<Corso, String>("argomento"));
        colFreq.setCellValueFactory(new PropertyValueFactory<Corso, String>("frequenza"));

        colInizio.setCellValueFactory(new PropertyValueFactory<Corso, LocalDate>("dataInizio"));
        colInizio.setCellFactory(tc -> new TableCell<Corso, LocalDate>() {
            { setAlignment(Pos.CENTER); }
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        colFine.setCellValueFactory(new PropertyValueFactory<Corso, LocalDate>("dataFine"));
        colFine.setCellFactory(tc -> new TableCell<Corso, LocalDate>() {
            { setAlignment(Pos.CENTER); }
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        colChef.setCellValueFactory(cd ->
                Bindings.createStringBinding(() -> {
                    Corso c = cd.getValue();
                    if (c == null || c.getChef() == null) return "";
                    Chef ch = c.getChef();
                    String nome = nz(ch.getNome());
                    String cogn = nz(ch.getCognome());
                    String full = (nome + " " + cogn).trim();
                    return full.isEmpty() ? nz(ch.getCF_Chef()) : full;
                })
        );

        table.getSortOrder().add(colInizio);
        colInizio.setSortType(TableColumn.SortType.DESCENDING);
    }

    private void reload() {
        if (corsoDao == null) return;
        try {
            List<Corso> list = corsoDao.findAll();
            if (list == null) list = Collections.<Corso>emptyList();
            backing.setAll(list);
            refilter();
            updateFiltersUI();
        } catch (Exception ex) {
            showError("Errore caricamento corsi: " + ex.getMessage());
        }
    }

    /* ================== FILTRI ================== */
    private void refilter() {
        filtered.setPredicate(new java.util.function.Predicate<Corso>() {
            @Override public boolean test(Corso c) {
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

                if (dateFrom != null) {
                    // escludi corsi che finiscono prima dell'intervallo
                    if (dfi != null && dfi.isBefore(dateFrom)) return false;
                    if (dfi == null && din != null && din.isBefore(dateFrom)) return false;
                }
                if (dateTo != null) {
                    // escludi corsi che iniziano dopo l'intervallo
                    if (din != null && din.isAfter(dateTo)) return false;
                }

                return true;
            }
        });
    }

    private void clearAllFilters() {
        filtroArg = filtroFreq = filtroChef = filtroId = null;
        dateFrom = dateTo = null;
        if (dpFrom != null) dpFrom.setValue(null);
        if (dpTo != null) dpTo.setValue(null);
        refilter(); updateFiltersUI();
    }

    /* ================== REPORT ================== */
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
            loader.setControllerFactory(new Callback<Class<?>, Object>() {
                @Override public Object call(Class<?> type) {
                    if (type == ReportController.class) return new ReportController(cf);
                    try { return type.getDeclaredConstructor().newInstance(); }
                    catch (Exception e) { throw new RuntimeException("Impossibile creare controller: " + type, e); }
                }
            });

            Parent reportRoot = loader.load();
            ReportController reportCtrl = loader.getController();
            reportCtrl.setPreviousRoot(table.getScene().getRoot());

            Stage stage = (Stage) table.getScene().getWindow();
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(reportRoot, 1000, 600);
                
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

    /* ================== EDIT / NEW / DELETE ================== */
    private void onEdit() {
        final Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        if (!isOwnedByLoggedChef(sel)) {
            new Alert(Alert.AlertType.WARNING,
                    "Puoi modificare solo i corsi che appartengono al tuo profilo.").showAndWait();
            return;
        }

        final javafx.concurrent.Task<List<Sessione>> loadTask =
                new javafx.concurrent.Task<List<Sessione>>() {
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
                ctrl.initWithCorsoAndExisting(sel, esistenti != null ? esistenti : Collections.<Sessione>emptyList());

                if (pane.getButtonTypes().isEmpty()) {
                    pane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                }

               
                

                Node content = pane.getContent();
                if (content instanceof Region) {
                    ScrollPane sc = new ScrollPane(content);
                    sc.setFitToWidth(true);
                    sc.setFitToHeight(true);
                    sc.setPannable(true);
                    sc.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    sc.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                    pane.setContent(sc);
                }

                Dialog<List<Sessione>> dlg = new Dialog<List<Sessione>>();
                dlg.setTitle("Modifica sessioni - " + safe(sel.getArgomento()));
                dlg.setDialogPane(pane);
                pane.setPrefSize(1200, 800);

                dlg.setOnShown(e2 -> {
                    Window w = dlg.getDialogPane().getScene().getWindow();
                    if (w instanceof Stage) {
                        Stage stg = (Stage) w;
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

                Optional<List<Sessione>> res = dlg.showAndWait();
                if (res.isPresent() && res.get() != null) {
                    final List<Sessione> result = res.get();
                    final javafx.concurrent.Task<Void> replaceTask = new javafx.concurrent.Task<Void>() {
                        @Override protected Void call() throws Exception {
                            sessioneDao.replaceForCorso(sel.getIdCorso(), result);
                            return null;
                        }
                    };
                    replaceTask.setOnSucceeded(ev2 -> showInfo("Sessioni aggiornate."));
                    replaceTask.setOnFailed(ev2 -> {
                        Throwable ex2 = replaceTask.getException();
                        showError("Errore salvataggio sessioni: " + (ex2 != null ? ex2.getMessage() : "sconosciuto"));
                        if (ex2 != null) ex2.printStackTrace();
                    });
                    new Thread(replaceTask, "replace-sessioni").start();
                }

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

            Dialog<Corso> dialog = new Dialog<Corso>();
            dialog.setTitle("Nuovo Corso");
            dialog.setDialogPane(pane);
            dialog.setResizable(true);

            dialog.setResultConverter(bt -> {
                if (bt == null) return null;
                return (bt == ctrl.getCreateButtonType()) ? ctrl.getResult() : null;
            });

            Optional<Corso> res = dialog.showAndWait();
            if (!res.isPresent()) return; // annullato

            Corso nuovo = res.get();

            // associa l'attuale chef
            if (corsoDao != null) {
                String cfChef = corsoDao.getOwnerCfChef();
                if (cfChef != null && cfChef.trim().length() > 0) {
                    Chef chef = new Chef();
                    chef.setCF_Chef(cfChef);
                    nuovo.setChef(chef);
                }
            }

            Optional<List<Sessione>> sessOpt = openSessioniWizard(nuovo);
            if (!sessOpt.isPresent()) return; // annullato

            List<Sessione> sessions = sessOpt.get();
            if (sessions == null || sessions.isEmpty()) return; // niente sessioni => non creare

            try {
                long id = corsoDao.insertWithSessions(nuovo, sessions);
                nuovo.setIdCorso(id);
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

    private void onDelete() {
        final Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Eliminare il corso: " + sel.getArgomento() + " ?");
       

        Optional<ButtonType> resp = a.showAndWait();
        if (resp.isPresent() && resp.get() == ButtonType.OK) {
            try {
                corsoDao.delete(sel.getIdCorso());
                backing.remove(sel);
                updateFiltersUI();
            } catch (Exception ex) {
                showError("Impossibile eliminare il corso: " + ex.getMessage());
            }
        }
    }

    /* ================== ASSOCIA RICETTE ================== */
    private void onAssociateRecipes() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        if (!isOwnedByLoggedChef(sel)) {
            new Alert(Alert.AlertType.WARNING,
                    "Puoi modificare solo i corsi che appartengono al tuo profilo.").showAndWait();
            return;
        }

        try {
            // 1) sessioni in presenza
            List<Sessione> tutte = sessioneDao.findByCorso(sel.getIdCorso());
            List<SessionePresenza> presenze = new ArrayList<SessionePresenza>();
            if (tutte != null) {
                for (int i = 0; i < tutte.size(); i++) {
                    Sessione s = tutte.get(i);
                    if (s instanceof SessionePresenza) presenze.add((SessionePresenza) s);
                }
            }
            if (presenze.isEmpty()) { showInfo("Il corso non ha sessioni in presenza."); return; }

            // 2) scegli la sessione target se > 1
            SessionePresenza target = (presenze.size() == 1) ? presenze.get(0) : choosePresenza(presenze).orElse(null);
            if (target == null) return;

            // 3) dati dialog
            if (ricettaDao == null) ricettaDao = new RicettaDao();
            List<Ricetta> tutteLeRicette = ricettaDao.findAll();
            List<Ricetta> ricetteGiaAssociate = sessioneDao.findRicetteBySessionePresenza(target.getId());

            // 4) carica dialog associazione
            URL url = AssociaRicetteController.class.getResource("/it/unina/foodlab/ui/AssociaRicette.fxml");
            if (url == null) throw new IllegalStateException("FXML non trovato: /it/unina/foodlab/ui/AssociaRicette.fxml");

            FXMLLoader fx = new FXMLLoader(url);
            fx.setControllerFactory(new Callback<Class<?>, Object>() {
                @Override public Object call(Class<?> t) {
                    if (t == AssociaRicetteController.class) {
                        return new AssociaRicetteController(
                                sessioneDao,
                                target.getId(),
                                (tutteLeRicette != null ? tutteLeRicette : Collections.<Ricetta>emptyList()),
                                (ricetteGiaAssociate != null ? ricetteGiaAssociate : Collections.<Ricetta>emptyList())
                        );
                    }
                    try { return t.getDeclaredConstructor().newInstance(); }
                    catch (Exception e) { throw new RuntimeException("Impossibile creare controller: " + t, e); }
                }
            });

            fx.load();
            AssociaRicetteController dlg = fx.getController();

            Optional<List<Long>> result = dlg.showAndWait();
            dlg.salvaSeConfermato(result);

        } catch (Exception e) {
            showError("Errore associazione ricette: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Optional<SessionePresenza> choosePresenza(List<SessionePresenza> presenze) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

        Map<String, SessionePresenza> map = new LinkedHashMap<String, SessionePresenza>();
        for (int i = 0; i < presenze.size(); i++) {
            SessionePresenza sp = presenze.get(i);
            String data = (sp.getData() != null) ? df.format(sp.getData()) : "";
            String orari = (sp.getOraInizio() != null ? tf.format(sp.getOraInizio()) : "") +
                    "-" +
                    (sp.getOraFine() != null ? tf.format(sp.getOraFine()) : "");
            String indirizzo = joinNonEmpty(" ",
                    nz(sp.getVia()), nz(sp.getNum()), nz(sp.getCap())).trim();
            String base = (data + "  " + orari + "  " + indirizzo).trim();

            String key = base;
            int suffix = 2;
            while (map.containsKey(key)) { key = base + " (" + (suffix++) + ")"; }
            map.put(key, sp);
        }

        ChoiceDialog<String> d = new ChoiceDialog<String>(firstKeyOr(map, ""), map.keySet());
        
        d.setTitle("Seleziona la sessione in presenza");
        d.setHeaderText("Scegli la sessione a cui associare le ricette");
        d.setContentText(null);

        Optional<String> pick = d.showAndWait();
        return pick.isPresent() ? Optional.of(map.get(pick.get())) : Optional.<SessionePresenza>empty();
    }

    private Optional<List<Sessione>> openSessioniWizard(Corso corso) {
        try {
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
            DialogPane pane = fx.load();

            SessioniWizardController ctrl = fx.getController();
            ctrl.initWithCorso(corso);

            Node content = pane.getContent();
            if (content instanceof Region) {
                ScrollPane sc = new ScrollPane(content);
                sc.setFitToWidth(true);
                sc.setFitToHeight(true);
                sc.setPannable(true);
                sc.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                sc.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                pane.setContent(sc);
            }

            Dialog<List<Sessione>> dlg = new Dialog<List<Sessione>>();
            dlg.setTitle("Configura sessioni del corso");
            dlg.setDialogPane(pane);
            pane.setPrefSize(1200, 800);

            dlg.setOnShown(e2 -> {
                Window w = dlg.getDialogPane().getScene().getWindow();
                if (w instanceof Stage) {
                    Stage stg = (Stage) w;
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

            return dlg.showAndWait();
        } catch (Exception ex) {
            showError("Errore apertura wizard sessioni: " + ex.getMessage());
            ex.printStackTrace();
            return Optional.<List<Sessione>>empty();
        }
    }

    /* ================== ANTEPRIMA SESSIONI ================== */
    private void openSessioniPreview(Corso corso) {
        if (corso == null) return;
        try {
            FXMLLoader l = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniPreview.fxml"));
            DialogPane pane = l.load();
            SessioniPreviewController ctrl = l.getController();

            long id = corso.getIdCorso();
            List<Sessione> sessions = sessioneDao.findByCorso(id);
            ctrl.init(corso, sessions);

            Dialog<Void> dlg = new Dialog<Void>();
            dlg.setTitle("Sessioni — " + safe(corso.getArgomento()));
            dlg.setDialogPane(pane);
            dlg.initOwner(table.getScene().getWindow());
            dlg.initModality(Modality.WINDOW_MODAL);

           
            dlg.showAndWait();

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "Errore apertura anteprima sessioni:\n" + ex.getMessage()).showAndWait();
        }
    }

    /* ================== HELPERS ================== */

    private static String safe(String s) { return s == null ? "" : s; }
    /* ---------------- Helpers dataset & UI ---------------- */

    private static String nz(String s) {
        return (s == null) ? "" : s;
    }

    private static String nz(int n) {
        // restituisce "" se 0 o negativo, altrimenti il numero
        return (n > 0) ? String.valueOf(n) : "";
    }

    private static String nz(long n) {
        return (n > 0L) ? String.valueOf(n) : "";
    }

    /** join di parti non vuote senza usare stream/lambda */
    private static String joinNonEmpty(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (p != null && !p.trim().isEmpty()) {
                    if (!first) sb.append(sep);
                    sb.append(p.trim());
                    first = false;
                }
            }
        }
        return sb.toString();
    }


    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText("Errore");
        a.setContentText(msg);
        
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
       
        a.showAndWait();
    }

    private boolean isOwnedByLoggedChef(Corso c) {
        if (c == null || c.getChef() == null || isBlank(c.getChef().getCF_Chef())) return false;
        String owner = (corsoDao != null) ? corsoDao.getOwnerCfChef() : null;
        return !isBlank(owner) && c.getChef().getCF_Chef().equalsIgnoreCase(owner);
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static boolean matchesEqIgnoreCase(String value, String filter) {
        if (isBlank(filter)) return true;
        return value != null && value.equalsIgnoreCase(filter);
    }
    private static boolean matchesContainsIgnoreCase(String value, String filter) {
        if (isBlank(filter)) return true;
        return value != null && value.toLowerCase().contains(filter.toLowerCase());
    }

    private void updateFiltersUI() {
        updateFiltersBadge();
        updateFiltersMenuLabels();
    }

    private void updateFiltersBadge() {
        StringBuilder sb = new StringBuilder();
        appendIf(sb, !isBlank(filtroArg), "Arg=" + filtroArg);
        appendIf(sb, !isBlank(filtroFreq), "Freq=" + filtroFreq);
        appendIf(sb, !isBlank(filtroChef), "Chef=" + filtroChef);
        appendIf(sb, !isBlank(filtroId), "ID=" + filtroId);
        if (dateFrom != null || dateTo != null) {
            appendIf(sb, true, formatDateRange(dateFrom, dateTo));
        }

        if (sb.length() == 0) {
            btnFilters.setText("Filtri");
            btnFilters.setTooltip(null);
            return;
        }

        String full = sb.toString();
        String shown = ellipsize(full, FILTERS_BADGE_MAX_CHARS);
        btnFilters.setText("Filtri (" + shown + ")");
        btnFilters.setTooltip(new Tooltip("Filtri attivi: " + full));
    }

    private void updateFiltersMenuLabels() {
        miFiltroArg.setText(  formatFilterLabel(LABEL_ARG,  filtroArg));
        miFiltroFreq.setText( formatFilterLabel(LABEL_FREQ, filtroFreq));
        miFiltroChef.setText( formatFilterLabel(LABEL_CHEF, filtroChef));
        miFiltroId.setText(   formatFilterLabel(LABEL_ID,   filtroId));
    }

    private static void appendIf(StringBuilder sb, boolean cond, String piece) {
        if (!cond) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(piece);
    }

    private String formatFilterLabel(String base, String value) {
        return base + ": " + (isBlank(value) ? "(tutte)" : value);
    }

    private String formatDateRange(LocalDate from, LocalDate to) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM");
        String l = (from != null) ? df.format(from) : "…";
        String r = (to   != null) ? df.format(to)   : "…";
        return l + "–" + r;
    }

    private String ellipsize(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        int take = Math.max(0, maxLen - 1);
        return s.substring(0, take) + "…";
    }

    private List<String> distinctArgomenti() {
        Set<String> s = new HashSet<String>();
        for (int i = 0; i < backing.size(); i++) {
            Corso c = backing.get(i);
            String a = (c != null) ? c.getArgomento() : null;
            if (a != null && a.trim().length() > 0) s.add(a.trim());
        }
        return sortedWithAllOption(s);
    }

    private List<String> distinctFrequenze() {
        Set<String> s = new HashSet<String>();
        for (int i = 0; i < backing.size(); i++) {
            Corso c = backing.get(i);
            String f = (c != null) ? c.getFrequenza() : null;
            if (f != null && f.trim().length() > 0) s.add(f.trim());
        }
        return sortedWithAllOption(s);
    }

    private List<String> distinctChefLabels() {
        Set<String> s = new HashSet<String>();
        for (int i = 0; i < backing.size(); i++) {
            Corso c = backing.get(i);
            s.add(chefLabelOf(c != null ? c.getChef() : null));
        }
        s.remove("");
        return sortedWithAllOption(s);
    }

    private List<String> distinctIdLabels() {
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < backing.size(); i++) {
            Corso c = backing.get(i);
            if (c != null) ids.add(String.valueOf(c.getIdCorso()));
        }
        Collections.sort(ids, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                long la = Long.parseLong(a);
                long lb = Long.parseLong(b);
                return Long.compare(la, lb);
            }
        });
        ids.add(0, ALL_OPTION);
        return ids;
    }

    private List<String> sortedWithAllOption(Set<String> set) {
        List<String> out = new ArrayList<String>(set);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        out.add(0, ALL_OPTION);
        return out;
    }

    private String chefLabelOf(Chef ch) {
        if (ch == null) return "";
        String full = (nz(ch.getNome()) + " " + nz(ch.getCognome())).trim();
        return full.isEmpty() ? nz(ch.getCF_Chef()) : full;
    }

    private String askChoice(String title, String header, List<String> options, String preselect) {
        if (options == null || options.isEmpty()) {
            showInfo("Nessuna opzione disponibile.");
            return null;
        }
        String def = options.get(0); // "Tutte"
        if (!isBlank(preselect)) {
            for (int i = 0; i < options.size(); i++) {
                String opt = options.get(i);
                if (opt.equalsIgnoreCase(preselect)) { def = opt; break; }
            }
        }

        ChoiceDialog<String> d = new ChoiceDialog<String>(def, options);
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText(null);

        Optional<String> res = d.showAndWait();
        return res.isPresent() ? res.get() : null;
    }

    private String normalizeAllToNull(String value) {
        if (value == null) return null;
        return ALL_OPTION.equalsIgnoreCase(value.trim()) ? null : value.trim();
    }

    private static String firstKeyOr(Map<String, SessionePresenza> map, String fallback) {
        for (String k : map.keySet()) return k;
        return fallback;
    }
}
