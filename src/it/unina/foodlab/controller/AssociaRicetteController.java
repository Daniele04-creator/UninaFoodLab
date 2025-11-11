package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;

public class AssociaRicetteController extends Dialog<List<Long>> {

	@FXML private VBox root;
	@FXML private TextField txtSearch;
	@FXML private ChoiceBox<String> chDifficolta;
	@FXML private Button btnSelAll, btnSelNone;
	@FXML private TableView<Riga> table;
	@FXML private TableColumn<Riga, Boolean> colChk;
	@FXML private TableColumn<Riga, String> colNome, colDiff, colDesc;
	@FXML private TableColumn<Riga, Number> colTempo;
	@FXML private Region topBarSpacer;

	private final SessioneDao sessioneDao;
	private final int idSessionePresenza;
	private final List<Ricetta> tutteLeRicette;
	private final List<Ricetta> ricetteGiaAssociate;

	private final ObservableSet<Long> selectedIds = FXCollections.observableSet();
	private FilteredList<Riga> filtered;

	public AssociaRicetteController(SessioneDao sessioneDao,
			int idSessionePresenza,
			List<Ricetta> tutteLeRicette,
			List<Ricetta> ricetteGiaAssociate) {
		this.sessioneDao = Objects.requireNonNull(sessioneDao, "sessioneDao");
		this.idSessionePresenza = idSessionePresenza;
		this.tutteLeRicette = tutteLeRicette != null ? tutteLeRicette : Collections.emptyList();
		this.ricetteGiaAssociate = ricetteGiaAssociate != null ? ricetteGiaAssociate : Collections.emptyList();

		setTitle("Associa ricette alla sessione (id=" + idSessionePresenza + ")");
		getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
		setResultConverter(bt -> {
			if (bt != ButtonType.OK) return null;
			List<Long> out = new ArrayList<>(selectedIds);
			Collections.sort(out);
			return out;
		});
	}

	@FXML
	private void initialize() {
		getDialogPane().setContent(root);
		getDialogPane().getStyleClass().add("associa-ricette-dialog");
		getDialogPane().getStylesheets().add(
				Objects.requireNonNull(getClass().getResource("/it/unina/foodlab/util/dark-theme.css")).toExternalForm()
				);
		getDialogPane().setPrefSize(960, 620);
		setResizable(true);

		Platform.runLater(() -> {
			Stage st = (Stage) getDialogPane().getScene().getWindow();
			if (st != null) {
				st.sizeToScene();
				st.setMinWidth(880);
				st.setMinHeight(560);
			}
		});

		chDifficolta.setItems(FXCollections.observableArrayList("Tutte", "Facile", "Medio", "Difficile"));
		chDifficolta.getSelectionModel().selectFirst();
		chDifficolta.getStyleClass().add("dark-choicebox");

		txtSearch.setPromptText("Cerca per nome o descrizione");
		txtSearch.getStyleClass().add("dark-textfield");

		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		table.setTableMenuButtonVisible(false);
		table.setPlaceholder(new Label("Nessuna ricetta trovata."));
		table.setFixedCellSize(40);
		table.getStyleClass().add("dark-table");

		colChk.setPrefWidth(60);
		colChk.setStyle("-fx-alignment: CENTER;");
		colChk.setCellValueFactory(c -> c.getValue().checked);
		colChk.setCellFactory(tc -> {
			CheckBoxTableCell<Riga, Boolean> cell = new CheckBoxTableCell<>();
			cell.setAlignment(Pos.CENTER);
			return cell;
		});

		colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));

		colDiff.setPrefWidth(140);
		colDiff.setCellValueFactory(new PropertyValueFactory<>("difficolta"));
		colDiff.setCellFactory(tc -> new TableCell<>() {
			private final Label chip = new Label();
			@Override
			protected void updateItem(String s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null) {
					setGraphic(null);
					return;
				}
				chip.setText(s);
				String cls = switch (s.trim().toLowerCase(Locale.ROOT)) {
				case "facile" -> "diff-facile";
				case "medio" -> "diff-medio";
				case "difficile" -> "diff-difficile";
				default -> "diff-unknown";
				};
				chip.getStyleClass().setAll("diff-chip", cls);
				setGraphic(chip);
			}
		});

		colTempo.setPrefWidth(140);
		colTempo.setStyle("-fx-alignment: CENTER;");
		colTempo.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().tempoPreparazione.get()));
		colTempo.setCellFactory(tc -> new TableCell<>() {
			{
				setStyle("-fx-alignment: CENTER;");
			}
			@Override
			protected void updateItem(Number n, boolean empty) {
				super.updateItem(n, empty);
				setText(empty || n == null ? null : n.intValue() + " min");
			}
		});

		colDesc.setCellValueFactory(new PropertyValueFactory<>("descrizione"));
		colDesc.setCellFactory(tc -> new TableCell<>() {
			private final Label lbl = new Label();
			{
				lbl.getStyleClass().add("muted-text");
				lbl.setEllipsisString("…");
			}
			@Override
			protected void updateItem(String s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null) {
					setGraphic(null);
					return;
				}
				lbl.setText(s);
				setGraphic(lbl);
			}
		});

		table.setEditable(true);
		colChk.setEditable(true);
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		table.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.SPACE) {
				List<Riga> sel = new ArrayList<>(table.getSelectionModel().getSelectedItems());
				if (!sel.isEmpty()) {
					boolean allChecked = sel.stream().allMatch(r -> r.checked.get());
					boolean newVal = !allChecked;
					sel.forEach(r -> r.checked.set(newVal));
					e.consume();
				}
			}
		});

		table.setRowFactory(tv -> {
			TableRow<Riga> row = new TableRow<>();
			row.setOnMouseClicked(evt -> {
				if (row.isEmpty()) return;
				if (evt.getClickCount() == 2 && evt.getButton() == MouseButton.PRIMARY) {
					Riga riga = row.getItem();
					riga.checked.set(!riga.checked.get());
				} else if (evt.getButton() == MouseButton.PRIMARY && (evt.isControlDown() || evt.isShortcutDown())) {
					Riga riga = row.getItem();
					riga.checked.set(!riga.checked.get());
					evt.consume();
				}
			});
			return row;
		});

		ObservableList<Riga> righe = FXCollections.observableArrayList();
		for (Ricetta r : tutteLeRicette) {
			if (r != null) righe.add(new Riga(r));
		}

		righe.forEach(r ->
		r.checked.addListener((obs, oldV, newV) -> {
			if (Boolean.TRUE.equals(newV)) selectedIds.add(r.idRicetta);
			else selectedIds.remove(r.idRicetta);
		})
				);

		filtered = new FilteredList<>(righe, r -> true);
		table.setItems(filtered);

		for (Ricetta r : ricetteGiaAssociate) {
			if (r != null) selectedIds.add(r.getIdRicetta());
		}
		filtered.forEach(r -> r.checked.set(selectedIds.contains(r.idRicetta)));

		txtSearch.textProperty().addListener((o, ov, nv) -> applyFilter());
		chDifficolta.valueProperty().addListener((o, ov, nv) -> applyFilter());

		if (btnSelAll != null) btnSelAll.setOnAction(this::selectAll);
		if (btnSelNone != null) btnSelNone.setOnAction(this::selectNone);
	}

	private void applyFilter() {
		if (filtered == null) return;
		String q = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
		String diff = Optional.ofNullable(chDifficolta.getValue()).orElse("Tutte");
		filtered.setPredicate(r -> {
			if (r == null) return false;
			boolean okTxt = q.isEmpty()
					|| containsIgnoreCase(r.nome.get(), q)
					|| containsIgnoreCase(r.descrizione.get(), q);
			boolean okDiff = "Tutte".equalsIgnoreCase(diff)
					|| (r.difficolta.get() != null && diff.equalsIgnoreCase(r.difficolta.get()));
			return okTxt && okDiff;
		});
	}

	private void selectAll(ActionEvent e) {
		if (filtered != null) filtered.forEach(r -> r.checked.set(true));
	}

	private void selectNone(ActionEvent e) {
		if (filtered != null) filtered.forEach(r -> r.checked.set(false));
	}

	public void salvaSeConfermato(Optional<List<Long>> resultOpt) {
		if (resultOpt == null || resultOpt.isEmpty()) return;
		List<Long> selectedNow = resultOpt.get();

		Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
		Button cancelBtn = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
		if (okBtn != null) okBtn.setDisable(true);
		if (cancelBtn != null) cancelBtn.setDisable(true);

		Task<Void> task = new Task<>() {
			@Override
			protected Void call() throws Exception {
				List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);
				Set<Long> before = new HashSet<>();
				if (gia != null) {
					for (Ricetta r : gia) if (r != null) before.add(r.getIdRicetta());
				}

				Set<Long> after = new HashSet<>(selectedNow);

				for (Long idAdd : after) if (!before.contains(idAdd))
					sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);

				for (Long idRem : before) if (!after.contains(idRem))
					sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);

				return null;
			}
		};

		task.setOnSucceeded(ev -> {
			showInfoDark("Operazione completata", "Associazioni ricette salvate correttamente.");
			if (okBtn != null) okBtn.setDisable(false);
			if (cancelBtn != null) cancelBtn.setDisable(false);
		});

		task.setOnFailed(ev -> {
			Throwable ex = task.getException();
			showErrorDark("Errore salvataggio", ex != null && ex.getMessage() != null ? ex.getMessage() : "Errore sconosciuto.");
			if (okBtn != null) okBtn.setDisable(false);
			if (cancelBtn != null) cancelBtn.setDisable(false);
		});

		Thread t = new Thread(task, "save-associa-ricette");
		t.setDaemon(true);
		t.start();
	}

	private static boolean containsIgnoreCase(String s, String q) {
		return s != null && q != null && s.toLowerCase(Locale.ROOT).contains(q);
	}

	public static class Riga {
		final long idRicetta;
		final javafx.beans.property.SimpleBooleanProperty checked = new javafx.beans.property.SimpleBooleanProperty(false);
		final javafx.beans.property.SimpleStringProperty nome = new javafx.beans.property.SimpleStringProperty();
		final javafx.beans.property.SimpleStringProperty descrizione = new javafx.beans.property.SimpleStringProperty();
		final javafx.beans.property.SimpleStringProperty difficolta = new javafx.beans.property.SimpleStringProperty();
		final javafx.beans.property.SimpleIntegerProperty tempoPreparazione = new javafx.beans.property.SimpleIntegerProperty();

		public Riga(Ricetta r) {
			this.idRicetta = r.getIdRicetta();
			this.nome.set(Objects.toString(r.getNome(), ""));
			this.descrizione.set(Objects.toString(r.getDescrizione(), ""));
			this.difficolta.set(Objects.toString(r.getDifficolta(), ""));
			Integer tp = r.getTempoPreparazione();
			this.tempoPreparazione.set(tp != null && tp >= 0 ? tp : 0);
		}

		public long getIdRicetta() { return idRicetta; }
		public String getNome() { return nome.get(); }
		public String getDescrizione() { return descrizione.get(); }
		public String getDifficolta() { return difficolta.get(); }
		public Integer getTempoPreparazione() { return tempoPreparazione.get(); }
		public javafx.beans.property.BooleanProperty checkedProperty() { return checked; }
	}

	private void showInfoDark(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle(title == null ? "Messaggio" : title);
		alert.setHeaderText(null);
		alert.setGraphic(null);
		alert.getDialogPane().setContent(new Label(message == null ? "" : message));
		DialogPane dp = alert.getDialogPane();
		dp.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/it/unina/foodlab/util/dark-theme.css")).toExternalForm());
		dp.getStyleClass().add("associa-ricette-dialog");
		dp.setMinWidth(460);
		alert.showAndWait();
	}

	private void showErrorDark(String title, String msg) {
		Alert a = new Alert(Alert.AlertType.ERROR);
		a.setTitle(title == null ? "Errore" : title);
		a.setHeaderText(null);
		a.getDialogPane().setContent(new Label(msg == null ? "" : msg));
		DialogPane dp = a.getDialogPane();
		dp.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/it/unina/foodlab/util/dark-theme.css")).toExternalForm());
		dp.getStyleClass().add("associa-ricette-dialog");
		dp.setMinWidth(520);
		a.showAndWait();
	}
}
