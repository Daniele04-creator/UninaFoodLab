package it.unina.foodlab.ui;

import it.unina.foodlab.dao.CorsoDao;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.dao.RicettaDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.model.Corso;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class CorsiPanel extends BorderPane {

    private final SessioneDao sessioneDao;
    private final CorsoDao corsoDao;
    private Node savedTop, savedBottom, savedCenter;
    private boolean showingReport = false;

    private final TextField txtSearch = new TextField();
    private final ComboBox<String> cbArgFiltro = new ComboBox<>();
    private final Button btnClear = new Button("Pulisci filtri");
    private final Button btnRefresh = new Button("Aggiorna");
    private final Button btnNew = new Button("Nuovo");
    private final Button btnEdit = new Button("Modifica");
    private final Button btnDelete = new Button("Elimina");
    private final Button btnAssocRicette = new Button("Associa ricette");
    private final Button btnReport = new Button("Report mensile");

    private final TableView<Corso> table = new TableView<>();
    private final ObservableList<Corso> backing = FXCollections.observableArrayList();
    private final FilteredList<Corso> filtered = new FilteredList<>(backing, c -> true);
    private final SortedList<Corso> sorted = new SortedList<>(filtered);

    public CorsiPanel(CorsoDao corsoDao, SessioneDao sessioneDao) {
        this.corsoDao = Objects.requireNonNull(corsoDao, "corsoDao");
        this.sessioneDao = Objects.requireNonNull(sessioneDao, "sessioneDao");
        buildUI();
        wireEvents();
        loadData();
    }

    /* ================= UI ================= */

    private void buildUI() {
        setPadding(new Insets(16));

        Label title = new Label("Corsi");
        title.getStyleClass().add("h2");

        txtSearch.setPromptText("Cerca per argomento, frequenza, chef…");
        txtSearch.setPrefWidth(260);

        cbArgFiltro.setPromptText("Argomento");
        cbArgFiltro.setMinWidth(160);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox top = new HBox(10,
                title, spacer,
                new Label("Argomento:"), cbArgFiltro,
                txtSearch, btnClear, btnRefresh, btnReport
        );
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 12, 0));
        setTop(top);

        // Colonne tabella
        TableColumn<Corso, Long> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("idCorso"));
        colId.setPrefWidth(80);
        colId.setStyle("-fx-alignment: CENTER;");

        TableColumn<Corso, String> colArg = new TableColumn<>("Argomento");
        colArg.setCellValueFactory(new PropertyValueFactory<>("argomento"));
        colArg.setPrefWidth(220);

        TableColumn<Corso, String> colFreq = new TableColumn<>("Frequenza");
        colFreq.setCellValueFactory(new PropertyValueFactory<>("frequenza"));
        colFreq.setPrefWidth(120);

        TableColumn<Corso, Integer> colNumSess = new TableColumn<>("# Sessioni");
        colNumSess.setCellValueFactory(new PropertyValueFactory<>("numSessioni"));
        colNumSess.setPrefWidth(110);
        colNumSess.setStyle("-fx-alignment: CENTER;");

        TableColumn<Corso, LocalDate> colInizio = new TableColumn<>("Inizio");
        colInizio.setCellValueFactory(new PropertyValueFactory<>("dataInizio"));
        colInizio.setPrefWidth(110);
        colInizio.setStyle("-fx-alignment: CENTER;");
        colInizio.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        TableColumn<Corso, LocalDate> colFine = new TableColumn<>("Fine");
        colFine.setCellValueFactory(new PropertyValueFactory<>("dataFine"));
        colFine.setPrefWidth(110);
        colFine.setStyle("-fx-alignment: CENTER;");
        colFine.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        TableColumn<Corso, String> colChef = new TableColumn<>("Chef");
        colChef.setPrefWidth(200);
        colChef.setCellValueFactory(cd -> {
            var chef = cd.getValue().getChef();
            String s = chef == null ? "" :
                    (chef.getNome() != null || chef.getCognome() != null)
                            ? ((chef.getNome() == null ? "" : chef.getNome()) + " " + (chef.getCognome() == null ? "" : chef.getCognome())).trim()
                            : chef.getCF_Chef();
            return javafx.beans.binding.Bindings.createStringBinding(() -> s);
        });

        table.getColumns().addAll(colId, colArg, colFreq, colInizio, colFine, colChef, colNumSess);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessun corso trovato."));
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        setCenter(table);

        btnAssocRicette.setDisable(true); // disabilitato finché non selezioni un tuo corso

        HBox bottom = new HBox(10, btnNew, btnEdit, btnAssocRicette, btnDelete);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(12, 0, 0, 0));
        setBottom(bottom);

        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
    }

    private void wireEvents() {
        table.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
            boolean hasSel = sel != null;
            boolean own = hasSel && isOwnedByLoggedChef(sel);
            btnEdit.setDisable(!hasSel || !own);
            btnDelete.setDisable(!hasSel || !own);
            btnAssocRicette.setDisable(!hasSel || !own);
        });

        // filtri
        cbArgFiltro.valueProperty().addListener((o, a, b) ->
                filtered.setPredicate(makeFilter(txtSearch.getText())));
        txtSearch.textProperty().addListener((o, a, b) ->
                filtered.setPredicate(makeFilter(b)));

        btnClear.setOnAction(e -> {
            txtSearch.clear();
            cbArgFiltro.setValue("Tutti");
            filtered.setPredicate(makeFilter(""));
        });

        btnRefresh.setOnAction(e -> loadData());
        btnNew.setOnAction(e -> onNew());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnAssocRicette.setOnAction(e -> onAssociateRecipes());
        btnReport.setOnAction(e -> showReport());

        table.setRowFactory(tv -> {
            TableRow<Corso> r = new TableRow<>();
            r.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !r.isEmpty()) onEdit();
            });
            return r;
        });

        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.DELETE && !btnDelete.isDisabled()) onDelete();
        });
    }

    private boolean isOwnedByLoggedChef(Corso c) {
        return c != null
                && c.getChef() != null
                && c.getChef().getCF_Chef() != null
                && c.getChef().getCF_Chef().equalsIgnoreCase(corsoDao.getOwnerCfChef());
    }

    private Predicate<Corso> makeFilter(String qRaw) {
        String q = qRaw == null ? "" : qRaw.trim().toLowerCase(Locale.ROOT);
        String argSel = cbArgFiltro.getValue();
        boolean filtraArgomento = (argSel != null && !"Tutti".equalsIgnoreCase(argSel));

        return c -> {
            // filtro per argomento
            if (filtraArgomento) {
                String a = c.getArgomento();
                if (a == null || !a.equalsIgnoreCase(argSel)) return false;
            }
            // filtro testo
            if (q.isEmpty()) return true;
            return contains(c.getArgomento(), q)
                    || contains(c.getFrequenza(), q)
                    || (c.getChef() != null && (
                    contains(c.getChef().getCF_Chef(), q)
                            || contains(c.getChef().getNome(), q)
                            || contains(c.getChef().getCognome(), q)
            ));
        };
    }

    private boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    /* ================= DATA ================= */

    public final void loadData() {
        try {
            var all = corsoDao.findAll();
            backing.setAll(all);

            // popola combo Argomento
            var args = backing.stream()
                    .map(Corso::getArgomento)
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            cbArgFiltro.getItems().setAll(args);
            if (!cbArgFiltro.getItems().contains("Tutti")) cbArgFiltro.getItems().add(0, "Tutti");
            if (cbArgFiltro.getValue() == null) cbArgFiltro.setValue("Tutti");

            if (!all.isEmpty()) {
                Platform.runLater(() -> table.getSelectionModel().selectFirst());
            }
        } catch (Exception ex) {
            showError("Errore caricamento corsi", ex);
        }
    }

    /* ================= ACTIONS ================= */

    private void onNew() {
        Optional<Corso> res = showEditor(null);
        res.ifPresent(c -> {
            try {
                long id = corsoDao.insert(c);
                c.setIdCorso(id);

                // ownership
                if (c.getChef() == null) {
                    Chef s = new Chef();
                    s.setCF_Chef(corsoDao.getOwnerCfChef());
                    c.setChef(s);
                } else if (c.getChef().getCF_Chef() == null) {
                    c.getChef().setCF_Chef(corsoDao.getOwnerCfChef());
                }

                backing.add(c);
                table.getSelectionModel().select(c);

                // Wizard sessioni (classe separata che restituisce List<Sessione>)
                SessioniWizardDialog wizard = new SessioniWizardDialog(c);
                var resSess = wizard.showAndWait();
                resSess.ifPresent(sessioni -> {
                    try {
                        sessioneDao.saveAll(sessioni);
                        new Alert(Alert.AlertType.INFORMATION, "Sessioni create correttamente.").showAndWait();
                    } catch (Exception ex) {
                        showError("Errore salvataggio sessioni", ex);
                    }
                });
            } catch (Exception ex) {
                showError("Impossibile creare il corso", ex);
            }
        });
    }

    private void onEdit() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!isOwnedByLoggedChef(sel)) {
            new Alert(Alert.AlertType.WARNING, "Puoi modificare solo i corsi che appartengono al tuo profilo.").showAndWait();
            return;
        }
        Optional<Corso> res = showEditor(sel);
        res.ifPresent(updated -> {
            try {
                corsoDao.update(updated);
                try {
                    sessioneDao.rescheduleByCourse(updated);
                } catch (Exception e) {
                    showError("Impossibile riallineare le date delle sessioni al corso", e);
                }
                int idx = backing.indexOf(sel);
                if (idx >= 0) backing.set(idx, updated);
                table.getSelectionModel().select(updated);
            } catch (Exception ex) {
                showError("Impossibile modificare il corso", ex);
            }
        });
    }

    private void onDelete() {
        Corso sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!isOwnedByLoggedChef(sel)) {
            new Alert(Alert.AlertType.WARNING, "Puoi eliminare solo i corsi che appartengono al tuo profilo.").showAndWait();
            return;
        }
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Conferma eliminazione");
        a.setHeaderText("Eliminare il corso selezionato?");
        String chefInfo = sel.getChef() == null ? "" :
                (sel.getChef().getCF_Chef() != null ? " – Chef: " + sel.getChef().getCF_Chef() : "");
        a.setContentText("ID: " + sel.getIdCorso() + " – " + sel.getArgomento() + chefInfo);
        a.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        a.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(bt -> {
            try {
                corsoDao.delete(sel.getIdCorso());
                backing.remove(sel);
            } catch (Exception ex) {
                showError("Impossibile eliminare il corso", ex);
            }
        });
    }

    private void onAssociateRecipes() {
        Corso corsoSel = table.getSelectionModel().getSelectedItem();
        if (corsoSel == null) return;

        try {
            // 1) Preleva TUTTE le sessioni e filtra SOLO Presenza
            var tutte = sessioneDao.findByCorso(corsoSel.getIdCorso());
            var soloPresenza = new java.util.ArrayList<it.unina.foodlab.model.SessionePresenza>();
            for (var s : tutte) {
                if (s instanceof it.unina.foodlab.model.SessionePresenza sp) {
                    soloPresenza.add(sp);
                }
            }
            if (soloPresenza.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION,
                    "Questo corso non ha sessioni in presenza.\n" +
                    "Le ricette possono essere associate solo alle sessioni in presenza."
                ).showAndWait();
                return;
            }

            // 2) Selezione sessione
            ChoiceDialog<it.unina.foodlab.model.SessionePresenza> pick =
                    new ChoiceDialog<>(soloPresenza.get(0), soloPresenza);
            pick.setTitle("Seleziona sessione (PRESENZA)");
            pick.setHeaderText("Scegli la sessione in presenza a cui associare ricette");
            pick.setContentText("Sessione:");
            var resSess = pick.showAndWait();
            if (resSess.isEmpty()) return;
            var sessioneSel = resSess.get();

            // 3) Lista ricette disponibili (multi-selezione)
            RicettaDao ricettaDao = new RicettaDao();
            var tutteRicette = FXCollections.observableArrayList(ricettaDao.findAll());

            Dialog<java.util.List<it.unina.foodlab.model.Ricetta>> dlg = new Dialog<>();
            dlg.setTitle("Aggiungi ricette alla sessione in presenza");
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

            ListView<it.unina.foodlab.model.Ricetta> lv = new ListView<>(tutteRicette);
            lv.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            lv.setCellFactory(list -> new ListCell<>() {
                @Override protected void updateItem(it.unina.foodlab.model.Ricetta item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); }
                    else setText(item.getNome() + " – " + item.getDifficolta()
                                 + " (" + item.getTempoPreparazione() + " min)");
                }
            });
            dlg.getDialogPane().setContent(lv);

            dlg.setResultConverter(bt -> bt == ButtonType.OK
                    ? new java.util.ArrayList<>(lv.getSelectionModel().getSelectedItems())
                    : null);

            var resRic = dlg.showAndWait();
            if (resRic.isEmpty() || resRic.get() == null || resRic.get().isEmpty()) return;

            // 4) **AGGIUNTA INCREMENTALE**: niente replace.
            //    Per ogni ricetta selezionata, aggiungi la riga nella tabella ponte.
            //    Le duplicazioni vengono ignorate dall'ON CONFLICT DO NOTHING nel DAO.
            for (it.unina.foodlab.model.Ricetta r : resRic.get()) {
                sessioneDao.addRicettaToSessionePresenza(sessioneSel.getId(), r.getIdRicetta());
            }

            new Alert(Alert.AlertType.INFORMATION, "Ricette aggiunte alla sessione.").showAndWait();

        } catch (Exception ex) {
            showError("Errore associazione ricette", ex);
        }
    }

    /* ================= EDITOR CORSO ================= */

    private static String readComboValue(ComboBox<String> cb) {
        String v = cb.isEditable() ? cb.getEditor().getText() : cb.getValue();
        return v == null ? "" : v.trim();
    }

    private Optional<Corso> showEditor(Corso original) {
    boolean edit = original != null;
    Dialog<Corso> d = new Dialog<>();
    d.setTitle(edit ? "Modifica Corso" : "Nuovo Corso");
    ButtonType okType = new ButtonType(edit ? "Salva" : "Crea", ButtonBar.ButtonData.OK_DONE);
    d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, okType);

    var suggeriti = FXCollections.observableArrayList(
            "Cucina Asiatica", "Pasticceria", "Panificazione", "Vegetariano",
            "Street Food", "Dolci al cucchiaio", "Cucina Mediterranea",
            "Finger Food", "Fusion", "Vegan"
    );
    backing.stream()
            .map(Corso::getArgomento)
            .filter(s -> s != null && !s.isBlank())
            .map(String::trim)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(a -> { if (!suggeriti.contains(a)) suggeriti.add(a); });

    ComboBox<String> cbArg = new ComboBox<>(suggeriti);
    cbArg.setEditable(true);
    cbArg.setPromptText("Es. Pasticceria");
    cbArg.setConverter(new StringConverter<>() {
        @Override public String toString(String s) { return s; }
        @Override public String fromString(String s) { return s; }
    });

    ChoiceBox<String> cbFreq = new ChoiceBox<>(FXCollections.observableArrayList(
            "settimanale", "ogni 2 giorni", "bisettimanale", "mensile"
    ));
    cbFreq.setConverter(new StringConverter<>() {
        @Override public String toString(String s) { return s; }
        @Override public String fromString(String s) { return s; }
    });

    Spinner<Integer> spNumSess = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 5)
    );
    spNumSess.setEditable(true);

    DatePicker dpInizio = new DatePicker(LocalDate.now().plusDays(7));

    // LABEL *sola lettura* con data fine calcolata
    Label lblFineAuto = new Label("—");

    if (edit) {
        cbArg.setValue(nz(original.getArgomento()));
        cbFreq.setValue(nz(original.getFrequenza()));
        dpInizio.setValue(original.getDataInizio());
        spNumSess.getValueFactory().setValue(Math.max(1, original.getNumSessioni()));
        // mostra subito una data fine coerente
        lblFineAuto.setText(formatOrDash(
                computeDataFine(dpInizio.getValue(), cbFreq.getValue(), spNumSess.getValue())
        ));
    } else {
        cbFreq.setValue("settimanale");
        lblFineAuto.setText(formatOrDash(
                computeDataFine(dpInizio.getValue(), cbFreq.getValue(), spNumSess.getValue())
        ));
    }

    GridPane gp = new GridPane();
    gp.setHgap(10); gp.setVgap(10); gp.setPadding(new Insets(16));
    int r=0;
    gp.add(new Label("Argomento*"), 0,r); gp.add(cbArg, 1,r++);
    gp.add(new Label("Frequenza*"), 0,r); gp.add(cbFreq, 1,r++);
    gp.add(new Label("Data inizio*"),0,r); gp.add(dpInizio,1,r++);
    gp.add(new Label("Data fine (auto)"), 0,r); gp.add(lblFineAuto, 1,r++); // <-- solo lettura
    gp.add(new Label("N. sessioni*"), 0,r); gp.add(spNumSess, 1,r++);

    d.getDialogPane().setContent(gp);

    Node okBtn = d.getDialogPane().lookupButton(okType);
    okBtn.setDisable(true);

    Runnable updateFineAndValidate = () -> {
        // aggiorna la data fine calcolata
        LocalDate fine = computeDataFine(dpInizio.getValue(), cbFreq.getValue(), safeInt(spNumSess.getValue()));
        lblFineAuto.setText(formatOrDash(fine));

        // valida i campi obbligatori
        String argVal = readComboValue(cbArg);
        boolean valid = !argVal.isEmpty()
                && cbFreq.getValue() != null && !cbFreq.getValue().trim().isEmpty()
                && dpInizio.getValue() != null
                && spNumSess.getValue() != null && spNumSess.getValue() >= 1;
        okBtn.setDisable(!valid);
    };

    cbArg.valueProperty().addListener((o,a,b)->updateFineAndValidate.run());
    cbArg.getEditor().textProperty().addListener((o,a,b)->updateFineAndValidate.run());
    cbFreq.valueProperty().addListener((o,a,b)->updateFineAndValidate.run());
    dpInizio.valueProperty().addListener((o,a,b)->updateFineAndValidate.run());
    spNumSess.valueProperty().addListener((o,a,b)->updateFineAndValidate.run());
    Platform.runLater(cbArg::requestFocus);
    updateFineAndValidate.run();

    d.setResultConverter(bt -> {
        if (bt != okType) return null;
        Corso c = edit ? clone(original) : new Corso();
        if (!edit) c.setIdCorso(0L);

        String argomentoScelto = readComboValue(cbArg);
        c.setArgomento(argomentoScelto);
        c.setFrequenza(cbFreq.getValue().trim());
        c.setDataInizio(dpInizio.getValue());
        c.setNumSessioni(spNumSess.getValue());

        // calcolo finale
        LocalDate dataFine = computeDataFine(c.getDataInizio(), c.getFrequenza(), c.getNumSessioni());
        c.setDataFine(dataFine);

        return c;
    });

    return d.showAndWait();
}

/* ==== helper ==== */
private static int safeInt(Integer v) { return v == null ? 0 : v; }
private static String formatOrDash(LocalDate d) { return d == null ? "—" : d.toString(); }

/** Calcola la data fine come data dell'ULTIMA sessione. */
private static LocalDate computeDataFine(LocalDate inizio, String frequenza, int numSessioni) {
    if (inizio == null || numSessioni <= 0) return null;
    int steps = Math.max(0, numSessioni - 1); // es: 1 sessione => fine = inizio
    String f = (frequenza == null ? "" : frequenza.trim().toLowerCase(Locale.ROOT));

    switch (f) {
        case "ogni 2 giorni":
            return inizio.plusDays(2L * steps);
        case "bisettimanale": // interpretato come "ogni due settimane"
            return inizio.plusWeeks(2L * steps);
        case "mensile":
            return inizio.plusMonths(steps);
        case "settimanale":
        default:
            return inizio.plusWeeks(steps);
    }
}

    private Corso clone(Corso src) {
        Corso c = new Corso();
        c.setIdCorso(src.getIdCorso());
        c.setArgomento(src.getArgomento());
        c.setFrequenza(src.getFrequenza());
        c.setDataInizio(src.getDataInizio());
        c.setDataFine(src.getDataFine());
        c.setNumSessioni(src.getNumSessioni());

        if (src.getChef() != null) {
            Chef s = new Chef();
            s.setCF_Chef(src.getChef().getCF_Chef());
            s.setNome(src.getChef().getNome());
            s.setCognome(src.getChef().getCognome());
            s.setNascita(src.getChef().getNascita());
            s.setUsername(src.getChef().getUsername());
            s.setPassword(src.getChef().getPassword());
            c.setChef(s);
        }
        return c;
    }

    private String nz(String s) { return s == null ? "" : s; }

    private void showError(String header, Exception ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Errore");
        a.setHeaderText(header);
        a.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        a.getDialogPane().setMinWidth(520);
        a.showAndWait();
    }
    
    private void showReport() {
        if (showingReport) return;

        // salva layout corrente
        savedTop = getTop();
        savedBottom = getBottom();
        savedCenter = getCenter();

        // barra con tasto "indietro"
        HBox backBar = new HBox(8);
        Button btnBack = new Button("← Indietro");
        btnBack.setOnAction(ev -> hideReport());
        backBar.getChildren().add(btnBack);
        backBar.setPadding(new Insets(8, 0, 12, 0));

        setTop(backBar);
        setBottom(null);

        // mostra il Report al centro
        ReportPanel report = new ReportPanel(corsoDao.getOwnerCfChef());
        setCenter(report);

        // ESC per tornare indietro
        if (getScene() != null) {
            getScene().setOnKeyPressed(k -> {
                if (k.getCode() == KeyCode.ESCAPE && showingReport) hideReport();
            });
        }

        showingReport = true;
    }

    private void hideReport() {
        setTop(savedTop);
        setBottom(savedBottom);
        setCenter(savedCenter);

        // rimuovi handler ESC
        if (getScene() != null) getScene().setOnKeyPressed(null);

        showingReport = false;
    }
    
    
}