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
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
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

	@FXML private ComboBox<String> cbFiltroArgomento;
	@FXML private TableView<Corso> table;
	@FXML private TableColumn<Corso, String> colChef;
	@FXML private Button btnEdit, btnDelete, btnAssocRicette;
	@FXML private TableColumn<Corso, String> colStato;

	private final ObservableList<Corso> backing = FXCollections.observableArrayList();
	private final FilteredList<Corso> filtered = new FilteredList<>(backing, c -> true);
	private final SortedList<Corso> sorted = new SortedList<>(filtered);

	private CorsoDao corsoDao;
	private SessioneDao sessioneDao;
	private RicettaDao ricettaDao;

	private String filtroArg = null;

	@FXML
	private void initialize() {
		initTableColumns();

		table.setItems(sorted);
		sorted.comparatorProperty().bind(table.comparatorProperty());

		table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		table.getSelectionModel().setCellSelectionEnabled(false);

		installRowFactory();

		table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, sel) -> {
			boolean disabled = sel == null || !isOwnedByLoggedChef(sel);
			btnEdit.setDisable(disabled);
			btnDelete.setDisable(disabled);
			btnAssocRicette.setDisable(disabled);
		});

		cbFiltroArgomento.valueProperty().addListener((obs, oldV, newV) -> {
			if (newV == null || ALL_OPTION.equalsIgnoreCase(newV)) {
				filtroArg = null;
			} else {
				filtroArg = newV;
			}
			refilter();
			refreshTitleWithCount();
		});

		refreshTitleWithCount();
	}


	private void initTableColumns() {
		colStato.setCellValueFactory(cd ->
		Bindings.createStringBinding(() -> statoOf(cd.getValue()))
				);

		colChef.setCellValueFactory(cd -> Bindings.createStringBinding(() -> {
			Corso c = cd.getValue();
			if (c == null || c.getChef() == null) return "";
			String nome = nz(c.getChef().getNome());
			String cognome = nz(c.getChef().getCognome());
			String full = (nome + " " + cognome).trim();
			return full.isEmpty() ? nz(c.getChef().getCF_Chef()) : full;
		}));
	}

	private String statoOf(Corso c) {
		if (c == null) return "";
		LocalDate oggi = LocalDate.now();
		LocalDate inizio = c.getDataInizio();
		LocalDate fine = c.getDataFine();

		if (inizio == null || fine == null) {
			return "";
		}

		if (oggi.isBefore(inizio)) {
			return "Futuro";
		}

		if (!oggi.isAfter(fine)) {
			return "In corso";
		}

		return "Concluso";
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

	@FXML
	private void reload() {
		if (corsoDao == null) {
			showError("DAO non inizializzato. Effettua il login.");
			return;
		}
		try {
			List<Corso> list = corsoDao.findAll();
			if (list == null) {
				list = Collections.emptyList();
			}
			backing.setAll(list);
			refilter();
			populateFiltroArgomento();
			refreshTitleWithCount();
		} catch (Exception ex) {
			showError("Errore durante il caricamento dei corsi: " + ex.getMessage());
		}
	}


	private void populateFiltroArgomento() {
		if (cbFiltroArgomento == null) return;

		List<String> argOpts = distinctArgomenti();
		cbFiltroArgomento.getItems().setAll(argOpts);

		String toSelect;
		if (filtroArg == null) {
			toSelect = ALL_OPTION;
		} else {
			toSelect = filtroArg;
		}

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
		filtered.setPredicate(corso -> {
			if (corso == null) return false;
			return matchesEqIgnoreCase(corso.getArgomento(), filtroArg);
		});
	}


	private List<String> distinctArgomenti() {
		Set<String> set = new HashSet<>();
		for (Corso c : backing) {
			if (c == null) continue;
			String a = c.getArgomento();
			if (a != null) {
				a = a.trim();
				if (!a.isEmpty()) {
					set.add(a);
				}
			}
		}
		List<String> out = new ArrayList<>(set);
		out.sort(String.CASE_INSENSITIVE_ORDER);
		out.add(0, ALL_OPTION);
		return out;
	}

	@FXML
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


	@FXML
	private void onEdit() {
		Corso sel = table.getSelectionModel().getSelectedItem();
		if (sel == null) {
			return;
		}
		if (!isOwnedByLoggedChef(sel)) {
			makeDarkAlert(Alert.AlertType.WARNING, "Attenzione",
					"Puoi modificare solo i corsi che appartengono al tuo profilo.")
			.showAndWait();
			return;
		}


		try {
			List<Sessione> esistenti = sessioneDao.findByCorso(sel.getIdCorso());
			if (esistenti == null) {
				esistenti = Collections.emptyList();
			}

			FXMLLoader fx = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniWizard.fxml"));
			DialogPane pane = fx.load();
			pane.getStyleClass().add("dark-dialog");

			SessioniWizardController ctrl = fx.getController();
			ctrl.initWithCorsoAndExisting(sel, esistenti);

			Node content = pane.getContent();
			if (content instanceof Region region) {
				ScrollPane sc = new ScrollPane(region);
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
			pane.setPrefSize(1600, 800);
			dlg.setResizable(true);

			dlg.setResultConverter(bt -> {
				if (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
					return ctrl.buildResult();
				}
				return null;
			});

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


	@FXML
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
			dialog.setResultConverter(bt -> {
				if (bt != null && bt == ctrl.getCreateButtonType()) {
					return ctrl.getResult();
				}
				return null;
			});

			Optional<Corso> res = dialog.showAndWait();
			if (res.isEmpty()) {
				return;
			}

			Corso nuovo = res.get();

			if (corsoDao != null) {
				String cfChef = corsoDao.getOwnerCfChef();
				if (cfChef != null) {
					cfChef = cfChef.trim();
					if (!cfChef.isEmpty()) {
						Chef chef = new Chef();
						chef.setCF_Chef(cfChef);
						nuovo.setChef(chef);
					}
				}
			}

			Optional<List<Sessione>> sessOpt = openSessioniWizard(nuovo, Math.max(0, nuovo.getNumSessioni()));
			if (sessOpt.isEmpty()) {
				return;
			}

			List<Sessione> sessions = sessOpt.get();
			if (sessions == null || sessions.isEmpty()) {
				return;
			}

			long id = corsoDao.insertWithSessions(nuovo, sessions);
			nuovo.setIdCorso(id);
			backing.add(nuovo);
			table.getSelectionModel().select(nuovo);

			String arg = nuovo.getArgomento();
			if (arg != null) {
				arg = arg.trim();
				if (!arg.isEmpty() && !argomentiCondivisi.contains(arg)) {
					argomentiCondivisi.add(arg);
					FXCollections.sort(argomentiCondivisi);
				}
			}

			populateFiltroArgomento();
			refreshTitleWithCount();
		} catch (Exception ex) {
			showError("Errore apertura/salvataggio corso: " + ex.getMessage());
			ex.printStackTrace();
		}
	}


	@FXML
	private void onDelete() {
		Corso sel = table.getSelectionModel().getSelectedItem();
		if (sel == null) {
			return;
		}

		boolean conferma = showConfirmDark(
				"Conferma eliminazione",
				"Eliminare il corso: " + sel.getArgomento() + " ?"
				);
		if (!conferma) {
			return;
		}

		try {
			corsoDao.delete(sel.getIdCorso());
			backing.remove(sel);
			populateFiltroArgomento();
			refreshTitleWithCount();
		} catch (Exception ex) {
			showInfoDark("Impossibile eliminare il corso: " + ex.getMessage());
		}
	}


	private void openSessioniPreview(Corso corso) {
		if (corso == null) {
			return;
		}
		try {
			FXMLLoader l = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/SessioniPreview.fxml"));
			DialogPane pane = l.load();
			pane.getStyleClass().add("dark-dialog");

			SessioniPreviewController ctrl = l.getController();
			long id = corso.getIdCorso();

			List<Sessione> sessions = Collections.emptyList();
			if (sessioneDao != null) {
				List<Sessione> found = sessioneDao.findByCorso(id);
				if (found != null) {
					sessions = found;
				}
			}

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


	@FXML
	private void onAssociateRecipes() {
		Corso sel = table.getSelectionModel().getSelectedItem();
		if (sel == null) {
			return;
		}
		if (!isOwnedByLoggedChef(sel)) {
			showInfoDark("Puoi modificare solo i corsi del tuo profilo.");
			return;
		}

		try {
			List<Sessione> tutte = sessioneDao.findByCorso(sel.getIdCorso());
			if (tutte == null) {
				tutte = Collections.emptyList();
			}

			List<SessionePresenza> presenze = new ArrayList<>();
			for (Sessione s : tutte) {
				if (s instanceof SessionePresenza sp) {
					presenze.add(sp);
				}
			}

			if (presenze.isEmpty()) {
				showInfoDark("Il corso non ha sessioni in presenza.");
				return;
			}

			SessionePresenza target;
			if (presenze.size() == 1) {
				target = presenze.get(0);
			} else {
				target = choosePresenza(presenze).orElse(null);
			}

			if (target == null) {
				return;
			}

			if (ricettaDao == null) {
				ricettaDao = new RicettaDao();
			}

			List<Ricetta> tutteLeRicette = ricettaDao.findAll();
			if (tutteLeRicette == null) {
				tutteLeRicette = Collections.emptyList();
			}

			List<Ricetta> associate = sessioneDao.findRicetteBySessionePresenza(target.getId());
			if (associate == null) {
				associate = Collections.emptyList();
			}

			URL url = AssociaRicetteController.class.getResource("/it/unina/foodlab/ui/AssociaRicette.fxml");
			if (url == null) {
				throw new IllegalStateException("FXML non trovato: /AssociaRicette.fxml");
			}

			List<Ricetta> tutteFinal = tutteLeRicette;
			List<Ricetta> associateFinal = associate;

			FXMLLoader fx = new FXMLLoader(url);
			fx.setControllerFactory(t -> {
				if (t == AssociaRicetteController.class) {
					return new AssociaRicetteController(
							sessioneDao,
							target.getId(),
							tutteFinal,
							associateFinal
							);
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
			if (initialRows > 0) {
				ctrl.initWithCorsoAndBlank(corso, initialRows);
			} else {
				ctrl.initWithCorso(corso);
			}

			Node content = pane.getContent();
			if (content instanceof Region region) {
				ScrollPane sc = new ScrollPane(region);
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
			pane.setPrefSize(1600, 800);
			dlg.setResizable(true);

			dlg.setResultConverter(bt -> {
				if (bt != null && bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
					return ctrl.buildResult();
				}
				return null;
			});

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
			String data = sp.getData() != null ? df.format(sp.getData()) : "";

			String oraInizio = sp.getOraInizio() != null ? tf.format(sp.getOraInizio()) : "";
			String oraFine = sp.getOraFine() != null ? tf.format(sp.getOraFine()) : "";
			String orari = oraInizio + "-" + oraFine;

			String indirizzo = joinNonEmpty(" ", nz(sp.getVia()), nz(sp.getNum()), nz(sp.getCap())).trim();
			String base = (data + " " + orari + " " + indirizzo).trim();

			String key = base;
			int suffix = 2;
			while (map.containsKey(key)) {
				key = base + " (" + suffix + ")";
				suffix++;
			}

			map.put(key, sp);
		}

		Dialog<String> dlg = new Dialog<>();
		dlg.setTitle("Seleziona la sessione in presenza");
		dlg.setHeaderText("Scegli la sessione a cui associare le ricette");
		dlg.getDialogPane().getStyleClass().add("dark-dialog1");

		ButtonType okType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelType = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
		dlg.getDialogPane().getButtonTypes().setAll(okType, cancelType);

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

		dlg.setResultConverter(bt -> {
			if (bt == okType) {
				return cb.getValue();
			}
			return null;
		});

		Optional<String> pick = dlg.showAndWait();
		if (pick.isEmpty()) {
			return Optional.empty();
		}

		String selectedKey = pick.get();
		SessionePresenza chosen = map.get(selectedKey);
		return Optional.ofNullable(chosen);
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
		return s == null ? "" : s;
	}

	private static String nz(int n) {
		return n > 0 ? String.valueOf(n) : "";
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static boolean matchesEqIgnoreCase(String value, String filter) {
		if (isBlank(filter)) return true;
		return value != null && value.equalsIgnoreCase(filter);
	}

	private boolean isOwnedByLoggedChef(Corso c) {
		if (c == null || c.getChef() == null || isBlank(c.getChef().getCF_Chef())) {
			return false;
		}
		String owner = corsoDao != null ? corsoDao.getOwnerCfChef() : null;
		return !isBlank(owner) && c.getChef().getCF_Chef().equalsIgnoreCase(owner);
	}


	private void refreshArgomentiCondivisi() {
		try {
			List<String> distinct = corsoDao.findDistinctArgomenti();
			argomentiCondivisi.setAll(distinct != null ? distinct : Collections.emptyList());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private Alert makeDarkAlert(Alert.AlertType type, String title, String message) {
		Alert alert = new Alert(type);
		alert.setTitle(title == null ? "" : title);
		alert.setHeaderText(null);
		alert.setGraphic(null);

		Label lbl = new Label(message == null ? "" : message);
		lbl.setWrapText(true);

		DialogPane dp = alert.getDialogPane();
		dp.setContent(lbl);
		dp.getStyleClass().add("dark-dialog");
		dp.getStylesheets().add(
				getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
				);

		dp.setMinWidth(460);
		return alert;
	}

	private void showError(String msg) {
		makeDarkAlert(Alert.AlertType.ERROR, "Errore", msg).showAndWait();
	}

	private void showInfoDark(String msg) {
		makeDarkAlert(Alert.AlertType.INFORMATION, "Informazione", msg).showAndWait();
	}

	private boolean showConfirmDark(String titolo, String messaggio) {
		Alert alert = makeDarkAlert(Alert.AlertType.CONFIRMATION, titolo, messaggio);

		ButtonType ok = new ButtonType("Conferma", ButtonBar.ButtonData.OK_DONE);
		ButtonType annulla = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(ok, annulla);

		Optional<ButtonType> res = alert.showAndWait();
		return res.isPresent() && res.get() == ok;
	}

}
