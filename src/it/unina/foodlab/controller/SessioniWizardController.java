package it.unina.foodlab.controller;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class SessioniWizardController {

	/* ---------- UI dal FXML ---------- */
	@FXML private DialogPane dialogPane;
	@FXML private ButtonType okButtonType;
	@FXML private ButtonType cancelButtonType;

	@FXML private TableView<SessionDraft> table;
	@FXML private TableColumn<SessionDraft, String> cData;
	@FXML private TableColumn<SessionDraft, String> cInizio;
	@FXML private TableColumn<SessionDraft, String> cFine;
	@FXML private TableColumn<SessionDraft, String> cTipo;
	@FXML private TableColumn<SessionDraft, String> cPiattaforma;
	@FXML private TableColumn<SessionDraft, String> cVia;
	@FXML private TableColumn<SessionDraft, String> cNum;
	@FXML private TableColumn<SessionDraft, String> cCap;
	@FXML private TableColumn<SessionDraft, String> cAula;
	@FXML private TableColumn<SessionDraft, String> cPosti;

	/* Bottoni “interni” (in FXML non usati: li rendo invisibili comunque) */
	@FXML private Button btnAdd;
	@FXML private Button btnRemove;

	/* ---------- Stato ---------- */
	private Corso corso;

	/** Modello temporaneo per riga */
	static class SessionDraft {
		LocalDate data;
		String oraInizio = "";
		String oraFine   = "";
		String tipo = "";        // "Online" | "In presenza"
		String piattaforma = ""; // solo Online
		String via = "";         // solo Presenza
		String num = "";
		String cap = "";
		String aula = "";
		String postiMax = "";    // 0/blank = illimitati
	}

	private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("H:mm");

	/* layout */
	private static final double ROW_HEIGHT    = 32;
	private static final double EDITOR_HEIGHT = 28;
	private static final double HEADER_H      = 30;

	@FXML
	private void initialize() {
		table.setEditable(true);
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		/* === Riga fissa (coerente con lo stile) === */
		table.setFixedCellSize(ROW_HEIGHT);
		table.setRowFactory(tv -> {
			TableRow<SessionDraft> r = new TableRow<>();
			r.setPrefHeight(ROW_HEIGHT);
			r.setMinHeight(ROW_HEIGHT);
			r.setMaxHeight(ROW_HEIGHT);
			return r;
		});

		/* DATA: NON editabile */
		cData.setCellValueFactory(cd ->
		new SimpleStringProperty(cd.getValue().data == null ? "" : cd.getValue().data.toString())
				);
		cData.setCellFactory(col -> new TableCell<>() {
			@Override protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty ? null : item);
				setGraphic(null);
			}
		});

		/* ORA inizio/fine con ComboBox (step 15') */
		makeTimePickerColumn(cInizio, d -> d.oraInizio, (d,v) -> d.oraInizio = v, 15);
		makeTimePickerColumn(cFine,   d -> d.oraFine,   (d,v) -> d.oraFine   = v, 15);

		/* MODALITÀ */
		cTipo.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tipo));
		cTipo.setCellFactory(col -> new TableCell<>() {
			private final ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList("Online", "In presenza"));
			private final HBox wrapper = new HBox(cb);
			private boolean internal = false;
			{
				cb.getStyleClass().addAll("cell-editor", "sessione-cell");
				cb.setMaxWidth(Double.MAX_VALUE);
				HBox.setHgrow(cb, Priority.ALWAYS);
				cb.setPrefHeight(EDITOR_HEIGHT);
				cb.setMinHeight(Region.USE_PREF_SIZE);
				cb.setMaxHeight(Region.USE_PREF_SIZE);
				wrapper.setFillHeight(true);

				cb.valueProperty().addListener((o, oldV, newV) -> {
					if (internal) return;
					int i = getIndex();
					var tv = getTableView();
					if (tv == null || i < 0 || i >= tv.getItems().size()) return;
					SessionDraft row = tv.getItems().get(i);
					if (Objects.equals(row.tipo, newV)) return;
					row.tipo = newV;
					if ("Online".equals(newV)) {
						row.via = ""; row.num = ""; row.cap = ""; row.aula = ""; row.postiMax = "";
					} else {
						row.piattaforma = "";
					}
					tv.refresh();
				});
			}
			@Override protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) { setGraphic(null); return; }
				internal = true;
				cb.setValue(item);
				internal = false;
				setGraphic(wrapper);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}
		});

		/* Colonne condizionali (visibili solo quando serve) */
		Predicate<SessionDraft> isPres = d -> "In presenza".equals(d.tipo);
		makeConditionalTextColumn(cPiattaforma, d -> "Online".equals(d.tipo), d -> d.piattaforma, (d,v) -> d.piattaforma = v);
		makeConditionalTextColumn(cVia,   isPres, d -> d.via,      (d,v) -> d.via = v);
		makeConditionalTextColumn(cNum,   isPres, d -> d.num,      (d,v) -> d.num = v);
		makeConditionalTextColumn(cCap,   isPres, d -> d.cap,      (d,v) -> d.cap = v);
		makeConditionalTextColumn(cAula,  isPres, d -> d.aula,     (d,v) -> d.aula = v);
		makeConditionalTextColumn(cPosti, isPres, d -> d.postiMax, (d,v) -> d.postiMax = v);

		/* --- LARGHEZZE: più spazio a Inizio/Fine --- */
		cData.setMinWidth(160);
		cInizio.setMinWidth(140); cInizio.setPrefWidth(140);
		cFine.setMinWidth(140);   cFine.setPrefWidth(140);
		cTipo.setMinWidth(170);
		cPiattaforma.setMinWidth(160);
		cVia.setMinWidth(140); cNum.setMinWidth(80); cCap.setMinWidth(80);
		cAula.setMinWidth(140); cPosti.setMinWidth(100);

		/* NON sovrascrivo la resize policy impostata in FXML (FLEX_LAST_COLUMN).
           Mi limito a “pesare” le colonne per una distribuzione migliore. */
		weightColumns();

		/* Lasciare che la TableView cresca nel VBox (niente forzature di altezza) */
		Platform.runLater(() -> {
			if (table.getParent() instanceof javafx.scene.layout.VBox) {
				javafx.scene.layout.VBox.setVgrow(table, Priority.ALWAYS);
			}
			autosizeColumnsToHeader();
		});

		/* Dialog più ampio (coerente col tuo FXML) */
		dialogPane.setPrefSize(1300, 760);
		dialogPane.setMinSize(1100, 650);
		dialogPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		/* Nascondo eventuali bottoni interni se presenti nell’FXML */
		if (btnAdd != null) { btnAdd.setVisible(false); btnAdd.setManaged(false); }
		if (btnRemove != null) { btnRemove.setVisible(false); btnRemove.setManaged(false); }

		/* Button bar: + / cestino a sinistra, OK/Cancel a destra + validazione */
		setupButtonBar();

		Platform.runLater(() -> {
			Button okBtn = okButtonType != null
					? (Button) dialogPane.lookupButton(okButtonType)
							: (Button) dialogPane.lookupButton(ButtonType.OK);
			if (okBtn != null) {
				okBtn.setText("");
				okBtn.setMnemonicParsing(false);
				okBtn.setStyle("-fx-graphic: url('/icons/ok-16.png'); -fx-content-display: graphic-only;");
				okBtn.setTooltip(new Tooltip("Conferma"));
				okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
					Optional<String> err = validateAllAndFocus();
					if (err.isPresent()) {
						ev.consume();
						error(err.get());
					}
				});
			}

			Button cancelBtn = cancelButtonType != null ? (Button) dialogPane.lookupButton(cancelButtonType) : null;
			if (cancelBtn == null) {
				for (ButtonType bt : dialogPane.getButtonTypes()) {
					if (bt.getButtonData() != null && bt.getButtonData().isCancelButton()) {
						cancelBtn = (Button) dialogPane.lookupButton(bt);
						if (cancelBtn != null) break;
					}
				}
			}
			if (cancelBtn == null) cancelBtn = (Button) dialogPane.lookupButton(ButtonType.CANCEL);
			if (cancelBtn == null) cancelBtn = (Button) dialogPane.lookupButton(ButtonType.CLOSE);
			if (cancelBtn != null) {
				cancelBtn.setText("");
				cancelBtn.setMnemonicParsing(false);
				cancelBtn.setStyle("-fx-graphic: url('/icons/cancel-16.png'); -fx-content-display: graphic-only;");
				cancelBtn.setTooltip(new Tooltip("Annulla"));
			}
		});
	}

	/** Diamo “pesi” alle colonne con il trucco del maxWidth. Somma ~1.0 */
	private void weightColumns() {
		setPerc(cData,        0.12);
		setPerc(cInizio,      0.14);
		setPerc(cFine,        0.14);
		setPerc(cTipo,        0.10);
		setPerc(cPiattaforma, 0.12);
		setPerc(cVia,         0.12);
		setPerc(cNum,         0.06);
		setPerc(cCap,         0.06);
		setPerc(cAula,        0.07);
		setPerc(cPosti,       0.07);
	}
	private void setPerc(TableColumn<?,?> c, double p) {
		c.setMaxWidth(1f * Integer.MAX_VALUE * p);
	}

	/** Bottoni nella button bar: “+” e cestino a sinistra, OK/Cancel a destra. */
	private void setupButtonBar() {
		if (dialogPane.getButtonTypes().isEmpty()) {
			dialogPane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		}

		ButtonType ADD_TYPE = new ButtonType("Aggiungi", ButtonBar.ButtonData.LEFT);
		ButtonType REM_TYPE = new ButtonType("Rimuovi",  ButtonBar.ButtonData.LEFT);

		dialogPane.getButtonTypes().add(0, REM_TYPE);
		dialogPane.getButtonTypes().add(0, ADD_TYPE);

		Button addBtn = (Button) dialogPane.lookupButton(ADD_TYPE);
		if (addBtn != null) {
			addBtn.setText("");
			addBtn.setMnemonicParsing(false);
			addBtn.getStyleClass().add("add-button");
			addBtn.setStyle("-fx-graphic: url('/icons/plus-16.png'); -fx-content-display: graphic-only;");
			addBtn.setTooltip(new Tooltip("Aggiungi"));
			addBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
				ev.consume();
				addBlankRowAfterLast();
			});
		}

		Button remBtn = (Button) dialogPane.lookupButton(REM_TYPE);
		if (remBtn != null) {
			remBtn.setText("");
			remBtn.setMnemonicParsing(false);
			remBtn.setStyle("-fx-graphic: url('/icons/trash-2-16.png'); -fx-content-display: graphic-only;");
			remBtn.setTooltip(new Tooltip("Rimuovi"));
			remBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
				ev.consume();
				removeSelectedRows();
			});
		}
	}

	/* ---------- API pubbliche ---------- */

	public void initWithCorso(Corso corso) {
		this.corso = Objects.requireNonNull(corso, "corso nullo");
		int n = Math.max(1, corso.getNumSessioni());
		table.setItems(FXCollections.observableArrayList(buildDrafts(corso, n)));
	}

	public void initWithCorsoAndExisting(Corso corso, List<Sessione> esistenti) {
		this.corso = Objects.requireNonNull(corso, "corso nullo");
		var drafts = new ArrayList<SessionDraft>();
		if (esistenti != null) {
			for (Sessione s : esistenti) {
				SessionDraft d = new SessionDraft();
				d.data = s.getData();
				d.oraInizio = s.getOraInizio() == null ? "10:00" : s.getOraInizio().toString();
				d.oraFine   = s.getOraFine()   == null ? "12:00" : s.getOraFine().toString();
				if (s instanceof SessioneOnline so) {
					d.tipo = "Online";
					d.piattaforma = nz(so.getPiattaforma());
				} else if (s instanceof SessionePresenza sp) {
					d.tipo = "In presenza";
					d.via  = nz(sp.getVia());
					d.num  = nz(sp.getNum());
					d.cap  = sp.getCap() > 0 ? String.valueOf(sp.getCap()) : "";
					d.aula = nz(sp.getAula());
					d.postiMax = sp.getPostiMax() > 0 ? String.valueOf(sp.getPostiMax()) : "";
				} else {
					d.tipo = "Online";
				}
				drafts.add(d);
			}
		}
		if (drafts.isEmpty()) {
			drafts.addAll(buildDrafts(corso, Math.max(1, corso.getNumSessioni())));
		}
		table.setItems(FXCollections.observableArrayList(drafts));
	}

	public void addBlankRowAfterLast() {
		var items = table.getItems();
		SessionDraft d = new SessionDraft();
		LocalDate base = items.stream()
				.map(s -> s.data)
				.filter(Objects::nonNull)
				.max(LocalDate::compareTo)
				.orElse((corso != null && corso.getDataInizio() != null) ? corso.getDataInizio() : LocalDate.now());
		d.data = base.plusDays(7);
		d.tipo = "Online";
		items.add(d);
		table.getSelectionModel().clearSelection();
		table.getSelectionModel().select(items.size() - 1);
		table.scrollTo(items.size() - 1);
	}

	public void removeSelectedRows() {
		var selIdx = new ArrayList<>(table.getSelectionModel().getSelectedIndices());
		if (selIdx.isEmpty()) return;
		selIdx.sort(Comparator.reverseOrder());
		for (int i : selIdx) {
			if (i >= 0 && i < table.getItems().size()) {
				table.getItems().remove(i);
			}
		}
	}

	/** Costruisce il risultato. La validazione è già fatta dall'EventFilter su OK. */
	public List<Sessione> buildResult() {
		List<Sessione> result = new ArrayList<>();
		for (SessionDraft d : table.getItems()) {
			LocalTime t1 = LocalTime.parse(d.oraInizio.trim(), TF);
			LocalTime t2 = LocalTime.parse(d.oraFine.trim(), TF);

			if ("Online".equals(d.tipo)) {
				result.add(new SessioneOnline(0, d.data, t1, t2, corso, d.piattaforma));
			} else {
			    int cap = Integer.parseInt(d.cap.trim()); // ora è sicuramente 5 cifre
			    int posti = 0;
			    if (!isBlank(d.postiMax)) posti = Integer.parseInt(d.postiMax.trim());
			    result.add(new SessionePresenza(0, d.data, t1, t2, corso, d.via, d.num, cap, d.aula, posti));
			}

		}
		return result;
	}

	/* ---------- Helpers colonne ---------- */

	private void makeEditableTextColumn(TableColumn<SessionDraft,String> col,
			Function<SessionDraft,String> getter,
			BiConsumer<SessionDraft,String> setter) {
		col.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
		col.setCellFactory(tc -> new TableCell<>() {
			private final TextField tf = new TextField();
			private final HBox wrapper = new HBox(tf) ;
			{
				tf.getStyleClass().addAll("cell-editor", "sessione-cell");
				tf.setMaxWidth(Double.MAX_VALUE);
				HBox.setHgrow(tf, Priority.ALWAYS);
				tf.setPrefHeight(EDITOR_HEIGHT);
				tf.setMinHeight(Region.USE_PREF_SIZE);
				tf.setMaxHeight(Region.USE_PREF_SIZE);
				wrapper.setFillHeight(true);

				tf.textProperty().addListener((o,a,b) -> {
					int i = getIndex();
					var tv = getTableView();
					if (tv != null && i >= 0 && i < tv.getItems().size()) {
						setter.accept(tv.getItems().get(i), b);
					}
				});
			}
			@Override protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) { setGraphic(null); return; }
				tf.setText(item);
				setGraphic(wrapper);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}
		});
	}

	private void makeConditionalTextColumn(TableColumn<SessionDraft,String> col,
			Predicate<SessionDraft> visibleWhen,
			Function<SessionDraft,String> getter,
			BiConsumer<SessionDraft,String> setter) {
		col.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
		col.setCellFactory(tc -> new TableCell<>() {
			private final TextField tf = new TextField();
			private final HBox wrapper = new HBox(tf);
			{
				tf.getStyleClass().addAll("cell-editor", "sessione-cell");
				tf.setMaxWidth(Double.MAX_VALUE);
				HBox.setHgrow(tf, Priority.ALWAYS);
				tf.setPrefHeight(EDITOR_HEIGHT);
				tf.setMinHeight(Region.USE_PREF_SIZE);
				tf.setMaxHeight(Region.USE_PREF_SIZE);
				wrapper.setFillHeight(true);

				tf.textProperty().addListener((o,a,b) -> {
					int i = getIndex();
					var tv = getTableView();
					if (tv != null && i >= 0 && i < tv.getItems().size()) {
						setter.accept(tv.getItems().get(i), b);
					}
				});
			}
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) { setGraphic(null); return; }
				int i = getIndex();
				var tv = getTableView();
				if (tv == null || i < 0 || i >= tv.getItems().size()) { setGraphic(null); return; }
				SessionDraft row = tv.getItems().get(i);
				if (row == null || !visibleWhen.test(row)) { setGraphic(null); return; }
				tf.setText(item);
				setGraphic(wrapper);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}
		});
	}

	/* ---------- Costruzione righe ---------- */

	private List<SessionDraft> buildDrafts(Corso corso, int n) {
		List<SessionDraft> list = new ArrayList<>();
		int step = frequencyToDays(nz(corso.getFrequenza()));
		LocalDate start = corso.getDataInizio() != null ? corso.getDataInizio() : LocalDate.now().plusDays(7);
		for (int i = 0; i < n; i++) {
			SessionDraft d = new SessionDraft();
			d.data = start.plusDays((long) i * step);
			d.tipo = (i < Math.min(2, n)) ? "Online" : "In presenza";
			list.add(d);
		}
		return list;
	}

	private int frequencyToDays(String f) {
		f = f == null ? "" : f.toLowerCase(Locale.ROOT).trim();
		if (f.contains("2") && f.contains("giorn")) return 2;
		if (f.contains("quindic") || (f.contains("bi") && f.contains("settiman"))) return 14;
		if (f.contains("mensil")) return 30;
		if (f.contains("settim")) return 7;
		return 7;
	}

	/* ---------- Validazione + util ---------- */

	/** Ritorna Optional.empty() se tutto ok, altrimenti messaggio errore e seleziona la riga. */
	private Optional<String> validateAllAndFocus() {
		List<SessionDraft> items = table.getItems();
		for (int i = 0; i < items.size(); i++) {
			SessionDraft d = items.get(i);

			if (d.data == null) {
				focusRow(i);
				return Optional.of("Data mancante");
			}

			try {
				LocalTime t1 = LocalTime.parse(nz(d.oraInizio).trim(), TF);
				LocalTime t2 = LocalTime.parse(nz(d.oraFine).trim(), TF);
				if (!t2.isAfter(t1)) {
					focusRow(i);
					return Optional.of("Ora fine deve essere successiva a inizio");
				}
			} catch (Exception e) {
				focusRow(i);
				return Optional.of("Formato orario non valido (HH:MM)");
			}

			if ("Online".equals(d.tipo)) {
				// se vuoi obbligatoria: if (isBlank(d.piattaforma)) { focusRow(i); return Optional.of("Piattaforma obbligatoria"); }
			} else { // In presenza
			    if (isBlank(d.via) || isBlank(d.aula)) {
			        focusRow(i);
			        return Optional.of("Via e Aula sono obbligatorie");
			    }

			    // CAP: obbligatorio e 5 cifre (es. 80132)
			    if (!isValidItalianCAP(d.cap)) {
			        focusRow(i);
			        return Optional.of("CAP non valido: deve essere di 5 cifre (es. 80132)");
			    }

			    // Posti: opzionale, ma se presente dev’essere > 0
			    if (!isBlank(d.postiMax)) {
			        Integer p = parseIntOrNull(d.postiMax);
			        if (p == null || p <= 0) {
			            focusRow(i);
			            return Optional.of("Posti deve essere > 0");
			        }
			    }
			}

		}
		return Optional.empty();
	}

	private void focusRow(int rowIndex) {
		table.getSelectionModel().clearAndSelect(rowIndex);
		table.scrollTo(rowIndex);
	}

	private boolean isBlank(String s){ return s == null || s.isBlank(); }
	private String nz(String s){ return s == null ? "" : s; }

	private void error(String msg) {
		Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
		a.setHeaderText("Dati non validi");
		a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		a.showAndWait();
	}

	private Integer parseIntOrNull(String s) {
		try { return Integer.valueOf(s.trim()); }
		catch (Exception ex) { return null; }
	}

	/* ---------- Autosize colonne ---------- */

	private void autosizeColumnsToHeader() {
		double padding = 28; // margine header + icona sort
		for (TableColumn<?,?> col : table.getColumns()) {
			String header = col.getText() == null ? "" : col.getText();
			Text t = new Text(header);
			double w = Math.ceil(t.prefWidth(-1) + padding);
			if (col.getMinWidth() < w) col.setMinWidth(w);
			if (col.getPrefWidth() < w) col.setPrefWidth(w);
		}
		table.layout();
	}

	/* ---------- Time picker column ---------- */

	private javafx.collections.ObservableList<LocalTime> buildTimes(int stepMinutes) {
		var list = FXCollections.<LocalTime>observableArrayList();
		for (int h = 0; h < 24; h++) {
			for (int m = 0; m < 60; m += stepMinutes) {
				list.add(LocalTime.of(h, m));
			}
		}
		return list;
	}

	private void makeTimePickerColumn(TableColumn<SessionDraft, String> col,
			Function<SessionDraft,String> getter,
			BiConsumer<SessionDraft,String> setter,
			int stepMinutes) {

		var TIMES = buildTimes(stepMinutes);

		col.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
		col.setCellFactory(tc -> new TableCell<>() {
			private final ComboBox<LocalTime> cb = new ComboBox<>(TIMES);
			private final HBox wrapper = new HBox(cb);
			private final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

			{
				cb.getStyleClass().addAll("cell-editor", "sessione-cell", "time-picker"); // per CSS freccetta nuda
				cb.setVisibleRowCount(10);
				cb.setPrefHeight(EDITOR_HEIGHT);
				cb.setMaxWidth(Double.MAX_VALUE);
				HBox.setHgrow(cb, Priority.ALWAYS);
				cb.setEditable(true);

				// Converter: mostra "HH:mm", accetta sia "9:00" che "09:00"
				cb.setConverter(new javafx.util.StringConverter<LocalTime>() {
					@Override public String toString(LocalTime t) {
						return t == null ? "" : t.format(HHMM);
					}
					@Override public LocalTime fromString(String s) {
						if (s == null) return null;
						String x = s.trim();
						if (x.isEmpty()) return null;
						try {
							// TF è "H:mm" (già definito nella classe): tollera 1 o 2 cifre per l'ora
							return LocalTime.parse(x, TF);
						} catch (Exception e) {
							try {
								// fallback rigoroso "HH:mm"
								return LocalTime.parse(x, HHMM);
							} catch (Exception ignore) {
								return null; // lascia nullo se non parsabile
							}
						}
					}
				});

				// Rendering coerente nel menu e nel bottone
				cb.setCellFactory(lv -> new ListCell<LocalTime>() {
					@Override protected void updateItem(LocalTime it, boolean empty) {
						super.updateItem(it, empty);
						setText(empty || it == null ? "" : it.format(HHMM));
					}
				});
				cb.setButtonCell(new ListCell<LocalTime>() {
					@Override protected void updateItem(LocalTime it, boolean empty) {
						super.updateItem(it, empty);
						setText(empty || it == null ? "" : it.format(HHMM));
					}
				});

				// Aggiorna il modello quando cambia il value
				cb.valueProperty().addListener((o, oldV, newV) -> {
					int i = getIndex();
					var tv = getTableView();
					if (tv != null && i >= 0 && i < tv.getItems().size()) {
						setter.accept(tv.getItems().get(i), newV == null ? "" : newV.format(HHMM));
					}
				});

				// Commit anche quando l'utente digita e poi esce/ENTER
				Runnable commitEditorText = () -> {
					String txt = cb.getEditor().getText();
					LocalTime parsed = cb.getConverter().fromString(txt);
					cb.setValue(parsed); // trigghera valueProperty listener
				};
				cb.getEditor().setOnAction(e -> commitEditorText.run());
				cb.getEditor().focusedProperty().addListener((obs, was, isNow) -> {
					if (!isNow) commitEditorText.run();
				});

				// Apri popup al click sulla cella
				wrapper.setOnMouseClicked(e -> cb.show());
			}

			@Override protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) { setGraphic(null); return; }

				LocalTime val = null;
				if (item != null && !item.isBlank()) {
					try { val = LocalTime.parse(item.trim(), TF); } catch (Exception ignore) { /* lascia null */ }
				}
				cb.setValue(val);

				setGraphic(wrapper);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}
		});
	}
	
	private boolean isValidItalianCAP(String s) {
	    return s != null && s.matches("\\d{5}");
	}

}
