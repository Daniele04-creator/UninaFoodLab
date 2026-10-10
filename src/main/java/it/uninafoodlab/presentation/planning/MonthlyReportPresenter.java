package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.BrowseChefPlanning;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.time.YearMonth;
import java.time.Month;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;

import javafx.application.Platform;

import static it.uninafoodlab.presentation.planning.PlanningLabels.periodLabel;

final class MonthlyReportPresenter {
    private final AuthenticatedUser user;
    private final BrowseChefPlanning browseChefPlanning;
    private final MonthlyReportView monthlyReportView;
    private final java.util.function.Consumer<Boolean> busy;
    private final Runnable clearFeedback;
    private final java.util.function.BiConsumer<String, Runnable> failure;
    private long reportRequestVersion;
    private final ComboBox<Month> periodField;
    private final ComboBox<Integer> reportYearField;
    private final Button previousMonthButton;
    private final Button nextMonthButton;
    private final Label reportScopeLabel;
    private final ScrollPane workspaceScroll;
    MonthlyReportPresenter(
            AuthenticatedUser user,
            BrowseChefPlanning browseChefPlanning,
            MonthlyReportView monthlyReportView,
            java.util.function.Consumer<Boolean> busy,
            Runnable clearFeedback,
            java.util.function.BiConsumer<String, Runnable> failure,
            ComboBox<Month> periodField,
            ComboBox<Integer> reportYearField,
            Button previousMonthButton,
            Button nextMonthButton,
            Label reportScopeLabel,
            ScrollPane workspaceScroll) {
        this.user = user;
        this.browseChefPlanning = browseChefPlanning;
        this.monthlyReportView = monthlyReportView;
        this.busy = busy;
        this.clearFeedback = clearFeedback;
        this.failure = failure;
        this.periodField = periodField;
        this.reportYearField = reportYearField;
        this.previousMonthButton = previousMonthButton;
        this.nextMonthButton = nextMonthButton;
        this.reportScopeLabel = reportScopeLabel;
        this.workspaceScroll = workspaceScroll;
    }

    void load() {
        var month = periodField.getValue();
        var year = reportYearField.getValue();
        if (month == null || year == null) {
            return;
        }
        var period = YearMonth.of(year, month);
        previousMonthButton.setDisable(ReportPeriodSelector.adjacentReportPeriod(period, -1).isEmpty());
        nextMonthButton.setDisable(ReportPeriodSelector.adjacentReportPeriod(period, 1).isEmpty());
        var version = ++reportRequestVersion;
        busy.accept(true);
        monthlyReportView.setResultsVisible(false);
        reportScopeLabel.setText("Caricamento di " + periodLabel(period) + "…");
        FxAsyncExecutor.run(
                "monthly-report-load",
                () -> browseChefPlanning.monthlyReport(user, period),
                report -> {
                    if (version != reportRequestVersion) return;
                    monthlyReportView.render(report);
                    clearFeedback.run();
                    busy.accept(false);
                    Platform.runLater(() -> workspaceScroll.setVvalue(0.0));
                },
                error -> {
                    if (version != reportRequestVersion) return;
                    reportScopeLabel.setText("Report non disponibile, " + periodLabel(period));
                    this.failure.accept("Non è stato possibile caricare il report di " + periodLabel(period) + ".", this::load);
                });
    }
}
