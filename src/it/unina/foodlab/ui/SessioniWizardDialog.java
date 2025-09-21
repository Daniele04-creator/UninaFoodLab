package it.unina.foodlab.ui;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.Objects;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SessioniWizardDialog extends Dialog<List<Sessione>> {

    /* ---- modello temporaneo per riga ---- */
    private static class SessionDraft {
        LocalDate data;                 // SOLO DISPLAY (auto dal corso)
        String oraInizio = "10:00";
        String oraFine   = "12:00";
        String tipo = "Online";         // "Online" | "In presenza"
        String piattaforma = "Teams";   // solo Online
        String via = "";                // solo Presenza
        String num = "";
        String cap = "";
        String aula = "";
        String postiMax = "20";
    }

    public SessioniWizardDialog(Corso corso) {
        setTitle("Configura sessioni del corso");
        setResizable(true);
        getDialogPane().setPrefSize(1100, 750);
        getDialogPane().setMinSize(1000, 650);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        int n = Math.max(1, corso.getNumSessioni());
        var bozze = FXCollections.observableArrayList(buildDrafts(corso, n));

        TableView<SessionDraft> tv = new TableView<>(bozze);
        tv.setEditable(true);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setPrefHeight(600);

        /* ====== COLONNE ====== */

        // Data (SOLO DISPLAY) — niente DatePicker
        TableColumn<SessionDraft, String> cData = new TableColumn<>("Data");
        cData.setPrefWidth(140);
        cData.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().data == null ? "" : cd.getValue().data.toString())
        );
        cData.setEditable(false);

        // Orari
        var cInizio = editableTextCol("Inizio",  d->d.oraInizio, (d,v)->d.oraInizio=v, 90);
        var cFine   = editableTextCol("Fine",    d->d.oraFine,   (d,v)->d.oraFine=v,   90);

        TableColumn<SessionDraft, String> cTipo = new TableColumn<>("Modalità");
        cTipo.setPrefWidth(140);
        cTipo.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().tipo));
        cTipo.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> cb =
                new ComboBox<>(FXCollections.observableArrayList("Online", "In presenza"));
            private boolean internalUpdate = false;

            {
                cb.valueProperty().addListener((o, oldV, newV) -> {
                    if (internalUpdate) return;                      // evita loop
                    int i = getIndex();
                    if (i < 0 || i >= getTableView().getItems().size()) return;
                    SessionDraft row = getTableView().getItems().get(i);

                    if (Objects.equals(row.tipo, newV)) return;      // niente da fare
                    row.tipo = newV;

                    // pulizia campi non ammessi
                    if ("Online".equals(newV)) {
                        row.via = ""; row.num = ""; row.cap = ""; row.aula = ""; row.postiMax = "";
                    } else {
                        row.piattaforma = "";
                    }

                    getTableView().refresh();                        // refresh UNA volta
                });
            }

            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                internalUpdate = true;                               // non scatenare il listener
                cb.setValue(item);
                internalUpdate = false;
                setGraphic(cb);
            }
        });

        // Solo ONLINE
        var cPiattaforma = conditionalTextCol("Piattaforma", d->d.piattaforma, (d,v)->d.piattaforma=v,
                                              160, d -> "Online".equals(d.tipo));

        // Solo PRESENZA
        var cVia   = conditionalTextCol("Via",   d->d.via,   (d,v)->d.via=v,
                                        180, d -> "In presenza".equals(d.tipo));
        var cNum   = conditionalTextCol("Num",   d->d.num,   (d,v)->d.num=v,
                                        80,  d -> "In presenza".equals(d.tipo));
        var cCap   = conditionalTextCol("CAP",   d->d.cap,   (d,v)->d.cap=v,
                                        100, d -> "In presenza".equals(d.tipo));
        var cAula  = conditionalTextCol("Aula",  d->d.aula,  (d,v)->d.aula=v,
                                        140, d -> "In presenza".equals(d.tipo));
        var cPosti = conditionalTextCol("Posti", d->d.postiMax,(d,v)->d.postiMax=v,
                                        90,  d -> "In presenza".equals(d.tipo));

        tv.getColumns().addAll(cData, cInizio, cFine, cTipo, cPiattaforma, cVia, cNum, cCap, cAula, cPosti);

        VBox box = new VBox(10,
            new Label("Imposta modalità e dettagli per ciascuna sessione:"),
            tv,
            new Label("Nota: orari nel formato HH:MM (24h).")
        );
        box.setPadding(new Insets(12));
        VBox.setVgrow(tv, Priority.ALWAYS);
        getDialogPane().setContent(box);

        /* ====== RESULT ====== */
        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;

            List<Sessione> result = new ArrayList<>();
            for (SessionDraft d : bozze) {
                if (d.data == null) { showAlert("Data mancante"); return null; }

                final LocalTime t1, t2;
                try {
                    t1 = LocalTime.parse(d.oraInizio.trim());
                    t2 = LocalTime.parse(d.oraFine.trim());
                    if (!t2.isAfter(t1)) { showAlert("Ora fine deve essere successiva a inizio"); return null; }
                } catch (Exception ex) {
                    showAlert("Formato orario non valido (HH:MM)"); return null;
                }

                if ("Online".equals(d.tipo)) {
                    result.add(new SessioneOnline(0, d.data, t1, t2, corso, d.piattaforma));
                } else {
                    if (d.via.isBlank() || d.aula.isBlank()) { showAlert("Via e Aula sono obbligatorie"); return null; }
                    int cap = d.cap.isBlank()? 0 : parseIntSafe(d.cap, "CAP non valido");
                    int posti = parseIntSafe(d.postiMax, "Posti non valido");
                    result.add(new SessionePresenza(0, d.data, t1, t2, corso, d.via, d.num, cap, d.aula, posti));
                }
            }
            return result;
        });
    }

    /* ---------- helpers UI ---------- */

    private TableColumn<SessionDraft,String> editableTextCol(String title,
            java.util.function.Function<SessionDraft,String> getter,
            java.util.function.BiConsumer<SessionDraft,String> setter,
            double prefW) {
        TableColumn<SessionDraft,String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        c.setCellFactory(tc -> new TableCell<>() {
            private final TextField tf = new TextField();
            { tf.textProperty().addListener((o,a,b)->{
                int i = getIndex();
                if (i>=0 && i<tc.getTableView().getItems().size())
                    setter.accept(tc.getTableView().getItems().get(i), b);
            });}
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : tf);
                if (!empty) tf.setText(item);
            }
        });
        c.setPrefWidth(prefW);
        return c;
    }

    private TableColumn<SessionDraft,String> conditionalTextCol(String title,
            java.util.function.Function<SessionDraft,String> getter,
            java.util.function.BiConsumer<SessionDraft,String> setter,
            double prefW,
            java.util.function.Predicate<SessionDraft> visibleWhen) {
        TableColumn<SessionDraft,String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        c.setCellFactory(tc -> new TableCell<>() {
            private final TextField tf = new TextField();
            { tf.textProperty().addListener((o,a,b)->{
                int i = getIndex();
                if (i>=0 && i<tc.getTableView().getItems().size())
                    setter.accept(tc.getTableView().getItems().get(i), b);
            });}
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                SessionDraft row = getTableView().getItems().get(getIndex());
                if (row == null || !visibleWhen.test(row)) { setGraphic(null); return; }
                tf.setText(item);
                setGraphic(tf);
            }
        });
        c.setPrefWidth(prefW);
        return c;
    }

    private List<SessionDraft> buildDrafts(Corso corso, int n) {
        List<SessionDraft> list = new ArrayList<>();
        int step = frequencyToDays(nz(corso.getFrequenza()));
        LocalDate start = corso.getDataInizio() != null ? corso.getDataInizio() : LocalDate.now().plusDays(7);
        for (int i=0;i<n;i++) {
            SessionDraft d = new SessionDraft();
            d.data = start.plusDays((long) i * step);     // DATA AUTO (non editabile)
            d.tipo = (i < Math.min(2, n)) ? "Online" : "In presenza";
            list.add(d);
        }
        return list;
    }

    private int frequencyToDays(String f) {
        f = f == null ? "" : f.toLowerCase(Locale.ROOT).trim();
        if (f.contains("2") && f.contains("giorn")) return 2;
        if (f.contains("bisettiman")) return 14;
        if (f.contains("mensil")) return 30;
        if (f.contains("settim")) return 7;
        return 7;
    }

    private String nz(String s){ return s==null ? "" : s; }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Dati non validi");
        a.showAndWait();
    }

    private int parseIntSafe(String s, String errMsg) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception ex) { showAlert(errMsg); throw new RuntimeException(errMsg); }
    }
}
