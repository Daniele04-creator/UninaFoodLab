package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SessioniWizardController {

	private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter HF = DateTimeFormatter.ofPattern("HH:mm");

	@FXML private DialogPane dialogPane;
	@FXML private ButtonType okButtonType;
	@FXML private ButtonType cancelButtonType;

	@FXML private TableView<Sessione> table;
	@FXML private TableColumn<Sessione, String> cData;
	@FXML private TableColumn<Sessione, String> cInizio;
	@FXML private TableColumn<Sessione, String> cFine;
	@FXML private TableColumn<Sessione, String> cTipo;
	@FXML private TableColumn<Sessione, String> cPiattaforma;
	@FXML private TableColumn<Sessione, String> cVia;
	@FXML private TableColumn<Sessione, String> cNum;
	@FXML private TableColumn<Sessione, String> cCap;
	@FXML private TableColumn<Sessione, String> cAla;
	@FXML private TableColumn<Sessione, String> cPosti;

	private Corso corso;
	private final ObservableList<Sessione> model = FXCollections.observableArrayList();

	@FXML
	private void initialize() {
		table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		table.setItems(model);
		setupColumns();
		setupOkCancelButtons();
	}

	public void initWithCorso(Corso c) {
		initWithCorsoAndExisting(c, null);
	}

	public void initWithCorsoAndExisting(Corso c, List<Sessione> esistenti) {
		if (c == null) {
			throw new IllegalStateException("Corso nullo");
		}
		this.corso = c;
		model.clear();
		if (esistenti != null) {
			model.addAll(esistenti);
		}
	}


	public List<Sessione> buildResult() {
		return new ArrayList<>(model);
	}

	@FXML
	private void onNuovo(ActionEvent e) {
		LocalDate suggested = null;
		if (!model.isEmpty()) {
			Sessione last = model.get(model.size() - 1);
			LocalDate lastDate = last.getData();
			if (lastDate != null && corso != null) {
				suggested = computeNthDate(lastDate, corso.getFrequenza(), 1);
			}

		}
		SessionePresenza s = new SessionePresenza();
		s.setCorso(corso);
		s.setData(suggested);
		model.add(s);
		table.getSelectionModel().select(model.size() - 1);
		table.scrollTo(model.size() - 1);
	}

	@FXML
	private void onRimuovi(ActionEvent e) {
		int idx = table.getSelectionModel().getSelectedIndex();
		if (idx >= 0 && idx < model.size()) {
			model.remove(idx);
		}
	}

	private void setupColumns() {
		cData.setCellValueFactory(cd -> new SimpleStringProperty(
				cd.getValue() != null && cd.getValue().getData() != null
				? DF.format(cd.getValue().getData())
						: ""
				));
		cData.setCellFactory(tc -> new TextCell());

		cInizio.setCellValueFactory(cd -> new SimpleStringProperty(
				cd.getValue() != null && cd.getValue().getOraInizio() != null
				? HF.format(cd.getValue().getOraInizio())
						: ""
				));
		cInizio.setCellFactory(tc -> new TimeCell(true));
		cInizio.setEditable(true);

		cFine.setCellValueFactory(cd -> new SimpleStringProperty(
				cd.getValue() != null && cd.getValue().getOraFine() != null
				? HF.format(cd.getValue().getOraFine())
						: ""
				));
		cFine.setCellFactory(tc -> new TimeCell(false));
		cFine.setEditable(true);

		cTipo.setCellValueFactory(cd -> new SimpleStringProperty(
				cd.getValue() instanceof SessionePresenza ? "In presenza" : "Online"
				));
		cTipo.setCellFactory(tc -> new TipoCell());
		cTipo.setEditable(true);

		setupEditableColumn(cPiattaforma, FieldKind.K_PIATTAFORMA);
		setupEditableColumn(cVia,         FieldKind.K_VIA);
		setupEditableColumn(cNum,         FieldKind.K_NUM);
		setupEditableColumn(cCap,         FieldKind.K_CAP);
		setupEditableColumn(cAla,         FieldKind.K_AULA);
		setupEditableColumn(cPosti,       FieldKind.K_POSTI);
	}

	private void setupEditableColumn(final TableColumn<Sessione, String> col, final int kind) {
		col.setEditable(true);
		col.setCellValueFactory(param -> new SimpleStringProperty(getFieldText(param.getValue(), kind)));
		col.setCellFactory(tc -> new EditableFieldCell(kind));
		col.setStyle("-fx-alignment: CENTER-LEFT;");
	}

	private String getFieldText(Sessione s, int kind) {
		if (s == null) {
			return "";
		}
		if (s instanceof SessioneOnline on) {
			return kind == FieldKind.K_PIATTAFORMA ? safe(on.getPiattaforma()) : "";
		} else {
			SessionePresenza sp = (SessionePresenza) s;
			switch (kind) {
			case FieldKind.K_VIA:
				return safe(sp.getVia());
			case FieldKind.K_NUM:
				return safe(sp.getNum());
			case FieldKind.K_CAP:
				return sp.getCap() <= 0 ? "" : String.valueOf(sp.getCap());
			case FieldKind.K_AULA:
				return safe(sp.getAula());
			case FieldKind.K_POSTI:
				return sp.getPostiMax() <= 0 ? "" : String.valueOf(sp.getPostiMax());
			default:
				return "";
			}
		}
	}

	private static final class FieldKind {
		static final int K_PIATTAFORMA = 1;
		static final int K_VIA = 2;
		static final int K_NUM = 3;
		static final int K_CAP = 4;
		static final int K_AULA = 5;
		static final int K_POSTI = 6;
		private FieldKind() {}
	}

	private static final class TextCell extends TableCell<Sessione, String> {
		@Override
		protected void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			setText(empty ? null : item);
			setGraphic(null);
		}
	}

	private final class EditableFieldCell extends TableCell<Sessione, String> {
		private final int kind;
		private final TextField tf = new TextField();

		EditableFieldCell(int kind) {
			this.kind = kind;
			tf.setFocusTraversable(true);
			tf.setMaxWidth(Double.MAX_VALUE);
			tf.setOnAction(ev -> commit());
			tf.focusedProperty().addListener((o, was, is) -> {
				if (!is) commit();
			});
			setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		}

		private void commit() {
			int idx = getIndex();
			if (idx < 0 || idx >= table.getItems().size()) {
				return;
			}

			Sessione s = table.getItems().get(idx);

			if (s instanceof SessioneOnline on) {
				if (kind == FieldKind.K_PIATTAFORMA) {
					on.setPiattaforma(tf.getText());
				}
			} else if (s instanceof SessionePresenza sp) {
				switch (kind) {
				case FieldKind.K_VIA:
					sp.setVia(tf.getText());
					break;
				case FieldKind.K_NUM:
					sp.setNum(tf.getText());
					break;
				case FieldKind.K_CAP: {
					int value = parseIntOrWarn(tf.getText(), "CAP");
					if (value < 0) return;
					sp.setCap(value);
					break;
				}
				case FieldKind.K_AULA:
					sp.setAula(tf.getText());
					break;

				case FieldKind.K_POSTI: {
					int value = parseIntOrWarn(tf.getText(), "Posti");
					if (value < 0) return;
					sp.setPostiMax(value);
					break;
				}
				default:
					break;
				}
			}

			table.refresh();
		}

		@Override
		protected void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || getTableRow() == null || getTableRow().getItem() == null) {
				setGraphic(null);
				return;
			}
			Sessione s = (Sessione) getTableRow().getItem();
			boolean applicable = (s instanceof SessioneOnline)
					? (kind == FieldKind.K_PIATTAFORMA)
							: (kind != FieldKind.K_PIATTAFORMA);
			if (applicable) {
				tf.setText(item == null ? "" : item);
				tf.setDisable(false);
				setGraphic(tf);
			} else {
				setGraphic(null);
			}
		}
	}

	private final class TimeCell extends TableCell<Sessione, String> {
		private final boolean isStart;
		private final ComboBox<LocalTime> cb = new ComboBox<>(buildTimes(15));
		private final HBox wrapper = new HBox(cb);

		TimeCell(boolean isStart) {
			this.isStart = isStart;
			cb.setVisibleRowCount(10);
			cb.setEditable(true);
			cb.setConverter(new StringConverter<LocalTime>() {
				@Override public String toString(LocalTime t) { return t == null ? "" : HF.format(t); }
				@Override public LocalTime fromString(String s) {
					if (s == null || s.trim().isEmpty()) return null;
					try {
						return LocalTime.parse(s.trim(), HF);
					} catch (Exception e) {
						return null;
					}
				}
			});
			HBox.setHgrow(cb, Priority.ALWAYS);
			setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		}

		@Override
		protected void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || getTableRow()==null || getTableRow().getItem()==null) {
				setGraphic(null);
				return;
			}
			cb.setValue(parseTimeOrNull(item));
			cb.setOnAction(null);
			cb.setOnAction(ev -> {
				Sessione s = (Sessione) getTableRow().getItem();
				LocalTime t = cb.getValue();
				if (s instanceof SessioneOnline so) {
					if (isStart) {
						so.setOraInizio(t);
					} else {
						so.setOraFine(t);
					}
				} else if (s instanceof SessionePresenza sp) {
					if (isStart) {
						sp.setOraInizio(t);
					} else {
						sp.setOraFine(t);
					}
				}
			});
			setGraphic(wrapper);
		}
	}

	private static ObservableList<LocalTime> buildTimes(int stepMinutes) {
		ObservableList<LocalTime> list = FXCollections.observableArrayList();
		LocalTime t = LocalTime.of(0, 0);
		while (true) {
			list.add(t);
			t = t.plusMinutes(stepMinutes);
			if (t.equals(LocalTime.MIDNIGHT)) break;
		}
		return list;
	}

	private static LocalTime parseTimeOrNull(String s) {
		if (s == null || s.trim().isEmpty()) return null;
		try {
			return LocalTime.parse(s, HF);
		} catch (Exception e) {
			return null;
		}
	}

	private final class TipoCell extends TableCell<Sessione, String> {
		private final ComboBox<String> combo = new ComboBox<>();

		TipoCell() {
			combo.getItems().addAll("Online", "In presenza");
			combo.setVisibleRowCount(6);
			combo.setPrefWidth(Double.MAX_VALUE);
			combo.setOnAction(ev -> {
				int idx = getIndex();
				if (idx < 0 || idx >= getTableView().getItems().size()) return;
				Sessione s = getTableView().getItems().get(idx);
				LocalDate d  = s.getData();
				LocalTime oi = s.getOraInizio();
				LocalTime of = s.getOraFine();

				if ("Online".equalsIgnoreCase(combo.getValue()) && !(s instanceof SessioneOnline)) {
					SessioneOnline on = new SessioneOnline();
					on.setCorso(corso);
					on.setData(d);
					on.setOraInizio(oi);
					on.setOraFine(of);
					getTableView().getItems().set(idx, on);
				} else if ("In presenza".equalsIgnoreCase(combo.getValue()) && !(s instanceof SessionePresenza)) {
					SessionePresenza sp = new SessionePresenza();
					sp.setCorso(corso);
					sp.setData(d);
					sp.setOraInizio(oi);
					sp.setOraFine(of);
					getTableView().getItems().set(idx, sp);
				}

				getTableView().refresh();
			});
			setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		}

		@Override
		protected void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || getTableRow() == null || getTableRow().getItem() == null) {
				setGraphic(null);
				setText(null);
				return;
			}
			Sessione s = (Sessione) getTableRow().getItem();
			combo.setValue(s instanceof SessionePresenza ? "In presenza" : "Online");
			setGraphic(combo);
			setText(null);
		}
	}

	private void setupOkCancelButtons() {
		Button okBtn  = (Button) dialogPane.lookupButton(okButtonType);
		Button cancel = (Button) dialogPane.lookupButton(cancelButtonType);

		if (okBtn != null) {
			okBtn.setText("Conferma");
			okBtn.addEventFilter(ActionEvent.ACTION, event -> {
				Optional<String> err = validateBeforeClose();
				if (err.isPresent()) {
					event.consume();
					showInfoDark(err.get());
				}
			});
		}

		if (cancel != null) {
			cancel.setText("Annulla");
		}
	}

	private Optional<String> validateBeforeClose() {
		for (Sessione s : model) {
			if (s instanceof SessioneOnline on) {
				if (isBlank(on.getPiattaforma())) {
					return Optional.of("Inserisci tutti i campi.");
				}
			}
			if (s instanceof SessionePresenza sp) {
				if (isBlank(sp.getVia()) || isBlank(sp.getNum()) || isBlank(sp.getAula()) || sp.getPostiMax() <= 0 || !isValidCAP(String.valueOf(sp.getCap()))) {
					return Optional.of("Inserisci tutti i campi.");
				}
			}
			if (s.getData() == null
					|| s.getOraInizio() == null
					|| s.getOraFine() == null) {
				return Optional.of("Inserisci tutti i campi.");
			}
			if (s.getOraFine().isBefore(s.getOraInizio())) {
				return Optional.of("L’orario di fine deve essere successivo all’inizio.");
			}
		}
		return Optional.empty();
	}



	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private static boolean isValidCAP(String s){
		return s != null && s.matches("\\d{5}");
	}

	private int parseIntOrWarn(String s, String fieldName) {
		if (s == null || s.trim().isEmpty()) {
			showInfoDark("Il campo \"" + fieldName + "\" deve contenere un numero.");
			return -1;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (Exception e) {
			showInfoDark("Il campo \"" + fieldName + "\" deve contenere un valore numerico valido.");
			return -1;
		}
	}

	private static String safe(String s){
		return s == null ? "" : s;
	}

	public void initWithCorsoAndBlank(Corso c, int initialRows) {
		initWithCorso(c);
		addBlankRows(c, initialRows);
	}

	private void addBlankRows(Corso c, int n) {
		if (n <= 0) return;
		LocalDate start = (c != null) ? c.getDataInizio() : null;
		String    freq  = (c != null) ? c.getFrequenza()  : null;
		for (int i = 0; i < n; i++) {
			SessionePresenza sp = new SessionePresenza();
			sp.setCorso(c);
			sp.setData(computeNthDate(start, freq, i));
			model.add(sp);
		}
		if (!model.isEmpty()) {
			table.getSelectionModel().select(model.size()-1);
			table.scrollTo(model.size()-1);
		}
	}

	private LocalDate computeNthDate(LocalDate start, String freq, int index) {
		if (start == null) return null;
		int steps = index;
		String f = (freq == null) ? "" : freq.trim().toLowerCase(Locale.ROOT);
		switch (f) {
		case "ogni 2 giorni":
			return start.plusDays(2L * steps);
		case "bisettimanale":
			return start.plusWeeks(2L * steps);
		case "mensile":
			return start.plusMonths(steps);
		default:
			return start.plusWeeks(steps);
		}
	}

	private void showInfoDark(String message) {
		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle("Attenzione");
		alert.setHeaderText(null);
		alert.setContentText(message);

		DialogPane pane = alert.getDialogPane();
		try {
			pane.getStylesheets().add(
					getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
					);
			pane.getStyleClass().add("dark-alert");
		} catch (Exception ignore) {
		}

		pane.setMinWidth(420);
		alert.showAndWait();
	}
}
