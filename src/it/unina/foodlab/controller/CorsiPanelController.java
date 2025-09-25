package it.unina.foodlab.controller;

import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
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
import javafx.stage.*;
import javafx.stage.Window;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

import java.util.List;
import java.util.Optional;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ButtonBar;


public class CorsiPanelController {

    public static final String APP_CSS = "/app.css";

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cbArgFiltro;
    @FXML private Button btnClear, btnRefresh, btnReport;
    @FXML private Button btnNew, btnEdit, btnDelete, btnAssocRicette;
    @FXML private TableView<Corso> table;
    @FXML private TableColumn<Corso, Long> colId;
    @FXML private TableColumn<Corso, String> colArg;
    @FXML private TableColumn<Corso, String> colFreq;
    @FXML private TableColumn<Corso, LocalDate> colInizio;
    @FXML private TableColumn<Corso, LocalDate> colFine;
    @FXML private TableColumn<Corso, String> colChef;

    private final ObservableList<Corso> backing  = FXCollections.observableArrayList();
    private final FilteredList<Corso> filtered   = new FilteredList<>(backing, c -> true);
    private final SortedList<Corso>   sorted     = new SortedList<>(filtered);

    private CorsoDao corsoDao;
    private SessioneDao sessioneDao;

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

        // Abilitazione bottoni
        table.getSelectionModel().selectedItemProperty().addListener((obs, ov, sel) -> {
            boolean has = sel != null;
            boolean own = isOwnedByLoggedChef(sel);
            btnEdit.setDisable(!has || !own);
            btnDelete.setDisable(!has || !own);
            btnAssocRicette.setDisable(!has || !own);
        });

        // DOPPIO CLICK: solo viewer (read-only)
        table.setRowFactory(tv -> {
            TableRow<Corso> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()
                        && e.getButton() == MouseButton.PRIMARY
                        && e.getClickCount() == 2) {
                    Corso corso = row.getItem();
                    showSessioniDialog(corso); // viewer
                }
            });
            return row;
        });

        // Bottoni CRUD
        btnNew.setOnAction(e -> onNew());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnAssocRicette.setOnAction(e -> onAssociateRecipes());
        
        
    }

    public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao) {
        this.corsoDao = corsoDao;
        this.sessioneDao = sessioneDao;
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
            String full = ((ch.getNome() != null ? ch.getNome() : "") + " " +
                    (ch.getCognome() != null ? ch.getCognome() : "")).trim();
            return full.isEmpty() ? (ch.getCF_Chef() == null ? "" : ch.getCF_Chef()) : full;
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

            // Aggiorna combo filtro
            List<String> args = new ArrayList<>();
            for (Corso c : list) {
                if (c.getArgomento() != null && !c.getArgomento().isBlank() && !args.contains(c.getArgomento().trim()))
                    args.add(c.getArgomento().trim());
            }
            args.sort(String.CASE_INSENSITIVE_ORDER);
            cbArgFiltro.getItems().setAll(args);
        } catch (Exception ex) {
            showError("Errore caricamento corsi: " + ex.getMessage());
        }
    }

    private void refilter() {
        String q = txtSearch.getText() != null ? txtSearch.getText().toLowerCase().trim() : "";
        String arg = cbArgFiltro.getValue();

        Predicate<Corso> p = c -> {
            if (c == null) return false;
            boolean okArg = (arg == null || arg.isBlank()) || arg.equalsIgnoreCase(c.getArgomento());
            if (!okArg) return false;

            if (q.isBlank()) return true;
            String chefStr = c.getChef() != null ?
                    ((c.getChef().getNome() != null ? c.getChef().getNome() : "") + " " +
                            (c.getChef().getCognome() != null ? c.getChef().getCognome() : "")).trim()
                    : "";
            return String.valueOf(c.getIdCorso()).contains(q)
                    || (c.getArgomento() != null && c.getArgomento().toLowerCase().contains(q))
                    || (c.getFrequenza() != null && c.getFrequenza().toLowerCase().contains(q))
                    || chefStr.toLowerCase().contains(q);
        };
        filtered.setPredicate(p);
    }

    private void openReportMode() {
        if (corsoDao == null) {
            showError("DAO non inizializzato. Effettua il login.");
            return;
        }
        String cf = corsoDao.getOwnerCfChef();
        if (cf == null || cf.isBlank()) {
            showError("Chef non identificato. Effettua il login.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/Report.fxml"));
            loader.setControllerFactory(type -> {
                if (type == ReportController.class) {
                    return new ReportController(cf);
                }
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

    /** Modifica: apre direttamente il Wizard già popolato; al salvataggio fa replace. */
 private void onEdit() {
    Corso sel = table.getSelectionModel().getSelectedItem();
    if (sel == null) return;

    if (!isOwnedByLoggedChef(sel)) {
        new Alert(Alert.AlertType.WARNING,
                "Puoi modificare solo i corsi che appartengono al tuo profilo.")
            .showAndWait();
        return;
    }

    // 1) Carica le sessioni in background
    javafx.concurrent.Task<List<Sessione>> loadTask = new javafx.concurrent.Task<>() {
        @Override protected List<Sessione> call() throws Exception {
            return sessioneDao.findByCorso(sel.getIdCorso());
        }
    };

    loadTask.setOnFailed(ev -> {
        Throwable ex = loadTask.getException();
        showError("Errore modifica sessioni (caricamento): " + (ex != null ? ex.getMessage() : "sconosciuto"));
        if (ex != null) ex.printStackTrace();
    });

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

            // Anti-zoom: wrappa se possibile e fissa dimensioni preferite
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
            dlg.setResizable(true);
            
            pane.setPrefSize(1150, 720); // match al controller
            dlg.setOnShown(e2 -> {
                Window w = dlg.getDialogPane().getScene().getWindow();
                if (w instanceof Stage stg) {
                    stg.setResizable(true);
                    stg.setMinWidth(1000);
                    stg.setMinHeight(650);
                    stg.centerOnScreen();
                }
            });

            
            // Owner e modality sicure
            Window owner = (table.getScene() != null) ? table.getScene().getWindow() : null;
            if (owner instanceof Stage st) {
                dlg.initOwner(st);
                dlg.initModality(Modality.WINDOW_MODAL); // modale SOLO alla finestra principale
            } else {
                // nessun owner -> NON modale per evitare freeze invisibili
                // dlg.initModality(Modality.NONE); // opzionale, è il default
            }

            // Mostra PRIMA, poi centra e blocca dimensioni
            dlg.show();

            Window w = dlg.getDialogPane().getScene().getWindow();
            if (w instanceof Stage stg) {
                stg.centerOnScreen();
                stg.setMinWidth(stg.getWidth());
                stg.setMaxWidth(stg.getWidth());
                stg.setMinHeight(stg.getHeight());
                stg.setMaxHeight(stg.getHeight());
                stg.toFront();
            }

            // Converter del risultato
            dlg.setResultConverter(bt ->
                (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                    ? ctrl.buildResult()
                    : null
            );

            // Gestisci l'esito quando il dialog si chiude
            dlg.setOnHidden(eh -> {
                Optional<List<Sessione>> res = Optional.ofNullable(dlg.getResult());
                if (res.isPresent() && res.get() != null) {
                    javafx.concurrent.Task<Void> replaceTask = new javafx.concurrent.Task<>() {
                        @Override protected Void call() throws Exception {
                            sessioneDao.replaceForCorso(sel.getIdCorso(), res.get());
                            return null;
                        }
                    };
                    replaceTask.setOnFailed(ev2 -> {
                        Throwable ex2 = replaceTask.getException();
                        showError("Errore salvataggio sessioni: " + (ex2 != null ? ex2.getMessage() : "sconosciuto"));
                        if (ex2 != null) ex2.printStackTrace();
                    });
                    replaceTask.setOnSucceeded(ev2 -> showInfo("Sessioni aggiornate: " + res.get().size()));
                    new Thread(replaceTask, "replace-sessioni").start();
                }
            });

        } catch (Exception e) {
            showError("Errore apertura wizard sessioni: " + e.getMessage());
            e.printStackTrace();
        }
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

            dialog.setResultConverter(bt ->
                    (bt == ctrl.getCreateButtonType() || bt.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                            ? ctrl.getResult()
                            : null
            );

            Optional<Corso> res = dialog.showAndWait();
            if (res.isPresent()) {
                Corso nuovo = res.get();

                // 1) wizard sessioni (opzionale)
                Optional<List<Sessione>> sessOpt = openSessioniWizard(nuovo);

                try {
                    final long id;
                    if (sessOpt.isPresent() && sessOpt.get() != null) {
                        List<Sessione> sessions = sessOpt.get();
                        if (!sessions.isEmpty()) {
                            id = corsoDao.insertWithSessions(nuovo, sessions);
                        } else {
                            id = corsoDao.insert(nuovo);
                        }
                    } else {
                        id = corsoDao.insert(nuovo);
                    }
                    nuovo.setIdCorso(id);

                    // 2) aggiorna UI
                    backing.add(nuovo);
                    table.getSelectionModel().select(nuovo);
                } catch (Exception saveEx) {
                    showError("Salvataggio corso/sessioni fallito: " + saveEx.getMessage());
                    saveEx.printStackTrace();
                }
            }

        } catch (Exception ex) {
            showError("Errore apertura editor corso: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Apre il wizard delle sessioni per un corso NON ancora salvato (nuovo). */
    private Optional<List<Sessione>> openSessioniWizard(Corso corso) {
        try {
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
            DialogPane pane = fx.load();

            SessioniWizardController ctrl = fx.getController();
            ctrl.initWithCorso(corso);

            Dialog<List<Sessione>> dlg = new Dialog<>();
            dlg.setTitle("Configura sessioni del corso");
            dlg.setDialogPane(pane);

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
        a.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            try {
                corsoDao.delete(sel.getIdCorso());
                backing.remove(sel);
            } catch (Exception ex) {
                showError("Impossibile eliminare il corso: " + ex.getMessage());
            }
        });
    }

    private void onAssociateRecipes() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            List<SessionePresenza> presenze = new ArrayList<>();
            sessioneDao.findByCorso(sel.getIdCorso()).forEach(s -> {
                if (s instanceof SessionePresenza sp) presenze.add(sp);
            });
            if (presenze.isEmpty()) {
                showInfo("Il corso non ha sessioni in presenza.");
                return;
            }
            showInfo("Funzionalità associa ricette qui (da implementare).");
        } catch (Exception e) {
            showError("Errore recupero sessioni: " + e.getMessage());
        }
    }

    private void showReport() {
        showInfo("Report mensile (placeholder).");
    }

    /* ---------------- Viewer read-only ---------------- */

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

    /* ---------------- Helpers UI ---------------- */

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
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private boolean isOwnedByLoggedChef(Corso c) {
        if (c == null || c.getChef() == null || c.getChef().getCF_Chef() == null) return false;
        return c.getChef().getCF_Chef().equalsIgnoreCase(corsoDao.getOwnerCfChef());
    }
}
