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
import javafx.animation.RotateTransition;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gestione elenco corsi con filtri, CRUD, wizard sessioni, associazione
 * ricette, anteprima sessioni e UI più leggibile (senza CSS esterno).
 */
public class CorsiPanelController {

	private static final String ALL_OPTION = "Tutte";
	private static final int FILTERS_BADGE_MAX_CHARS = 32;
	// in CorsiPanelController
	private final javafx.collections.ObservableList<String> argomentiCondivisi = javafx.collections.FXCollections
			.observableArrayList();

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
	@FXML
	private TableColumn<Corso, String> colStato; // aggiungi in FXML a destra di Argomento/Frequenza

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
	private String filtroStato = null; // <— NEW
	private LocalDate dateFrom = null;
	private LocalDate dateTo = null;

	/* --- Menu Filtri --- */
	private ContextMenu filtersMenu;
	private Label labArgomentoVal, labFrequenzaVal, labChefVal, labIdVal, labStatoVal;
	private Button btnClearArg, btnClearFreq, btnClearChef, btnClearId, btnClearStato;

	/* --- UI Comfort Mode --- */
	private boolean comfortable = true; // righe alte + font più grande
	private static final double ROW_HEIGHT_COMFORT = 56;
	private static final double ROW_HEIGHT_COMPACT = 36;

	@FXML
	private void initialize() {
		initTableColumns();
		table.setItems(sorted);
		sorted.comparatorProperty().bind(table.comparatorProperty());

		// Selezione singola
		table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		table.getSelectionModel().setCellSelectionEnabled(false);

		// Stili di base e metrica "comfort"
		applyComfortMetrics(); // altezza riga + padding + font
		applyTableStyling(); // tema scuro coerente
		installRowFactory(); // hover/selection eleganti + doppio click

		// Re-applica stile quando cambia skin/size/columns
		table.skinProperty().addListener((obs, o, n) -> Platform.runLater(this::applyTableStyling));
		table.getColumns()
				.addListener((javafx.collections.ListChangeListener<? super TableColumn<Corso, ?>>) c -> Platform
						.runLater(this::applyTableStyling));
		table.widthProperty().addListener((o, a, b) -> Platform.runLater(this::applyTableStyling));

		// Bottoni abilitati solo se selezione presente e di proprietà
		table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, sel) -> {
			boolean has = sel != null;
			boolean own = isOwnedByLoggedChef(sel);
			btnEdit.setDisable(!has || !own);
			btnDelete.setDisable(!has || !own);
			btnAssocRicette.setDisable(!has || !own);
		});

		// Filtri UI
		hookFilterMenuButtonStyling();
		styleFilterMenuButton();
		buildAndAttachFiltersContextMenu();
		updatePrettyFilterRows();
		updateFiltersUI();

		// Azioni
		btnRefresh.setOnAction(e -> {
			playRefreshAnimation(btnRefresh); // 🔄 effetto visivo
			reload(); // tua logica di ricarica
		});

		btnReport.setOnAction(e -> openReportMode());
		btnNew.setOnAction(e -> onNew());
		btnEdit.setOnAction(e -> onEdit());
		btnDelete.setOnAction(e -> onDelete());
		btnAssocRicette.setOnAction(e -> onAssociateRecipes());

		// Ordinamento predefinito per data inizio (desc)
		table.getSortOrder().add(colInizio);
		colInizio.setSortType(TableColumn.SortType.DESCENDING);

		// Placeholder quando non ci sono corsi
		installPrettyPlaceholder();

		// Min size finestra comoda
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

	private final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private void initTableColumns() {
		// ID
		colId.setCellValueFactory(new PropertyValueFactory<>("idCorso"));
		colId.setStyle("-fx-alignment: CENTER;");

		// Argomento (bold)
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

		// Frequenza
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

		// STATO (chip)
		colStato.setCellValueFactory(cd -> Bindings.createStringBinding(() -> statoOf(cd.getValue())));
		colStato.setCellFactory(tc -> new TableCell<Corso, String>() {
			private final Label chip = new Label();

			@Override
			protected void updateItem(String s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null || s.isEmpty()) {
					setGraphic(null);
					return;
				}
				chip.setText(s);
				String bg = switch (s) {
				case "In corso" -> "#1fb57a";
				case "Futuro" -> "#3b82f6";
				case "Concluso" -> "#6b7280";
				default -> "#374151";
				};
				chip.setStyle("-fx-font-weight:700; -fx-font-size:12px; -fx-text-fill:white;" + "-fx-background-color:"
						+ bg + "; -fx-background-radius:999; -fx-padding:2 8;");
				setGraphic(chip);
			}
		});

		// Inizio
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

		// Fine
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

		// Chef (nome cognome, fallback CF)
		colChef.setCellValueFactory(cd -> Bindings.createStringBinding(() -> {
			Corso c = cd.getValue();
			if (c == null || c.getChef() == null)
				return "";
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

		// Policy e larghezze comode
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

		// Ordinamento iniziale
		table.getSortOrder().setAll(colInizio);
		colInizio.setSortType(TableColumn.SortType.DESCENDING);
	}

	/** Label per celle con padding e font coerenti (no CSS esterno). */
	private Label makeCellLabel(boolean bold) {
		Label l = new Label();
		l.setPadding(new Insets(2, 10, 2, 10));
		l.setStyle("-fx-text-fill:#e9f5ec;"
				+ (bold ? "-fx-font-size:15px; -fx-font-weight:700;" : "-fx-font-size:14px; -fx-font-weight:500;"));
		l.setMaxWidth(Double.MAX_VALUE);
		return l;
	}

	/** Metrica comoda/compatta (puoi fare un toggle se vuoi). */
	private void applyComfortMetrics() {
		double h = comfortable ? ROW_HEIGHT_COMFORT : ROW_HEIGHT_COMPACT;
		table.setFixedCellSize(h);
		// Per evitare spazi strani, lascia che la TableView calcoli l'altezza vista.
	}

	/** Tema scuro con bordi soft e testo leggibile. */
	private void applyTableStyling() {
		final String BG = "#20282b"; // celle
		final String BG_HDR = "#242c2f"; // header
		final String TXT = "#e9f5ec";
		final String GRID = "rgba(255,255,255,0.06)";

		table.setStyle("-fx-background-color:#20282b;" + "-fx-control-inner-background:#20282b;"
				+ "-fx-text-background-color:#e9f5ec;" + "-fx-table-cell-border-color: rgba(255,255,255,0.06);"
				+ "-fx-table-header-border-color: rgba(255,255,255,0.06);" + "-fx-selection-bar: transparent;"
				+ "-fx-selection-bar-non-focused: transparent;" +
				// spegne focus blu della table e dei figli
				"-fx-focus-color: transparent;" + "-fx-faint-focus-color: transparent;" + "-fx-accent: transparent;"
				+ "-fx-background-insets: 0;" + "-fx-padding: 6;");

		Platform.runLater(() -> {
			Node headerRow = table.lookup("TableHeaderRow");
			if (headerRow != null)
				headerRow.setStyle("-fx-background-color:" + BG_HDR + ";");

			Node headerBg = table.lookup(".column-header-background");
			if (headerBg != null)
				headerBg.setStyle("-fx-background-color:" + BG_HDR + ";");

			for (Node n : table.lookupAll(".column-header")) {
				if (n instanceof Region r) {
					r.setStyle("-fx-background-color:" + BG_HDR + "; -fx-background-insets: 0; -fx-border-color: "
							+ GRID + "; -fx-border-width: 0 0 1 0;");
				}
				Node lab = n.lookup(".label");
				if (lab instanceof Label lbl) {
					lbl.setTextFill(javafx.scene.paint.Color.web(TXT));
					lbl.setStyle("-fx-font-weight:700; -fx-font-size:13px;");
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

	private String statoOf(Corso c) {
		if (c == null)
			return "";
		LocalDate oggi = LocalDate.now();
		LocalDate in = c.getDataInizio(), fin = c.getDataFine();
		if (in != null && fin != null) {
			if (oggi.isBefore(in))
				return "Futuro";
			if ((oggi.isEqual(in) || oggi.isAfter(in)) && (oggi.isBefore(fin) || oggi.isEqual(fin)))
				return "In corso";
			if (oggi.isAfter(fin))
				return "Concluso";
		}
		return "";
	}

	/** Riga con hover/selection “card-like” e doppio click → anteprima sessioni. */

	/** Placeholder carino quando non ci sono corsi. */
	private void installPrettyPlaceholder() {
		Label ph = new Label("Non hai ancora corsi.\nCrea il primo corso per iniziare a pianificare le sessioni.");
		ph.setAlignment(Pos.CENTER);
		ph.setStyle("-fx-text-fill:#b7c5cf; -fx-font-size:14.5px; -fx-font-weight:600; -fx-opacity:0.95;");
		ph.setPadding(new Insets(16));
		table.setPlaceholder(ph);
	}

	/* ================== CARICAMENTO ================== */

	// dopo reload() o dove preferisci
	private void refreshTitleWithCount() {
		if (table == null)
			return;

		// se la Scene non è ancora attaccata, riprova al prossimo tick FX
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

		// se la TableView non è ancora in scena, aspetta che venga attaccata e poi
		// ricarica
		if (table == null || table.getScene() == null) {
			table.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene != null) {
					Platform.runLater(this::reload);
				}
			});
		} else {
			reload();
		}
	}

	public void setDaos(CorsoDao corsoDao, SessioneDao sessioneDao) {
		this.corsoDao = corsoDao;
		this.sessioneDao = sessioneDao;

		// se la TableView non è ancora in scena, aspetta che venga attaccata e poi
		// ricarica
		if (table == null || table.getScene() == null) {
			table.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene != null) {
					Platform.runLater(this::reload);
				}
			});
		} else {
			reload();
		}
	}

	// call in reload()
	private void reload() {
		if (corsoDao == null) {
			showError("DAO non inizializzato. Effettua il login.");
			return;
		}
		try {
			List<Corso> list = corsoDao.findAll();
			if (list == null)
				list = java.util.Collections.emptyList();
			backing.setAll(list);
			refilter();
			updateFiltersUI();
			refreshTitleWithCount();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Effetto rotazione dell'icona del bottone refresh per dare feedback visivo.
	 */
	private void playRefreshAnimation(Button btn) {
		if (btn == null)
			return;
		RotateTransition rt = new RotateTransition(Duration.millis(600), btn);
		rt.setFromAngle(0);
		rt.setToAngle(360);
		rt.setCycleCount(1);
		rt.setAutoReverse(false);
		rt.play();
	}

	// tooltip su ogni riga (argomento + periodo + chef)
	/**
	 * Riga con hover/selection “card-like” e doppio click → anteprima sessioni,
	 * senza cambiare la selezione quando il mouse passa sopra.
	 */
	private void installRowFactory() {
		table.setRowFactory(tv -> {
			TableRow<Corso> row = new TableRow<>() {
				@Override
				protected void updateItem(Corso item, boolean empty) {
					super.updateItem(item, empty);
					applyRowStyle(this, false, isSelected());
				}
			};

			// SOLO stile in hover, NIENTE selezione forzata
			row.setOnMouseEntered(e -> applyRowStyle(row, true, row.isSelected()));
			row.setOnMouseExited(e -> applyRowStyle(row, false, row.isSelected()));
			row.hoverProperty().addListener((o, w, h) -> applyRowStyle(row, h, row.isSelected()));
			row.selectedProperty().addListener((o, w, s) -> applyRowStyle(row, row.isHover(), s));

			// Selezione solo con click; doppio click apre le sessioni
			row.setOnMouseClicked(e -> {
				if (row.isEmpty())
					return;
				if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
					openSessioniPreview(row.getItem());
				} else if (e.getClickCount() == 1 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
					table.getSelectionModel().select(row.getIndex()); // selezione esplicita SOLO al click
				}
			});
			return row;
		});
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
			if (!isBlank(filtroStato)) {
				String st = statoOf(c);
				if (!matchesEqIgnoreCase(st, filtroStato))
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
		filtroArg = filtroFreq = filtroChef = filtroId = filtroStato = null; // <—
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
		appendIf(sb, !isBlank(filtroStato), "Stato=" + filtroStato); // <— NEW
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

	private List<String> distinctStati() {
		List<String> s = new ArrayList<>(Arrays.asList("Futuro", "In corso", "Concluso"));
		s.add(0, ALL_OPTION);
		return s;
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

	/**
	 * Dialog dark coerente con l'app: restituisce la scelta o null se
	 * Annulla/chiuso.
	 */
	private String askChoice(String title, String header, List<String> options, String preselect) {
		if (options == null || options.isEmpty()) {
			showInfo("Nessuna opzione disponibile.");
			return null;
		}

		// Dialog base
		Dialog<String> dlg = new Dialog<>();
		dlg.setTitle(title);
		dlg.setHeaderText(header);

		// Bottoni
		ButtonType OK = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
		ButtonType CANCEL = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
		dlg.getDialogPane().getButtonTypes().setAll(OK, CANCEL);

		// Contenuto: combobox + padding
		ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(options));
		cb.setEditable(false);
		// preselect (case insensitive)
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

		// Layout semplice
		HBox box = new HBox(10, cb);
		box.setPadding(new Insets(6, 0, 0, 0));
		dlg.getDialogPane().setContent(box);

		// Applica stile dark coerente
		styleDarkDialog(dlg.getDialogPane());
		styleDarkCombo(cb);
		styleDarkButtons(dlg.getDialogPane(), OK, CANCEL);

		// Mostra e ritorna
		dlg.setResultConverter(bt -> (bt == OK) ? cb.getValue() : null);
		Optional<String> res = dlg.showAndWait();
		return res.orElse(null);
	}

	/** Stile dark per il DialogPane + header/scrollbar + rimozione focus blu. */
	private void styleDarkDialog(DialogPane pane) {
		pane.setStyle("-fx-background-color:#20282b;" + "-fx-background-radius:12;"
				+ "-fx-border-color: rgba(255,255,255,0.08);" + "-fx-border-radius:12;" + "-fx-border-width:1;"
				+ "-fx-padding:14;" + "-fx-focus-color: transparent;" + "-fx-faint-focus-color: transparent;"
				+ "-fx-accent: transparent;");
		// header
		Node header = pane.lookup(".header-panel");
		if (header != null) {
			header.setStyle("-fx-background-color: transparent; -fx-padding: 0 0 8 0;");
			Node lbl = header.lookup(".label");
			if (lbl instanceof Label l) {
				l.setStyle("-fx-text-fill:#e9f5ec; -fx-font-weight:800; -fx-font-size:14.5px;");
			}
		}
		// contenuto
		Node content = pane.lookup(".content");
		if (content instanceof Region r) {
			r.setStyle("-fx-background-color: transparent; -fx-text-fill:#e9f5ec;");
		}
	}

	/** Stile dark per ComboBox (niente alone blu). */
	private void styleDarkCombo(ComboBox<String> cb) {
		// base: sfondo/padding/bordo + disattiva il blu di focus
		cb.setStyle("-fx-background-color:#2e3845;" + "-fx-control-inner-background:#2e3845;"
				+ "-fx-background-radius:8;" + "-fx-border-color:#3a4657;" + "-fx-border-radius:8;"
				+ "-fx-padding: 4 10 4 10;" + "-fx-focus-color: transparent;" + "-fx-faint-focus-color: transparent;"
				+ "-fx-accent: transparent;" + "-fx-opacity:1;");
		cb.setDisable(false);

		// ===== 1) BUTTON CELL (testo visibile quando la combo è chiusa) =====
		cb.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					setStyle("-fx-background-color: transparent;");
				} else {
					setText(item);
					setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:700; -fx-background-color: transparent;");
				}
			}
		});

		// ===== 2) CELLE DEL POPUP (lista aperta) =====
		cb.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					setStyle("-fx-background-color: transparent;");
				} else {
					setText(item);
					setStyle("-fx-text-fill:#e9f5ec; -fx-font-weight:700; -fx-background-color: transparent;");
				}
			}
		});

		// ===== 3) Popup list scura coerente =====
		cb.showingProperty().addListener((obs, was, is) -> {
			if (is) {
				// prova a raggiungere la ListView del popup e i suoi elementi
				Scene sc = cb.getScene();
				if (sc != null) {
					for (Node n : sc.getRoot().lookupAll(".list-view")) {
						n.setStyle("-fx-background-color:#20282b;" + "-fx-control-inner-background:#20282b;"
								+ "-fx-text-fill:#e9f5ec;" + "-fx-background-insets:0;"
								+ "-fx-focus-color: transparent;" + "-fx-faint-focus-color: transparent;"
								+ "-fx-accent: transparent;");
					}
					for (Node n : sc.getRoot().lookupAll(".list-cell")) {
						// fallback nel caso la cellFactory non agganci tutto
						n.setStyle("-fx-text-fill:#e9f5ec; -fx-background-color: transparent;");
					}
				}
			}
		});

		// ===== 4) Editor (non usato perché non editable, ma per sicurezza) =====
		if (cb.getEditor() != null) {
			cb.getEditor().setStyle("-fx-background-color: transparent; -fx-text-fill:#e5e7eb;"
					+ "-fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-accent: transparent;");
		}
	}

	/** Stile dark per i bottoni del dialog (OK verde, Annulla neutro). */
	private void styleDarkButtons(DialogPane pane, ButtonType okType, ButtonType cancelType) {
		Button ok = (Button) pane.lookupButton(okType);
		if (ok != null) {
			ok.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;"
					+ "-fx-background-radius:10; -fx-padding:6 14;");
			ok.setOnMouseEntered(
					e -> ok.setStyle("-fx-background-color:#16a56e; -fx-text-fill:#0a1410; -fx-font-weight:800;"
							+ "-fx-background-radius:10; -fx-padding:6 14;"));
			ok.setOnMouseExited(
					e -> ok.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;"
							+ "-fx-background-radius:10; -fx-padding:6 14;"));
		}
		Button cancel = (Button) pane.lookupButton(cancelType);
		if (cancel != null) {
			cancel.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec;"
					+ "-fx-background-radius:10; -fx-padding:6 14;");
			cancel.setOnMouseEntered(e -> cancel.setStyle("-fx-background-color:#374047; -fx-text-fill:#e9f5ec;"
					+ "-fx-background-radius:10; -fx-padding:6 14;"));
			cancel.setOnMouseExited(e -> cancel.setStyle("-fx-background-color:#2b3438; -fx-text-fill:#e9f5ec;"
					+ "-fx-background-radius:10; -fx-padding:6 14;"));
		}
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
			refreshArgomentiCondivisi();
			DialogPane pane = fx.load();

			CorsoEditorDialogController ctrl = fx.getController();
			ctrl.bindArgomenti(argomentiCondivisi);
			ctrl.setCorso(null);

			Dialog<Corso> dialog = new Dialog<>();
			dialog.setTitle("Nuovo Corso");
			dialog.setDialogPane(pane);
			dialog.setResizable(true);
			dialog.setResultConverter(bt -> (bt != null && bt == ctrl.getCreateButtonType()) ? ctrl.getResult() : null);

			Optional<Corso> res = dialog.showAndWait();
			if (!res.isPresent())
				return;

			Corso nuovo = res.get();
			if (corsoDao != null) {
				String cfChef = corsoDao.getOwnerCfChef();
				if (cfChef != null && !cfChef.trim().isEmpty()) {
					var chef = new it.unina.foodlab.model.Chef();
					chef.setCF_Chef(cfChef);
					nuovo.setChef(chef);
				}
			}

			// pre-carica N righe nel wizard
			Optional<List<Sessione>> sessOpt = openSessioniWizard(nuovo, Math.max(0, nuovo.getNumSessioni()));
			if (!sessOpt.isPresent())
				return;
			List<Sessione> sessions = sessOpt.get();
			if (sessions == null || sessions.isEmpty())
				return;

			long id = corsoDao.insertWithSessions(nuovo, sessions);
			nuovo.setIdCorso(id);
			backing.add(nuovo);
			table.getSelectionModel().select(nuovo);
			// allinea lista argomenti condivisa
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
		if (sel == null)
			return;

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

	/* ================== UI: Menu Filtri (Date inline + '×') ================== */

	private void buildAndAttachFiltersContextMenu() {
		filtersMenu = new ContextMenu();
		filtersMenu.setStyle(
				// colori coerenti con la tabella
				"-fx-background-color:#20282b;" + "-fx-background-insets:0;" + "-fx-background-radius:14;"
						+ "-fx-border-color: rgba(255,255,255,0.06);" + "-fx-border-radius:14;" + "-fx-border-width:1;"
						+ "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 18, 0.2, 0, 6);" +
						// spegne blu di focus/selection del menu
						"-fx-base:#20282b;" + "-fx-control-inner-background:#20282b;"
						+ "-fx-selection-bar: transparent;" + "-fx-accent: transparent;"
						+ "-fx-focus-color: transparent;" + "-fx-faint-focus-color: transparent;");

		// Date
		CustomMenuItem rowFrom = createDateRow("Inizio da", true);
		CustomMenuItem rowTo = createDateRow("Fine fino a", false);

		rowFrom.setStyle("-fx-background-color: transparent;");
		rowTo.setStyle("-fx-background-color: transparent;");

		// Argomento
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

		rowArg.setStyle("-fx-background-color: transparent;");

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

		rowFreq.setStyle("-fx-background-color: transparent;");

		// Stato <<<<<<<<<< NEW
		CustomMenuItem rowStato = createFilterRow("Stato", "(tutti)", iconPathStatus(), e -> {
			String scelto = askChoice("Filtro Stato", "Seleziona stato", distinctStati(), filtroStato);
			filtroStato = normalizeAllToNull(scelto);
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, () -> {
			filtroStato = null;
			refilter();
			updateFiltersUI();
			updatePrettyFilterRows();
		}, b -> btnClearStato = b, l -> labStatoVal = l);

		rowStato.setStyle("-fx-background-color: transparent;");

		// Chef
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

		rowChef.setStyle("-fx-background-color: transparent;");

		// ID
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

		rowId.setStyle("-fx-background-color: transparent;");

		CustomMenuItem sep1 = separatorItem();
		CustomMenuItem sep2 = separatorItem();

		sep1.setStyle("-fx-background-color: transparent;");

		// >>> Bottone rosso "Pulisci tutti i filtri" (CustomMenuItem)
		CustomMenuItem clearBtn = createClearAllButtonItem();

		clearBtn.setStyle("-fx-background-color: transparent;");

		filtersMenu.getItems().setAll(rowFrom, rowTo, sep1, rowArg, rowFreq, rowStato, rowChef, rowId, sep2, clearBtn);

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
				case SPACE, ENTER -> {
					if (filtersMenu.isShowing())
						filtersMenu.hide();
					filtersMenu.show(btnFilters, javafx.geometry.Side.BOTTOM, 0, 6);
					ke.consume();
				}
				default -> {
				}
				}
			});
		}
	}

	private CustomMenuItem createClearAllButtonItem() {
		final String ACCENT = "#1fb57a";

		// icona (scopa)
		javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
		svg.setContent("M3 14l6-6 1 1-6 6H3zm7-7l2-2 5 5-2 2-5-5zm7 6l-2 2 2 2h2v-2l-2-2z");
		svg.setStyle("-fx-fill:white; -fx-min-width:16; -fx-min-height:16; -fx-max-width:16; -fx-max-height:16;");

		Label text = new Label("Pulisci tutti i filtri");
		text.setStyle("-fx-text-fill:white; -fx-font-weight:800;");

		HBox btn = new HBox(8, svg, text);
		btn.setAlignment(Pos.CENTER_LEFT);
		btn.setPadding(new Insets(8, 12, 8, 12));
		btn.setStyle("-fx-background-color:#ef4444; -fx-background-radius:10;"); // rosso “azione”
		btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color:#dc2626; -fx-background-radius:10;"));
		btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color:#ef4444; -fx-background-radius:10;"));
		btn.setOnMouseClicked(e -> {
			clearAllFilters();
			updatePrettyFilterRows();
			filtersMenu.hide();
		});

		CustomMenuItem item = new CustomMenuItem(btn, false);
		return item;
	}

	private CustomMenuItem createFilterRow(String title, String value, String svgPath,
			javafx.event.EventHandler<ActionEvent> onChooseClick, Runnable onClearClick,
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

		CustomMenuItem row = new CustomMenuItem(box, false);

		box.setOnMouseClicked(e -> {
			if (e.getTarget() instanceof Button)
				return;
			if (onChooseClick != null)
				onChooseClick.handle(new ActionEvent());
		});

		box.setOnMouseEntered(e -> {
			box.setStyle("-fx-background-color: linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT
					+ " 3px, transparent 3px);" + "-fx-background-radius:10;");
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
			box.setStyle("-fx-background-color: linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT
					+ " 3px, transparent 3px);" + "-fx-background-radius:10;");
			labTitle.setStyle("-fx-text-fill:#cfe5d9; -fx-font-size:12.5px;");
		});

		box.setOnMouseExited(e -> {
			box.setStyle("-fx-background-color: transparent; -fx-background-radius:10;");
			labTitle.setStyle("-fx-text-fill:#9fb6aa; -fx-font-size:12.5px;");
		});

		return item;
	}

	private void styleDatePickerInline(DatePicker dp) {
		if (dp == null)
			return;
		final String TEXT_LIGHT = "#e5e7eb"; // colore testo chiaro
		final String BG = "#2e3845"; // sfondo chiaro ma sufficiente
		final String BORDER = "#3a4657";

		dp.setEditable(false);
		dp.setStyle("-fx-background-color: " + BG + ";" + "-fx-control-inner-background: " + BG + ";"
				+ "-fx-text-fill: " + TEXT_LIGHT + ";" + "-fx-prompt-text-fill: rgba(255,255,255,0.6);"
				+ "-fx-background-radius:8;" + "-fx-border-color: " + BORDER + ";" + "-fx-border-radius:8;"
				+ "-fx-padding: 2 8 2 8;" + "-fx-focus-color: transparent;" + "-fx-faint-focus-color: transparent;"
				+ "-fx-accent: transparent;");

		if (dp.getEditor() != null) {
			dp.getEditor()
					.setStyle("-fx-background-color: transparent;" + "-fx-text-fill: " + TEXT_LIGHT + ";"
							+ "-fx-prompt-text-fill: rgba(255,255,255,0.6);" + "-fx-focus-color: transparent;"
							+ "-fx-faint-focus-color: transparent;" + "-fx-accent: transparent;");
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
		if (labStatoVal != null)
			labStatoVal.setText(isBlank(filtroStato) ? "(tutti)" : filtroStato);

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

	/* ================== Helpers vari ================== */

	private static void applyRowStyle(TableRow<Corso> row, boolean hovered, boolean selected) {
		final String ACCENT = "#1fb57a"; // barra verde
		final String SELECT_BG = "rgba(31,181,122,0.14)"; // selezione
		final String HOVER_BG = "rgba(255,255,255,0.07)"; // hover
		final String ZEBRA_BG = "rgba(255,255,255,0.03)"; // zebra righe pari
		final String CLEAR = "transparent";

		if (row == null || row.isEmpty() || row.getItem() == null) {
			row.setStyle("");
			row.setCursor(Cursor.DEFAULT);
			return;
		}

		String base = (row.getIndex() % 2 == 0) ? ZEBRA_BG : CLEAR;

		if (selected) {
			row.setStyle("-fx-background-color: " + "linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT
					+ " 4px, transparent 4px), " + SELECT_BG + ";" + "-fx-background-radius:8;");
			row.setCursor(Cursor.HAND);
		} else if (hovered) {
			row.setStyle("-fx-background-color: " + "linear-gradient(to right, " + ACCENT + " 0px, " + ACCENT
					+ " 4px, transparent 4px), " + HOVER_BG + ";" + "-fx-background-radius:8;");
			row.setCursor(Cursor.HAND);
		} else {
			row.setStyle("-fx-background-color:" + base + "; -fx-background-radius:8;");
			row.setCursor(Cursor.DEFAULT);
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	private static String nz(String s) {
		return (s == null) ? "" : s;
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

	private void openSessioniPreview(Corso corso) {
		if (corso == null)
			return;
		try {
			FXMLLoader l = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniPreview.fxml"));
			DialogPane pane = l.load();
			SessioniPreviewController ctrl = l.getController();

			long id = corso.getIdCorso();
			List<Sessione> sessions = (sessioneDao != null) ? sessioneDao.findByCorso(id)
					: java.util.Collections.<Sessione>emptyList();

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
				showInfoDark("Il corso non ha sessioni in presenza.");
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

	// OVERLOAD: apre il wizard con N righe “vuote”
	private Optional<List<Sessione>> openSessioniWizard(Corso corso, int initialRows) {
		try {
			FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
			DialogPane pane = fx.load();

			SessioniWizardController ctrl = fx.getController();

			// se N > 0 pre-popoliamo, altrimenti init normale
			if (initialRows > 0) {
				ctrl.initWithCorsoAndBlank(corso, initialRows); // <— metodo che aggiungi nel wizard
			} else {
				ctrl.initWithCorso(corso);
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

	private static String nz(int n) {
		return (n > 0) ? String.valueOf(n) : "";
	}

	private void refreshArgomentiCondivisi() {
		try {
			// implementa in CorsoDao: SELECT DISTINCT argomento FROM corso ORDER BY
			// argomento
			java.util.List<String> distinct = corsoDao.findDistinctArgomenti();
			argomentiCondivisi.setAll(distinct != null ? distinct : java.util.Collections.emptyList());
		} catch (Exception ex) {
			// fallback: lascia la lista com’è
		}
	}

	/** Mostra un messaggio informativo in stile dark, coerente con l’app. */
	/** Alert informativo scuro, senza header né icona. Testo ben leggibile. */
	private void showInfoDark(String message) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);

		// NIENTE header, NIENTE icona (altrimenti resta la banda chiara)
		alert.setHeaderText(null);
		alert.setGraphic(null);

		// testo nel CORPO (non nell'header)
		Label content = new Label(message == null ? "" : message);
		content.setWrapText(true);
		content.setStyle("-fx-text-fill:#e9f5ec; -fx-font-size:14px; -fx-font-weight:600;");
		alert.getDialogPane().setContent(content);

		// stile dark coerente
		DialogPane dp = alert.getDialogPane();
		dp.setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);"
				+ "-fx-border-color: rgba(255,255,255,0.08);" + "-fx-border-width: 1;" + "-fx-border-radius: 12;"
				+ "-fx-background-radius: 12;" + "-fx-padding: 14;" + "-fx-focus-color: transparent;"
				+ "-fx-faint-focus-color: transparent;" + "-fx-accent: transparent;");
		// header panel (se esiste) completamente trasparente
		Node header = dp.lookup(".header-panel");
		if (header instanceof Region r) {
			r.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
		}
		// graphic container (icona info) via
		Node graphic = dp.lookup(".graphic-container");
		if (graphic instanceof Region g) {
			g.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
		}

		// bottone OK in stile brand
		Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
		if (okBtn != null) {
			okBtn.setText("OK");
			okBtn.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;"
					+ "-fx-background-radius:10; -fx-padding:8 16;");
			okBtn.setOnMouseEntered(
					e -> okBtn.setStyle("-fx-background-color:#16a56e; -fx-text-fill:#0a1410; -fx-font-weight:800;"
							+ "-fx-background-radius:10; -fx-padding:8 16;"));
			okBtn.setOnMouseExited(
					e -> okBtn.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;"
							+ "-fx-background-radius:10; -fx-padding:8 16;"));
		}

		dp.setMinWidth(460);
		alert.showAndWait();
	}

	/**
	 * Mostra un dialogo di conferma in stile dark. Ritorna true se l'utente preme
	 * "Conferma", false se preme "Annulla" o chiude la finestra.
	 */
	private boolean showConfirmDark(String titolo, String messaggio) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle(titolo == null ? "Conferma" : titolo);
		alert.setHeaderText(null);
		alert.setGraphic(null);

		Label lbl = new Label(messaggio == null ? "" : messaggio);
		lbl.setWrapText(true);
		lbl.setStyle("-fx-text-fill:#e9f5ec; -fx-font-size:14px; -fx-font-weight:600;");
		alert.getDialogPane().setContent(lbl);

		DialogPane dp = alert.getDialogPane();
		dp.setStyle("-fx-background-color: linear-gradient(to bottom,#242c2f,#20282b);"
				+ "-fx-border-color: rgba(255,255,255,0.08);" + "-fx-border-width: 1;" + "-fx-border-radius: 12;"
				+ "-fx-background-radius: 12;" + "-fx-padding: 14;" + "-fx-focus-color: transparent;"
				+ "-fx-faint-focus-color: transparent;");

		// Rimuovi header/graphic panel chiari
		Node header = dp.lookup(".header-panel");
		if (header instanceof Region r) {
			r.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
		}
		Node graphic = dp.lookup(".graphic-container");
		if (graphic instanceof Region g) {
			g.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
		}

		// === Bottoni ===
		ButtonType confermaType = new ButtonType("Conferma", ButtonBar.ButtonData.OK_DONE);
		ButtonType annullaType = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
		dp.getButtonTypes().setAll(confermaType, annullaType);

		Button btnConferma = (Button) dp.lookupButton(confermaType);
		btnConferma.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410; -fx-font-weight:800;"
				+ "-fx-background-radius:10; -fx-padding:8 16;");
		btnConferma.setOnMouseEntered(e -> btnConferma.setStyle("-fx-background-color:#16a56e; -fx-text-fill:#0a1410;"
				+ "-fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 16;"));
		btnConferma.setOnMouseExited(e -> btnConferma.setStyle("-fx-background-color:#1fb57a; -fx-text-fill:#0a1410;"
				+ "-fx-font-weight:800; -fx-background-radius:10; -fx-padding:8 16;"));

		Button btnAnnulla = (Button) dp.lookupButton(annullaType);
		btnAnnulla.setStyle("-fx-background-color:#ef4444; -fx-text-fill:white; -fx-font-weight:700;"
				+ "-fx-background-radius:10; -fx-padding:8 16;");
		btnAnnulla.setOnMouseEntered(
				e -> btnAnnulla.setStyle("-fx-background-color:#dc2626; -fx-text-fill:white; -fx-font-weight:700;"
						+ "-fx-background-radius:10; -fx-padding:8 16;"));
		btnAnnulla.setOnMouseExited(
				e -> btnAnnulla.setStyle("-fx-background-color:#ef4444; -fx-text-fill:white; -fx-font-weight:700;"
						+ "-fx-background-radius:10; -fx-padding:8 16;"));

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
