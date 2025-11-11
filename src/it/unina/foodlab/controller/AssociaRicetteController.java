package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;

public class AssociaRicetteController extends Dialog<List<Long>> {


	@FXML private VBox root;
	@FXML private TextField txtSearch;
	@FXML private ChoiceBox<String>  chDifficolta;
	@FXML private Button btnSelAll, btnSelNone;
	@FXML private TableView<Riga>    table;
	@FXML private TableColumn<Riga, Boolean> colChk;
	@FXML private TableColumn<Riga, String>  colNome, colDiff, colDesc;
	@FXML private TableColumn<Riga, Number>  colTempo;
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
		this.sessioneDao = sessioneDao;
		this.idSessionePresenza = idSessionePresenza;
		this.tutteLeRicette = (tutteLeRicette != null) ? tutteLeRicette : Collections.emptyList(); 
		this.ricetteGiaAssociate = (ricetteGiaAssociate != null) ? ricetteGiaAssociate : Collections.emptyList(); 


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
				Objects.requireNonNull(
						getClass().getResource("/it/unina/foodlab/util/dark-theme.css")
						).toExternalForm()
				);

		getDialogPane().setPrefSize(960, 620);
		setResizable(true);


		Platform.runLater(() -> {
			Scene sc = getDialogPane().getScene();
			if (sc != null) {
				sc.getRoot().getStyleClass().add("associa-ricette-dialog");
			}
		});


		Platform.runLater(() -> {
			Stage st = (Stage) getDialogPane().getScene().getWindow();
			if (st != null) {
				st.sizeToScene();
				st.setMinWidth(880);
				st.setMinHeight(560);
			}
		});


		chDifficolta.setItems(FXCollections.observableArrayList("Tutte","Facile","Medio","Difficile"));
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
			cell.setAlignment(javafx.geometry.Pos.CENTER);
			return cell;
		});


		colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));


		colDiff.setPrefWidth(140);
		colDiff.setCellValueFactory(new PropertyValueFactory<>("difficolta"));
		colDiff.setCellFactory(tc -> new TableCell<Riga, String>() {
			private final Label chip = new Label();
			@Override
			protected void updateItem(String s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null) { setGraphic(null); return; }
				chip.setText(s);
				chip.getStyleClass().setAll("diff-chip",
						switch (s.trim().toLowerCase(Locale.ROOT)) {
						case "facile"    -> "diff-facile";
						case "medio"     -> "diff-medio";
						case "difficile" -> "diff-difficile";
						default          -> "diff-unknown";
						}
						);
				setGraphic(chip);
			}
		});


		colTempo.setPrefWidth(140);
		colTempo.setStyle("-fx-alignment: CENTER;");
		colTempo.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().tempoPreparazione.get()));
		colTempo.setCellFactory(tc -> new TableCell<>() {
			@Override protected void updateItem(Number n, boolean empty) {
				super.updateItem(n, empty);
				setText(empty || n == null ? null : n.intValue() + " min");
			}
		});


		colDesc.setCellValueFactory(new PropertyValueFactory<>("descrizione"));
		colDesc.setCellFactory(tc -> new TableCell<Riga, String>() {
			private final Label lbl = new Label();
			{
				lbl.getStyleClass().add("muted-text"); 
				lbl.setEllipsisString("…");            
			}
			@Override
			protected void updateItem(String s, boolean empty) {
				super.updateItem(s, empty);
				if (empty || s == null) { setGraphic(null); return; }
				lbl.setText(s);
				setGraphic(lbl);
			}
		});



		table.setEditable(true);
		colChk.setEditable(true);
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		table.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.SPACE) {
				var sel = new ArrayList<>(table.getSelectionModel().getSelectedItems());
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
				if (evt.getClickCount() == 2 && evt.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
					Riga riga = row.getItem();
					riga.checked.set(!riga.checked.get());
				} else if (evt.getButton() == javafx.scene.input.MouseButton.PRIMARY
						&& (evt.isControlDown() || evt.isShortcutDown())) {
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
			else                           selectedIds.remove(r.idRicetta);
		})
				);

		filtered = new FilteredList<>(righe, r -> true);
		table.setItems(filtered);


		for (Ricetta r : ricetteGiaAssociate) {
			if (r != null) selectedIds.add(r.getIdRicetta());
		}
		filtered.forEach(r -> r.checked.set(selectedIds.contains(r.idRicetta)));


		txtSearch.textProperty().addListener((o,ov,nv) -> applyFilter());
		chDifficolta.valueProperty().addListener((o,ov,nv) -> applyFilter());


		if (btnSelAll   != null) btnSelAll.setOnAction(this::selectAll);
		if (btnSelNone  != null) btnSelNone.setOnAction(this::selectNone);
	}

	private void applyFilter() {
		if (filtered == null) return;
		final String q = (txtSearch.getText() == null) ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
		final String diff = Optional.ofNullable(chDifficolta.getValue()).orElse("Tutte");
		filtered.setPredicate(r -> {
			if (r == null) return false;
			boolean okTxt  = q.isEmpty()
					|| containsIgnoreCase(r.nome.get(), q)
					|| containsIgnoreCase(r.descrizione.get(), q);
			boolean okDiff = "Tutte".equalsIgnoreCase(diff)
					|| (r.difficolta.get() != null && diff.equalsIgnoreCase(r.difficolta.get()));
			return okTxt && okDiff;
		});
	}

	private void selectAll(ActionEvent e)  { if (filtered != null) filtered.forEach(r -> r.checked.set(true)); }
	private void selectNone(ActionEvent e) { if (filtered != null) filtered.forEach(r -> r.checked.set(false)); }

	public void salvaSeConfermato(Optional<List<Long>> resultOpt) {
		if (resultOpt == null || resultOpt.isEmpty()) return;
		List<Long> selectedNow = resultOpt.get();
		try {
			List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);
			Set<Long> before = new HashSet<>();
			for (Ricetta r : gia) if (r != null) before.add(r.getIdRicetta());
			Set<Long> after = new HashSet<>(selectedNow);

			for (Long idAdd : after)   if (!before.contains(idAdd)) sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);
			for (Long idRem : before)  if (!after.contains(idRem))  sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);

			showInfoDark("Operazione completata", "Associazioni ricette salvate correttamente.");
		} catch (Exception ex) {
			showErrorDark("Errore salvataggio", ex.getMessage());
		}
	}



	private static boolean containsIgnoreCase(String s, String q) {
		return s != null && q != null && s.toLowerCase(Locale.ROOT).contains(q);
	}


	public static class Riga {
		final long idRicetta;
		final javafx.beans.property.SimpleBooleanProperty checked = new javafx.beans.property.SimpleBooleanProperty(false);
		final javafx.beans.property.SimpleStringProperty  nome        = new javafx.beans.property.SimpleStringProperty();
		final javafx.beans.property.SimpleStringProperty  descrizione = new javafx.beans.property.SimpleStringProperty();
		final javafx.beans.property.SimpleStringProperty  difficolta  = new javafx.beans.property.SimpleStringProperty();
		final javafx.beans.property.SimpleIntegerProperty tempoPreparazione = new javafx.beans.property.SimpleIntegerProperty();

		public Riga(Ricetta r) {
			this.idRicetta = r.getIdRicetta();
			this.nome.set(r.getNome());
			this.descrizione.set(r.getDescrizione());
			this.difficolta.set(r.getDifficolta());
			this.tempoPreparazione.set(r.getTempoPreparazione());
		}

		public long getIdRicetta() { return idRicetta; }
		public String getNome()    { return nome.get(); }
		public String getDescrizione() { return descrizione.get(); }
		public String getDifficolta()  { return difficolta.get(); }
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
		dp.getStylesheets().add(
				Objects.requireNonNull(getClass().getResource("/it/unina/foodlab/util/dark-theme.css")).toExternalForm()
				);
		dp.getStyleClass().add("associa-ricette-dialog");


		dp.setMinWidth(460);
		alert.showAndWait();
	}

	private void showErrorDark(String title, String msg) {
		Alert a = new Alert(Alert.AlertType.ERROR);
		a.setTitle(title);
		a.setHeaderText(null);
		a.getDialogPane().setContent(new Label(msg == null ? "" : msg));


		DialogPane dp = a.getDialogPane();
		dp.getStylesheets().add(
				Objects.requireNonNull(getClass().getResource("/it/unina/foodlab/util/dark-theme.css")).toExternalForm()
				);
		dp.getStyleClass().add("associa-ricette-dialog");

		dp.setMinWidth(520);
		a.showAndWait();
	}
}
