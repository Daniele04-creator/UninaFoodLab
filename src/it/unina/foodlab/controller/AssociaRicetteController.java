package it.unina.foodlab.controller;

import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Ricetta;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.VBox;

import java.util.*;

public class AssociaRicetteController extends Dialog<List<Long>> {

	@FXML private VBox root;
	@FXML private TableView<Riga> table;
	@FXML private TableColumn<Riga, Boolean> colChk;
	@FXML private TableColumn<Riga, Number> colTempo;

	private final SessioneDao sessioneDao;
	private final int idSessionePresenza;
	private final List<Ricetta> tutteLeRicette;
	private final List<Ricetta> ricetteGiaAssociate;

	public AssociaRicetteController(SessioneDao sessioneDao,
			int idSessionePresenza,
			List<Ricetta> tutteLeRicette,
			List<Ricetta> ricetteGiaAssociate) {

		this.sessioneDao = Objects.requireNonNull(sessioneDao);
		this.idSessionePresenza = idSessionePresenza;
		this.tutteLeRicette = tutteLeRicette != null ? tutteLeRicette : Collections.emptyList();
		this.ricetteGiaAssociate = ricetteGiaAssociate != null ? ricetteGiaAssociate : Collections.emptyList();

		setTitle("Associa ricette alla sessione pratica");
		getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);

		setResultConverter(bt -> {
			if (bt != ButtonType.OK) {
				return null;
			}
			List<Long> ids = new ArrayList<>();
			for (Riga r : table.getItems()) {
				if (r.checkedProperty().get()) {
					ids.add(r.getIdRicetta());
				}
			}
			ids.sort(Long::compareTo);
			return ids;
		});

	}

	@FXML
	private void initialize() {
	    DialogPane pane = getDialogPane();
	    pane.setContent(root);
	    pane.getStyleClass().add("associa-ricette-dialog");
	    pane.getStylesheets().add(
	            getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
	    );
	    pane.setPrefSize(960, 620);
	    setResizable(true);

	    colChk.setCellValueFactory(c -> c.getValue().checkedProperty());
	    colChk.setCellFactory(CheckBoxTableCell.forTableColumn(colChk));
	    colChk.setEditable(true);

	    colTempo.setCellValueFactory(c ->
	            new ReadOnlyIntegerWrapper(c.getValue().getTempoPreparazione()));

	    Set<Long> preSel = new HashSet<>();
	    for (Ricetta r : ricetteGiaAssociate) {
	        preSel.add(r.getIdRicetta());
	    }

	    ObservableList<Riga> righe = FXCollections.observableArrayList();
	    for (Ricetta r : tutteLeRicette) {
	        Riga riga = new Riga(r);
	        riga.checkedProperty().set(preSel.contains(r.getIdRicetta()));
	        righe.add(riga);
	    }

	    table.setItems(righe);
	}

	@FXML
	private void selectAll(ActionEvent e) {
		for (Riga r : table.getItems()) {
			r.checkedProperty().set(true);
		}
	}

	@FXML
	private void selectNone(ActionEvent e) {
		for (Riga r : table.getItems()) {
			r.checkedProperty().set(false);
		}
	}

	public void salvaSeConfermato(List<Long> result) {
		if (result == null) {
			return;
		}

		Set<Long> after = new HashSet<>(result);

		try {
			List<Ricetta> gia = sessioneDao.findRicetteBySessionePresenza(idSessionePresenza);
			Set<Long> before = new HashSet<>();
			if (gia != null) {
				for (Ricetta r : gia) {
					before.add(r.getIdRicetta());
				}
			}

			if (before.equals(after)) {
				return;
			}

			for (Long idAdd : after) {
				if (!before.contains(idAdd)) {
					sessioneDao.addRicettaToSessionePresenza(idSessionePresenza, idAdd);
				}
			}

			for (Long idRem : before) {
				if (!after.contains(idRem)) {
					sessioneDao.removeRicettaFromSessionePresenza(idSessionePresenza, idRem);
				}
			}

			showInfoDark("Operazione completata", "Associazioni ricette salvate correttamente.");
		} catch (Exception ex) {
			String msg = ex.getMessage() != null ? ex.getMessage() : "Errore sconosciuto.";
			showErrorDark("Errore salvataggio", msg);
		}
	}


	public static class Riga {
		private final long idRicetta;
		private final BooleanProperty checked = new SimpleBooleanProperty(false);
		private final StringProperty nome = new SimpleStringProperty();
		private final StringProperty descrizione = new SimpleStringProperty();
		private final StringProperty difficolta = new SimpleStringProperty();
		private final IntegerProperty tempoPreparazione = new SimpleIntegerProperty();

		public Riga(Ricetta r) {
			this.idRicetta = r.getIdRicetta();
			nome.set(r.getNome() != null ? r.getNome() : "");
			descrizione.set(r.getDescrizione() != null ? r.getDescrizione() : "");
			difficolta.set(r.getDifficolta() != null ? r.getDifficolta() : "");
			Integer tp = r.getTempoPreparazione();
			tempoPreparazione.set(tp != null && tp >= 0 ? tp : 0);
		}


		public long getIdRicetta() { return idRicetta; }
		public String getNome() { return nome.get(); }
		public String getDescrizione() { return descrizione.get(); }
		public String getDifficolta() { return difficolta.get(); }
		public int getTempoPreparazione() { return tempoPreparazione.get(); }
		public BooleanProperty checkedProperty() { return checked; }
	}


	private void showAlert(Alert.AlertType type, String title, String text) {
		Alert a = new Alert(type);
		a.setTitle(title != null ? title : "");
		a.setHeaderText(null);
		a.getDialogPane().setContent(new Label(text != null ? text : ""));
		a.getDialogPane().getStylesheets().add(
				getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
				);
		a.getDialogPane().getStyleClass().add("associa-ricette-dialog");
		a.showAndWait();
	}

	private void showInfoDark(String title, String message) {
		showAlert(Alert.AlertType.INFORMATION, title == null ? "Messaggio" : title, message);
	}

	private void showErrorDark(String title, String message) {
		showAlert(Alert.AlertType.ERROR, title == null ? "Errore" : title, message);
	}

}
