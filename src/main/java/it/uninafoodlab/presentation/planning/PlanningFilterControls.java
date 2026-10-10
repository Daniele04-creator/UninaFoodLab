package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;

import javafx.scene.layout.VBox;

final class PlanningFilterControls {
    private final javafx.scene.layout.FlowPane planningFilterFields;
    private final VBox calculationScope;
    private final ComboBox<PlanningPresentation.CourseScope> scopeField;
    private final ComboBox<PlanningFilterChoice<CourseSummary>> courseField;
    private final ComboBox<PlanningFilterChoice<CourseSessionSummary>> sessionField;
    private final ToggleButton openScopeButton;
    private final ToggleButton archiveScopeButton;
    private final Button requirementsLink;
    private final ToggleButton singleScopeButton;
    private final ToggleButton futureScopeButton;
    PlanningFilterControls(
            javafx.scene.layout.FlowPane planningFilterFields,
            VBox calculationScope,
            javafx.scene.layout.FlowPane reportCharts,
            ComboBox<PlanningPresentation.CourseScope> scopeField,
            ComboBox<PlanningFilterChoice<CourseSummary>> courseField,
            ComboBox<PlanningFilterChoice<CourseSessionSummary>> sessionField,
            ToggleButton openScopeButton,
            ToggleButton archiveScopeButton,
            Button requirementsLink,
            ToggleButton singleScopeButton,
            ToggleButton futureScopeButton) {
        this.planningFilterFields = planningFilterFields;
        this.calculationScope = calculationScope;
        this.scopeField = scopeField;
        this.courseField = courseField;
        this.sessionField = sessionField;
        this.openScopeButton = openScopeButton;
        this.archiveScopeButton = archiveScopeButton;
        this.requirementsLink = requirementsLink;
        this.singleScopeButton = singleScopeButton;
        this.futureScopeButton = futureScopeButton;
        planningFilterFields.widthProperty().addListener((observable, before, after) -> sizePlanningFilters());
        reportCharts.widthProperty().addListener((observable, before, after) ->
                sizeReportPanels(reportCharts, after.doubleValue() >= 860 ? 2 : 1));
        calculationScope.managedProperty().addListener((observable, before, after) -> sizePlanningFilters());
    }

    private void sizeReportPanels(javafx.scene.layout.FlowPane pane, int columns) {
        pane.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("stacked"), columns == 1);
        ReportColumnLayout.resize(pane, columns);
    }

    private void sizePlanningFilters() {
        var insets = planningFilterFields.getInsets();
        var width = planningFilterFields.getWidth() - insets.getLeft() - insets.getRight();
        if (width <= 0) return;
        var columns = width >= 660 ? 2 : 1;
        if (calculationScope.isManaged() && width >= 960) columns = 3;

        var fieldWidth = Math.max(0, Math.floor((width - planningFilterFields.getHgap() * (columns - 1)) / columns) - 1);
        planningFilterFields.getChildren().stream().filter(javafx.scene.Node::isManaged)
                .filter(VBox.class::isInstance).map(VBox.class::cast)
                .forEach(group -> group.setPrefWidth(fieldWidth));
    }

    void update(boolean busy, PlanningPresentation.CourseScope scope, boolean aggregateRequirements, boolean requirementsVisible, boolean hasSessions) {
        scopeField.setDisable(busy);
        courseField.setDisable(busy);
        sessionField.setDisable(busy || courseField.getValue() == null || courseField.getValue().value().isEmpty() || !hasSessions || aggregateRequirements && requirementsVisible);
        openScopeButton.setSelected(scope == PlanningPresentation.CourseScope.OPEN);
        archiveScopeButton.setSelected(scope == PlanningPresentation.CourseScope.ARCHIVE);
        openScopeButton.setDisable(busy);
        archiveScopeButton.setDisable(busy);
        requirementsLink.setText(aggregateRequirements ? "Torna al totale" : "Vedi fabbisogni");
        requirementsLink.setGraphic(aggregateRequirements
                ? it.uninafoodlab.presentation.common.LabIcons.back(16) : null);
        singleScopeButton.setSelected(!aggregateRequirements);
        futureScopeButton.setSelected(aggregateRequirements);
        singleScopeButton.setDisable(busy);
        futureScopeButton.setDisable(busy || scope != PlanningPresentation.CourseScope.OPEN || courseField.getValue() == null || courseField.getValue().value().isEmpty());
    }
}
