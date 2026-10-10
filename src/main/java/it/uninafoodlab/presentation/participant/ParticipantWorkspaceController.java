package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.course.BrowsePublishedCourses;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.course.CoursePeriod;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.enrollment.AttendanceCommitment;
import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.application.enrollment.BrowseEnrollments;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.ManageEnrollment;
import it.uninafoodlab.application.enrollment.ManagePracticalAttendance;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.application.notification.BrowseNotifications;
import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.application.notification.ParticipantNotification;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Function;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;


public final class ParticipantWorkspaceController {

    private final ParticipantWorkspaceData data;
    private final AuthenticatedUser user;
    private final Clock clock;
    private Runnable logoutAction;
    private Runnable accountAction;
    private long requestVersion;
    private boolean disposed;
    private boolean busy;
    private boolean writing;
    private Function<String, Boolean> attendanceConflictConfirmation;
    private ParticipantWorkspaceAsync async;
    private ParticipantWorkspaceView.Section currentView = ParticipantWorkspaceView.Section.BROWSE;
    private List<PublishedCourseSummary> courses = List.of();
    private List<CategorySummary> categories = List.of();
    private List<EnrollmentSummary> enrollments = List.of();
    private Map<UUID, List<PracticalAttendanceSummary>> ownPracticalSessions = Map.of();
    private Map<UUID, List<PublishedCourseSessionSummary>> ownCourseSessions = Map.of();
    private List<AttendanceCommitment> commitments = List.of();
    private final javafx.animation.PauseTransition searchDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
    private List<ParticipantNotification> notifications = List.of();
    private PublishedCourseSummary selectedCourse;
    private EnrollmentSummary selectedEnrollment;
    private ParticipantNotification selectedNotification;
    private ParticipantWorkspaceView view;
    private ParticipantCardLayout cards;

    @FXML private ScrollPane rootScroll;
    @FXML private VBox workspace;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button accountButton;
    @FXML private Button logoutButton;
    @FXML private MenuButton profileMenu;
    @FXML private Label participantLogo;
    @FXML private ToggleButton browseTab;
    @FXML private ToggleButton mineTab;
    @FXML private ToggleButton notificationsTab;
    @FXML private Label feedbackLabel;
    @FXML private Button retryButton;
    @FXML private VBox browsePanel;
    @FXML private Label browseSummary;
    @FXML private TextField courseSearch;
    @FXML private ComboBox<CategorySummary> categoryFilter;
    @FXML private Button clearFiltersButton;
    @FXML private Label courseCount;
    @FXML private ListView<PublishedCourseSummary> courseList;
    @FXML private VBox courseEmptyState;
    @FXML private VBox courseDetail;
    @FXML private Label courseCategory;
    @FXML private Label courseTitle;
    @FXML private Label courseMeta;
    @FXML private Button enrollButton;
    @FXML private Button withdrawButton;
    @FXML private Label courseDescription;
    @FXML private ListView<PublishedCourseSessionSummary> sessionList;
    @FXML private ListView<PublishedSessionRecipeSummary> menuList;
    @FXML private VBox minePanel;
    @FXML private ListView<EnrollmentSummary> enrollmentList;
    @FXML private VBox attendanceEmptyState;
    @FXML private VBox attendanceDetail;
    @FXML private Label attendanceCourseTitle;
    @FXML private Label attendanceCourseMeta;
    @FXML private Label attendanceWarning;
    @FXML private ListView<PracticalAttendanceSummary> attendanceList;
    @FXML private VBox notificationsPanel;
    @FXML private ListView<ParticipantNotification> notificationList;
    @FXML private Label unreadCount;
    @FXML private VBox notificationEmptyState;
    @FXML private VBox notificationDetail;
    @FXML private Label notificationTitle;
    @FXML private Label notificationMeta;
    @FXML private Label notificationMessage;
    @FXML private Button markReadButton;
    @FXML private Button openNotificationCourseButton;

    public ParticipantWorkspaceController(
            AuthenticatedUser user,
            BrowsePublishedCourses browsePublishedCourses,
            BrowseCatalog browseCatalog,
            BrowseEnrollments browseEnrollments,
            ManageEnrollment manageEnrollment,
            ManagePracticalAttendance managePracticalAttendance,
            BrowseNotifications browseNotifications,
            ManageNotifications manageNotifications) {
        this(user, browsePublishedCourses, browseCatalog, browseEnrollments, manageEnrollment,
                managePracticalAttendance, browseNotifications, manageNotifications,
                Clock.system(ParticipantWorkspaceRules.ROME));
    }

    ParticipantWorkspaceController(
            AuthenticatedUser user,
            BrowsePublishedCourses browsePublishedCourses,
            BrowseCatalog browseCatalog,
            BrowseEnrollments browseEnrollments,
            ManageEnrollment manageEnrollment,
            ManagePracticalAttendance managePracticalAttendance,
            BrowseNotifications browseNotifications,
            ManageNotifications manageNotifications,
            Clock clock) {
        this(user, browsePublishedCourses, browseCatalog, browseEnrollments, manageEnrollment,
                managePracticalAttendance, browseNotifications, manageNotifications, clock, null);
    }

    ParticipantWorkspaceController(
            AuthenticatedUser user,
            BrowsePublishedCourses browsePublishedCourses,
            BrowseCatalog browseCatalog,
            BrowseEnrollments browseEnrollments,
            ManageEnrollment manageEnrollment,
            ManagePracticalAttendance managePracticalAttendance,
            BrowseNotifications browseNotifications,
            ManageNotifications manageNotifications,
            Clock clock,
            Function<String, Boolean> attendanceConflictConfirmation) {
        this.user = Objects.requireNonNull(user, "user");
        data = new ParticipantWorkspaceData(
                user, browsePublishedCourses, browseCatalog, browseEnrollments, manageEnrollment,
                managePracticalAttendance, browseNotifications, manageNotifications);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.attendanceConflictConfirmation = attendanceConflictConfirmation;
    }

    void setAttendanceConflictConfirmation(Function<String, Boolean> confirmation) {
        attendanceConflictConfirmation = Objects.requireNonNull(confirmation, "confirmation");
    }

    public void setLogoutAction(Runnable action) {
        logoutAction = Objects.requireNonNull(action, "action");
    }

    public void setAccountAction(Runnable action) {
        accountAction = Objects.requireNonNull(action, "action");
    }


    public void activate() {
        if (disposed || workspace == null || busy) {
            return;
        }
        switch (currentView) {
            case BROWSE -> loadBrowse(view.selectedCategoryId());
            case MINE -> loadMine(false);
            case NOTIFICATIONS -> loadNotifications();
        }
    }


    public void dispose() {
        disposed = true;
        searchDelay.stop();
        requestVersion++;
    }

    public boolean isBusy() {
        return busy;
    }

    @FXML
    private void initialize() {
        profileMenu.setText(user.displayName());
        profileMenu.setAccessibleText("Profilo di " + user.displayName());
        it.uninafoodlab.presentation.common.ProfileMenus.configure(profileMenu, user, this::openAccount, this::logout);
        if (attendanceConflictConfirmation == null) {
            attendanceConflictConfirmation = message ->
                    ParticipantAttendanceConfirmation.show(rootScroll, message);
        }
        view = new ParticipantWorkspaceView(new ParticipantWorkspaceView.Controls(
                rootScroll, workspace, progressIndicator, accountButton, logoutButton,
                browseTab, mineTab, notificationsTab, feedbackLabel, retryButton,
                browsePanel, browseSummary, courseSearch, categoryFilter, clearFiltersButton,
                courseCount, courseList, courseEmptyState, courseDetail, courseCategory,
                courseTitle, courseMeta, enrollButton, withdrawButton, courseDescription,
                sessionList, menuList, minePanel, enrollmentList, attendanceEmptyState,
                attendanceDetail, attendanceCourseTitle, attendanceCourseMeta, attendanceWarning,
                attendanceList, notificationsPanel, notificationList, unreadCount,
                notificationEmptyState, notificationDetail, notificationTitle, notificationMeta,
                notificationMessage, markReadButton, openNotificationCourseButton), clock);
        view.configure(new ParticipantWorkspaceView.Callbacks(
                this::selectCourse,
                this::selectEnrollment,
                this::selectNotification,
                this::courseSearchChanged,
                this::categoryChanged,
                this::changeAttendance,
                () -> busy));
        async = new ParticipantWorkspaceAsync(new ParticipantWorkspaceAsync.Hooks(
                this::nextRequestVersion,
                this::isStale,
                this::isCurrent,
                this::setBusy,
                this::setFeedback,
                visible -> view.setRetryVisible(visible),
                view::hideFeedbackIfLoading));
        view.setRetryAction(this::retry);
        searchDelay.setOnFinished(event -> {
            if (!busy) loadBrowse(view.selectedCategoryId());
        });
        openNotificationCourseButton.setOnAction(event -> navigateNotification(selectedNotification));
        cards = ParticipantCardLayout.install(workspace, courseList, enrollmentList, notificationList,
                browseTab, mineTab, notificationsTab, () -> courses, () -> enrollments,
                () -> ownCourseSessions, clock, this::selectCourse, participantLogo, progressIndicator,
                () -> loadBrowse(view.selectedCategoryId()), () -> loadMine(false));
        showBrowse();
    }

    @FXML
    private void showBrowse() {
        view.clearSessionFeedback();
        currentView = ParticipantWorkspaceView.Section.BROWSE;
        view.showSection(currentView);
        if (!busy) {
            loadBrowse(view.selectedCategoryId());
        }
    }

    @FXML
    private void showMine() {
        view.clearSessionFeedback();
        currentView = ParticipantWorkspaceView.Section.MINE;
        view.showSection(currentView);
        if (!busy) {
            loadMine(false);
        }
    }

    @FXML
    private void showNotifications() {
        view.clearSessionFeedback();
        currentView = ParticipantWorkspaceView.Section.NOTIFICATIONS;
        view.showSection(currentView);
        if (!busy) {
            loadNotifications();
        }
    }

    @FXML
    private void clearFilters() {
        cards.resetBrowsePage();
        view.clearFilters();
        loadBrowse(Optional.empty());
    }

    @FXML
    private void retry() {
        if (!busy) async.retry();
    }

    private void categoryChanged() {
        cards.resetBrowsePage();
        if (!busy) {
            loadBrowse(view.selectedCategoryId());
        }
    }

    private void courseSearchChanged() {
        cards.resetBrowsePage();
        searchDelay.playFromStart();
    }

    private void loadBrowse(Optional<UUID> categoryId) {
        searchDelay.stop();
        var requestedCategory = Objects.requireNonNull(categoryId, "categoryId");
        var filter = new CourseFilter(requestedCategory, courseSearch.getText(), cards.browsePeriod(), LocalDateTime.now(clock));
        var page = new PageRequest(cards.browsePage(), 12);
        async.read(
                "participant-browse",
                "Caricamento dei corsi…",
                () -> data.browse(filter, page),
                snapshot -> {
                    invalidateAttendance();
                    categories = snapshot.categories();
                    courses = snapshot.courses();
                    enrollments = snapshot.enrollments();
                    view.renderCategories(categories, requestedCategory);
                    cards.updateBrowsePage(snapshot.page(), snapshot.total());
                    renderCourses();
                    courseCount.setText(Integer.toString(snapshot.total()));
                    view.setBrowseSummary(ParticipantWorkspaceRules.summaryForCourses(snapshot.total()));
                },
                "Non è stato possibile caricare il catalogo. Riprova.",
                () -> {});
    }

    private void loadMine(boolean refreshCourse) {
        var filter = new CourseFilter(Optional.empty(), "", cards.minePeriod(), LocalDateTime.now(clock));
        var page = new PageRequest(cards.minePage(), 12);
        var selectedId = Optional.ofNullable(selectedCourse).map(PublishedCourseSummary::id);
        async.read(
                "participant-enrollments",
                "Caricamento delle tue iscrizioni…",
                () -> data.mine(filter, page, selectedId),
                snapshot -> {
                    invalidateAttendance();
                    enrollments = snapshot.enrollments();
                    ownCourseSessions = snapshot.sessions();
                    courses = snapshot.courses();
                    cards.updateMinePage(snapshot.page(), snapshot.total());
                    renderEnrollments(snapshot.selected());
                    if (refreshCourse && selectedCourse != null) {
                        var courseId = selectedCourse.id();
                        selectCourse(courses.stream().filter(course -> course.id().equals(courseId)).findFirst().orElse(null));
                    }
                },
                "Non è stato possibile caricare le tue iscrizioni. Riprova.",
                () -> {});
    }

    private void loadNotifications() {
        async.read(
                "participant-notifications",
                "Caricamento delle comunicazioni…",
                data::notifications,
                loaded -> {
                    notifications = loaded;
                    renderNotifications();
                },
                "Non è stato possibile caricare le comunicazioni. Riprova.",
                () -> {});
    }

    private void renderCourses() {
        var selectedId = Optional.ofNullable(selectedCourse).map(PublishedCourseSummary::id);
        var selected = view.renderCourses(courses, "", selectedId);
        selectCourse(selected);
    }

    private void renderEnrollments(Optional<EnrollmentSummary> retainedSelection) {
        var selectedId = Optional.ofNullable(selectedEnrollment).map(EnrollmentSummary::courseId);
        var selected = view.renderEnrollments(enrollments, selectedId);
        selectEnrollment(retainedSelection.orElse(selected));
    }

    private void renderNotifications() {
        var selectedId = Optional.ofNullable(selectedNotification).map(ParticipantNotification::id);
        var selected = view.renderNotifications(notifications, selectedId);
        selectNotification(selected);
    }

    private void selectCourse(PublishedCourseSummary course) {
        if (writing) return;
        view.clearSessionFeedbackIfCourseChanged(selectedCourse, course);
        selectedCourse = course;
        if (course == null) {
            async.invalidateRead();
            view.showNoCourse();
            view.resetInitialViewport();
            return;
        }
        view.showCourse(course,
                ParticipantWorkspaceRules.enrollmentContext(enrollments, selectedEnrollment, course), busy);
        var selectedId = course.id();
        async.read(
                "participant-course-detail",
                "Caricamento del programma…",
                () -> data.course(selectedId),
                snapshot -> {
                    if (selectedCourse != null && selectedId.equals(selectedCourse.id())) {
                        view.renderCourseDetail(snapshot);
                        view.resetInitialViewport();


                        var enrolled = ParticipantWorkspaceRules.enrollmentContext(
                                enrollments, selectedEnrollment, course).stream()
                                .filter(enrollment -> enrollment.courseId().equals(selectedId)).findFirst().orElse(null);
                        selectEnrollment(enrolled);
                    }
                },
                "Non è stato possibile caricare il programma. Riprova.",
                view::resetInitialViewport);
    }

    private void selectEnrollment(EnrollmentSummary enrollment) {
        if (writing) return;
        if (ParticipantWorkspaceRules.enrollmentChanged(selectedEnrollment, enrollment)) {
            view.clearSessionFeedback();
            async.contextChanged();
        }
        selectedEnrollment = enrollment;
        if (enrollment == null) {
            view.showNoEnrollment();
            return;
        }
        view.showEnrollment(enrollment);
        view.setAttendanceWarning("");
        var selectedId = enrollment.courseId();
        if (enrollment.status() != EnrollmentStatus.ACTIVE) {
            view.showArchivedAttendance();
            return;
        }
        var cached = ownPracticalSessions.get(selectedId);
        if (cached != null) {
            view.renderAttendance(cached);
            view.setAttendanceWarning(ParticipantWorkspaceAttendance.warning(
                    selectedId, cached, commitments));
            return;
        }
        async.read(
                "participant-attendance",
                "Caricamento delle sessioni pratiche…",
                () -> data.attendance(selectedId),
                loaded -> {
                    if (selectedEnrollment != null && selectedId.equals(selectedEnrollment.courseId())) {
                        var updated = new HashMap<>(ownPracticalSessions);
                        updated.put(selectedId, loaded.sessions());
                        ownPracticalSessions = Map.copyOf(updated);
                        commitments = loaded.commitments();
                        view.renderAttendance(loaded.sessions());
                        view.setAttendanceWarning(ParticipantWorkspaceAttendance.warning(
                                selectedId, loaded.sessions(), commitments));
                    }
                },
                "Non è stato possibile caricare le adesioni. Riprova.",
                () -> {});
    }

    private void selectNotification(ParticipantNotification notification) {
        selectedNotification = notification;
        if (notification == null) {
            view.showNoNotification();
            return;
        }
        view.showNotification(notification, busy, notification.courseId().isPresent());
    }

    private void navigateNotification(ParticipantNotification notification) {
        var filter = new CourseFilter(Optional.empty(), "", CoursePeriod.ALL, LocalDateTime.now(clock));
        ParticipantNotificationNavigation.open(notification, busy, data, filter,
                (task, result) -> async.read("participant-notification-course", "Caricamento del corso collegato…",
                        task, result, "Non è stato possibile caricare il corso collegato. Riprova.", () -> {}),
                this::showNotificationCourse,
                () -> setFeedback("Il corso collegato non è disponibile nel catalogo.", ParticipantWorkspaceView.FeedbackType.INFO));
    }

    private void showNotificationCourse(ParticipantWorkspaceData.NotificationCourseSnapshot target) {
        invalidateAttendance();
        cards.resetBrowsePage();
        cards.updateBrowsePage(target.browse().page(), target.browse().total());
        courses = target.browse().courses();
        categories = target.browse().categories();
        enrollments = target.browse().enrollments();
        selectedCourse = target.course();
        currentView = ParticipantWorkspaceView.Section.BROWSE;
        view.clearFilters();
        view.renderCategories(categories, Optional.empty());
        view.renderCourses(courses, "", Optional.of(selectedCourse.id()));
        courseCount.setText(Integer.toString(target.browse().total()));
        view.showCourse(selectedCourse, enrollments, false);
        view.renderCourseDetail(target.detail());
        view.showSection(currentView);
        cards.showCourseRoute(selectedCourse);
        selectEnrollment(enrollments.stream().filter(enrollment -> enrollment.courseId().equals(selectedCourse.id()))
                .findFirst().orElse(null));
    }

    @FXML
    private void enroll() {
        if (selectedCourse == null || busy
                || ParticipantWorkspaceRules.isEnrolled(ParticipantWorkspaceRules.enrollmentContext(
                        enrollments, selectedEnrollment, selectedCourse), selectedCourse.id())) {
            return;
        }
        var courseId = selectedCourse.id();
        runWrite(
                "participant-enroll",
                () -> {
                    data.enroll(courseId);
                    return null;
                },
                () -> {
                    invalidateAttendance();
                    async.refreshAfterWrite("Iscrizione registrata. Ora puoi rispondere alle sessioni pratiche.",
                            () -> {
                                if (currentView == ParticipantWorkspaceView.Section.MINE) loadMine(true);
                                else loadBrowse(view.selectedCategoryId());
                            });
                },
                "Non è stato possibile completare l'iscrizione. Aggiorna l'elenco e riprova.");
    }

    @FXML
    private void withdraw() {
        if (selectedCourse == null || busy
                || !ParticipantWorkspaceRules.isEnrolled(ParticipantWorkspaceRules.enrollmentContext(
                        enrollments, selectedEnrollment, selectedCourse), selectedCourse.id())) {
            return;
        }
        var courseId = selectedCourse.id();
        if (!ParticipantWithdrawalConfirmation.show(rootScroll, selectedCourse.title().value())) {
            return;
        }
        runWrite(
                "participant-withdraw",
                () -> {
                    data.withdraw(courseId);
                    return null;
                },
                () -> {
                    invalidateAttendance();
                    async.refreshAfterWrite("Ritiro registrato. Puoi iscriverti di nuovo se il corso è ancora disponibile.",
                            () -> {
                                if (currentView == ParticipantWorkspaceView.Section.MINE) loadMine(true);
                                else loadBrowse(view.selectedCategoryId());
                            });
                },
                "Non è stato possibile ritirarti dal corso. Aggiorna l'elenco e riprova.");
    }

    private void changeAttendance(PracticalAttendanceSummary session, AttendanceStatus status) {
        var now = LocalDateTime.now(clock);
        var allowed = ParticipantWorkspaceRules.canChangeAttendance(session, status, now);
        if (busy || !allowed) {
            return;
        }
        view.beginSessionFeedback(session.sessionId());
        var conflict = status == AttendanceStatus.CONFIRMED
                ? ParticipantWorkspaceAttendance.conflict(
                        session, selectedEnrollment == null ? null : selectedEnrollment.courseId(), commitments)
                : Optional.<String>empty();
        if (conflict.isPresent()) {
            var message = conflict.orElseThrow();
            view.setAttendanceWarning("Attenzione: questa sessione si sovrappone a " + message
                    + ". Controlla gli orari prima di rispondere.");
            var prompt = ParticipantWorkspaceRules.conflictPrompt(session, message);
            var accepted = attendanceConflictConfirmation.apply(prompt);
            if (!ParticipantWorkspaceRules.canPersistAttendanceChange(status, conflict,
                    Boolean.TRUE.equals(accepted))) {
                setFeedback("Presenza non registrata: conferma annullata.",
                        ParticipantWorkspaceView.FeedbackType.INFO);
                return;
            }
        }
        runWrite(
                "participant-attendance",
                () -> {
                    if (status == AttendanceStatus.CONFIRMED) {
                        data.confirm(session.sessionId());
                    } else {
                        data.decline(session.sessionId());
                    }
                    return null;
                },
                () -> {
                    invalidateAttendance();
                    async.refreshAfterWrite(status == AttendanceStatus.CONFIRMED
                            ? "Presenza confermata." : "Rinuncia registrata.",
                            () -> selectEnrollment(selectedEnrollment));
                },
                "Non è stato possibile aggiornare l'adesione. Controlla scadenza e posti disponibili.");
    }

    private void invalidateAttendance() {
        ownPracticalSessions = Map.of();
        commitments = List.of();
    }

    @FXML
    private void markRead() {
        if (selectedNotification == null || selectedNotification.readAt().isPresent() || busy) {
            return;
        }
        var notification = selectedNotification;
        runWrite(
                "participant-mark-notification-read",
                () -> {
                    data.markRead(notification.id());
                    return null;
                },
                () -> {
                    var read = new ParticipantNotification(notification.id(), notification.title(),
                            notification.message(), notification.reason(), notification.courseTitle(),
                            notification.createdAt(), Optional.of(java.time.Instant.now(clock)),
                            notification.courseId());
                    notifications = notifications.stream()
                            .map(item -> item.id().equals(read.id()) ? read : item).toList();
                    selectedNotification = read;
                    renderNotifications();
                    setFeedback("Comunicazione segnata come letta.",
                            ParticipantWorkspaceView.FeedbackType.SUCCESS);
                },
                "Non è stato possibile aggiornare la comunicazione. Riprova.");
    }

    @FXML
    private void openAccount() {
        ParticipantWorkspaceSessionActions.invoke(accountAction,
                () -> setFeedback("Il profilo è gestito dall'area di accesso.",
                        ParticipantWorkspaceView.FeedbackType.INFO));
    }

    @FXML
    private void logout() {
        ParticipantWorkspaceSessionActions.invoke(logoutAction,
                () -> setFeedback("La sessione può essere chiusa dal menu principale.",
                        ParticipantWorkspaceView.FeedbackType.INFO));
    }

    private <T> void runWrite(
            String operationName,
            Callable<T> operation,
            Runnable onSuccess,
            String failureMessage) {
        if (writing) return;
        writing = true;
        view.prepareOperationFeedback(operationName);
        async.write(operationName, operation, onSuccess, failureMessage);
    }

    private long nextRequestVersion() {
        return ++requestVersion;
    }

    private boolean isCurrent(long version) {
        return version == requestVersion;
    }

    private boolean isStale(long version) {
        return disposed || version != requestVersion;
    }

    private void setBusy(boolean value) {
        if (!value) writing = false;
        busy = value;
        view.setBusy(value, writing, selectedCourse, ParticipantWorkspaceRules.enrollmentContext(
                enrollments, selectedEnrollment, selectedCourse), selectedNotification);
    }

    private void setFeedback(String message, ParticipantWorkspaceView.FeedbackType type) {
        view.setFeedback(message, type);
    }
}
