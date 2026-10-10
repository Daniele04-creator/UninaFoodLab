package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.ManageSessionLifecycle;
import it.uninafoodlab.application.planning.SessionLifecycleRepository.ReportRow;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class ReportDetailsView {
    private final VBox content;
    private final AuthenticatedUser user;
    private final Supplier<ManageSessionLifecycle> lifecycle;
    private final Supplier<YearMonth> period;
    private long request;

    ReportDetailsView(VBox content, AuthenticatedUser user,
            Supplier<ManageSessionLifecycle> lifecycle, Supplier<YearMonth> period) {
        this.content = content;
        this.user = user;
        this.lifecycle = lifecycle;
        this.period = period;
    }

    void reset() {
        request++;
        content.getChildren().clear();
    }

    void show() { load(); }

    private void load() {
        var service = lifecycle.get();
        if (service == null) {
            content.getChildren().setAll(new Label("Dettaglio non disponibile."));
            return;
        }
        long version = ++request;
        var selectedPeriod = period.get();
        content.getChildren().setAll(new Label("Caricamento delle sessioni…"));
        FxAsyncExecutor.run("monthly-report-details", () -> service.report(user, selectedPeriod), rows -> {
            if (version != request) return;
            render(rows);
        }, error -> {
            if (version != request) return;
            var retry = new Button("Riprova");
            retry.setOnAction(event -> load());
            content.getChildren().setAll(new Label("Impossibile leggere le sessioni del mese."), retry);
        });
    }

    private void render(List<ReportRow> rows) {
        var explanation = new Label("Una sessione è conteggiata dopo l’orario previsto di fine. "
                + "Le sessioni future, in corso e annullate sono escluse.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("ops-muted");
        long countedRows = rows.stream().filter(row -> "COMPLETED".equals(row.state())).count();
        var counted = new ToggleButton("Conteggiate (" + countedRows + ")");
        var excluded = new ToggleButton("Escluse (" + (rows.size() - countedRows) + ")");
        counted.getStyleClass().add("ops-scope-tab");
        excluded.getStyleClass().add("ops-scope-tab");
        var group = new ToggleGroup();
        counted.setToggleGroup(group);
        excluded.setToggleGroup(group);
        counted.setSelected(true);
        var list = new VBox(12);
        list.setId("reportDetailsList");
        list.setAccessibleText("Sessioni del report mensile");
        Runnable filter = () -> renderRows(list, rows, counted.isSelected());
        counted.setOnAction(event -> { counted.setSelected(true); filter.run(); });
        excluded.setOnAction(event -> { excluded.setSelected(true); filter.run(); });
        filter.run();
        content.getChildren().setAll(explanation, new HBox(4, counted, excluded), list);
    }

    private static void renderRows(VBox list, List<ReportRow> rows, boolean counted) {
        var values = rows.stream().filter(row -> "COMPLETED".equals(row.state()) == counted).toList();
        list.getChildren().setAll(values.stream().map(ReportSessionRow::create).toList());
        if (values.isEmpty()) {
            var empty = new Label(counted ? "Nessuna sessione conteggiata." : "Nessuna sessione esclusa.");
            empty.setWrapText(true);
            empty.getStyleClass().add("ops-muted");
            list.getChildren().add(empty);
        }
    }
}
