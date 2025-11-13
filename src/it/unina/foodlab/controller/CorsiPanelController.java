package it.unina.foodlab.controller;

import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.RicettaDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessionePresenza;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
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
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CorsiPanelController {

	private static final String ALL_OPTION = "Tutte";
	private static final int FILTERS_BADGE_MAX_CHARS = 32;

	private final ObservableList<String> argomentiCondivisi = FXCollections.observableArrayList();

	@FXML private MenuButton btnFilters;
	@FXML private Button btnRefresh, btnReport;
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
	private String filtroFreq = null;
	private String filtroChef = null;
	private String filtroId = null;
	private String filtroStato = null;
	private LocalDate dateFrom = null;
	private LocalDate dateTo = null;

	private ContextMenu filtersMenu;
	private Label labArgomentoVal, labFrequenzaVal, labChefVal, labIdVal, labStatoVal;
	private Button btnClearArg, btnClearFreq, btnClearChef, btnClearId, btnClearStato;

	private boolean comfortable = true;
	private static final double ROW_HEIGHT_COMFORT = 56;
	private static final double ROW_HEIGHT_COMPACT = 36;

	@FXML
	private void initialize() {
		table.getStyleClass().add("dark-table");
		btnFilters.getStyleClass().add("dark-menubutton");

		initTableColumns();
		table.setItems(sorted);
		sorted.comparatorProperty().bind(table.comparatorProperty());

		table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		table.getSelectionModel().setCellSelectionEnabled(false);

		applyComfortMetrics();
		installRowFactory();

		table.skinProperty().addListener((obs, o, n) -> Platform.runLater(this::refreshTitleWithCount));
		table.getColumns().addListener((javafx.collections.ListChangeListener<? super TableColumn<Corso, ?>>) c -> Platform.runLater(this::refreshTitleWithCount));
		table.widthProperty().addListener((o, a, b) -> Platform.runLater(this::refreshTitleWithCount));

		table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, sel) -> {
			boolean has = sel != null;
			boolean own = isOwnedByLoggedChef(sel);
			btnEdit.setDisable(!has || !own);
			btnDelete.setDisable(!has || !own);
			btnAssocRicette.setDisable(!has || !own);
		});

		buildAndAttachFiltersContextMenu();
		updatePrettyFilterRows();
		updateFiltersUI();

		btnRefresh.setOnAction(e -> {
			playRefreshAnimation(btnRefresh);
			reload();
		});
		btnReport.setOnAction(e -> openReportMode());
		btnNew.setOnAction(e -> onNew());
		btnEdit.setOnAction(e -> onEdit());
		btnDelete.setOnAction(e -> onDelete());
		btnAssocRicette.setOnAction(e -> onAssociateRecipes());

		table.getSortOrder().add(colInizio);
		colInizio.setSortType(TableColumn.SortType.DESCENDING);

		installPrettyPlaceholder();

		Platform.runLater(() -> {
			if (table == null || table.getScene() == null) return;
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

	private final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

		Platform.runLater(() -> {
			table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
			colId.setMaxWidth(80);
			colId.setMinWidth(70);
			colArg.setPrefWidth(260);
			colFreq.setPrefWidth(140);
			colStato.setPrefWidth(120);
			colInizio.setPrefWidth(130);
			colFine.setPrefWidth(130);
			colChef.setPrefWidth(220);
		});

		colInizio.setSortType(TableColumn.SortType.DESCENDING);
	}

	private Label makeCellLabel(boolean bold) {
		Label l = new Label();
		l.setPadding(new Insets(2, 10, 2, 10));
		l.getStyleClass().add(bold ? "cell-label-bold" : "cell-label");
		l.setMaxWidth(Double.MAX_VALUE);
		return l;
	}

	private void applyComfortMetrics() {
		double h = comfortable ? ROW_HEIGHT_COMFORT : ROW_HEIGHT_COMPACT;
		table.setFixedCellSize(h);
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

	private void installPrettyPlaceholder() {
		Label ph = new Label("Non hai ancora corsi.\nCrea il primo corso per iniziare a pianificare le sessioni.");
		ph.setAlignment(Pos.CENTER);
		table.setPlaceholder(ph);
	}

	private void refreshTitleWithCount() {
		if (table == null) return;
		if (table.getScene() == null) {
			Platform.runLater(this::refreshTitleWithCount);
			return;
		}
		if (table.getScene().getWindow() instanceof Stage st) {
			st.setTitle("Corsi • " + filtered.size() + " elementi");
		}
	}

	public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao, RicettaDao ricettaDao) {
		this.corsoDao = corsoDao;
		this.sessioneDao = sessioneDao;
		this.ricettaDao = ricettaDao;
		if (table == null || table.getScene() == null) {
			table.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene != null) Platform.runLater(this::reload);
			});
		} else {
			reload();
		}
	}

	public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao) {
		this.corsoDao = corsoDao;
		this.sessioneDao = sessioneDao;
		if (table == null || table.getScene() == null) {
			table.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene != null) Platform.runLater(this::reload);
			});
		} else {
			reload();
		}
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
			updateFiltersUI();
			refreshTitleWithCount();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void playRefreshAnimation(Button btn) {
		if (btn == null) return;
		RotateTransition rt = new RotateTransition(Duration.millis(600), btn);
		rt.setFromAngle(0);
		rt.setToAngle(360);
		rt.setCycleCount(1);
		rt.setAutoReverse(false);
		rt.play();
	}

	private void installRowFactory() {
		table.setRowFactory(tv -> {
			TableRow<Corso> row = new TableRow<>() {
				@Override
				protected void updateItem(Corso item, boolean empty) {
					super.updateItem(item, empty);
				}
			};
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
		filtered.setPredicate(c -> {
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

			if (!isBlank(filtroStato)) {
				String st = statoOf(c);
				if (!matchesEqIgnoreCase(st, filtroStato)) return false;
			}

			LocalDate din = c.getDataInizio();
			LocalDate dfi = c.getDataFine();

			if (dateFrom != null) {
				if (din == null || din.isBefore(dateFrom)) return false;
			}
			if (dateTo != null) {
				if (dfi == null || dfi.isAfter(dateTo)) return false;
			}

			return true;
		});
	}


	private void clearAllFilters() {
		filtroArg = filtroFreq = filtroChef = filtroId = filtroStato = null;
		dateFrom = dateTo = null;

		if (filtersMenu != null) {
			for (MenuItem item : filtersMenu.getItems()) {
				if (item instanceof CustomMenuItem cmi && cmi.getContent() instanceof HBox box) {
					for (Node n : box.getChildren()) {
						if (n instanceof DatePicker dp) dp.setValue(null);
					}
				}
			}
		}

		refilter();
		updateFiltersUI();
	}


	private void updateFiltersUI() {
		updateFiltersBadge();
		updatePrettyFilterRows();
	}

	private void updateFiltersBadge() {
		StringBuilder sb = new StringBuilder();
		appendIf(sb, !isBlank(filtroArg), "Arg=" + filtroArg);
		appendIf(sb, !isBlank(filtroFreq), "Freq=" + filtroFreq);
		appendIf(sb, !isBlank(filtroChef), "Chef=" + filtroChef);
		appendIf(sb, !isBlank(filtroId), "ID=" + filtroId);
		appendIf(sb, !isBlank(filtroStato), "Stato=" + filtroStato);
		if (dateFrom != null || dateTo != null) appendIf(sb, true, formatDateRange(dateFrom, dateTo));
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
		if (s == null || s.length() <= maxLen) return s;
		int take = Math.max(0, maxLen - 1);
		return s.substring(0, take) + "…";
	}

	private List<String> distinctArgomenti() {
		Set<String> s = new HashSet<>();
		for (Corso c : backing) {
			String a = (c != null) ? c.getArgomento() : null;
			if (a != null && !a.trim().isEmpty()) s.add(a.trim());
		}
		return sortedWithAllOption(s);
	}

	private List<String> distinctStati() {
		List<String> s = new ArrayList<>(Arrays.asList("Futuro", "In corso", "Concluso"));
		s.add(0, ALL_OPTION);
		return s;
	}

	private List<String> distinctFrequenze() {
		Set<String> s = new HashSet<>();
		for (Corso c : backing) {
			String f = (c != null) ? c.getFrequenza() : null;
			if (f != null && !f.trim().isEmpty()) s.add(f.trim());
		}
		return sortedWithAllOption(s);
	}

	private List<String> distinctChefLabels() {
		Set<String> s = new HashSet<>();
		for (Corso c : backing) s.add(chefLabelOf(c != null ? c.getChef() : null));
		s.remove("");
		return sortedWithAllOption(s);
	}

	private List<String> distinctIdLabels() {
		List<String> ids = new ArrayList<>();
		for (Corso c : backing) if (c != null) ids.add(String.valueOf(c.getIdCorso()));
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
		if (ch == null) return "";
		String full = (nz(ch.getNome()) + " " + nz(ch.getCognome())).trim();
		return full.isEmpty() ? nz(ch.getCF_Chef()) : full;
	}

	private String askChoice(String title, String header, List<String> options, String preselect) {
		if (options == null || options.isEmpty()) {
			showInfoDark("Nessuna opzione disponibile.");
			return null;
		}
		Dialog<String> dlg = new Dialog<>();
		dlg.setTitle(title);
		dlg.setHeaderText(header);
		ButtonType OK = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
		ButtonType CANCEL = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
		dlg.getDialogPane().getButtonTypes().setAll(OK, CANCEL);
		ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(options));
		cb.setEditable(false);
		cb.getStyleClass().add("dark-combobox");
		String def = options.get(0);
		if (preselect != null) {
			for (String opt : options) {
				if (opt.equalsIgnoreCase(preselect)) {
					def = opt;
					break;
				}
			}
		}
		cb.getSelectionModel().select(def);
		HBox box = new HBox(10, cb);
		box.setPadding(new Insets(6, 0, 0, 0));
		dlg.getDialogPane().setContent(box);
		dlg.getDialogPane().getStyleClass().add("dark-dialog");
		dlg.setResultConverter(bt -> (bt == OK) ? cb.getValue() : null);
		Optional<String> res = dlg.showAndWait();
		return res.orElse(null);
	}

	private String normalizeAllToNull(String value) {
		if (value == null) return null;
		return ALL_OPTION.equalsIgnoreCase(value.trim()) ? null : value.trim();
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
		final Corso sel = table.getSelectionModel().getSelectedItem();
		if (sel == null) return;
		if (!isOwnedByLoggedChef(sel)) {
			new Alert(Alert.AlertType.WARNING, "Puoi modificare solo i corsi che appartengono al tuo profilo.").showAndWait();
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
				dlg.setResultConverter(bt -> (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) ? ctrl.buildResult() : null);
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
					replaceTask.setOnSucceeded(ev2 -> showInfoDark("Sessioni aggiornate."));
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
			if (arg != null && !arg.isBlank() && !argomentiCondivisi.contains(arg)) {
				arg = arg.trim();
				argomentiCondivisi.add(arg);
				FXCollections.sort(argomentiCondivisi);
			}
			updateFiltersUI();
		} catch (Exception ex) {
			showError("Errore apertura/salvataggio corso: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private void onDelete() {
		final Corso sel = table.getSelectionModel().getSelectedItem();
		if (sel == null) return;
		boolean conferma = showConfirmDark("Conferma eliminazione", "Eliminare il corso: " + sel.getArgomento() + " ?");
		if (conferma) {
			try {
				corsoDao.delete(sel.getIdCorso());
				backing.remove(sel);
				updateFiltersUI();
			} catch (Exception ex) {
				showInfoDark("Impossibile eliminare il corso: " + ex.getMessage());
			}
		}
	}

	private void buildAndAttachFiltersContextMenu() {
		filtersMenu = new ContextMenu();
		filtersMenu.getStyleClass().add("filters-menu");

		CustomMenuItem rowFrom = createDateRow("Inizio", true);
		CustomMenuItem rowTo = createDateRow("Fine", false);

		CustomMenuItem rowArg = createFilterRow("Argomento", "(tutte)", iconPathList(), e -> {
			String scelto = askChoice("Filtro Argomento", "Seleziona argomento", distinctArgomenti(), filtroArg);
			filtroArg = normalizeAllToNull(scelto);
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, () -> {
			filtroArg = null;
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, b -> btnClearArg = b, l -> labArgomentoVal = l);

		CustomMenuItem rowFreq = createFilterRow("Frequenza", "(tutte)", iconPathCalendar(), e -> {
			String scelto = askChoice("Filtro Frequenza", "Seleziona frequenza", distinctFrequenze(), filtroFreq);
			filtroFreq = normalizeAllToNull(scelto);
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, () -> {
			filtroFreq = null;
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, b -> btnClearFreq = b, l -> labFrequenzaVal = l);

		CustomMenuItem rowStato = createFilterRow("Stato", "(tutti)", iconPathStatus(), e -> {
			String scelto = askChoice("Filtro Stato", "Seleziona stato", distinctStati(), filtroStato);
			filtroStato = normalizeAllToNull(scelto);
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, () -> {
			filtroStato = null;
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, b -> btnClearStato = b, l -> labStatoVal = l);

		CustomMenuItem rowChef = createFilterRow("Chef", "(tutte)", iconPathChefHat(), e -> {
			String scelto = askChoice("Filtro Chef", "Seleziona Chef", distinctChefLabels(), filtroChef);
			filtroChef = normalizeAllToNull(scelto);
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, () -> {
			filtroChef = null;
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, b -> btnClearChef = b, l -> labChefVal = l);

		CustomMenuItem rowId = createFilterRow("ID", "(tutte)", iconPathId(), e -> {
			String scelto = askChoice("Filtro ID", "Seleziona ID corso", distinctIdLabels(), filtroId);
			filtroId = normalizeAllToNull(scelto);
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, () -> {
			filtroId = null;
			refilter(); updateFiltersUI(); updatePrettyFilterRows();
		}, b -> btnClearId = b, l -> labIdVal = l);

		CustomMenuItem sep1 = separatorItem();
		CustomMenuItem sep2 = separatorItem();
		CustomMenuItem clearBtn = createClearAllButtonItem();

		filtersMenu.getItems().setAll(rowFrom, rowTo, sep1, rowArg, rowFreq, rowStato, rowChef, rowId, sep2, clearBtn);

		if (btnFilters != null) {
			btnFilters.getItems().clear();
			btnFilters.setOnMousePressed(e -> {
				if (filtersMenu.isShowing()) filtersMenu.hide();
				else filtersMenu.show(btnFilters, javafx.geometry.Side.BOTTOM, 0, 6);
				e.consume();
			});
			btnFilters.setOnKeyPressed(ke -> {
				switch (ke.getCode()) {
				case SPACE, ENTER -> {
					if (filtersMenu.isShowing()) filtersMenu.hide();
					else filtersMenu.show(btnFilters, javafx.geometry.Side.BOTTOM, 0, 6);
					ke.consume();
				}
				default -> {}
				}
			});
		}
	}

	private CustomMenuItem createClearAllButtonItem() {
		javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
		svg.setContent("M3 14l6-6 1 1-6 6H3zm7-7l2-2 5 5-2 2-5-5zm7 6l-2 2 2 2h2v-2l-2-2z");
		svg.getStyleClass().add("icon");
		Label text = new Label("Pulisci tutti i filtri");
		HBox btn = new HBox(8, svg, text);
		btn.setAlignment(Pos.CENTER_LEFT);
		btn.setPadding(new Insets(8, 12, 8, 12));
		btn.getStyleClass().add("danger-button");
		btn.setOnMouseClicked(e -> {
			clearAllFilters();
			updatePrettyFilterRows();
			filtersMenu.hide();
		});
		return new CustomMenuItem(btn, false);
	}

	private CustomMenuItem createFilterRow(String title, String value, String svgPath,
			javafx.event.EventHandler<ActionEvent> onChooseClick, Runnable onClearClick,
			java.util.function.Consumer<Button> clearButtonSink, java.util.function.Consumer<Label> valueLabelSink) {
		javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
		svg.setContent(svgPath);
		svg.getStyleClass().add("icon-muted");
		Label labTitle = new Label(title + ":");
		labTitle.getStyleClass().add("filter-row-title");
		Label labVal = new Label(value);
		labVal.getStyleClass().add("filter-row-value");
		Region spacer = new Region();
		HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
		Button btnX = new Button("×");
		btnX.setFocusTraversable(false);
		btnX.setOnAction(e -> {
			if (onClearClick != null) onClearClick.run();
		});
		btnX.getStyleClass().add("clear-btn");
		btnX.setTooltip(new Tooltip("Rimuovi filtro " + title.toLowerCase()));
		HBox box = new HBox(10, svg, labTitle, labVal, spacer, btnX);
		box.setAlignment(Pos.CENTER_LEFT);
		box.setPadding(new Insets(8, 12, 8, 12));
		box.getStyleClass().add("filter-row");
		box.setMinWidth(300);
		box.setPrefWidth(320);
		CustomMenuItem row = new CustomMenuItem(box, false);
		box.setOnMouseClicked(e -> {
			if (e.getTarget() instanceof Button) return;
			if (onChooseClick != null) onChooseClick.handle(new ActionEvent());
		});
		if (clearButtonSink != null) clearButtonSink.accept(btnX);
		if (valueLabelSink != null) valueLabelSink.accept(labVal);
		return row;
	}

	private CustomMenuItem createDateRow(String title, boolean isFrom) {
		javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
		svg.setContent(iconPathCalendar());
		svg.getStyleClass().add("icon-muted");
		Label labTitle = new Label(title + ":");
		labTitle.getStyleClass().add("filter-row-title");
		DatePicker dp = new DatePicker(isFrom ? dateFrom : dateTo);
		dp.setShowWeekNumbers(false);
		dp.getStyleClass().add("dark-datepicker");
		dp.setPromptText("gg/mm/aaaa");
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		dp.setConverter(new StringConverter<>() {
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
		clear.getStyleClass().add("clear-btn");
		clear.setFocusTraversable(false);
		boolean hasValue = dp.getValue() != null;
		clear.setVisible(hasValue);
		clear.setManaged(hasValue);
		dp.valueProperty().addListener((obs, oldV, newV) -> {
			if (isFrom) dateFrom = newV;
			else dateTo = newV;
			boolean on = newV != null;
			clear.setVisible(on);
			clear.setManaged(on);
			refilter();
			updateFiltersUI();
		});
		HBox box = new HBox(10, svg, labTitle, dp, clear);
		box.setAlignment(Pos.CENTER_LEFT);
		box.setPadding(new Insets(8, 12, 8, 12));
		box.getStyleClass().add("filter-row");
		box.setMinWidth(320);
		box.setPrefWidth(320);
		return new CustomMenuItem(box, false);
	}

	private CustomMenuItem separatorItem() {
		CustomMenuItem sep = new CustomMenuItem();
		Region line = new Region();
		line.getStyleClass().add("separator-line");
		sep.setContent(line);
		sep.setHideOnClick(false);
		return sep;
	}

	private void updatePrettyFilterRows() {
		if (labArgomentoVal != null) labArgomentoVal.setText(isBlank(filtroArg) ? "(tutte)" : filtroArg);
		if (labFrequenzaVal != null) labFrequenzaVal.setText(isBlank(filtroFreq) ? "(tutte)" : filtroFreq);
		if (labChefVal != null) labChefVal.setText(isBlank(filtroChef) ? "(tutte)" : filtroChef);
		if (labIdVal != null) labIdVal.setText(isBlank(filtroId) ? "(tutte)" : filtroId);
		if (labStatoVal != null) labStatoVal.setText(isBlank(filtroStato) ? "(tutti)" : filtroStato);

		if (btnClearStato != null) {
			boolean on = !isBlank(filtroStato);
			btnClearStato.setVisible(on);
			btnClearStato.setManaged(on);
		}
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

	private static String nz(String s) {
		return (s == null) ? "" : s;
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static boolean matchesEqIgnoreCase(String value, String filter) {
		if (isBlank(filter)) return true;
		return value != null && value.equalsIgnoreCase(filter);
	}

	private static boolean matchesContainsIgnoreCase(String value, String filter) {
		if (isBlank(filter)) return true;
		return value != null && value.toLowerCase().contains(filter.toLowerCase());
	}

	private void showError(String msg) {
		Alert a = new Alert(Alert.AlertType.ERROR);
		a.setHeaderText("Errore");
		a.setContentText(msg);
		a.getDialogPane().getStyleClass().add("dark-dialog");
		a.showAndWait();
	}

	private boolean isOwnedByLoggedChef(Corso c) {
		if (c == null || c.getChef() == null || isBlank(c.getChef().getCF_Chef())) return false;
		String owner = (corsoDao != null) ? corsoDao.getOwnerCfChef() : null;
		return !isBlank(owner) && c.getChef().getCF_Chef().equalsIgnoreCase(owner);
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
			dlg.salvaSeConfermato(result);
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
		dlg.setResultConverter(bt -> (bt == OK) ? cb.getValue() : null);
		Optional<String> pick = dlg.showAndWait();
		return pick.map(map::get);
	}

	private static void appendIf(StringBuilder sb, boolean cond, String piece) {
		if (!cond) return;
		if (sb.length() > 0) sb.append(", ");
		sb.append(piece);
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

	private static String nz(int n) {
		return (n > 0) ? String.valueOf(n) : "";
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
		alert.getDialogPane().setContent(content);
		alert.getDialogPane().getStyleClass().add("dark-dialog");
		alert.getDialogPane().setMinWidth(460);
		alert.showAndWait();
	}

	private boolean showConfirmDark(String titolo, String messaggio) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle(titolo == null ? "Conferma" : titolo);
		alert.setHeaderText(null);
		alert.setGraphic(null);
		Label lbl = new Label(messaggio == null ? "" : messaggio);
		lbl.setWrapText(true);
		alert.getDialogPane().setContent(lbl);
		DialogPane dp = alert.getDialogPane();
		dp.getStyleClass().add("dark-dialog");
		ButtonType confermaType = new ButtonType("Conferma", ButtonBar.ButtonData.OK_DONE);
		ButtonType annullaType = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
		dp.getButtonTypes().setAll(confermaType, annullaType);
		dp.setMinWidth(460);
		Optional<ButtonType> result = alert.showAndWait();
		return result.isPresent() && result.get() == confermaType;
	}

	private String iconPathList() {
		return "M3 5h14v2H3V5zm0 4h14v2H3V9zm0 4h10v2H3v-2z";
	}

	private String iconPathCalendar() {
		return "M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-2 .9-2 2v12c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 14H5V8h14v9z";
	}

	private String iconPathChefHat() {
		return "M4 10c0-2.2 1.8-4 4-4 .5 0 .9.1 1.3.3C9.9 5.5 10.9 5 12 5c1.7 0 3.1.8 4 2.1.6-.1 1.3-.1 2-.1 2.2 0 4 1.8 4 4v1H4v-2z M4 14h18v2c0 1.1-.9 2-2 2H6c-1.1 0-2-.9-2-2v-2z";
	}

	private String iconPathId() {
		return "M3 3h18v4H3V3zm0 6h18v12H3V9zm4 2v8h10v-8H7z";
	}

	private String iconPathStatus() {
		return "M4 4h10l2 2h4v11H4zM6 6v9h12V8h-3l-2-2H6z";
	}
}
