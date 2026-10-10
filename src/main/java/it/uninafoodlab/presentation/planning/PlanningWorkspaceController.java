package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.planning.BrowseChefPlanning;
import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.application.planning.SessionParticipant;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import it.uninafoodlab.presentation.common.WorkspaceState;
import java.time.Month;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javafx.scene.control.Button;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.application.Platform;

import static it.uninafoodlab.presentation.planning.PlanningSelection.selectedValue;

public final class PlanningWorkspaceController implements WorkspaceState {

    @Override public boolean hasUnsavedChanges() { return false; }
    @Override public boolean isBusy() { return feedback != null && feedback.isBusy(); }

    private final AuthenticatedUser user;
    private final BrowseCourses browseCourses;
    private PlanningSelection selection;
    private PlanningFeedback feedback;
    private PlanningFilterControls filterControls;
    private final BrowseChefPlanning browseChefPlanning;
    private it.uninafoodlab.application.planning.ManageSessionLifecycle lifecycle;
    private String exportContext = "";

    public void setSessionLifecycle(it.uninafoodlab.application.planning.ManageSessionLifecycle value) { lifecycle = value; }

    @FXML private void printRequirements() {
        var context = selectedValue(courseField).map(course -> course.title().value()).orElse("Lista della spesa") + ", " + exportContext;
        PlanningDocuments.print(workspaceScroll.getScene().getWindow(), feedback.isBusy(),
                context, requirementTable.getItems(), feedback);
    }

    @FXML private void showReportDetails() {
        if (feedback.isBusy()) return;
        reportPanel.setVisible(false);
        reportPanel.setManaged(false);
        pageHeader.setVisible(false);
        pageHeader.setManaged(false);
        reportDetailsPanel.setVisible(true);
        reportDetailsPanel.setManaged(true);
        reportDetailsTitle.setText("Dettaglio sessioni, " + PlanningLabels.periodLabel(
                java.time.YearMonth.of(reportYearField.getValue(), periodField.getValue())));
        detailsView.show();
        workspaceScroll.setVvalue(0);
        reportDetailsBack.requestFocus();
    }

    @FXML private HBox pageHeader;
    @FXML private VBox workspaceContent;
    @FXML private Button requirementsLink;
    @FXML private HBox participantFooter;
    @FXML private Button previousMonthButton;
    @FXML private Button nextMonthButton;
    private boolean embedded;
    @FXML private Button courseReturnButton;
    private Runnable courseReturnAction;

    @FXML private ComboBox<Month> periodField;
    @FXML private ComboBox<Integer> reportYearField;
    @FXML private ToggleButton openScopeButton;
    @FXML private ToggleButton archiveScopeButton;
    @FXML private ToggleButton singleScopeButton;
    @FXML private ToggleButton futureScopeButton;
    @FXML private VBox calculationScope;
    @FXML private javafx.scene.layout.FlowPane planningFilterFields;
    @FXML private Label sessionContextLabel;
    private boolean aggregateRequirements;

    private ReportPeriodSelector reportPeriod;
    @FXML private ScrollPane workspaceScroll;
    @FXML private VBox reportPanel;
    @FXML private VBox reportResults;
    @FXML private javafx.scene.layout.FlowPane reportMetrics;
    @FXML private javafx.scene.layout.FlowPane reportCharts;
    @FXML private VBox sessionFilters;
    @FXML private ComboBox<PlanningPresentation.CourseScope> scopeField;
    @FXML private Label courseScopeDescription;
    @FXML private VBox requirementPanel;
    @FXML private VBox participantPanel;
    @FXML private Label requirementSummaryLabel;
    @FXML private Label requirementNoteLabel;
    @FXML private VBox missingMenusBox;
    @FXML private Label participantEmptyLabel;
    @FXML private Label recipeRangeLabel;
    @FXML private Label totalCoursesLabel;
    @FXML private Label onlineSessionsLabel;
    @FXML private Label practicalSessionsLabel;
    @FXML private Label recipeStatisticsLabel;
    @FXML private BarChart<Number, String> sessionChart;
    @FXML private BarChart<Number, String> recipeChart;
    @FXML private ComboBox<PlanningFilterChoice<CourseSummary>> courseField;
    @FXML private ComboBox<PlanningFilterChoice<CourseSessionSummary>> sessionField;
    @FXML private TableView<IngredientRequirement> requirementTable;
    @FXML private TableColumn<IngredientRequirement, String> ingredientColumn;
    @FXML private TableColumn<IngredientRequirement, String> confirmedParticipantsColumn;
    @FXML private TableColumn<IngredientRequirement, String> quantityColumn;
    @FXML private TableView<SessionParticipant> participantTable;
    @FXML private TableColumn<SessionParticipant, String> participantColumn;
    @FXML private TableColumn<SessionParticipant, String> enrollmentColumn;
    @FXML private TableColumn<SessionParticipant, String> attendanceColumn;
    @FXML private Label participantSummaryLabel;
    @FXML private javafx.scene.layout.FlowPane participantMetrics;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label feedbackLabel;
    @FXML private HBox feedbackPanel;
    @FXML private Button retryButton;

    @FXML private Label pageTitle;
    @FXML private Button printShoppingButton;
    @FXML private Button embeddedPrintButton;
    @FXML private VBox reportDetailsPanel;
    @FXML private Label reportDetailsTitle;
    @FXML private Button reportDetailsBack;
    @FXML private Button reportDetailsButton;
    @FXML private void returnToReport() {
        detailsView.reset();
        reportDetailsPanel.setVisible(false);
        reportDetailsPanel.setManaged(false);
        reportPanel.setVisible(true);
        reportPanel.setManaged(true);
        pageHeader.setVisible(true);
        pageHeader.setManaged(true);
        workspaceScroll.setVvalue(0);
        reportDetailsButton.requestFocus();
    }
    @FXML private VBox reportDetailsContent;
    private ReportDetailsView detailsView;
    @FXML private Label pageDescription;
    @FXML private Label reportScopeLabel;
    @FXML private Label reportEmptyLabel;
    @FXML private Label recipeEmptyLabel;

    private PlanningResultsView planningResults;

    private int planningLoadVersion;
    private MonthlyReportPresenter reportPresenter;

    private java.util.function.BiConsumer<UUID, UUID> sessionNavigation = this::showSessionPlanning;

    public PlanningWorkspaceController(
            AuthenticatedUser user,
            BrowseCourses browseCourses,
            BrowseChefPlanning browseChefPlanning) {
        this.user = Objects.requireNonNull(user, "user");
        this.browseCourses = Objects.requireNonNull(browseCourses, "browseCourses");
        this.browseChefPlanning = Objects.requireNonNull(browseChefPlanning, "browseChefPlanning");
    }

    @FXML
    private void initialize() {
        reportDetailsBack.setGraphic(it.uninafoodlab.presentation.common.LabIcons.back(16));
        courseReturnButton.setGraphic(it.uninafoodlab.presentation.common.LabIcons.back(16));
        feedback = new PlanningFeedback(
                this::updateFilterControls,
                retryButton,
                feedbackLabel,
                progressIndicator,
                requirementPanel,
                workspaceScroll,
                requirementTable);
        selection = new PlanningSelection(user, browseCourses, new PlanningSelection.Host() {
            public void invalidatePlanning() { planningLoadVersion++; }
            public void useSingleSession() { aggregateRequirements = false; }
            public boolean isBusy() { return feedback.isBusy(); }
            public void setEmbedded(boolean value) { PlanningWorkspaceController.this.setEmbedded(value); }
            public void showPlanning() { PlanningWorkspaceController.this.showPlanning(); }
            public void showPanels(boolean requirements, boolean participants, boolean report) {
                PlanningWorkspaceController.this.showPanels(requirements, participants, report);
            }
            public void clearSessionPlanning(String summary) { PlanningWorkspaceController.this.clearSessionPlanning(summary); }
            public void fail(String message) { feedback.fail(message); }
            public void fail(String message, Runnable retry) { feedback.fail(message, retry); }
            public void setBusy(boolean value) { feedback.setBusy(value); }
            public void loadCourseParticipants(UUID courseId) { PlanningWorkspaceController.this.loadCourseParticipants(courseId); }
            public void loadSessionPlanning() { PlanningWorkspaceController.this.loadSessionPlanning(); }
            public void loadCourseRequirements() { PlanningWorkspaceController.this.loadCourseRequirements(); }
        }, scopeField, courseField, sessionField, courseScopeDescription);
        feedbackPanel.visibleProperty().bind(feedbackLabel.visibleProperty());
        feedbackPanel.managedProperty().bind(feedbackLabel.visibleProperty());
        filterControls = new PlanningFilterControls(
                planningFilterFields,
                calculationScope,
                reportCharts,
                scopeField,
                courseField,
                sessionField,
                openScopeButton,
                archiveScopeButton,
                requirementsLink,
                singleScopeButton,
                futureScopeButton);
        planningResults = new PlanningResultsView(
                missingMenusBox,
                requirementTable,
                participantTable,
                participantSummaryLabel,
                participantMetrics,
                sessionContextLabel,
                participantEmptyLabel,
                requirementSummaryLabel,
                requirementNoteLabel,
                confirmedParticipantsColumn);
        var monthlyReportView = new MonthlyReportView(
                reportMetrics,
                reportCharts,
                reportScopeLabel,
                reportEmptyLabel,
                recipeEmptyLabel,
                recipeRangeLabel,
                totalCoursesLabel,
                onlineSessionsLabel,
                practicalSessionsLabel,
                recipeStatisticsLabel,
                sessionChart,
                recipeChart);
        reportPresenter = new MonthlyReportPresenter(user, browseChefPlanning, monthlyReportView, feedback::setBusy,
                () -> { feedbackLabel.setManaged(false); feedbackLabel.setVisible(false); }, feedback::fail, periodField, reportYearField, previousMonthButton, nextMonthButton, reportScopeLabel, workspaceScroll);
        reportPeriod = new ReportPeriodSelector(periodField, reportYearField, reportPanel::isVisible, reportPresenter::load);
        reportPeriod.configure();
        reportDetailsButton.disableProperty().bind(reportMetrics.visibleProperty().not());
        reportResults.visibleProperty().bind(reportMetrics.visibleProperty());
        reportResults.managedProperty().bind(reportMetrics.visibleProperty());
        detailsView = new ReportDetailsView(reportDetailsContent, user, () -> lifecycle,
                () -> java.time.YearMonth.of(reportYearField.getValue(), periodField.getValue()));
        periodField.valueProperty().addListener((observable, oldValue, newValue) -> detailsView.reset());
        reportYearField.valueProperty().addListener((observable, oldValue, newValue) -> detailsView.reset());
        selection.configureSelectors();
        new PlanningTables(requirementTable, participantTable, ingredientColumn, confirmedParticipantsColumn, quantityColumn, participantColumn, enrollmentColumn, attendanceColumn).configure();
        selection.loadCourses();
    }

    public void setSessionNavigation(java.util.function.BiConsumer<UUID, UUID> navigation) {
        sessionNavigation = Objects.requireNonNull(navigation, "navigation");
    }

    @FXML private void openRequirements() {
        showPlanning();
        feedback.requestRequirementsFocus();
        loadSessionPlanning();
    }

    private void showPanels(boolean requirements, boolean participants, boolean report) {
        detailsView.reset();
        reportDetailsPanel.setVisible(false);
        reportDetailsPanel.setManaged(false);
        if (!requirements) feedback.cancelFocusRestore();
        embeddedPrintButton.setVisible(requirements && embedded);
        embeddedPrintButton.setManaged(requirements && embedded);
        printShoppingButton.setVisible(requirements && !embedded);
        printShoppingButton.setManaged(requirements && !embedded);
        pageTitle.setVisible(true);
        pageTitle.setManaged(true);
        pageDescription.setVisible(!report && !pageDescription.getText().isBlank());
        pageDescription.setManaged(pageDescription.isVisible());
        sessionFilters.setVisible(!report && !embedded);
        sessionFilters.setManaged(!report && !embedded);
        requirementPanel.setVisible(requirements);
        requirementPanel.setManaged(requirements);
        participantPanel.setVisible(participants);
        participantPanel.setManaged(participants);
        reportPanel.setVisible(report);
        reportPanel.setManaged(report);
        courseScopeDescription.setVisible(requirements);
        courseScopeDescription.setManaged(requirements);
        sessionContextLabel.setVisible(participants);
        sessionContextLabel.setManaged(participants);
        calculationScope.setVisible(requirements);
        calculationScope.setManaged(requirements);
        openScopeButton.setVisible(!requirements);
        openScopeButton.setManaged(!requirements);
        archiveScopeButton.setVisible(!requirements);
        archiveScopeButton.setManaged(!requirements);
        workspaceScroll.setVvalue(0.0);
    }

    public void showCourseShoppingList(UUID courseId, String courseTitle, Runnable returnAction) {
        showPlanning();
        aggregateRequirements = true;
        courseReturnAction = Objects.requireNonNull(returnAction, "returnAction");
        courseReturnButton.setVisible(true);
        courseReturnButton.setManaged(true);
        pageTitle.setText("Lista della spesa");
        pageDescription.setText(courseTitle);
        pageDescription.setVisible(true);
        pageDescription.setManaged(true);
        sessionFilters.setVisible(false);
        sessionFilters.setManaged(false);
        requirementTable.setAccessibleText("Ingredienti per le sessioni pratiche future del corso");
        selection.showCourseRequirements(courseId);
    }

    @FXML private void returnToCourse() { courseReturnAction.run(); }

    public void showMonthlyReport() {
        detailsView.reset();
        setEmbedded(false);
        pageTitle.setText("Report mensile");
        pageDescription.setText("");
        showPanels(false, false, true);
        reportPresenter.load();
    }

    public void showParticipants() {
        setEmbedded(false);
        selection.setShoppingOnly(false);
        pageTitle.setText("Partecipanti");
        pageDescription.setText("Iscrizioni e adesioni alle sessioni del tuo laboratorio.");
        showPanels(false, true, false);
        sessionContextLabel.setText("");
        updateFilterControls();
        selection.loadAvailableSession();
    }

    public void showPlanning() {
        setEmbedded(false);
        selection.setShoppingOnly(true);
        pageTitle.setText("Lista della spesa");
        pageDescription.setText("");
        showPanels(true, false, false);
    }

    private void setEmbedded(boolean value) {
        embedded = value;
        courseReturnButton.setVisible(false);
        courseReturnButton.setManaged(false);
        if (!value) selection.leaveEmbedded();
        pageHeader.setVisible(!value);
        pageHeader.setManaged(!value);
        requirementsLink.setVisible(!value);
        requirementsLink.setManaged(!value);
        workspaceContent.setPadding(value ? javafx.geometry.Insets.EMPTY : new javafx.geometry.Insets(20));
        workspaceContent.getStyleClass().remove("planning-embedded");
        if (value) workspaceContent.getStyleClass().add("planning-embedded");
        pageDescription.setVisible(!value);
        pageDescription.setManaged(!value);
    }

    @FXML
    private void loadSessionPlanning() {
        if (selection.isUpdating()) return;
        updateFilterControls();
        if (aggregateRequirements && requirementPanel.isVisible()) {
            loadCourseRequirements();
            return;
        }
        var session = selectedValue(sessionField);
        if (session.isEmpty()) {
            showUnselectedSession();
            return;
        }
        attendanceColumn.setVisible(true);
        participantTable.setAccessibleText("Partecipanti e adesioni alla sessione");
        participantFooter.setVisible(true);
        participantFooter.setManaged(true);
        var selectedSession = session.orElseThrow();
        var version = ++planningLoadVersion;
        clearSessionPlanning("Caricamento della sessione in corso.");
        feedback.setBusy(true);
        FxAsyncExecutor.run(
                "session-planning-load",
                () -> {
                    var snapshots = browseChefPlanning.exportSnapshots(user, List.of(selectedSession.id()));
                    return new SessionPlanning(snapshots.getFirst().requirements(),
                            browseChefPlanning.participants(user, selectedSession.id()),
                            PlanningDocuments.exportContext(List.of(selectedSession), snapshots));
                },
                planning -> {
                    if (version != planningLoadVersion) return;
                    planningResults.renderSession(selectedSession, planning.requirements(), planning.participants());
                    exportContext = planning.context();
                    requirementNoteLabel.setText(exportContext);
                    feedbackLabel.setManaged(false);
                    feedbackLabel.setVisible(false);
                    feedback.setBusy(false);
                    Platform.runLater(() -> workspaceScroll.setVvalue(0.0));
                },
                failure -> { if (version == planningLoadVersion) feedback.fail("Non è stato possibile caricare la pianificazione della sessione.", this::loadSessionPlanning); });
    }

    private void loadCourseRequirements() {
        var version = ++planningLoadVersion;
        var courseId = selectedValue(courseField).map(CourseSummary::id).orElse(null);
        if (courseId == null) {
            clearSessionPlanning("Scegli un corso.");
            return;
        }
        clearSessionPlanning("Calcolo dei fabbisogni futuri in corso.");
        feedback.setBusy(true);
        FxAsyncExecutor.run(
                "course-planning-load",
                () -> {
                    var courseExport = browseChefPlanning.courseExport(user, courseId);
                    var futureSessions = courseExport.sessions();
                    var snapshots = courseExport.snapshots();
                    return new SessionPlanning(BrowseChefPlanning.aggregateRequirements(snapshots), List.of(),
                            PlanningDocuments.exportContext(futureSessions, snapshots), futureSessions);
                },
                result -> {
                    if (version != planningLoadVersion) return;
                    planningResults.renderFutureRequirements(result.requirements(), result.futureSessions(), courseId, sessionNavigation);
                    exportContext = result.context();
                    requirementNoteLabel.setText("Totale delle pratiche future, calcolato sulle adesioni confermate.");
                    feedbackLabel.setManaged(false);
                    feedbackLabel.setVisible(false);
                    feedback.setBusy(false);
                },
                failure -> { if (version == planningLoadVersion) feedback.fail("Non è stato possibile calcolare i fabbisogni del corso.", this::loadSessionPlanning); });
    }

    @FXML private void showOpenCourses() { scopeField.setValue(PlanningPresentation.CourseScope.OPEN); }
    @FXML private void showArchivedCourses() { scopeField.setValue(PlanningPresentation.CourseScope.ARCHIVE); }
    @FXML private void showSingleRequirements() { aggregateRequirements = false; loadSessionPlanning(); }
    @FXML private void showFutureRequirements() { aggregateRequirements = true; loadSessionPlanning(); }

    @FXML private void showPreviousMonth() { reportPeriod.shiftMonth(-1); }
    @FXML private void showNextMonth() { reportPeriod.shiftMonth(1); }

    @FXML private void showCurrentMonth() { reportPeriod.showCurrentMonth(); }

    private void loadCourseParticipants(UUID courseId) {
        participantTable.setAccessibleText("Iscritti al corso");
        var version = ++planningLoadVersion;
        attendanceColumn.setVisible(false);
        participantFooter.setVisible(false);
        participantFooter.setManaged(false);
        clearSessionPlanning("Caricamento degli iscritti al corso.");
        feedback.setBusy(true);
        FxAsyncExecutor.run("course-participants-load",
                () -> browseChefPlanning.courseParticipants(user, courseId),
                participants -> {
                    if (version != planningLoadVersion) return;
                    planningResults.renderCourseParticipants(participants);
                    feedback.setBusy(false);
                }, failure -> {
                    if (version == planningLoadVersion) feedback.fail("Non è stato possibile caricare gli iscritti al corso.",
                            () -> loadCourseParticipants(courseId));
                });
    }

    private record SessionPlanning(
            List<IngredientRequirement> requirements,
            List<SessionParticipant> participants, String context, List<CourseSessionSummary> futureSessions) {
        private SessionPlanning(List<IngredientRequirement> requirements, List<SessionParticipant> participants,
                String context) {
            this(requirements, participants, context, List.of());
        }
    }

    private void clearSessionPlanning(String summary) {
        planningResults.clear(summary);
        feedbackLabel.setManaged(false);
        feedbackLabel.setVisible(false);
        updateFilterControls();
    }

    private void showUnselectedSession() {
        selection.loadUnselectedSession(requirementPanel.isVisible());
    }

    public void refreshForNavigation() { selection.refreshForNavigation(); }
    public void showSessionPlanning(UUID courseId, UUID sessionId) { selection.showSessionPlanning(courseId, sessionId); }
    public void showEmbeddedSession(UUID courseId, UUID sessionId, boolean participants) {
        selection.showEmbeddedSession(courseId, sessionId, participants);
    }
    @FXML private void loadSelectedCourseSessions() { selection.loadSelectedCourseSessions(); }
    @FXML private void changeCourseScope() { selection.changeCourseScope(); }
    private void updateFilterControls() {
        printShoppingButton.setDisable(feedback.isBusy());
        embeddedPrintButton.setDisable(feedback.isBusy());
        filterControls.update(feedback.isBusy(), selection.scope(), aggregateRequirements, requirementPanel.isVisible(), !selection.sessions().isEmpty());
    }

    @FXML private void retryFailedLoad() { feedback.retry(); }

}
