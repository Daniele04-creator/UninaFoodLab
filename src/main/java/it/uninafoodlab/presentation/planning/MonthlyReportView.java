package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.MonthlyReport;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import static it.uninafoodlab.presentation.planning.PlanningLabels.recipeStatisticsLabel;

final class MonthlyReportView {
    private final Label averageRecipesValue = new Label("—");
    private final javafx.scene.layout.FlowPane reportMetrics;
    private final javafx.scene.layout.FlowPane reportCharts;
    private final Label reportScopeLabel;
    private final Label reportEmptyLabel;
    private final Label recipeEmptyLabel;
    private final Label recipeRangeLabel;
    private final Label totalCoursesLabel;
    private final Label onlineSessionsLabel;
    private final Label practicalSessionsLabel;
    private final Label recipeStatisticsLabel;
    private final BarChart<Number, String> sessionChart;
    private final BarChart<Number, String> recipeChart;
    MonthlyReportView(
            javafx.scene.layout.FlowPane reportMetrics,
            javafx.scene.layout.FlowPane reportCharts,
            Label reportScopeLabel,
            Label reportEmptyLabel,
            Label recipeEmptyLabel,
            Label recipeRangeLabel,
            Label totalCoursesLabel,
            Label onlineSessionsLabel,
            Label practicalSessionsLabel,
            Label recipeStatisticsLabel,
            BarChart<Number, String> sessionChart,
            BarChart<Number, String> recipeChart) {
        this.reportMetrics = reportMetrics;
        this.reportCharts = reportCharts;
        this.reportScopeLabel = reportScopeLabel;
        this.reportEmptyLabel = reportEmptyLabel;
        this.recipeEmptyLabel = recipeEmptyLabel;
        this.recipeRangeLabel = recipeRangeLabel;
        this.totalCoursesLabel = totalCoursesLabel;
        this.onlineSessionsLabel = onlineSessionsLabel;
        this.practicalSessionsLabel = practicalSessionsLabel;
        this.recipeStatisticsLabel = recipeStatisticsLabel;
        this.sessionChart = sessionChart;
        this.recipeChart = recipeChart;
        averageRecipesValue.setId("averageRecipesValue");
        averageRecipesValue.getStyleClass().add("report-summary-value");
        var caption = new Label("Media ricette per sessione pratica");
        caption.getStyleClass().add("ops-muted");
        caption.setWrapText(true);
        caption.setTooltip(new Tooltip("Media delle ricette realizzate nelle sessioni pratiche svolte. Minimo e massimo nel grafico."));
        var measure = new javafx.scene.layout.VBox(4, averageRecipesValue, caption);
        measure.getStyleClass().add("workbench-report-measure");
        reportMetrics.getChildren().add(measure);
        var practical = reportMetrics.getChildren().remove(1);
        reportMetrics.getChildren().add(2, practical);
        reportMetrics.getChildren().stream().filter(javafx.scene.layout.VBox.class::isInstance)
                .map(javafx.scene.layout.VBox.class::cast).forEach(box -> {
                    if (box.getChildren().size() == 2) {
                        var value = box.getChildren().removeFirst();
                        box.getChildren().add(value);
                    }
                });
        reportMetrics.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        reportCharts.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        reportMetrics.widthProperty().addListener((observable, before, after) ->
                ReportColumnLayout.resize(reportMetrics, after.doubleValue() >= 760 ? 4 : 2));
    }

    void render(MonthlyReport report) {
        setResultsVisible(true);
        renderSummary(report);
        renderSessionChart(report);
        renderRecipeChart(report);
    }

    private void renderSummary(MonthlyReport report) {
        averageRecipesValue.setText(report.averageRecipes().map(ReportNumberFormat::format).orElse("—"));
        averageRecipesValue.setAccessibleText(report.averageRecipes()
                .map(value -> "Media ricette per pratica: " + ReportNumberFormat.format(value))
                .orElse("Media ricette per pratica non disponibile"));
        reportScopeLabel.setText("Tutti i tuoi corsi, 1 – " + report.month().lengthOfMonth() + " " + report.month().getMonth().getDisplayName(TextStyle.FULL, Locale.ITALIAN) + " " + report.month().getYear());
        totalCoursesLabel.setText(Long.toString(report.totalCourses()));
        onlineSessionsLabel.setText(Long.toString(report.onlineSessions()));
        practicalSessionsLabel.setText(Long.toString(report.practicalSessions()));
        recipeStatisticsLabel.setText(report.averageRecipes().isPresent()
                ? recipeStatisticsLabel(report) : "");
        recipeRangeLabel.setText(report.minimumRecipes().isPresent()
                ? "Min " + report.minimumRecipes().orElseThrow() + ", Max " + report.maximumRecipes().orElseThrow()
                : "Nessuna sessione pratica");

    }

    private void renderSessionChart(MonthlyReport report) {
        var hasSessions = report.onlineSessions() + report.practicalSessions() > 0;
        reportEmptyLabel.setVisible(!hasSessions);
        reportEmptyLabel.setManaged(!hasSessions);
        sessionChart.setVisible(hasSessions);
        sessionChart.setManaged(hasSessions);
        var categories = (javafx.scene.chart.CategoryAxis) sessionChart.getYAxis();
        categories.setAutoRanging(true);
        categories.setTickLabelsVisible(true);
        categories.setTickLabelRotation(0);
        categories.setTickLabelGap(10);
        categories.setMinWidth(118);
        categories.setPrefWidth(118);
        var axis = (NumberAxis) sessionChart.getXAxis();
        axis.setAnimated(false);
        axis.setAutoRanging(false);
        axis.setLowerBound(0);
        var maximum = Math.max(report.onlineSessions(), report.practicalSessions());
        var step = Math.max(1, Math.ceil(maximum / 5.0));
        axis.setTickUnit(step);
        axis.setUpperBound(Math.max(1, Math.ceil((maximum + 1) / step) * step));
        axis.setMinorTickVisible(false);
        var sessionSeries = new XYChart.Series<Number, String>();
        sessionSeries.getData().addAll(List.of(
                horizontalBar("In presenza", report.practicalSessions()),
                horizontalBar("Online", report.onlineSessions())));
        sessionChart.getData().setAll(List.of(sessionSeries));
        sessionSeries.getData().get(0).getNode().getStyleClass().add("report-practical-bar");
        sessionSeries.getData().get(1).getNode().getStyleClass().add("report-online-bar");
        sessionSeries.getData().forEach(data -> Tooltip.install(data.getNode(),
                new Tooltip(data.getYValue() + ": " + data.getXValue())));

    }

    private void renderRecipeChart(MonthlyReport report) {
        recipeChart.getData().clear();
        recipeChart.setVisible(report.averageRecipes().isPresent());
        recipeChart.setManaged(report.averageRecipes().isPresent());
        recipeEmptyLabel.setVisible(report.averageRecipes().isEmpty());
        recipeEmptyLabel.setManaged(report.averageRecipes().isEmpty());
        if (report.averageRecipes().isPresent()) {
            recipeChart.setTitle(null);
            var recipeAxis = (NumberAxis) recipeChart.getXAxis();
            recipeAxis.setAutoRanging(false);
            recipeAxis.setLowerBound(0);
            var maximum = report.maximumRecipes().orElseThrow();
            var step = Math.max(1, Math.ceil(maximum / 5.0));
            recipeAxis.setTickUnit(step);
            recipeAxis.setUpperBound(Math.max(1, Math.ceil((maximum + 1.0) / step) * step));
            recipeAxis.setMinorTickVisible(false);
            var recipeCategories = (javafx.scene.chart.CategoryAxis) recipeChart.getYAxis();
            recipeCategories.setAutoRanging(true);
            recipeCategories.setMinWidth(100);
            recipeCategories.setPrefWidth(100);
            ((NumberAxis) recipeChart.getXAxis()).setTickLabelFormatter(new StringConverter<>() {
                @Override public String toString(Number value) {
                    return value == null ? "" : ReportNumberFormat.format(value);
                }

                @Override public Number fromString(String value) {
                    return new java.math.BigDecimal(value.replace(',', '.'));
                }
            });
            var recipeSeries = new XYChart.Series<Number, String>();
            recipeSeries.setName("Ricette");
            recipeSeries.getData().setAll(List.of(
                    horizontalBar("Massimo", report.maximumRecipes().orElseThrow()),
                    horizontalBar("Media", report.averageRecipes().orElseThrow()),
                    horizontalBar("Minimo", report.minimumRecipes().orElseThrow())));
            recipeChart.getData().setAll(List.of(recipeSeries));
            recipeSeries.getData().forEach(data -> {
                data.getNode().getStyleClass().add("report-recipe-bar");
                Tooltip.install(data.getNode(),
                        new Tooltip(data.getYValue() + ": " + ReportNumberFormat.format(data.getXValue())));
            });
        } else {
            recipeChart.setTitle("Nessuna sessione pratica nel mese");
        }
    }

    void setResultsVisible(boolean visible) {
        reportMetrics.setVisible(visible);
        reportMetrics.setManaged(visible);
        reportCharts.setVisible(visible);
        reportCharts.setManaged(visible);
    }

    private static XYChart.Data<Number, String> horizontalBar(String category, Number value) {
        var label = new Label(ReportNumberFormat.format(value));
        label.getStyleClass().add("ops-bar-value");
        label.setManaged(false);
        label.setMouseTransparent(true);
        var bar = new StackPane(label) {
            @Override protected void layoutChildren() {
                super.layoutChildren();
                var width = label.prefWidth(-1);
                var height = label.prefHeight(width);
                label.resizeRelocate(getWidth() + 8, (getHeight() - height) / 2, width, height);
            }
        };
        bar.setAccessibleText(category + ": " + value);
        var data = new XYChart.Data<Number, String>(value, category);
        data.setNode(bar);
        return data;
    }

}
