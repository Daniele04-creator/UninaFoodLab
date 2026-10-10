package it.uninafoodlab.presentation.dashboard;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import it.uninafoodlab.presentation.common.LabIcons;
import it.uninafoodlab.presentation.common.WorkspaceState;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class DashboardWorkspaceController implements WorkspaceState {
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.ITALIAN);
    private final DashboardOverviewLoader overviewLoader;
    private final Runnable openCoursesAction;
    private final BiConsumer<UUID, UUID> openSessionActionsAction;
    private final BiConsumer<UUID, UUID> openRecipeAssignmentAction;
    private final DashboardCalendar<DashboardSession> calendar =
            new DashboardCalendar<>(LocalDate.now(ROME), item -> item.session().scheduledAt());
    private Runnable createCourseAction;
    private DashboardPreparationQueue preparationQueue;
    private DashboardAgendaView agendaView;
    private long requestVersion;
    private boolean disposed;
    private boolean busy;

    @FXML private Label monthLabel;
    @FXML private Label selectedDayLabel;
    @FXML private Label feedbackLabel;
    @FXML private Label emptyDayLabel;
    @FXML private Button nextAvailableButton;
    @FXML private Button refreshButton;
    @FXML private Button previousWeekButton;
    @FXML private Button nextWeekButton;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private HBox weekSchedule;
    @FXML private VBox daySessions;
    @FXML private VBox emptyDayPanel;
    @FXML private VBox taskRows;
    @FXML private VBox preparationSection;
    @FXML private ToggleButton preparationToggle;
    @FXML private GridPane calendarHeader;
    @FXML private HBox weekControls;
    @FXML private Button createCourseButton;

    public DashboardWorkspaceController(AuthenticatedUser user, BrowseCourses browseCourses,
            Runnable openCoursesAction,
            BiConsumer<UUID, UUID> openSessionActionsAction,
            BiConsumer<UUID, UUID> openRecipeAssignmentAction) {
        overviewLoader = new DashboardOverviewLoader(Objects.requireNonNull(user),
                Objects.requireNonNull(browseCourses));
        this.openCoursesAction = Objects.requireNonNull(openCoursesAction);
        this.openSessionActionsAction = Objects.requireNonNull(openSessionActionsAction);
        this.openRecipeAssignmentAction = Objects.requireNonNull(openRecipeAssignmentAction);
    }

    @FXML private void initialize() {
        agendaView = new DashboardAgendaView(taskRows, openSessionActionsAction);
        preparationQueue = new DashboardPreparationQueue(preparationToggle, preparationSection, openRecipeAssignmentAction);
        previousWeekButton.setGraphic(LabIcons.icon("chevron-down", "dark", 16));
        previousWeekButton.getGraphic().setRotate(90);
        nextWeekButton.setGraphic(LabIcons.icon("chevron-down", "dark", 16));
        nextWeekButton.getGraphic().setRotate(-90);
        createCourseButton.setGraphic(LabIcons.icon("plus-square", "light", 17));
        calendarHeader.widthProperty().addListener((observable, before, after) -> layoutCalendarHeader());
        layoutCalendarHeader();
    }

    private void layoutCalendarHeader() {
        boolean wide = calendarHeader.getWidth() >= 900;
        var month = new ColumnConstraints();
        month.setMinWidth(0);
        month.setPrefWidth(wide ? 150 : 0);
        month.setHgrow(wide ? Priority.NEVER : Priority.ALWAYS);
        var middle = new ColumnConstraints();
        middle.setMinWidth(0);
        middle.setHgrow(Priority.ALWAYS);
        var controls = new ColumnConstraints();
        controls.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        calendarHeader.getColumnConstraints().setAll(month, middle, controls);
        GridPane.setConstraints(monthLabel, 0, 0, wide ? 1 : 2, 1);
        GridPane.setConstraints(weekSchedule, wide ? 1 : 0, wide ? 0 : 1, wide ? 1 : 3, 1);
        GridPane.setConstraints(weekControls, 2, 0);
        GridPane.setHalignment(weekControls, javafx.geometry.HPos.RIGHT);
        GridPane.setHgrow(weekSchedule, Priority.ALWAYS);
    }

    public void setCreateCourseAction(Runnable action) { createCourseAction = Objects.requireNonNull(action); }
    public void activate() { refresh(); }

    @Override public boolean hasUnsavedChanges() { return false; }
    @Override public boolean isBusy() { return busy; }

    @Override public void dispose() {
        disposed = true;
        requestVersion++;
        busy = false;
    }

    @FXML private void createCourse() {
        if (createCourseAction != null) createCourseAction.run(); else openCoursesAction.run();
    }

    @FXML private void togglePreparation() { preparationQueue.toggle(); }
    @FXML private void previousWeek() { calendar.moveWeeks(-1); showSelectedDay(); }
    @FXML private void nextWeek() { calendar.moveWeeks(1); showSelectedDay(); }
    @FXML private void goToToday() { selectDay(LocalDate.now(ROME)); }
    private void selectDay(LocalDate day) { calendar.selectDay(day); showSelectedDay(); }

    @FXML private void goToNextAvailable() {
        DashboardSessionPolicy.nextAgendaSession(calendar)
                .ifPresentOrElse(item -> selectDay(item.session().scheduledAt().toLocalDate()), openCoursesAction);
    }

    @FXML private void refresh() {
        if (disposed) return;
        var version = ++requestVersion;
        refreshButton.setManaged(false);
        refreshButton.setVisible(false);
        feedbackLabel.setManaged(false);
        feedbackLabel.setVisible(false);
        setBusy(true);
        FxAsyncExecutor.run("dashboard-load", overviewLoader::load, snapshot -> {
            if (!disposed && version == requestVersion) showSnapshot(snapshot);
        }, ignored -> {
            if (!disposed && version == requestVersion) fail();
        });
    }

    private void showSnapshot(DashboardOverviewLoader.DashboardSnapshot snapshot) {
        calendar.setSessions(snapshot.sessions());
        preparationQueue.show(snapshot.menusToPrepare());
        agendaView.showTasks(snapshot.menusToPrepare().size());
        showSelectedDay();
        setBusy(false);
    }

    private void showSelectedDay() {
        DashboardWeekStrip.render(weekSchedule, calendar.monday(), LocalDate.now(ROME),
                calendar.selectedDay(), calendar.sessionCounts(), this::selectDay);
        monthLabel.setText(calendar.selectedDay().format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ITALIAN)));
        selectedDayLabel.setText(formatDashboardDate(calendar.selectedDay()));
        var sessions = calendar.sessionsOnSelectedDay();
        agendaView.showDaySessions(daySessions, sessions);
        agendaView.showEmptyDay(sessions.isEmpty(), calendar, daySessions, emptyDayPanel, emptyDayLabel, nextAvailableButton);
    }

    static String formatDashboardDate(LocalDate date) {
        var text = Objects.requireNonNull(date, "date").format(DATE);
        return text.substring(0, 1).toUpperCase(Locale.ITALIAN) + text.substring(1);
    }

    private void fail() {
        refreshButton.setVisible(true);
        refreshButton.setManaged(true);
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
        feedbackLabel.setText("Non è stato possibile aggiornare l’agenda. Premi Riprova.");
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        progressIndicator.setManaged(busy);
        progressIndicator.setVisible(busy);
        refreshButton.setDisable(busy);
    }

    record DashboardSession(UUID courseId, String courseTitle, CourseSessionSummary session) {
        DashboardSession {
            Objects.requireNonNull(courseId, "courseId");
            Objects.requireNonNull(courseTitle, "courseTitle");
            Objects.requireNonNull(session, "session");
        }
    }
}
