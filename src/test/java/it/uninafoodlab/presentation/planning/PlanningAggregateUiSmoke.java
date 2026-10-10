package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import it.uninafoodlab.application.course.*;
import it.uninafoodlab.application.planning.*;
import it.uninafoodlab.domain.course.*;
import it.uninafoodlab.domain.identity.*;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public final class PlanningAggregateUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Username.of("smoke.chef"), "Chef", Role.CHEF);
            var controller = new PlanningWorkspaceController(user, new BrowseCourses(empty(CourseRepository.class)), new BrowseChefPlanning(empty(ChefPlanningRepository.class)));
            var stage = fx(() -> {
                var loader = new FXMLLoader(PlanningAggregateUiSmoke.class.getResource("planning-workspace.fxml"));
                loader.setController(controller);
                Parent root = loader.load();
                var window = new Stage();
                window.setScene(new Scene(root, 1100, 800));
                window.getScene().getStylesheets().add(PlanningAggregateUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                window.show();
                root.applyCss();
                root.layout();
                return window;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> !(boolean)get(get(controller,"feedback"),"busy"), 10);
            fx(() -> {
                verifyPrintingFeedback(controller);
                check(((Label)get(controller,"participantEmptyLabel")).getText()
                        .equals("Nessun corso attivo."),
                        "Empty shopping list explains the lack of active courses");
                controller.showParticipants();
                return null;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> !(boolean)get(get(controller,"feedback"),"busy"), 10);
            fx(() -> {
                ((ToggleButton)get(controller,"archiveScopeButton")).fire();
                return null;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> !(boolean)get(get(controller,"feedback"),"busy"), 10);
            fx(() -> {
                check(((Label)get(controller,"participantEmptyLabel")).getText().equals("Nessun corso passato."),
                        "Empty archive uses its own explanation, without active-course guidance; actual=" + ((Label)get(controller,"participantEmptyLabel")).getText() + "; selection=" + ((PlanningSelection)get(controller,"selection")).scope());
                ((ToggleButton)get(controller,"openScopeButton")).fire();
                return null;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> !(boolean)get(get(controller,"feedback"),"busy"), 10);
            fx(() -> { controller.showPlanning(); return null; });
            it.uninafoodlab.presentation.FxAwait.until(() -> !(boolean)get(get(controller,"feedback"),"busy"), 10);
            fx(() -> {
                var session = new CourseSessionSummary(UUID.randomUUID(), 1, LocalDateTime.now().plusDays(1), SessionMode.PRACTICAL, SessionStatus.SCHEDULED, 60, Optional.empty(), Optional.of("Laboratorio"), Optional.of(12), Optional.empty(), Optional.empty(), List.of(), 0);
                var selector = (ComboBox<PlanningFilterChoice<CourseSessionSummary>>)get(controller,"sessionField");
                set(get(controller,"selection"),"updatingSelectors",true);
                selector.getItems().setAll(PlanningFilterChoice.all("Fabbisogni delle sessioni future"), PlanningFilterChoice.of(session,"Sessione di domani"));
                selector.getSelectionModel().selectFirst();
                set(get(controller,"selection"),"updatingSelectors",false);
                set(get(controller,"selection"),"planningSessions",List.of(session));
                set(controller,"aggregateRequirements",true);
                controller.showPlanning();
                var panel = (VBox)get(controller,"requirementPanel");
                check(panel.lookup("#participantsLink") == null,"Requirements must not expose participant navigation");
                check(panel.lookup("#participantSessionChoices") == null,"Requirements must not offer participant selection");
                check(!((VBox)get(controller,"participantPanel")).isVisible(),"Participant list is not part of requirements");
                check(!((ToggleButton)get(controller,"archiveScopeButton")).isVisible(), "Shopping list does not expose past courses");
                check(!((ToggleButton)get(controller,"archiveScopeButton")).isManaged(), "Hidden archive filter does not occupy layout space");
                check((boolean)get(controller,"aggregateRequirements"),"Aggregate scope retained");
                return null;
            });
            fx(() -> {
                var course = new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Cucina italiana",
                        CourseTitle.of("Corso demo"), "Descrizione", java.time.LocalDate.now(),
                        java.time.LocalTime.of(10, 0), 7, 2, Optional.of(LocalDateTime.now().plusDays(2)),
                        CourseStatus.PUBLISHED, IngredientMargin.zero());
                var courses = (ComboBox<PlanningFilterChoice<CourseSummary>>)get(controller,"courseField");
                set(get(controller,"selection"),"updatingSelectors",true);
                courses.getItems().setAll(PlanningFilterChoice.of(course,"Corso demo"));
                courses.getSelectionModel().selectFirst();
                set(get(controller,"selection"),"updatingSelectors",false);
                invoke(controller,"loadCourseRequirements");
                return null;
            });
            it.uninafoodlab.presentation.FxAwait.until(() -> !(boolean)get(get(controller,"feedback"),"busy"), 10);
            fx(() -> {
                var summary = ((Label)get(controller,"requirementSummaryLabel")).getText();
                check(summary.equals("Menu disponibili: 0 su 1 sessioni"),
                        "Aggregate requirements show menu coverage without margin: " + summary);
                return null;
            });
            captureMockViews(stage, controller);
            fx(() -> { stage.close(); PlanningRenderingChecks.verify(); return null; });
            System.out.println("PASS: focused aggregate requirements, preserved menu coverage and session management");
        } finally { Platform.exit(); }
    }
    private static void verifyPrintingFeedback(PlanningWorkspaceController controller) throws Exception {
        var feedback = (PlanningFeedback) get(controller, "feedback");
        feedback.printingStarted();
        check(feedback.isBusy() && ((ProgressIndicator) get(controller, "progressIndicator")).isVisible(),
                "Printing must show progress and keep the workspace busy");
        check(((Button) get(controller, "printShoppingButton")).isDisabled()
                        && ((Button) get(controller, "embeddedPrintButton")).isDisabled(),
                "Printing must prevent duplicate jobs from both print actions");
        feedback.printingCancelled();
        check(!feedback.isBusy() && ((Label) get(controller, "feedbackLabel")).getText().equals("Stampa annullata."),
                "Cancellation must release the workspace and explain the outcome");
        feedback.printingStarted();
        feedback.printingFailed("Stampa non completata.");
        check(!feedback.isBusy() && ((Label) get(controller, "feedbackLabel")).getStyleClass().contains("course-feedback-error"),
                "Printing failure must release the workspace and expose an error");
        feedback.printingStarted();
        feedback.printingCompleted();
        check(!feedback.isBusy() && ((Label) get(controller, "feedbackLabel")).getText().equals("Stampa completata."),
                "Completed printing must release the workspace and confirm the outcome");
    }

    private static void captureMockViews(Stage stage, PlanningWorkspaceController controller) throws Exception {
        fx(() -> {
            var table = (TableView<IngredientRequirement>) get(controller, "requirementTable");
            table.getItems().setAll(
                    new IngredientRequirement("Farina 00", MeasurementUnit.GRAM, 8, new java.math.BigDecimal("1600")),
                    new IngredientRequirement("Uova", MeasurementUnit.PIECE, 8, new java.math.BigDecimal("8")),
                    new IngredientRequirement("Ricotta", MeasurementUnit.GRAM, 8, new java.math.BigDecimal("640")),
                    new IngredientRequirement("Spinaci", MeasurementUnit.GRAM, 8, new java.math.BigDecimal("400")),
                    new IngredientRequirement("Sale", MeasurementUnit.GRAM, 8, new java.math.BigDecimal("8")));
            var missing = (VBox) get(controller, "missingMenusBox");
            missing.setVisible(false);
            missing.setManaged(false);
            ((Label) get(controller, "requirementSummaryLabel")).setText("Laboratorio A, 8 adesioni confermate");
            ((Label) get(controller, "requirementNoteLabel")).setText("Quantità basate sulle adesioni confermate. Unità diverse restano separate.");
            return null;
        });
        captureWidths(stage, controller, "shopping");
        fx(() -> {
            controller.showMonthlyReport();
            check(((Label) get(controller, "pageTitle")).isManaged(),
                    "Report heading precedes the selected period and its continuous sheet");
            var previous = (Button) get(controller, "previousMonthButton");
            var next = (Button) get(controller, "nextMonthButton");
            check(previous.getText().equals("←") && next.getText().equals("→"),
                    "Month navigation uses compact arrows beside the period fields");
            check(previous.getAccessibleText().equals("Mese precedente")
                            && next.getAccessibleText().equals("Mese successivo")
                            && previous.getTooltip() != null && next.getTooltip() != null,
                    "Compact month arrows retain spoken names and hover explanations");
            return null;
        });
        it.uninafoodlab.presentation.FxAwait.until(() -> !(boolean)get(get(controller,"feedback"),"busy"), 10);
        captureWidths(stage, controller, "report");
    }

    private static void captureWidths(Stage stage, PlanningWorkspaceController controller, String name) throws Exception {
        for (int width : new int[] {1000, 1440}) {
            fx(() -> {
                stage.setWidth(width + stage.getWidth() - stage.getScene().getWidth());
                stage.setHeight(900 + stage.getHeight() - stage.getScene().getHeight());
                return null;
            });

            it.uninafoodlab.presentation.FxAwait.until(() -> Math.abs(stage.getScene().getWidth() - width) < 1, 10);
            fx(() -> {
                var root = stage.getScene().getRoot();
                root.applyCss();
                root.layout();
                if (root instanceof ScrollPane scroll) scroll.setVvalue(0);
                if (name.equals("shopping")) {
                    var selected = (ToggleButton) get(controller, "futureScopeButton");
                    var primary = javafx.scene.paint.Color.web("#9f432b");
                    check(selected.isSelected() && selected.getBackground().getFills().stream()
                                    .anyMatch(fill -> primary.equals(fill.getFill()))
                                    && javafx.scene.paint.Color.WHITE.equals(selected.getTextFill()),
                            "Selected shopping scope uses terracotta fill and white text");
                    selected.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focus-visible"), true);
                    root.applyCss();
                    check(selected.getBorder().getStrokes().stream().anyMatch(stroke ->
                                    javafx.scene.paint.Color.web("#173946").equals(stroke.getTopStroke()))
                                    && selected.getBorder().getStrokes().stream().anyMatch(stroke ->
                                    javafx.scene.paint.Color.WHITE.equals(stroke.getTopStroke())),
                            "Selected shopping scope has a visible keyboard focus ring");
                    selected.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focus-visible"), false);
                    root.applyCss();
                }
                if (name.equals("report")) assertReportBarColors(controller);
                return null;
            });
            if (name.equals("report")) {
                it.uninafoodlab.presentation.FxAwait.until(() -> {
                    var metrics = (javafx.scene.layout.FlowPane) get(controller, "reportMetrics");
                    return Math.abs(metrics.getHeight() - metrics.getChildren().getFirst().getLayoutBounds().getHeight()) < 2;
                }, 10);
            }
            fx(() -> {
                if (name.equals("report")) assertMetricSeparators(controller);
                it.uninafoodlab.presentation.FxSnapshot.save(stage.getScene().getRoot(),
                        "target/ui-polish/mock-" + name + "-" + width + ".png");
                return null;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertReportBarColors(PlanningWorkspaceController controller) throws Exception {
        var recipeChart = (javafx.scene.chart.BarChart<Number, String>) get(controller, "recipeChart");
        assertCountAxisTicks((javafx.scene.chart.NumberAxis) recipeChart.getXAxis());
        var recipeBars = recipeChart.getData().getFirst().getData();
        var primary = javafx.scene.paint.Color.web("#9f432b");
        check(recipeBars.stream().allMatch(data -> data.getNode().getStyleClass().contains("report-recipe-bar")),
                "Recipe bars keep their report-specific style class");
        check(recipeBars.stream().allMatch(data -> effectiveBarFill(data.getNode()).equals(primary)),
                "Recipe chart bars render in terracotta; effective fills="
                        + recipeBars.stream().map(data -> effectiveBarFill(data.getNode())).toList());

        var sessionChart = (javafx.scene.chart.BarChart<Number, String>) get(controller, "sessionChart");
        var sessionAxis = (javafx.scene.chart.NumberAxis) sessionChart.getXAxis();
        check(!sessionAxis.getAnimated(), "Session count axis does not animate range changes");
        assertCountAxisTicks(sessionAxis);
        var sessionBars = sessionChart.getData().getFirst().getData();
        check(sessionBars.get(0).getNode().getStyleClass().contains("report-practical-bar")
                        && sessionBars.get(1).getNode().getStyleClass().contains("report-online-bar"),
                "Session bars retain their mode-specific style classes");
        check(sessionBars.stream().allMatch(data -> ((javafx.scene.layout.Region) data.getNode()).getWidth() > 0
                        && ((javafx.scene.layout.Region) data.getNode()).getHeight() > 0),
                "Every nonzero session bar has visible native geometry");
        check(recipeBars.stream().allMatch(data -> ((javafx.scene.layout.Region) data.getNode()).getWidth() > 0
                        && ((javafx.scene.layout.Region) data.getNode()).getHeight() > 0),
                "Every nonzero recipe bar has visible native geometry");
        check(effectiveBarFill(sessionBars.get(0).getNode()).equals(javafx.scene.paint.Color.web("#9f432b"))
                        && effectiveBarFill(sessionBars.get(1).getNode()).equals(primary),
                "Online and practical use the same terracotta while category labels distinguish them; effective fills="
                        + sessionBars.stream().map(data -> effectiveBarFill(data.getNode())).toList());
    }

    private static void assertMetricSeparators(PlanningWorkspaceController controller) throws Exception {
        var metrics = (javafx.scene.layout.FlowPane) get(controller, "reportMetrics");
        check(metrics.getBorder().getStrokes().stream().allMatch(stroke -> stroke.getWidths().getTop() == 0),
                "Report metrics have no decorative top rule");
        var measures = metrics.getChildren().stream().map(javafx.scene.layout.Region.class::cast).toList();
        check(measures.getLast().getBorder().getStrokes().stream()
                        .allMatch(stroke -> stroke.getWidths().getRight() == 0),
                "The last metric does not draw an outside right separator");
        check(measures.stream().allMatch(measure -> Math.abs(measure.getHeight() - metrics.getHeight() + 1) < 2),
                "Metric separators span the entire summary row instead of ending inside its padding; summary="
                        + metrics.getWidth() + "x" + metrics.getHeight() + "; measures="
                        + measures.stream().map(measure -> measure.getWidth() + "x" + measure.getHeight()
                                + " at " + measure.getLayoutY()).toList() + "; padding=" + metrics.getPadding());
        var caption = (Label) ((VBox) measures.getLast()).getChildren().getFirst();
        check(caption.getText().equals("Media ricette per sessione pratica") && caption.getTooltip() != null
                        && caption.getTooltip().getText().contains("pratiche"),
                "The recipe caption names the mean and keeps its practical-session definition available");
        var valueBaselines = measures.stream().map(VBox.class::cast).map(box -> box.getChildren().getLast())
                .mapToDouble(value -> value.localToScene(0, value.getBaselineOffset()).getY()).toArray();
        check(java.util.Arrays.stream(valueBaselines).max().orElseThrow()
                        - java.util.Arrays.stream(valueBaselines).min().orElseThrow() < 1,
                "Metric values stay aligned when the practical-recipe caption wraps");
        var charts = (javafx.scene.layout.FlowPane) get(controller, "reportCharts");
        var metricMiddle = measures.get(2).localToScene(0, 0).getX();
        var chartMiddle = charts.getChildren().get(1).localToScene(0, 0).getX();
        check(measures.get(2).getBorder().getStrokes().stream().anyMatch(stroke -> stroke.getWidths().getLeft() > 0),
                "KPI and chart middle dividers paint on the same side of their shared boundary");
        check(Math.abs(metricMiddle - chartMiddle) < 0.01,
                "The middle KPI divider aligns exactly with the chart divider; KPI=" + metricMiddle + ", chart=" + chartMiddle);
    }

    private static void assertCountAxisTicks(javafx.scene.chart.NumberAxis axis) {
        var ticks = axis.getTickMarks();
        var labels = axis.lookupAll("*").stream()
                .filter(javafx.scene.text.Text.class::isInstance)
                .map(javafx.scene.text.Text.class::cast)
                .filter(text -> text.getParent() == axis)
                .filter(javafx.scene.text.Text::isVisible)
                .map(javafx.scene.text.Text::getText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
        check(axis.getLowerBound() == 0 && axis.getUpperBound() == Math.rint(axis.getUpperBound()),
                "Count axis has zero origin and an integer upper bound; actual=" + axis.getUpperBound());
        check(!labels.isEmpty(), "Count axis has visible tick labels");
        check(labels.stream().distinct().count() == labels.size(),
                "Count axis labels are unique; actual=" + labels);
        var labelValues = new HashSet<java.math.BigDecimal>();
        for (var label : labels) {
            check(label.matches("-?\\d+"),
                    "Count axis labels are integers; actual=" + labels);
            labelValues.add(new java.math.BigDecimal(label));
        }
        var tickValues = new HashSet<java.math.BigDecimal>();
        for (var tick : ticks) {
            var tickValue = new java.math.BigDecimal(tick.getValue().toString()).stripTrailingZeros();
            check(tickValue.scale() <= 0,
                    "Count axis tick values are integers; actual="
                            + ticks.stream().map(mark -> mark.getValue()).toList());
            check(tickValues.add(tickValue),
                    "Count axis tick values are unique; actual="
                            + ticks.stream().map(mark -> mark.getValue()).toList());
        }
        check(labelValues.equals(tickValues),
                "Count axis text labels match the unique tick values; labels=" + labels
                        + "; values=" + ticks.stream().map(mark -> mark.getValue()).toList());
    }

    private static javafx.scene.paint.Color effectiveBarFill(javafx.scene.Node node) {
        var background = ((javafx.scene.layout.Region) node).getBackground();
        check(background != null && !background.getFills().isEmpty(),
                "Chart bar has no effective CSS background: " + node.getStyleClass());
        var fill = background.getFills().getFirst().getFill();
        check(fill instanceof javafx.scene.paint.Color,
                "Chart bar fill is not a solid CSS color: " + fill);
        return (javafx.scene.paint.Color) fill;
    }

    private static Object get(Object target,String name) throws Exception { var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target); }
    private static void set(Object target,String name,Object value) throws Exception { var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value); }
    private static void invoke(Object target,String name) throws Exception { var method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(target); }
    @SuppressWarnings("unchecked")
    private static <T> T empty(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (type == ChefPlanningRepository.class && method.getName().equals("monthlyReport")) {
                return new MonthlyReport((java.time.YearMonth) arguments[1], 3, 2, 4,
                        Optional.of(new java.math.BigDecimal("2.5")), Optional.of(1), Optional.of(4));
            }
            if (type == ChefPlanningRepository.class && method.getName().equals("exportSnapshots")) {
                return ((List<UUID>) arguments[1]).stream()
                        .map(id -> new SessionPlanningSnapshot(id, LocalDateTime.now().plusDays(1), 0, "PRELIMINARY", "CAPTURED", List.of()))
                        .toList();
            }
            if (type == ChefPlanningRepository.class && method.getName().equals("courseExport")) {
                var session = new CourseSessionSummary(UUID.randomUUID(), 1, LocalDateTime.now().plusDays(1),
                        SessionMode.PRACTICAL, SessionStatus.SCHEDULED, 60, Optional.empty(), Optional.of("Laboratorio"),
                        Optional.of(12), Optional.empty(), Optional.empty(), List.of(), 0);
                var snapshot = new SessionPlanningSnapshot(session.id(), session.scheduledAt(), 0,
                        "PRELIMINARY", "CAPTURED", List.of());
                return new CoursePlanningExport(List.of(session), List.of(snapshot));
            }
            return List.of();
        }));
    }
    private static <T>T fx(Callable<T> action) throws Exception { var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(10,TimeUnit.SECONDS); }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
