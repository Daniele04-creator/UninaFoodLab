package it.unina.foodlab.controller;

import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.RicettaDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessionePresenza;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CorsiPanelController {

    private static final String ALL_OPTION = "Tutte";

    private final ObservableList<String> argomentiCondivisi = FXCollections.observableArrayList();

    @FXML private Button btnRefresh, btnReport;
    @FXML private ComboBox<String> cbFiltroArgomento;
    @FXML private TableView<Corso> table;
    @FXML private TableColumn<Corso, Long> colId;
    @FXML private TableColumn<Corso, String> colArg;
    @FXML private TableColumn<Corso, String> colFreq;
    @FXML private TableColumn<Corso, LocalDate> colInizio;
    @FXML private TableColumn<Corso, LocalDate> colFine;
    @FXML private TableColumn<Corso, String> colChef;
    @FXML private Button btnNew, btnEdit, btnDelete, btnAssocRicette;
    @FXML private TableColumn<Corso, String> colStato;

    private final ObservableList<Corso> backing = FXCollections.observableArrayList();
    private final FilteredList<Corso> filtered = new FilteredList<>(backing, c -> true);
    private final SortedList<Corso> sorted = new SortedList<>(filtered);

    private CorsoDao corsoDao;
    private SessioneDao sessioneDao;
    private RicettaDao ricettaDao;

    private String filtroArg = null;

    private final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private void initialize() {
        table.getStyleClass().add("dark-table");

        initTableColumns();
        table.setItems(sorted);
        sorted.comparatorProperty().bind(table.comparatorProperty());

        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.setFixedCellSize(48);

        installRowFactory();

        Label ph = new Label("Non hai ancora corsi.\nCrea il primo corso per iniziare a pianificare le sessioni.");
        ph.setAlignment(Pos.CENTER);
        table.setPlaceholder(ph);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, sel) -> {
            boolean has = sel != null;
            boolean own = isOwnedByLoggedChef(sel);
            btnEdit.setDisable(!has || !own);
            btnDelete.setDisable(!has || !own);
            btnAssocRicette.setDisable(!has || !own);
        });

        btnRefresh.setOnAction(e -> reload());
        btnReport.setOnAction(e -> openReportMode());
        btnNew.setOnAction(e -> onNew());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnAssocRicette.setOnAction(e -> onAssociateRecipes());

        cbFiltroArgomento.valueProperty().addListener((obs, oldV, newV) -> {
            filtroArg = (newV == null || ALL_OPTION.equalsIgnoreCase(newV)) ? null : newV;
            refilter();
            refreshTitleWithCount();
        });

        table.getSortOrder().add(colInizio);
        colInizio.setSortType(TableColumn.SortType.DESCENDING);

        refreshTitleWithCount();
    }

    private void initTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("idCorso"));
        colId.setStyle("-fx-alignment: CENTER;");

        colArg.setCellValueFactory(new PropertyValueFactory<>("argomento"));
        colArg.setCellFactory(tc -> new TableCell<Corso, String>() {
            private final Label lbl = makeCellLabel(true);
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setGraphic(null);
                    return;
                }
                lbl.setText(v);
                setGraphic(lbl);
            }
        });

        colFreq.setCellValueFactory(new PropertyValueFactory<>("frequenza"));
        colFreq.setCellFactory(tc -> new TableCell<Corso, String>() {
            private final Label lbl = makeCellLabel(false);
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setGraphic(null);
                    return;
                }
                lbl.setText(v);
                setGraphic(lbl);
            }
        });

        colStato.setCellValueFactory(cd -> Bindings.createStringBinding(() -> statoOf(cd.getValue())));
        colStato.setCellFactory(tc -> new TableCell<Corso, String>() {
            private final Label chip = new Label();
            {
                chip.getStyleClass().addAll("stato-chip");
            }
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null || s.isEmpty()) {
                    setGraphic(null);
                    return;
                }
                chip.setText(s);
                chip.getStyleClass().removeAll("stato-futuro", "stato-in-corso", "stato-concluso", "stato-unknown");
                switch (s) {
                    case "In corso" -> chip.getStyleClass().add("stato-in-corso");
                    case "Futuro" -> chip.getStyleClass().add("stato-futuro");
                    case "Concluso" -> chip.getStyleClass().add("stato-concluso");
                    default -> chip.getStyleClass().add("stato-unknown");
                }
                setGraphic(chip);
            }
        });

        colInizio.setCellValueFactory(new PropertyValueFactory<>("dataInizio"));
        colInizio.setCellFactory(tc -> new TableCell<Corso, LocalDate>() {
            private final Label lbl = makeCellLabel(false);
            {
                setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) {
                    setGraphic(null);
                    return;
                }
                lbl.setText(DF.format(d));
                setGraphic(lbl);
            }
        });

        colFine.setCellValueFactory(new PropertyValueFactory<>("dataFine"));
        colFine.setCellFactory(tc -> new TableCell<Corso, LocalDate>() {
            private final Label lbl = makeCellLabel(false);
            {
                setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) {
                    setGraphic(null);
                    return;
                }
                lbl.setText(DF.format(d));
                setGraphic(lbl);
            }
        });

        colChef.setCellValueFactory(cd -> Bindings.createStringBinding(() -> {
            Corso c = cd.getValue();
            if (c == null || c.getChef() == null) return "";
            String nome = nz(c.getChef().getNome());
            String cogn = nz(c.getChef().getCognome());
            String full = (nome + " " + cogn).trim();
            return full.isEmpty() ? nz(c.getChef().getCF_Chef()) : full;
        }));
        colChef.setCellFactory(tc -> new TableCell<Corso, String>() {
            private final Label lbl = makeCellLabel(false);
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setGraphic(null);
                    return;
                }
                lbl.setText(v);
                setGraphic(lbl);
            }
        });

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colId.setMaxWidth(80);
        colId.setMinWidth(70);
        colArg.setPrefWidth(260);
        colFreq.setPrefWidth(140);
        colStato.setPrefWidth(120);
        colInizio.setPrefWidth(130);
        colFine.setPrefWidth(130);
        colChef.setPrefWidth(220);

        colInizio.setSortType(TableColumn.SortType.DESCENDING);
    }

    private Label makeCellLabel(boolean bold) {
        Label l = new Label();
        l.setPadding(new Insets(2, 10, 2, 10));
        l.getStyleClass().add(bold ? "cell-label-bold" : "cell-label");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private String statoOf(Corso c) {
        if (c == null) return "";
        LocalDate oggi = LocalDate.now();
        LocalDate in = c.getDataInizio(), fin = c.getDataFine();
        if (in != null && fin != null) {
            if (oggi.isBefore(in)) return "Futuro";
            if ((oggi.isEqual(in) || oggi.isAfter(in)) && (oggi.isBefore(fin) || oggi.isEqual(fin))) return "In corso";
            if (oggi.isAfter(fin)) return "Concluso";
        }
        return "";
    }

    private void refreshTitleWithCount() {
        if (table == null || table.getScene() == null) return;
        if (table.getScene().getWindow() instanceof Stage st) {
            st.setTitle("Corsi • " + filtered.size() + " elementi");
        }
    }

    public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao, RicettaDao ricettaDao) {
        this.ricettaDao = ricettaDao;
        setDaos(corsoDao, sessioneDao);
    }

    public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao) {
        this.corsoDao = corsoDao;
        this.sessioneDao = sessioneDao;
        reload();
    }

    private void reload() {
        if (corsoDao == null) {
            showError("DAO non inizializzato. Effettua il login.");
            return;
        }
        try {
            List<Corso> list = corsoDao.findAll();
            if (list == null) list = Collections.emptyList();
            backing.setAll(list);
            refilter();
            populateFiltroArgomento();
            refreshTitleWithCount();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void populateFiltroArgomento() {
        if (cbFiltroArgomento == null) return;
        List<String> argOpts = distinctArgomenti();
        cbFiltroArgomento.getItems().setAll(argOpts);
        String toSelect = filtroArg == null ? ALL_OPTION : filtroArg;
        if (!argOpts.contains(toSelect)) {
            toSelect = ALL_OPTION;
            filtroArg = null;
        }
        cbFiltroArgomento.getSelectionModel().select(toSelect);
    }

    private void installRowFactory() {
        table.setRowFactory(tv -> {
            TableRow<Corso> row = new TableRow<>();
            row.setOnMouseEntered(e -> row.setCursor(Cursor.HAND));
            row.setOnMouseExited(e -> row.setCursor(Cursor.DEFAULT));
            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) return;
                if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                    openSessioniPreview(row.getItem());
                } else if (e.getClickCount() == 1 && e.getButton() == MouseButton.PRIMARY) {
                    table.getSelectionModel().select(row.getIndex());
                }
            });
            return row;
        });
    }

    private void refilter() {
        filtered.setPredicate(c -> c != null && matchesEqIgnoreCase(c.getArgomento(), filtroArg));
    }

    private List<String> distinctArgomenti() {
        Set<String> s = new HashSet<>();
        for (Corso c : backing) {
            String a = (c != null) ? c.getArgomento() : null;
            if (a != null && !a.trim().isEmpty()) s.add(a.trim());
        }
        List<String> out = new ArrayList<>(s);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        out.add(0, ALL_OPTION);
        return out;
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

    private void onEdit() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!isOwnedByLoggedChef(sel)) {
            new Alert(Alert.AlertType.WARNING, "Puoi modificare solo i corsi che appartengono al tuo profilo.").showAndWait();
            return;
        }
        try {
            List<Sessione> esistenti = sessioneDao.findByCorso(sel.getIdCorso());
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
            DialogPane pane = fx.load();
            pane.getStyleClass().add("dark-dialog");
            SessioniWizardController ctrl = fx.getController();
            ctrl.initWithCorsoAndExisting(sel, esistenti != null ? esistenti : Collections.emptyList());
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
            Dialog<List<Sessione>> dlg = new Dialog<>();
            dlg.setTitle("Modifica sessioni - " + nz(sel.getArgomento()));
            dlg.setDialogPane(pane);
            pane.setPrefSize(1200, 800);
            dlg.setResultConverter(bt -> (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) ? ctrl.buildResult() : null);
            Optional<List<Sessione>> res = dlg.showAndWait();
            if (res.isPresent() && res.get() != null) {
                sessioneDao.replaceForCorso(sel.getIdCorso(), res.get());
                showInfoDark("Sessioni aggiornate.");
            }
        } catch (Exception e) {
            showError("Errore modifica sessioni: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void onNew() {
        try {
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/CorsiEditorDialog.fxml"));
            refreshArgomentiCondivisi();
            DialogPane pane = fx.load();
            pane.getStyleClass().add("dark-dialog");
            CorsoEditorDialogController ctrl = fx.getController();
            ctrl.bindArgomenti(argomentiCondivisi);
            ctrl.setCorso(null);
            Dialog<Corso> dialog = new Dialog<>();
            dialog.setTitle("Nuovo Corso");
            dialog.setDialogPane(pane);
            dialog.setResizable(true);
            dialog.setResultConverter(bt -> (bt != null && bt == ctrl.getCreateButtonType()) ? ctrl.getResult() : null);
            Optional<Corso> res = dialog.showAndWait();
            if (!res.isPresent()) return;
            Corso nuovo = res.get();
            if (corsoDao != null) {
                String cfChef = corsoDao.getOwnerCfChef();
                if (cfChef != null && !cfChef.trim().isEmpty()) {
                    Chef chef = new Chef();
                    chef.setCF_Chef(cfChef);
                    nuovo.setChef(chef);
                }
            }
            Optional<List<Sessione>> sessOpt = openSessioniWizard(nuovo, Math.max(0, nuovo.getNumSessioni()));
            if (!sessOpt.isPresent()) return;
            List<Sessione> sessions = sessOpt.get();
            if (sessions == null || sessions.isEmpty()) return;
            long id = corsoDao.insertWithSessions(nuovo, sessions);
            nuovo.setIdCorso(id);
            backing.add(nuovo);
            table.getSelectionModel().select(nuovo);
            String arg = nuovo.getArgomento();
            if (arg != null && !argomentiCondivisi.contains(arg)) {
                arg = arg.trim();
                argomentiCondivisi.add(arg);
                FXCollections.sort(argomentiCondivisi);
            }
            populateFiltroArgomento();
            refreshTitleWithCount();
        } catch (Exception ex) {
            showError("Errore apertura/salvataggio corso: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void onDelete() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        boolean conferma = showConfirmDark("Conferma eliminazione", "Eliminare il corso: " + sel.getArgomento() + " ?");
        if (conferma) {
            try {
                corsoDao.delete(sel.getIdCorso());
                backing.remove(sel);
                populateFiltroArgomento();
                refreshTitleWithCount();
            } catch (Exception ex) {
                showInfoDark("Impossibile eliminare il corso: " + ex.getMessage());
            }
        }
    }

    private void openSessioniPreview(Corso corso) {
        if (corso == null) return;
        try {
            FXMLLoader l = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniPreview.fxml"));
            DialogPane pane = l.load();
            pane.getStyleClass().add("dark-dialog");
            SessioniPreviewController ctrl = l.getController();
            long id = corso.getIdCorso();
            List<Sessione> sessions = (sessioneDao != null) ? sessioneDao.findByCorso(id) : Collections.emptyList();
            ctrl.init(corso, sessions, sessioneDao);
            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle("Sessioni — " + corso.getArgomento());
            dlg.setDialogPane(pane);
            dlg.initOwner(table.getScene().getWindow());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.showAndWait();
        } catch (Exception ex) {
            showError("Errore apertura anteprima sessioni: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void onAssociateRecipes() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!isOwnedByLoggedChef(sel)) {
            showInfoDark("Puoi modificare solo i corsi del tuo profilo.");
            return;
        }
        try {
            List<Sessione> tutte = sessioneDao.findByCorso(sel.getIdCorso());
            List<SessionePresenza> presenze = new ArrayList<>();
            for (Sessione s : tutte) if (s instanceof SessionePresenza sp) presenze.add(sp);
            if (presenze.isEmpty()) {
                showInfoDark("Il corso non ha sessioni in presenza.");
                return;
            }
            SessionePresenza target = (presenze.size() == 1) ? presenze.get(0) : choosePresenza(presenze).orElse(null);
            if (target == null) return;
            if (ricettaDao == null) ricettaDao = new RicettaDao();
            List<Ricetta> tutteLeRicette = ricettaDao.findAll();
            List<Ricetta> associate = sessioneDao.findRicetteBySessionePresenza(target.getId());
            URL url = AssociaRicetteController.class.getResource("/it/unina/foodlab/ui/AssociaRicette.fxml");
            if (url == null) throw new IllegalStateException("FXML non trovato: /AssociaRicette.fxml");
            FXMLLoader fx = new FXMLLoader(url);
            fx.setControllerFactory(t -> {
                if (t == AssociaRicetteController.class) {
                    return new AssociaRicetteController(sessioneDao, target.getId(),
                            (tutteLeRicette != null ? tutteLeRicette : Collections.emptyList()),
                            (associate != null ? associate : Collections.emptyList()));
                }
                try {
                    return t.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Errore creazione controller: " + t, e);
                }
            });
            fx.load();
            AssociaRicetteController dlg = fx.getController();
            Optional<List<Long>> result = dlg.showAndWait();
            dlg.salvaSeConfermato(result.orElse(null));
        } catch (Exception e) {
            showError("Errore associazione ricette: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Optional<List<Sessione>> openSessioniWizard(Corso corso, int initialRows) {
        try {
            FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
            DialogPane pane = fx.load();
            pane.getStyleClass().add("dark-dialog");
            SessioniWizardController ctrl = fx.getController();
            if (initialRows > 0) ctrl.initWithCorsoAndBlank(corso, initialRows);
            else ctrl.initWithCorso(corso);
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
            Dialog<List<Sessione>> dlg = new Dialog<>();
            dlg.setTitle("Configura sessioni del corso");
            dlg.setDialogPane(pane);
            pane.setPrefSize(1200, 800);
            dlg.setResultConverter(bt -> (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) ? ctrl.buildResult() : null);
            return dlg.showAndWait();
        } catch (Exception ex) {
            showError("Errore apertura wizard sessioni: " + ex.getMessage());
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    private Optional<SessionePresenza> choosePresenza(List<SessionePresenza> presenze) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");
        Map<String, SessionePresenza> map = new LinkedHashMap<>();
        for (SessionePresenza sp : presenze) {
            String data = (sp.getData() != null) ? df.format(sp.getData()) : "";
            String orari = (sp.getOraInizio() != null ? tf.format(sp.getOraInizio()) : "") + "-" + (sp.getOraFine() != null ? tf.format(sp.getOraFine()) : "");
            String indirizzo = joinNonEmpty(" ", nz(sp.getVia()), nz(sp.getNum()), nz(sp.getCap())).trim();
            String base = (data + " " + orari + " " + indirizzo).trim();
            String key = base;
            int suffix = 2;
            while (map.containsKey(key)) key = base + " (" + (suffix++) + ")";
            map.put(key, sp);
        }
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Seleziona la sessione in presenza");
        dlg.setHeaderText("Scegli la sessione a cui associare le ricette");
        ButtonType OK = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType CANCEL = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().setAll(OK, CANCEL);
        ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(map.keySet()));
        cb.setEditable(false);
        cb.getStyleClass().add("dark-combobox");
        cb.getSelectionModel().select(firstKeyOr(map, ""));
        HBox box = new HBox(10, cb);
        box.setPadding(new Insets(6, 0, 0, 0));
        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getStyleClass().add("dark-dialog");
        dlg.getDialogPane().getStylesheets().add(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );
        dlg.setResultConverter(bt -> (bt == OK) ? cb.getValue() : null);
        Optional<String> pick = dlg.showAndWait();
        return pick.map(map::get);
    }

    private static String firstKeyOr(Map<String, SessionePresenza> map, String fallback) {
        for (String k : map.keySet()) return k;
        return fallback;
    }

    private static String joinNonEmpty(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (parts != null) {
            for (String p : parts) {
                if (p != null && !p.trim().isEmpty()) {
                    if (!first) sb.append(sep);
                    sb.append(p.trim());
                    first = false;
                }
            }
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return (s == null) ? "" : s;
    }
    
    private static String nz(int n) { return n > 0 ? String.valueOf(n) : ""; }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean matchesEqIgnoreCase(String value, String filter) {
        if (isBlank(filter)) return true;
        return value != null && value.equalsIgnoreCase(filter);
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText("Errore");
        a.setContentText(msg);
        DialogPane dp = a.getDialogPane();
        dp.getStyleClass().add("dark-dialog");
        dp.getStylesheets().add(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );
        a.showAndWait();
    }

    private boolean isOwnedByLoggedChef(Corso c) {
        if (c == null || c.getChef() == null || isBlank(c.getChef().getCF_Chef())) return false;
        String owner = (corsoDao != null) ? corsoDao.getOwnerCfChef() : null;
        return !isBlank(owner) && c.getChef().getCF_Chef().equalsIgnoreCase(owner);
    }

    private void refreshArgomentiCondivisi() {
        try {
            List<String> distinct = corsoDao.findDistinctArgomenti();
            argomentiCondivisi.setAll(distinct != null ? distinct : Collections.emptyList());
        } catch (Exception ex) {
        }
    }

    private void showInfoDark(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setGraphic(null);
        Label content = new Label(message == null ? "" : message);
        content.setWrapText(true);
        DialogPane dp = alert.getDialogPane();
        dp.setContent(content);
        dp.getStyleClass().add("dark-dialog");
        dp.getStylesheets().add(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );
        dp.setMinWidth(460);
        alert.showAndWait();
    }

    private boolean showConfirmDark(String titolo, String messaggio) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(titolo == null ? "Conferma" : titolo);
        alert.setHeaderText(null);
        alert.setGraphic(null);
        Label lbl = new Label(messaggio == null ? "" : messaggio);
        lbl.setWrapText(true);
        DialogPane dp = alert.getDialogPane();
        dp.setContent(lbl);
        dp.getStyleClass().add("dark-dialog");
        dp.getStylesheets().add(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );
        ButtonType confermaType = new ButtonType("Conferma", ButtonBar.ButtonData.OK_DONE);
        ButtonType annullaType = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
        dp.getButtonTypes().setAll(confermaType, annullaType);
        dp.setMinWidth(460);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == confermaType;
    }
}
