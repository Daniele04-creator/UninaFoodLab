package it.unina.foodlab.controller;

import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.RicettaDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessionePresenza;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gestione elenco corsi con filtri, CRUD, wizard sessioni, associazione ricette
 * a sessioni in presenza e anteprima sessioni.
 */
public class CorsiPanelController {

	private static final String ALL_OPTION = "Tutte";
	private static final int FILTERS_BADGE_MAX_CHARS = 32;

	/* ----------- TOP BAR ----------- */
	@FXML
	private MenuButton btnFilters;
	@FXML
	private Button btnRefresh, btnReport;

	/* ----------- TABELLA ----------- */
	@FXML
	private TableView<Corso> table;
	@FXML
	private TableColumn<Corso, Long> colId;
	@FXML
	private TableColumn<Corso, String> colArg;
	@FXML
	private TableColumn<Corso, String> colFreq;
	@FXML
	private TableColumn<Corso, LocalDate> colInizio;
	@FXML
	private TableColumn<Corso, LocalDate> colFine;
	@FXML
	private TableColumn<Corso, String> colChef;

	/* ----------- BOTTOM BAR ----------- */
	@FXML
	private Button btnNew, btnEdit, btnDelete, btnAssocRicette;

	/* ----------- DATI/STATE ----------- */
	private final ObservableList<Corso> backing = FXCollections.observableArrayList();
	private final FilteredList<Corso> filtered = new FilteredList<>(backing, c -> true);
	private final SortedList<Corso> sorted = new SortedList<>(filtered);

	private CorsoDao corsoDao;
	private SessioneDao sessioneDao;
	private RicettaDao ricettaDao;

	/* Filtri correnti */
	private String filtroArg = null;
	private String filtroFreq = null;
	private String filtroChef = null;
	private String filtroId = null;
	private LocalDate dateFrom = null;
	private LocalDate dateTo = null;

	/* --- Menu Filtri --- */
	private ContextMenu filtersMenu;
	private Label labArgomentoVal, labFrequenzaVal, labChefVal, labIdVal;
	private Button btnClearArg, btnClearFreq, btnClearChef, btnClearId;

	@FXML
	private void initialize() {
		initTableColumns();
		table.setItems(sorted);
		sorted.comparatorProperty().bind(table.comparatorProperty());

		// Selezione "per riga" e singola
		table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		table.getSelectionModel().setCellSelectionEnabled(false);

		// Bottone Filtri (aspetto coerente)
		hookFilterMenuButtonStyling();
		styleFilterMenuButton();

		// Menu Filtri custom (con date inline e '×' su ogni filtro)
		buildAndAttachFiltersContextMenu();
		updatePrettyFilterRows();

		// Tabella tema scuro allineato al login
		table.setTableMenuButtonVisible(false);
		applyTableStyling();

		table.skinProperty().addListener((obs, o, n) -> Platform.runLater(this::applyTableStyling));
		table.getColumns()
				.addListener((javafx.collections.ListChangeListener<? super TableColumn<Corso, ?>>) c -> Platform
						.runLater(this::applyTableStyling));
		table.widthProperty().addListener((o, a, b) -> Platform.runLater(this::applyTableStyling));

		// Abilitazione pulsanti in base alla selezione/ownership
		table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, sel) -> {
			boolean has = sel != null;
			boolean own = isOwnedByLoggedChef(sel);
			btnEdit.setDisable(!has || !own);
			btnDelete.setDisable(!has || !own);
			btnAssocRicette.setDisable(!has || !own);
		});

		// Doppio click: anteprima sessioni
		table.setRowFactory(tv -> {
			TableRow<Corso> row = new TableRow<>() {
				@Override
				protected void updateItem(Corso item, boolean empty) {
					super.updateItem(item, empty);
					applyRowStyle(this, false, isSelected());
				}
			};

			// Se il mouse entra: seleziona la riga e colora in verde
			row.setOnMouseEntered(e -> {
				if (!row.isEmpty()) {
					table.getSelectionModel().select(row.getIndex());
					applyRowStyle(row, true, true);
				}
			});
			row.setOnMouseExited(e -> applyRowStyle(row, false, row.isSelected()));
			row.hoverProperty().addListener((o, w, h) -> applyRowStyle(row, h, row.isSelected()));
			row.selectedProperty().addListener((o, w, s) -> applyRowStyle(row, row.isHover(), s));

			row.setOnMouseClicked(e -> {
				if (!row.isEmpty() && e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
					openSessioniPreview(row.getItem());
				}
			});
			return row;
		});

		// Azioni
		btnRefresh.setOnAction(e -> reload());
		btnReport.setOnAction(e -> openReportMode());
		btnNew.setOnAction(e -> onNew());
		btnEdit.setOnAction(e -> onEdit());
		btnDelete.setOnAction(e -> onDelete());
		btnAssocRicette.setOnAction(e -> onAssociateRecipes());

		// Badge iniziale
		updateFiltersUI();

		// Min size finestra
		Platform.runLater(() -> {
			if (table == null || table.getScene() == null)
				return;
			Stage stage = (Stage) table.getScene().getWindow();
			if (stage != null) {
				stage.setMinWidth(1000);
				stage.setMinHeight(600);
				stage.setWidth(1200);
				stage.setHeight(800);
				stage.centerOnScreen();
			}
		});
	}

	/* ================== TABELLA ================== */
	private void initTableColumns() {
		colId.setCellValueFactory(new PropertyValueFactory<>("idCorso"));
		colArg.setCellValueFactory(new PropertyValueFactory<>("argomento"));
		colFreq.setCellValueFactory(new PropertyValueFactory<>("frequenza"));

		colInizio.setCellValueFactory(new PropertyValueFactory<>("dataInizio"));
		colInizio.setCellFactory(tc -> new TableCell<Corso, LocalDate>() {
			{
				setAlignment(Pos.CENTER);
			}

			@Override
			protected void updateItem(LocalDate item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : item.toString());
			}
		});

		colFine.setCellValueFactory(new PropertyValueFactory<>("dataFine"));
		colFine.setCellFactory(tc -> new TableCell<Corso, LocalDate>() {
			{
				setAlignment(Pos.CENTER);
			}

			@Override
			protected void updateItem(LocalDate item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : item.toString());
			}
		});

		colChef.setCellValueFactory(cd -> Bindings.createStringBinding(() -> {
			Corso c = cd.getValue();
			if (c == null || c.getChef() == null)
				return "";
			Chef ch = c.getChef();
			String nome = nz(ch.getNome());
			String cogn = nz(ch.getCognome());
			String full = (nome + " " + cogn).trim();
			return full.isEmpty() ? nz(ch.getCF_Chef()) : full;
		}));

		table.getSortOrder().add(colInizio);
		colInizio.setSortType(TableColumn.SortType.DESCENDING);
	}

	/**
	 * Tema tabella allineato al login (card #242c2f → #20282b, testo #e9f5ec, bordi
	 * soft).
	 */
	private void applyTableStyling() {
		final String BG = "#20282b"; // fondo celle
		final String BG_HDR = "#242c2f"; // header/filler
		final String TXT = "#e9f5ec"; // testo chiaro
		final String GRID = "rgba(255,255,255,0.06)"; // bordi soft

		table.setStyle("-fx-background-color:" + BG + ";" + "-fx-control-inner-background:" + BG + ";"
				+ "-fx-text-background-color:" + TXT + ";" + "-fx-table-cell-border-color:" + GRID + ";"
				+ "-fx-table-header-border-color:" + GRID + ";" + "-fx-selection-bar: transparent;"
				+ "-fx-selection-bar-non-focused: transparent;" + "-fx-background-insets: 0;" + "-fx-padding: 4;");

		Platform.runLater(() -> {
			Node headerRow = table.lookup("TableHeaderRow");
			if (headerRow != null)
				headerRow.setStyle("-fx-background-color:" + BG_HDR + ";");

			Node headerBg = table.lookup(".column-header-background");
			if (headerBg != null)
				headerBg.setStyle("-fx-background-color:" + BG_HDR + ";");

			for (Node n : table.lookupAll(".column-header")) {
				if (n instanceof Region r) {
					r.setStyle("-fx-background-color:" + BG_HDR + ";" + "-fx-background-insets: 0;"
							+ "-fx-border-color: " + GRID + ";" + "-fx-border-width: 0 0 1 0;");
				}
				Node lab = n.lookup(".label");
				if (lab instanceof Label lbl) {
					lbl.setTextFill(javafx.scene.paint.Color.web(TXT));
					lbl.setStyle("-fx-font-weight:700;");
				}
			}

			Node filler = table.lookup(".filler");
			if (filler != null)
				filler.setStyle("-fx-background-color:" + BG_HDR + ";");

			Node menuBtn = table.lookup(".show-hide-columns-button");
			if (menuBtn != null) {
				menuBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
				menuBtn.setOpacity(0.0);
				menuBtn.setMouseTransparent(true);
			}
		});
	}

	/* ================== CARICAMENTO ================== */
	private void reload() {
		if (corsoDao == null)
			return;
		try {
			List<Corso> list = corsoDao.findAll();
			if (list == null)
				list = Collections.emptyList();
			backing.setAll(list);
			refilter();
			updateFiltersUI();
		} catch (Exception ex) {
			showError("Errore caricamento corsi: " + ex.getMessage());
		}
	}

	/* ================== FILTRI ================== */
	private void refilter() {
		filtered.setPredicate(c -> {
			if (c == null)
				return false;

			if (!matchesEqIgnoreCase(c.getArgomento(), filtroArg))
				return false;
			if (!matchesContainsIgnoreCase(c.getFrequenza(), filtroFreq))
				return false;

			if (!isBlank(filtroChef)) {
				String chefLabel = chefLabelOf(c.getChef());
				if (!matchesEqIgnoreCase(chefLabel, filtroChef))
					return false;
			}

			if (!isBlank(filtroId)) {
				String idS = String.valueOf(c.getIdCorso());
				if (!idS.equals(filtroId))
					return false;
			}

			LocalDate din = c.getDataInizio();
			LocalDate dfi = c.getDataFine();

			if (dateFrom != null) {
				if (dfi != null && dfi.isBefore(dateFrom))
					return false;
				if (dfi == null && din != null && din.isBefore(dateFrom))
					return false;
			}
			if (dateTo != null) {
				if (din != null && din.isAfter(dateTo))
					return false;
			}

			return true;
		});
	}

	private void clearAllFilters() {
		filtroArg = filtroFreq = filtroChef = filtroId = null;
		dateFrom = dateTo = null;
		refilter();
		updateFiltersUI();
	}

	private void updateFiltersUI() {
		updateFiltersBadge();
		styleFilterMenuButton();
		updatePrettyFilterRows();
	}

	private void updateFiltersBadge() {
		StringBuilder sb = new StringBuilder();
		appendIf(sb, !isBlank(filtroArg), "Arg=" + filtroArg);
		appendIf(sb, !isBlank(filtroFreq), "Freq=" + filtroFreq);
		appendIf(sb, !isBlank(filtroChef), "Chef=" + filtroChef);
		appendIf(sb, !isBlank(filtroId), "ID=" + filtroId);
		if (dateFrom != null || dateTo != null)
			appendIf(sb, true, formatDateRange(dateFrom, dateTo));

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

	private String formatDateRange(LocalDate from, LocalDate to) {
		DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM");
		String l = (from != null) ? df.format(from) : "…";
		String r = (to != null) ? df.format(to) : "…";
		return l + "–" + r;
	}

	private String ellipsize(String s, int maxLen) {
		if (s == null || s.length() <= maxLen)
			return s;
		int take = Math.max(0, maxLen - 1);
		return s.substring(0, take) + "…";
	}

	private List<String> distinctArgomenti() {
		Set<String> s = new HashSet<>();
		for (Corso c : backing) {
			String a = (c != null) ? c.getArgomento() : null;
			if (a != null && !a.trim().isEmpty())
				s.add(a.trim());
		}
		return sortedWithAllOption(s);
	}

	private List<String> distinctFrequenze() {
		Set<String> s = new HashSet<>();
		for (Corso c : backing) {
			String f = (c != null) ? c.getFrequenza() : null;
			if (f != null && !f.trim().isEmpty())
				s.add(f.trim());
		}
		return sortedWithAllOption(s);
	}

	private List<String> distinctChefLabels() {
		Set<String> s = new HashSet<>();
		for (Corso c : backing)
			s.add(chefLabelOf(c != null ? c.getChef() : null));
		s.remove("");
		return sortedWithAllOption(s);
	}

	private List<String> distinctIdLabels() {
		List<String> ids = new ArrayList<>();
		for (Corso c : backing)
			if (c != null)
				ids.add(String.valueOf(c.getIdCorso()));
		ids.sort((a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)));
		ids.add(0, ALL_OPTION);
		return ids;
	}

	private List<String> sortedWithAllOption(Set<String> set) {
		List<String> out = new ArrayList<>(set);
		out.sort(String.CASE_INSENSITIVE_ORDER);
		out.add(0, ALL_OPTION);
		return out;
	}

	private String chefLabelOf(Chef ch) {
		if (ch == null)
			return "";
		String full = (nz(ch.getNome()) + " " + nz(ch.getCognome())).trim();
		return full.isEmpty() ? nz(ch.getCF_Chef()) : full;
	}

	private String askChoice(String title, String header, List<String> options, String preselect) {
		if (options == null || options.isEmpty()) {
			showInfo("Nessuna opzione disponibile.");
			return null;
		}
		String def = options.get(0);
		if (!isBlank(preselect)) {
			for (String opt : options)
				if (opt.equalsIgnoreCase(preselect)) {
					def = opt;
					break;
				}
		}
		ChoiceDialog<String> d = new ChoiceDialog<>(def, options);
		d.setTitle(title);
		d.setHeaderText(header);
		d.setContentText(null);
		Optional<String> res = d.showAndWait();
		return res.orElse(null);
	}

	private String normalizeAllToNull(String value) {
		if (value == null)
			return null;
		return ALL_OPTION.equalsIgnoreCase(value.trim()) ? null : value.trim();
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
			loader.setControllerFactory(type -> {
				if (type == ReportController.class)
					return new ReportController(cf);
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

	/* ================== CRUD/ASSOCIA ================== */
	private void onEdit() {
		final Corso sel = table.getSelectionModel().getSelectedItem();
		if (sel == null)
			return;

		if (!isOwnedByLoggedChef(sel)) {
			new Alert(Alert.AlertType.WARNING, "Puoi modificare solo i corsi che appartengono al tuo profilo.")
					.showAndWait();
			return;
		}

		final javafx.concurrent.Task<List<Sessione>> loadTask = new javafx.concurrent.Task<>() {
			@Override
			protected List<Sessione> call() throws Exception {
				return sessioneDao.findByCorso(sel.getIdCorso());
			}
		};

		loadTask.setOnSucceeded(ev -> {
			List<Sessione> esistenti = loadTask.getValue();
			try {
				FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
				DialogPane pane = fx.load();
				SessioniWizardController ctrl = fx.getController();
				ctrl.initWithCorsoAndExisting(sel, esistenti != null ? esistenti : Collections.emptyList());

				if (pane.getButtonTypes().isEmpty())
					pane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

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
				dlg.setTitle("Modifica sessioni - " + safe(sel.getArgomento()));
				dlg.setDialogPane(pane);
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

				dlg.setResultConverter(
						bt -> (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) ? ctrl.buildResult()
								: null);

				Optional<List<Sessione>> res = dlg.showAndWait();
				if (res.isPresent() && res.get() != null) {
					final List<Sessione> result = res.get();
					final javafx.concurrent.Task<Void> replaceTask = new javafx.concurrent.Task<>() {
						@Override
						protected Void call() throws Exception {
							sessioneDao.replaceForCorso(sel.getIdCorso(), result);
							return null;
						}
					};
					replaceTask.setOnSucceeded(ev2 -> showInfo("Sessioni aggiornate."));
					replaceTask.setOnFailed(ev2 -> {
						Throwable ex2 = replaceTask.getException();
						showError("Errore salvataggio sessioni: " + (ex2 != null ? ex2.getMessage() : "sconosciuto"));
						if (ex2 != null)
							ex2.printStackTrace();
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
			if (ex != null)
				ex.printStackTrace();
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
				if (bt == null)
					return null;
				return (bt == ctrl.getCreateButtonType()) ? ctrl.getResult() : null;
			});

			Optional<Corso> res = dialog.showAndWait();
			if (!res.isPresent())
				return;

			Corso nuovo = res.get();

			if (corsoDao != null) {
				String cfChef = corsoDao.getOwnerCfChef();
				if (cfChef != null && !cfChef.trim().isEmpty()) {
					Chef chef = new Chef();
					chef.setCF_Chef(cfChef);
					nuovo.setChef(chef);
				}
			}

			Optional<List<Sessione>> sessOpt = openSessioniWizard(nuovo);
			if (!sessOpt.isPresent())
				return;

			List<Sessione> sessions = sessOpt.get();
			if (sessions == null || sessions.isEmpty())
				return;

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
		if (sel == null)
			return;

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

	/* ================== UI: Menu Filtri (Date inline + '×') ================== */

	private void buildAndAttachFiltersContextMenu() {
		filtersMenu = new ContextMenu();
		filtersMenu.setStyle("-fx-background-color: linear-gradient(to bottom,#262f3c,#212938);"
				+ "-fx-background-insets: 0;" + "-fx-background-radius: 14;" + "-fx-border-color: #3a4657;"
				+ "-fx-border-radius: 14;" + "-fx-border-width: 1;"
				+ "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 18, 0.2, 0, 6);");

		// Date
		CustomMenuItem rowFrom = createDateRow("Inizio da", true);
		CustomMenuItem rowTo = createDateRow("Fine fino a", false);

		// Altri filtri
		CustomMenuItem rowArg = createFilterRow("Argomento", "(tutte)", iconPathList(), e -> {
			String scelto = askChoice("Filtro Argomento", "Seleziona argomento", distinctArgomenti(), filtroArg);
			filtroArg = normalizeAllToNull(scelto);
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, () -> {
			filtroArg = null;
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, b -> btnClearArg = b, l -> labArgomentoVal = l);

		CustomMenuItem rowFreq = createFilterRow("Frequenza", "(tutte)", iconPathCalendar(), e -> {
			String scelto = askChoice("Filtro Frequenza", "Seleziona frequenza", distinctFrequenze(), filtroFreq);
			filtroFreq = normalizeAllToNull(scelto);
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, () -> {
			filtroFreq = null;
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, b -> btnClearFreq = b, l -> labFrequenzaVal = l);

		CustomMenuItem rowChef = createFilterRow("Chef", "(tutte)", iconPathChefHat(), e -> {
			String scelto = askChoice("Filtro Chef", "Seleziona Chef", distinctChefLabels(), filtroChef);
			filtroChef = normalizeAllToNull(scelto);
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, () -> {
			filtroChef = null;
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, b -> btnClearChef = b, l -> labChefVal = l);

		CustomMenuItem rowId = createFilterRow("ID", "(tutte)", iconPathId(), e -> {
			String scelto = askChoice("Filtro ID", "Seleziona ID corso", distinctIdLabels(), filtroId);
			filtroId = normalizeAllToNull(scelto);
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, () -> {
			filtroId = null;
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, b -> btnClearId = b, l -> labIdVal = l);

		CustomMenuItem sep1 = separatorItem();
		CustomMenuItem sep2 = separatorItem();

		MenuItem clearAll = new MenuItem("Pulisci tutti i filtri");
		clearAll.setStyle("-fx-text-fill:#ffadb3; -fx-font-weight:bold;");
		clearAll.setOnAction(e -> {
			clearAllFilters();
			updatePrettyFilterRows();
		});

		filtersMenu.getItems().setAll(rowFrom, rowTo, sep1, rowArg, rowFreq, rowChef, rowId, sep2, clearAll);

		// Collega al MenuButton (disabilita il popup nativo)
		if (btnFilters != null) {
			btnFilters.getItems().clear();

			btnFilters.setOnMousePressed(e -> {
				if (filtersMenu.isShowing())
					filtersMenu.hide();
				filtersMenu.show(btnFilters, javafx.geometry.Side.BOTTOM, 0, 6);
				e.consume();
			});

			btnFilters.setOnKeyPressed(ke -> {
				switch (ke.getCode()) {
				case SPACE:
				case ENTER:
					if (filtersMenu.isShowing())
						filtersMenu.hide();
					filtersMenu.show(btnFilters, javafx.geometry.Side.BOTTOM, 0, 6);
					ke.consume();
					break;
				default:
					break;
				}
			});
		}
	}

	/**
	 * Riga compatta: icona + titolo + valore + '×' con hover molto visibile (barra
	 * verde).
	 */
	private CustomMenuItem createFilterRow(String title, String value, String svgPath,
			EventHandler<ActionEvent> onChooseClick, Runnable onClearClick,
			java.util.function.Consumer<Button> clearButtonSink, java.util.function.Consumer<Label> valueLabelSink) {

		final String ACCENT = "#1fb57a";

		javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
		svg.setContent(svgPath);
		svg.setStyle("-fx-fill:#93a5be; -fx-min-width:16; -fx-min-height:16; -fx-max-width:16; -fx-max-height:16;");

		Label labTitle = new Label(title + ":");
		labTitle.setStyle("-fx-text-fill:#9fb6aa; -fx-font-size:12.5px;");

		Label labVal = new Label(value);
		labVal.setStyle("-fx-text-fill:#e9f5ec; -fx-font-size:13.5px; -fx-font-weight:bold;");

		Region spacer = new Region();
		HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

		Button btnX = new Button("×");
		btnX.setFocusTraversable(false);
		btnX.setOnAction(e -> {
			if (onClearClick != null)
				onClearClick.run();
		});
		btnX.setStyle(
				"-fx-background-color: transparent; -fx-text-fill:#e9f5ec; -fx-font-size:14px; -fx-padding:0 6 0 6;");
		btnX.setTooltip(new Tooltip("Rimuovi filtro " + title.toLowerCase()));

		HBox box = new HBox(10, svg, labTitle, labVal, spacer, btnX);
		box.setAlignment(Pos.CENTER_LEFT);
		box.setPadding(new Insets(8, 12, 8, 12));
		box.setStyle("-fx-background-color: transparent; -fx-background-radius:10;");
		box.setMinWidth(300);
		box.setPrefWidth(320);

		CustomMenuItem row = new CustomMenuItem(box, false); // non chiude il menu

		// click sulla riga apre il chooser (non sulla '×')
		box.setOnMouseClicked(e -> {
			if (e.getTarget() instanceof Button)
				return;
			if (onChooseClick != null)
				onChooseClick.handle(new ActionEvent());
		});

		// hover forte + barra verde
		box.setOnMouseEntered(e -> {
		    box.setStyle(
		        "-fx-background-color: " +
		            "linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT + " 3px, transparent 3px), " +
		            "rgba(255,255,255,0.10);" +
		        "-fx-background-radius:10;"
		    );
		    labTitle.setStyle("-fx-text-fill:#cfe5d9; -fx-font-size:12.5px;");
		});

		box.setOnMouseExited(e -> {
			box.setStyle("-fx-background-color: transparent; -fx-background-radius:10;");
			labTitle.setStyle("-fx-text-fill:#9fb6aa; -fx-font-size:12.5px;");
		});

		if (clearButtonSink != null)
			clearButtonSink.accept(btnX);
		if (valueLabelSink != null)
			valueLabelSink.accept(labVal);

		return row;
	}

	/**
	 * Riga con DatePicker scuro inline. isFrom=true => dateFrom, false => dateTo.
	 */
	private CustomMenuItem createDateRow(String title, boolean isFrom) {
		final String ACCENT = "#1fb57a";

		javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
		svg.setContent(iconPathCalendar());
		svg.setStyle("-fx-fill:#93a5be; -fx-min-width:16; -fx-min-height:16; -fx-max-width:16; -fx-max-height:16;");

		Label labTitle = new Label(title + ":");
		labTitle.setStyle("-fx-text-fill:#9fb6aa; -fx-font-size:12.5px;");

		DatePicker dp = new DatePicker(isFrom ? dateFrom : dateTo);
		dp.setShowWeekNumbers(false);
		styleDatePickerInline(dp);
		dp.setPromptText("gg/mm/aaaa");

		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		dp.setConverter(new StringConverter<LocalDate>() {
			@Override
			public String toString(LocalDate d) {
				return d == null ? "" : fmt.format(d);
			}

			@Override
			public LocalDate fromString(String s) {
				return (s == null || s.trim().isEmpty()) ? null : LocalDate.parse(s, fmt);
			}
		});

		Button clear = new Button("×");
		clear.setOnAction(e -> dp.setValue(null));
		clear.setStyle(
				"-fx-background-color: transparent; -fx-text-fill:#e9f5ec; -fx-font-size:14px; -fx-padding:0 6 0 6;");
		clear.setFocusTraversable(false);
		boolean hasValue = dp.getValue() != null;
		clear.setVisible(hasValue);
		clear.setManaged(hasValue);

		dp.valueProperty().addListener((obs, oldV, newV) -> {
			if (isFrom)
				dateFrom = newV;
			else
				dateTo = newV;
			boolean on = newV != null;
			clear.setVisible(on);
			clear.setManaged(on);
			refilter();
			updateFiltersUI();
		});

		HBox box = new HBox(10, svg, labTitle, dp, clear);
		box.setAlignment(Pos.CENTER_LEFT);
		box.setPadding(new Insets(8, 12, 8, 12));
		box.setStyle("-fx-background-color: transparent; -fx-background-radius:10;");
		box.setMinWidth(320);
		box.setPrefWidth(320);

		CustomMenuItem item = new CustomMenuItem(box, false);
		box.setOnMouseEntered(e -> {
		    box.setStyle(
		        "-fx-background-color: " +
		            "linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT + " 3px, transparent 3px), " +
		            "rgba(255,255,255,0.06);" +
		        "-fx-background-radius:10;"
		    );
		    labTitle.setStyle("-fx-text-fill:#cfe5d9; -fx-font-size:12.5px;");
		});

		box.setOnMouseExited(e -> {
			box.setStyle("-fx-background-color: transparent; -fx-background-radius:10;");
			labTitle.setStyle("-fx-text-fill:#9fb6aa; -fx-font-size:12.5px;");
		});

		return item;
	}

	/**
	 * Stile scuro essenziale per DatePicker dentro il menu (niente CSS esterno).
	 */
	private void styleDatePickerInline(DatePicker dp) {
		if (dp == null)
			return;
		final String TEXT_LIGHT = "#e5e7eb";
		dp.setEditable(false);
		dp.setStyle("-fx-background-color: #2e3845; -fx-background-radius:8; -fx-text-fill:" + TEXT_LIGHT + ";"
				+ "-fx-control-inner-background: #2e3845; -fx-prompt-text-fill: rgba(255,255,255,0.6);"
				+ "-fx-border-color: #3a4657; -fx-border-radius:8; -fx-padding: 2 8 2 8;");
		if (dp.getEditor() != null) {
			dp.getEditor().setStyle("-fx-background-color: transparent; -fx-text-fill:" + TEXT_LIGHT + ";");
		}
	}

	private CustomMenuItem separatorItem() {
		CustomMenuItem sep = new CustomMenuItem();
		Region line = new Region();
		line.setMinHeight(1);
		line.setPrefHeight(1);
		line.setMaxHeight(1);
		line.setStyle("-fx-background-color:#3a4657;");
		sep.setContent(line);
		sep.setHideOnClick(false);
		return sep;
	}

	private void updatePrettyFilterRows() {
		if (labArgomentoVal != null)
			labArgomentoVal.setText(isBlank(filtroArg) ? "(tutte)" : filtroArg);
		if (labFrequenzaVal != null)
			labFrequenzaVal.setText(isBlank(filtroFreq) ? "(tutte)" : filtroFreq);
		if (labChefVal != null)
			labChefVal.setText(isBlank(filtroChef) ? "(tutte)" : filtroChef);
		if (labIdVal != null)
			labIdVal.setText(isBlank(filtroId) ? "(tutte)" : filtroId);

		if (btnClearArg != null) {
			boolean on = !isBlank(filtroArg);
			btnClearArg.setVisible(on);
			btnClearArg.setManaged(on);
		}
		if (btnClearFreq != null) {
			boolean on = !isBlank(filtroFreq);
			btnClearFreq.setVisible(on);
			btnClearFreq.setManaged(on);
		}
		if (btnClearChef != null) {
			boolean on = !isBlank(filtroChef);
			btnClearChef.setVisible(on);
			btnClearChef.setManaged(on);
		}
		if (btnClearId != null) {
			boolean on = !isBlank(filtroId);
			btnClearId.setVisible(on);
			btnClearId.setManaged(on);
		}
	}

	/* ================== Helpers vari ================== */

	private static void applyRowStyle(TableRow<Corso> row, boolean hovered, boolean selected) {
		final String ACCENT = "#1fb57a"; // barra verde
		final String SELECT_BG = "rgba(31,181,122,0.15)"; // verde leggero
		final String HOVER_BG = "rgba(255,255,255,0.10)";
		final String ZEBRA_BG = "rgba(255,255,255,0.03)";
		final String CLEAR = "transparent";

		if (row == null || row.isEmpty() || row.getItem() == null) {
			row.setStyle("");
			row.setCursor(Cursor.DEFAULT);
			return;
		}

		String base = (row.getIndex() % 2 == 0) ? ZEBRA_BG : CLEAR;

		if (selected) {
			row.setStyle(
				    "-fx-background-color: " +
				        "linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT + " 4px, transparent 4px), " +
				        SELECT_BG + ";"
				);
			row.setCursor(Cursor.HAND);
		} else if (hovered) {
			row.setStyle(
				    "-fx-background-color: " +
				        "linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT + " 4px, transparent 4px), " +
				        HOVER_BG + ";"
				);
			row.setCursor(Cursor.HAND);
		} else {
			row.setStyle("-fx-background-color: " + base + ";");
			row.setCursor(Cursor.DEFAULT);
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	private static String nz(String s) {
		return (s == null) ? "" : s;
	}

	private static String nz(int n) {
		return (n > 0) ? String.valueOf(n) : "";
	}

	private static String nz(long n) {
		return (n > 0L) ? String.valueOf(n) : "";
	}

	private static String joinNonEmpty(String sep, String... parts) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		if (parts != null) {
			for (String p : parts) {
				if (p != null && !p.trim().isEmpty()) {
					if (!first)
						sb.append(sep);
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
		if (c == null || c.getChef() == null || isBlank(c.getChef().getCF_Chef()))
			return false;
		String owner = (corsoDao != null) ? corsoDao.getOwnerCfChef() : null;
		return !isBlank(owner) && c.getChef().getCF_Chef().equalsIgnoreCase(owner);
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static boolean matchesEqIgnoreCase(String value, String filter) {
		if (isBlank(filter))
			return true;
		return value != null && value.equalsIgnoreCase(filter);
	}

	private static boolean matchesContainsIgnoreCase(String value, String filter) {
		if (isBlank(filter))
			return true;
		return value != null && value.toLowerCase().contains(filter.toLowerCase());
	}

	/** Colori e freccetta del bottone "Filtri" */
	private void styleFilterMenuButton() {
		final String BG = "#2b3438";
		final String TXT = "#e9f5ec";
		if (btnFilters == null)
			return;

		btnFilters.setStyle("-fx-background-color:" + BG + ";" + "-fx-text-fill:" + TXT + ";"
				+ "-fx-background-radius: 10;" + "-fx-padding: 8 10;");

		Platform.runLater(() -> {
			Node label = btnFilters.lookup(".label");
			if (label instanceof Label lbl) {
				lbl.setTextFill(javafx.scene.paint.Color.web(TXT));
				lbl.setStyle("-fx-font-weight: 700;");
			}
			Node arrow = btnFilters.lookup(".arrow");
			if (arrow != null) {
				arrow.setStyle("-fx-background-color: " + TXT + "; -fx-padding: 0.0;");
				arrow.setOpacity(0.75);
			}
		});
	}

	private void hookFilterMenuButtonStyling() {
		if (btnFilters == null)
			return;
		btnFilters.skinProperty().addListener((o, oldSkin, newSkin) -> Platform.runLater(this::styleFilterMenuButton));
		btnFilters.textProperty().addListener((o, oldText, newText) -> Platform.runLater(this::styleFilterMenuButton));
	}

	/* ================== DAO Setter ================== */
	public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao) {
		this.corsoDao = corsoDao;
		this.sessioneDao = sessioneDao;
		if (this.ricettaDao == null)
			this.ricettaDao = new RicettaDao();
		reload();
	}

	public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao, RicettaDao ricettaDao) {
		this.corsoDao = corsoDao;
		this.sessioneDao = sessioneDao;
		this.ricettaDao = ricettaDao;
		reload();
	}

	private void openSessioniPreview(Corso corso) {
    if (corso == null) return;
    try {
        FXMLLoader l = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniPreview.fxml"));
        DialogPane pane = l.load();
        SessioniPreviewController ctrl = l.getController();

        long id = corso.getIdCorso();
        List<Sessione> sessions = (sessioneDao != null) ? sessioneDao.findByCorso(id) : java.util.Collections.<Sessione>emptyList();

        // >>> PASSO ANCHE sessioneDao <<<
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


	/* ==================== ASSOCIAZIONE RICETTE ==================== */
	private void onAssociateRecipes() {
		Corso sel = table.getSelectionModel().getSelectedItem();
		if (sel == null)
			return;

		if (!isOwnedByLoggedChef(sel)) {
			showInfo("Puoi modificare solo i corsi del tuo profilo.");
			return;
		}

		try {
			List<Sessione> tutte = sessioneDao.findByCorso(sel.getIdCorso());
			List<SessionePresenza> presenze = new ArrayList<>();
			for (Sessione s : tutte)
				if (s instanceof SessionePresenza sp)
					presenze.add(sp);

			if (presenze.isEmpty()) {
				showInfo("Il corso non ha sessioni in presenza.");
				return;
			}

			SessionePresenza target = (presenze.size() == 1) ? presenze.get(0) : choosePresenza(presenze).orElse(null);
			if (target == null)
				return;

			if (ricettaDao == null)
				ricettaDao = new RicettaDao();
			List<Ricetta> tutteLeRicette = ricettaDao.findAll();
			List<Ricetta> associate = sessioneDao.findRicetteBySessionePresenza(target.getId());

			URL url = AssociaRicetteController.class.getResource("/it/unina/foodlab/ui/AssociaRicette.fxml");
			if (url == null)
				throw new IllegalStateException("FXML non trovato: /AssociaRicette.fxml");

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
			dlg.salvaSeConfermato(result);

		} catch (Exception e) {
			showError("Errore associazione ricette: " + e.getMessage());
			e.printStackTrace();
		}
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
			Dialog<List<Sessione>> dlg = new Dialog<>();
			dlg.setTitle("Configura sessioni del corso");
			dlg.setDialogPane(pane);
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
			dlg.setResultConverter(
					bt -> (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) ? ctrl.buildResult()
							: null);
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
			String orari = (sp.getOraInizio() != null ? tf.format(sp.getOraInizio()) : "") + "-"
					+ (sp.getOraFine() != null ? tf.format(sp.getOraFine()) : "");
			String indirizzo = joinNonEmpty(" ", nz(sp.getVia()), nz(sp.getNum()), nz(sp.getCap())).trim();
			String base = (data + " " + orari + " " + indirizzo).trim();
			String key = base;
			int suffix = 2;
			while (map.containsKey(key))
				key = base + " (" + (suffix++) + ")";
			map.put(key, sp);
		}
		ChoiceDialog<String> d = new ChoiceDialog<>(firstKeyOr(map, ""), map.keySet());
		d.setTitle("Seleziona la sessione in presenza");
		d.setHeaderText("Scegli la sessione a cui associare le ricette");
		d.setContentText(null);
		Optional<String> pick = d.showAndWait();
		return pick.map(map::get);
	}

	/* ================== Utility ================== */
	private static void appendIf(StringBuilder sb, boolean cond, String piece) {
		if (!cond)
			return;
		if (sb.length() > 0)
			sb.append(", ");
		sb.append(piece);
	}

	private static String firstKeyOr(Map<String, SessionePresenza> map, String fallback) {
		for (String k : map.keySet())
			return k;
		return fallback;
	}
	
	private String iconPathList()     { return "M3 5h14v2H3V5zm0 4h14v2H3V9zm0 4h10v2H3v-2z"; }
	private String iconPathCalendar() { return "M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 14H5V8h14v9z"; }
	private String iconPathChefHat()  { return "M4 10c0-2.2 1.8-4 4-4 .5 0 .9.1 1.3.3C9.9 5.5 10.9 5 12 5c1.7 0 3.1.8 4 2.1.6-.1 1.3-.1 2-.1 2.2 0 4 1.8 4 4v1H4v-2z M4 14h18v2c0 1.1-.9 2-2 2H6c-1.1 0-2-.9-2-2v-2z"; }
	private String iconPathId()       { return "M3 3h18v4H3V3zm0 6h18v12H3V9zm4 2v8h10v-8H7z"; }


}
