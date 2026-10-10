package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.ManageRecipeCatalog;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.catalog.CreateCategory;
import it.uninafoodlab.application.course.AssignRecipeToSession;
import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.course.CreateCourse;
import it.uninafoodlab.application.schedule.ManageCourseSchedule;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class CourseWorkspaceController implements it.uninafoodlab.presentation.common.WorkspaceState {

    private final AuthenticatedUser user;
    private final BrowseCatalog browseCatalog;
    private final CreateCategory createCategory;
    private final CreateCourse createCourse;
    private final BrowseCourses browseCourses;
    private final AssignRecipeToSession assignRecipeToSession;
    private final ManageCourseSchedule manageCourseSchedule;
    private final Clock clock;
    private CourseCreationForm creationForm;
    private CourseRecipeMenu recipeMenu;
    private CourseDetailPane detailPane;
    private CourseSessionNavigation navigation;
    private CourseCatalogPane catalogPane;
    private CourseSessionDetails sessionDetails;

    @FXML private ComboBox<CategorySummary> categoryField;
    @FXML private Button createCategoryButton;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private DatePicker startDateField;
    @FXML private TimeSelector startTimeField;
    @FXML private Spinner<Integer> recurrenceField;
    @FXML private Spinner<Integer> sessionCountField;
    @FXML private Spinner<Integer> durationField;
    @FXML private javafx.scene.layout.TilePane sessionModeEditor;
    @FXML private VBox commonSessionSettings;
    @FXML private javafx.scene.layout.GridPane courseCreationOverview;
    @FXML private Label courseSummaryLabel;
    @FXML private Label createFeedbackLabel;
    @FXML private Button createButton;
    @FXML private Button cancelCreateCourseButton;
    @FXML private Button courseRetryButton;
    @FXML private ProgressIndicator createProgressIndicator;
    @FXML private Label createProgressLabel;
    @FXML private Button openCreateCourseButton;
    @FXML private VBox courseCatalogView;
    @FXML private HBox courseCatalogLayout;
    @FXML private HBox coursePageHeader;
    @FXML private VBox courseSelectorRail;
    @FXML private VBox courseOverview;
    @FXML private Label recipeCourseContext;
    @FXML private Label recipeTaskDescription;
    @FXML private ScrollPane createCourseView;
    @FXML private VBox createCoursePanel;
    @FXML private ToggleButton activeCoursesTab;
    @FXML private ToggleButton archiveCoursesTab;
    @FXML private ToggleButton allCoursesTab;
    @FXML private Button refreshSessionRecipesButton;
    @FXML private Label courseCountLabel;
    @FXML private ComboBox<CourseCategoryFilter> categoryFilter;
    @FXML private ListView<CourseSummary> courseList;
    @FXML private javafx.scene.control.TextField courseSearchField;

    @FXML private Label selectedCourseLabel;
    @FXML private Label selectedCourseMetaLabel;
    @FXML private Label selectedCourseDescriptionLabel;
    @FXML private Label selectedCourseFactsLabel;
    @FXML private Button communicateCourseButton;
    @FXML private ToggleButton programTab;
    @FXML private ToggleButton recipeTab;
    @FXML private VBox programPane;
    @FXML private VBox sessionCommandBar;
    @FXML private VBox sessionAgenda;
    @FXML private Label archiveReadOnlyLabel;
    @FXML private VBox recipePane;
    @FXML private javafx.scene.layout.ColumnConstraints menuReadColumn;
    @FXML private javafx.scene.layout.ColumnConstraints menuEditColumn;
    @FXML private VBox sessionDetailHeader;
    @FXML private Label sessionDetailTitle;
    @FXML private Label sessionDetailContext;
    @FXML private javafx.scene.layout.FlowPane sessionDetailMetadata;
    @FXML private Label sessionDetailMode;
    @FXML private Label sessionDetailStatus;
    @FXML private Label sessionDetailLocation;
    @FXML private ToggleButton sessionMenuTab;
    @FXML private ToggleButton sessionParticipantsTab;
    @FXML private Button courseParticipantsButton;
    @FXML private ScrollPane courseContentScroll;
    @FXML private javafx.scene.layout.StackPane sessionPlanningHost;
    private SessionPlanningAction sessionPlanningAction;
    private it.uninafoodlab.application.planning.ManageSessionLifecycle lifecycle;
    public void setSessionLifecycle(it.uninafoodlab.application.planning.ManageSessionLifecycle value) { lifecycle = value; }
    private CourseSessionCommands sessionCommands;

    @FXML private javafx.scene.layout.GridPane courseCards;
    @FXML private javafx.scene.layout.HBox coursePaginationBottom;
    @FXML private ScrollPane courseCatalogScroll;
    private CourseCardGrid cardGrid;
    @FXML private VBox courseDetailView;
    @FXML private Button courseReturnButton;
    @FXML private Button createCourseReturnButton;

    @FXML private void refreshSessionRecipes() { sessionCommands.refreshRecipes(); }

    @FunctionalInterface
    public interface SessionPlanningAction {
        javafx.scene.Node open(UUID courseId, UUID sessionId, boolean participants);
    }

    public void setSessionPlanningAction(SessionPlanningAction action) {
        sessionPlanningAction = Objects.requireNonNull(action, "action");
    }
    @FXML private TableView<CourseSessionSummary> sessionTable;
    @FXML private TableColumn<CourseSessionSummary, String> sequenceColumn;
    @FXML private TableColumn<CourseSessionSummary, String> dateColumn;
    @FXML private TableColumn<CourseSessionSummary, String> modeColumn;
    @FXML private TableColumn<CourseSessionSummary, String> detailsColumn;
    @FXML private TableColumn<CourseSessionSummary, String> recipeCountColumn;
    @FXML private VBox recipeCatalogRows;
    @FXML private javafx.scene.layout.HBox recipePaginationBottom;
    @FXML private Label recipeResultsLabel;
    @FXML private Button recipeRetryButton;

    @FXML private Button sessionReturnButton;

    @FXML private TextField recipeSearchField;
    @FXML private ToggleButton recipeRequirementsButton;

    private java.util.function.BiConsumer<UUID, UUID> requirementsAction;
    @FXML private Label assignedRecipesLabel;
    @FXML private VBox assignedRecipeRows;
    @FXML private ComboBox<CourseSessionSummary> recipeSessionField;
    @FXML private Label recipeAvailabilityLabel;
    @FXML private VBox recipeAddPanel;
    @FXML private Label recipeSessionContext;
    @FXML private Label selectedSessionContext;
    @FXML private Label sessionLocationCaption;
    @FXML private Label sessionLocationValue;
    @FXML private HBox programLessonActions;
    @FXML private HBox detailLessonActions;
    @FXML private Button openProgramLessonButton;
    @FXML private Button copyProgramLessonButton;
    @FXML private Button openDetailLessonButton;
    @FXML private Button copyDetailLessonButton;
    @FXML private Label sessionCapacityValue;
    @FXML private Label sessionCutoffValue;
    @FXML private VBox sessionRecipeLinks;
    @FXML private Button sessionMenuButton;
    private ManageRecipeCatalog recipeCatalog;
    @FXML private Button rescheduleButton;
    @FXML private Button addSessionButton;
    @FXML private Button cancelSessionButton;
    @FXML private Label feedbackLabel;
    @FXML private ProgressIndicator progressIndicator;

    private Consumer<UUID> communicationAction;
    private Consumer<CourseSummary> shoppingListAction;
    @FXML private Button shoppingListButton;
    private CourseWorkspaceCoordinator coordinator;
    private CourseWorkspaceData workspaceData;
    private CourseWorkspaceAvailability availability;
    private CourseScheduleCommands scheduleCommands;

    @Override
    public boolean isBusy() { return coordinator != null && coordinator.isBusy(); }

    @Override
    public boolean hasUnsavedChanges() { return creationForm != null && creationForm.hasDraft(); }
    private final ReactiveFilterGuard filterGuard = new ReactiveFilterGuard();

    public CourseWorkspaceController(
            AuthenticatedUser user,
            BrowseCatalog browseCatalog,
            CreateCategory createCategory,
            CreateCourse createCourse,
            BrowseCourses browseCourses,
            AssignRecipeToSession assignRecipeToSession,
            ManageCourseSchedule manageCourseSchedule) {
        this(user, browseCatalog, createCategory, createCourse, browseCourses, assignRecipeToSession,
                manageCourseSchedule,
                Clock.system(ZoneId.of("Europe/Rome")));
    }

    CourseWorkspaceController(
            AuthenticatedUser user,
            BrowseCatalog browseCatalog,
            CreateCategory createCategory,
            CreateCourse createCourse,
            BrowseCourses browseCourses,
            AssignRecipeToSession assignRecipeToSession,
            ManageCourseSchedule manageCourseSchedule,
            Clock clock) {
        this.user = Objects.requireNonNull(user, "user");
        this.browseCatalog = Objects.requireNonNull(browseCatalog, "browseCatalog");
        this.createCategory = Objects.requireNonNull(createCategory, "createCategory");
        this.createCourse = Objects.requireNonNull(createCourse, "createCourse");
        this.browseCourses = Objects.requireNonNull(browseCourses, "browseCourses");
        this.assignRecipeToSession = Objects.requireNonNull(
                assignRecipeToSession, "assignRecipeToSession");
        this.manageCourseSchedule = Objects.requireNonNull(manageCourseSchedule, "manageCourseSchedule");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @FXML
    private void initialize() {
        it.uninafoodlab.presentation.common.TextAreaNavigation.install(descriptionField);
        shoppingListButton.disableProperty().bind(courseParticipantsButton.disableProperty());

        CourseWorkspaceViewSetup.configureIcons(new CourseWorkspaceViewSetup.Controls(
                openCreateCourseButton, addSessionButton, courseParticipantsButton,
                communicateCourseButton, sessionMenuButton));
        CourseWorkspaceViewSetup.configureReturns(courseReturnButton, sessionReturnButton, createCourseReturnButton);
        cardGrid = new CourseCardGrid(courseCards, courseList, clock, () -> detailPane.showProgram());
        cardGrid.installPagination(coursePaginationBottom, () -> courseCatalogScroll.setVvalue(0));

        var operationDialogs = new CourseOperationDialogs(selectedCourseLabel, this::isBusy);
        coordinator = new CourseWorkspaceCoordinator(new CourseWorkspaceCoordinator.Fields(
                createButton, createCategoryButton, categoryFilter, activeCoursesTab, archiveCoursesTab, allCoursesTab, courseList, courseSearchField, recipeSessionField, progressIndicator, createCourseView, createFeedbackLabel, feedbackLabel, courseCatalogView, openCreateCourseButton, titleField, createCoursePanel, cancelCreateCourseButton, createCourseReturnButton, createProgressIndicator, createProgressLabel, courseRetryButton), () -> { availability.update(); sessionCommands.update(); },
                () -> workspaceData.resumePendingReads(), () -> recipeMenu.markCatalogUnavailable(),
                operationDialogs::setBusy, operationDialogs::showError);
        sessionDetails = new CourseSessionDetails(new CourseSessionDetails.Fields(
                selectedSessionContext, sessionLocationCaption, sessionLocationValue,
                programLessonActions, detailLessonActions, openProgramLessonButton,
                copyProgramLessonButton, openDetailLessonButton, copyDetailLessonButton,
                sessionCapacityValue, sessionCutoffValue, sessionRecipeLinks, sessionMenuButton),
                sessionTable, coordinator);
        coordinator.setCourseFormVisible(false);
        creationForm = new CourseCreationForm(new CourseCreationForm.Fields(
                categoryField, titleField, descriptionField, startDateField, startTimeField,
                recurrenceField, sessionCountField, durationField, sessionModeEditor, courseSummaryLabel),
                clock, createCourse, user, coordinator);
        creationForm.configureForm();
        creationForm.installCommonSettings(commonSessionSettings);
        CourseWorkspaceViewSetup.configureCreationLayout(courseCreationOverview, sessionModeEditor, createCategoryButton);
        var scheduleEditor = new CourseScheduleEditor(clock,
                () -> courseList.getSelectionModel().getSelectedItem(),
                () -> sessionTable.getSelectionModel().getSelectedItem());
        recipeMenu = new CourseRecipeMenu(new CourseRecipeMenu.Fields(recipeCatalogRows,
                recipeResultsLabel, recipeRetryButton, recipeSearchField, recipeSessionField,
                assignedRecipeRows, recipeAvailabilityLabel), clock, user, assignRecipeToSession,
                coordinator, () -> catalogPane.isArchive(),
                () -> courseList.getSelectionModel().getSelectedItem(),
                new CourseRecipeDetails(user, recipeCatalog, operationDialogs::configure), lifecycle);
        recipeMenu.installPagination(recipePaginationBottom);
        detailPane = new CourseDetailPane(new CourseDetailPane.Fields(
                courseList, sessionTable, recipeSessionField, sessionReturnButton, courseReturnButton, feedbackLabel, programTab, recipeTab, sessionMenuTab, sessionParticipantsTab, recipeRequirementsButton, programPane, recipePane, coursePageHeader, courseSelectorRail, courseDetailView, courseOverview, sessionDetailHeader, courseContentScroll, sessionPlanningHost, courseSearchField, sessionDetailTitle, sessionDetailContext, sessionDetailMetadata, sessionDetailMode, sessionDetailStatus, sessionDetailLocation, detailLessonActions, courseParticipantsButton, rescheduleButton, cancelSessionButton), coordinator,
                () -> { availability.update(); sessionCommands.update(); }, recipeMenu::renderRecipeCatalog,
                () -> catalogPane.isArchive(),
                () -> sessionPlanningAction);
        navigation = new CourseSessionNavigation(courseList, sessionTable, recipeSessionField,
                detailPane, coordinator);
        catalogPane = new CourseCatalogPane(new CourseCatalogPane.Fields(
                courseList, courseSearchField, categoryFilter, activeCoursesTab, archiveCoursesTab, allCoursesTab,
                courseCountLabel, selectedCourseLabel, selectedCourseMetaLabel,
                selectedCourseDescriptionLabel, selectedCourseFactsLabel), clock, coordinator,
                filterGuard, this::courseSelected);
        catalogPane.setResetPageAction(cardGrid::resetPage);
        sessionCommands = new CourseSessionCommands(new CourseSessionCommands.Fields(
                refreshSessionRecipesButton, recipeSessionField),
                clock, user, coordinator, catalogPane::isArchive, () -> lifecycle, this::refreshSelectedSession);
        recipeSearchField.textProperty().addListener((observable, previous, query) -> recipeMenu.filterRecipeCatalog());
        var selection = new CourseSessionSelection(sessionTable, recipeSessionField, scheduleEditor,
                () -> { availability.update(); sessionCommands.update(); }, recipeMenu::renderRecipeCatalog);
        new CourseSessionTable(sessionTable, sequenceColumn, dateColumn, modeColumn,
                detailsColumn, recipeCountColumn, clock, selection::selectSession);
        var sessionAgendaView = new CourseSessionAgenda(sessionAgenda, sessionCommandBar, sessionTable, clock,
                sessionRecipeLinks, recipeMenu::loadAssignedRecipeNames);
        sessionAgendaView.setOpenSession(detailPane::showRecipes);
        availability = new CourseWorkspaceAvailability(new CourseWorkspaceAvailability.Fields(
                sessionTable, courseList, recipeCourseContext, sessionCommandBar, rescheduleButton,
                cancelSessionButton, addSessionButton, archiveReadOnlyLabel, communicateCourseButton,
                programTab, recipeTab, recipeSessionField, recipeTaskDescription, assignedRecipesLabel,
                recipeAddPanel, menuReadColumn, menuEditColumn, recipeSessionContext,
                recipeRequirementsButton, recipeAvailabilityLabel), clock, catalogPane, recipeMenu,
                detailPane, sessionAgendaView, sessionDetails, coordinator::isBusy,
                () -> communicationAction != null, () -> sessionPlanningAction != null,
                () -> requirementsAction != null);
        scheduleCommands = new CourseScheduleCommands(user, manageCourseSchedule, scheduleEditor,
                operationDialogs, navigation, coordinator,
                () -> courseList.getSelectionModel().getSelectedItem(),
                () -> sessionTable.getSelectionModel().getSelectedItem(), catalogPane::isArchive);
        availability.update();
        sessionCommands.update();
        detailPane.showCatalog();
        workspaceData = new CourseWorkspaceData(new CourseWorkspaceData.Fields(categoryField, categoryFilter,
                courseList, sessionTable, recipeSessionField), user, browseCatalog, browseCourses,
                coordinator, catalogPane, recipeMenu, detailPane, navigation, filterGuard,
                () -> { availability.update(); sessionCommands.update(); }, cardGrid);
        coordinator.setWorkspaceData(workspaceData);
        workspaceData.loadInitialData();
    }

    @FXML
    public void refreshCatalog() { workspaceData.refreshCatalog(); }

    @FXML
    private void retryCourseRead() { coordinator.retryRead(); }

    public void refreshSelectedSession() {
        workspaceData.refreshSelectedCourse();
    }

    @FXML
    private void createCategory() {
        new CourseCategoryDialog(openCreateCourseButton, createCategory, user, coordinator).show();
    }

    @FXML
    private void createCourse() {
        creationForm.createCourse();
    }

    public void showCourse(UUID courseId) {
        detailPane.setSessionReturnAction(null, null);
        navigation.showCourse(courseId);
    }

    public void showCourseCreation() {
        coordinator.whenIdle(() -> {
            showCourseIndex();
            coordinator.setCourseFormVisible(true);
        });
    }

    @FXML
    private void toggleCourseForm() {
        coordinator.whenIdle(() -> coordinator.setCourseFormVisible(!createCourseView.isVisible()));
    }

    @FXML
    private void discardCourseDraft() {
        coordinator.whenIdle(creationForm::discardDraft);
    }

    @FXML
    private void filterCourses() {
        catalogPane.filterCourses();
    }

    @FXML
    private void resetFilters() {
        catalogPane.resetFilters();
    }

    @FXML
    private void showActiveCourses() {
        catalogPane.selectCatalogSection(CourseOperationalView.CatalogSection.ACTIVE);
    }

    @FXML
    private void showArchivedCourses() {
        catalogPane.selectCatalogSection(CourseOperationalView.CatalogSection.ARCHIVE);
    }

    @FXML
    private void showAllCourses() {
        catalogPane.selectCatalogSection(CourseOperationalView.CatalogSection.ALL);
    }

    @FXML
    public void showCourseIndex() {
        coordinator.whenIdle(() -> {
            detailPane.setSessionReturnAction(null, null);
            coordinator.setCourseFormVisible(false);
            detailPane.showCatalog();
        });
    }

    public void setSessionReturnAction(String label, Runnable action) {
        detailPane.setSessionReturnAction(label, action);
    }

    @FXML
    private void returnFromSession() {
        detailPane.returnFromSession();
    }

    @FXML
    private void showProgram() {
        detailPane.showProgram();
    }

    @FXML
    private void showRecipes() {
        detailPane.showRecipes();
    }

    @FXML
    private void showSessionParticipants() {
        detailPane.showSessionParticipants();
    }

    @FXML
    private void showCourseParticipants() {
        detailPane.showCourseParticipants();
    }

    @FXML
    private void openAddSessionDialog() { scheduleCommands.openAddSessionDialog(); }

    @FXML
    private void openRescheduleSessionDialog() { scheduleCommands.openRescheduleSessionDialog(); }

    @FXML
    private void cancelSession() { scheduleCommands.cancelSession(); }

    @FXML
    private void openLesson() { sessionDetails.openLesson(); }

    @FXML
    private void copyLessonLink() { sessionDetails.copyLessonLink(); }

    public void setRequirementsAction(java.util.function.BiConsumer<UUID, UUID> action) {
        requirementsAction = Objects.requireNonNull(action);
    }

    @FXML
    private void openRecipeRequirements() {
        CourseExternalActions.requirements(coordinator.isBusy(), courseList.getSelectionModel().getSelectedItem(),
                sessionTable.getSelectionModel().getSelectedItem(), sessionPlanningAction != null,
                () -> detailPane.selectCourseDetailSection(CourseDetailSection.REQUIREMENTS), requirementsAction);
    }

    public void setShoppingListAction(Consumer<CourseSummary> action) {
        shoppingListAction = Objects.requireNonNull(action, "action");
    }

    @FXML
    private void openShoppingList() {
        CourseExternalActions.shopping(shoppingListButton.isDisabled(),
                courseList.getSelectionModel().getSelectedItem(), shoppingListAction);
    }

    public void setCommunicationAction(Consumer<UUID> action) {
        communicationAction = Objects.requireNonNull(action, "action");
        if (communicateCourseButton != null) {
            communicateCourseButton.setDisable(coordinator.isBusy() || courseList.getSelectionModel().getSelectedItem() == null);
        }
    }

    @FXML
    private void communicateToSelectedCourse() {
        CourseExternalActions.communication(coordinator.isBusy(),
                courseList.getSelectionModel().getSelectedItem(), communicationAction);
    }

    public void showSessionActions(UUID courseId, UUID sessionId) {
        navigation.showRequestedWorkspace(courseId, sessionId, CourseDetailSection.RECIPES);
    }

    public void showSessionParticipants(UUID courseId, UUID sessionId) {
        navigation.showRequestedWorkspace(courseId, sessionId, CourseDetailSection.PARTICIPANTS);
    }

    public void showSessionRequirements(UUID courseId, UUID sessionId) {
        navigation.showRequestedWorkspace(courseId, sessionId, CourseDetailSection.REQUIREMENTS);
    }

    private void courseSelected(CourseSummary course) {
        workspaceData.selectCourse(course);
    }

    public void setRecipeCatalog(ManageRecipeCatalog recipeCatalog) {
        this.recipeCatalog = Objects.requireNonNull(recipeCatalog);
    }

}
