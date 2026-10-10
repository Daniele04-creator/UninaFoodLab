package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.application.notification.ParticipantNotification;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.application.Platform;
import javafx.util.StringConverter;

final class ParticipantWorkspaceView {

    private final Controls controls;
    private final Clock clock;
    private boolean suppressSelection;
    private boolean suppressFilters;
    private boolean busy;
    private boolean notificationCourseLinkAvailable;
    private boolean initialViewportResetPending = true;
    private Callbacks callbacks;
    private boolean enrolled;
    private UUID feedbackSessionId;
    private String sessionFeedback = "";
    private FeedbackType sessionFeedbackType = FeedbackType.INFO;
    private boolean sessionRetry;
    private Runnable retryAction;
    private boolean sessionFocusPending;

    ParticipantWorkspaceView(Controls controls, Clock clock) {
        this.controls = Objects.requireNonNull(controls, "controls");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void configure(Callbacks callbacks) {
        this.callbacks = callbacks;
        var fixedFeedback = controls.feedbackLabel().getParent();
        fixedFeedback.managedProperty().bind(controls.feedbackLabel().visibleProperty()
                .or(controls.retryButton().visibleProperty()));
        fixedFeedback.visibleProperty().bind(fixedFeedback.managedProperty());
        ((VBox) controls.workspace().lookup("#sessionTimeline")).disableProperty()
                .bind(controls.progressIndicator().visibleProperty());
        ((VBox) controls.workspace().lookup("#attendanceTimeline")).disableProperty()
                .bind(controls.progressIndicator().visibleProperty());
        controls.courseList().setPlaceholder(new Label("Nessun corso corrisponde ai filtri."));
        controls.enrollmentList().setPlaceholder(new Label("Non hai ancora iscrizioni."));
        controls.sessionList().setPlaceholder(new Label("Programma non disponibile."));
        controls.menuList().setPlaceholder(new Label("Il menu verrà pubblicato dallo Chef."));
        controls.attendanceList().setPlaceholder(new Label("Nessuna sessione pratica da confermare."));
        controls.notificationList().setPlaceholder(new Label("Non hai ancora comunicazioni."));
        controls.courseList().setCellFactory(ignored -> ParticipantWorkspaceCells.courseCell());
        controls.enrollmentList().setCellFactory(ignored -> ParticipantWorkspaceCells.enrollmentCell());
        controls.sessionList().setCellFactory(ignored -> ParticipantWorkspaceCells.sessionCell(
                this::setLocationError));
        controls.menuList().setCellFactory(ignored -> ParticipantWorkspaceCells.menuCell(this::sessionSequence));
        controls.attendanceList().setCellFactory(ignored -> ParticipantWorkspaceCells.attendanceCell(
                callbacks.attendanceChanged(), callbacks.busy(), clock));
        controls.notificationList().setCellFactory(ignored -> ParticipantWorkspaceCells.notificationCell());
        controls.courseList().getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, value) -> {
                    if (!suppressSelection) callbacks.courseSelected().accept(value);
                });
        controls.enrollmentList().getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, value) -> {
                    if (!suppressSelection) callbacks.enrollmentSelected().accept(value);
                });
        controls.notificationList().getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, value) -> {
                    if (!suppressSelection) callbacks.notificationSelected().accept(value);
                });
        controls.courseSearch().textProperty().addListener(
                (observable, previous, value) -> {
                    if (!suppressFilters) {
                        callbacks.courseSearchChanged().run();
                    }
                });
        controls.categoryFilter().valueProperty().addListener(
                (observable, previous, value) -> {
                    if (!suppressFilters) {
                        callbacks.categoryChanged().run();
                    }
                });
        controls.categoryFilter().setConverter(new StringConverter<>() {
            @Override
            public String toString(CategorySummary value) {
                return value == null ? "" : value.name();
            }

            @Override
            public CategorySummary fromString(String value) {
                return controls.categoryFilter().getItems().stream()
                        .filter(category -> category.name().equals(value)).findFirst().orElse(null);
            }
        });
    }

    void showSection(Section section) {
        controls.browseTab().setSelected(section == Section.BROWSE);
        controls.mineTab().setSelected(section == Section.MINE);
        controls.notificationsTab().setSelected(section == Section.NOTIFICATIONS);
        show(controls.browsePanel(), section == Section.BROWSE);
        show(controls.minePanel(), section == Section.MINE);
        show(controls.notificationsPanel(), section == Section.NOTIFICATIONS);
    }

    void renderCategories(List<CategorySummary> categories, Optional<UUID> selectedCategory) {
        suppressFilters = true;
        try {
            controls.categoryFilter().getItems().setAll(categories.stream()
                    .sorted(Comparator.comparing(CategorySummary::name, String.CASE_INSENSITIVE_ORDER))
                    .toList());
            controls.categoryFilter().setValue(categories.stream()
                    .filter(category -> selectedCategory.filter(id -> id.equals(category.id())).isPresent())
                    .findFirst().orElse(null));
        } finally {
            suppressFilters = false;
        }
    }

    void clearFilters() {
        suppressFilters = true;
        try {
            controls.courseSearch().clear();
            controls.categoryFilter().setValue(null);
        } finally {
            suppressFilters = false;
        }
    }

    void setBrowseSummary(String summary) {
        controls.browseSummary().setText(summary);
    }

    void setRetryVisible(boolean visible) {
        sessionRetry = feedbackSessionId != null && visible;
        show(controls.retryButton(), feedbackSessionId == null && visible);
        refreshSessionFeedback();
    }

    void setRetryAction(Runnable action) { retryAction = action; }

    void beginSessionFeedback(UUID sessionId) {
        clearSessionFeedback();
        feedbackSessionId = sessionId;
        var scene = controls.workspace().getScene();
        for (Node node = scene == null ? null : scene.getFocusOwner(); node != null; node = node.getParent()) {
            if (sessionId.equals(node.getUserData())) { sessionFocusPending = true; break; }
        }
    }

    void clearSessionFeedback() {
        feedbackSessionId = null;
        sessionFeedback = "";
        sessionRetry = false;
        sessionFocusPending = false;
        refreshSessionFeedback();
    }

    void clearSessionFeedbackIfCourseChanged(PublishedCourseSummary previous, PublishedCourseSummary current) {
        if (previous == null || current == null || !previous.id().equals(current.id())) clearSessionFeedback();
    }

    void prepareOperationFeedback(String operationName) {
        if (!operationName.equals("participant-attendance")) clearSessionFeedback();
    }

    PublishedCourseSummary renderCourses(
            List<PublishedCourseSummary> courses, String query, Optional<UUID> selectedId) {
        var filtered = ParticipantWorkspaceRules.filterCourses(courses, query);
        var match = filtered.stream()
                .filter(course -> selectedId.filter(id -> id.equals(course.id())).isPresent())
                .findFirst().orElse(filtered.isEmpty() ? null : filtered.getFirst());
        suppressSelection = true;
        try {
            controls.courseList().getItems().setAll(filtered);
            controls.courseList().getSelectionModel().select(match);
        } finally {
            suppressSelection = false;
        }
        controls.courseCount().setText(Integer.toString(filtered.size()));
        return match;
    }

    EnrollmentSummary renderEnrollments(
            List<EnrollmentSummary> enrollments, Optional<UUID> selectedId) {
        var sorted = enrollments.stream()
                .sorted(Comparator.comparing(EnrollmentSummary::startDate).reversed()).toList();
        var match = sorted.stream()
                .filter(item -> selectedId.filter(id -> id.equals(item.courseId())).isPresent())
                .findFirst().orElse(sorted.isEmpty() ? null : sorted.getFirst());
        suppressSelection = true;
        try {
            controls.enrollmentList().getItems().setAll(sorted);
            controls.enrollmentList().getSelectionModel().select(match);
        } finally {
            suppressSelection = false;
        }
        return match;
    }

    ParticipantNotification renderNotifications(
            List<ParticipantNotification> notifications, Optional<UUID> selectedId) {
        var match = notifications.stream()
                .filter(item -> selectedId.filter(id -> id.equals(item.id())).isPresent())
                .findFirst().orElse(notifications.isEmpty() ? null : notifications.getFirst());
        suppressSelection = true;
        try {
            controls.notificationList().getItems().setAll(notifications);
            controls.notificationList().getSelectionModel().select(match);
        } finally {
            suppressSelection = false;
        }
        long unread = notifications.stream().filter(item -> item.readAt().isEmpty()).count();
        controls.unreadCount().setText(unread == 0 ? "Tutto letto" : unread + " da leggere");
        return match;
    }

    Optional<UUID> selectedCategoryId() {
        return Optional.ofNullable(controls.categoryFilter().getValue()).map(CategorySummary::id);
    }

    void showCourse(PublishedCourseSummary course, List<EnrollmentSummary> enrollments, boolean busy) {
        enrolled = ParticipantWorkspaceRules.isEnrolled(enrollments, course.id());
        controls.attendanceList().getItems().clear();
        setAttendanceWarning("");
        clearCourseProgram();
        show(controls.courseEmptyState(), false);
        show(controls.courseDetail(), true);
        controls.courseCategory().setText(course.categoryName());
        controls.courseTitle().setText(course.title().value());
        controls.courseDescription().setText(course.description());
        controls.courseMeta().setText(ParticipantWorkspaceRules.courseMeta(course));
        refreshEnrollmentActions(course, enrollments, busy);
    }

    void showNoCourse() {
        clearCourseProgram();
        show(controls.courseEmptyState(), true);
        show(controls.courseDetail(), false);
    }


    void resetInitialViewport() {
        if (!initialViewportResetPending) {
            return;
        }
        initialViewportResetPending = false;
        Platform.runLater(() -> controls.rootScroll().setVvalue(0.0));
    }

    void renderCourseDetail(ParticipantWorkspaceData.CourseDetailSnapshot snapshot) {
        controls.sessionList().getItems().setAll(snapshot.sessions());
        controls.menuList().getItems().setAll(snapshot.menu());
        renderTimeline();
    }

    private void clearCourseProgram() {
        controls.sessionList().getSelectionModel().clearSelection();
        controls.menuList().getSelectionModel().clearSelection();
        controls.sessionList().getItems().clear();
        controls.menuList().getItems().clear();
        ((VBox) controls.workspace().lookup("#sessionTimeline")).getChildren().clear();
    }

    void showEnrollment(EnrollmentSummary enrollment) {
        controls.attendanceList().getSelectionModel().clearSelection();
        controls.attendanceList().getItems().clear();
        renderTimeline();
        show(controls.attendanceEmptyState(), false);
        show(controls.attendanceDetail(), !controls.courseDetail().isVisible());
        controls.attendanceCourseTitle().setText(enrollment.title().value());
        controls.attendanceCourseMeta().setText(enrollment.categoryName() + "\nChef "
                + enrollment.chefName() + "\n"
                + ParticipantWorkspaceRules.enrollmentStatus(enrollment.status()));
    }

    void showNoEnrollment() {
        controls.attendanceList().getSelectionModel().clearSelection();
        controls.attendanceList().getItems().clear();
        renderTimeline();
        show(controls.attendanceEmptyState(), true);
        show(controls.attendanceDetail(), false);
    }

    void renderAttendance(List<PracticalAttendanceSummary> sessions) {
        controls.attendanceList().getItems().setAll(sessions);
        renderTimeline();
    }

    void showArchivedAttendance() {
        controls.attendanceList().getItems().clear();
        enrolled = false;
        renderTimeline();
        setAttendanceWarning("Ti sei ritirato da questo corso: le nuove adesioni non sono disponibili.");
    }

    void setAttendanceWarning(String message) {
        controls.attendanceWarning().setText(message == null ? "" : message);
        show(controls.attendanceWarning(), message != null && !message.isBlank());
    }

    void showNotification(
            ParticipantNotification notification, boolean busy, boolean linkedCourseVisible) {
        show(controls.notificationEmptyState(), false);
        show(controls.notificationDetail(), true);
        controls.notificationTitle().setText(notification.title());
        controls.notificationMeta().setText(notification.courseTitle().orElse("Tutti i corsi") + "\n"
                + ParticipantWorkspaceRules.DATE_TIME.format(notification.createdAt()
                .atZone(ParticipantWorkspaceRules.ROME)) + "\n"
                + (notification.readAt().isPresent() ? "Letta" : "Da leggere"));
        controls.notificationMessage().setText(notification.message());
        controls.markReadButton().setDisable(busy || notification.readAt().isPresent());
        notificationCourseLinkAvailable = linkedCourseVisible;
        show(controls.openNotificationCourseButton(), linkedCourseVisible);
        controls.openNotificationCourseButton().setDisable(busy || !linkedCourseVisible);
    }

    void showNotification(ParticipantNotification notification, boolean busy) {
        showNotification(notification, busy, false);
    }

    void showNoNotification() {
        show(controls.notificationEmptyState(), true);
        show(controls.notificationDetail(), false);
        notificationCourseLinkAvailable = false;
        show(controls.openNotificationCourseButton(), false);
    }

    void setNotificationCourseLinkVisible(boolean visible) {
        notificationCourseLinkAvailable = visible;
        show(controls.openNotificationCourseButton(), visible);
        controls.openNotificationCourseButton().setDisable(busy || !visible);
    }

    void setBusy(
            boolean busy, boolean writing, PublishedCourseSummary selectedCourse,
            List<EnrollmentSummary> enrollments, ParticipantNotification notification) {
        this.busy = busy;
        controls.courseList().setDisable(writing);
        controls.enrollmentList().setDisable(writing);
        controls.progressIndicator().setVisible(busy);
        controls.progressIndicator().setManaged(busy);
        controls.browseTab().setDisable(busy);
        controls.mineTab().setDisable(busy);
        controls.notificationsTab().setDisable(busy);
        controls.courseSearch().setDisable(busy);
        controls.categoryFilter().setDisable(busy);
        controls.clearFiltersButton().setDisable(busy);
        controls.accountButton().setDisable(busy);
        controls.logoutButton().setDisable(busy);
        refreshEnrollmentActions(selectedCourse, enrollments, busy);
        controls.markReadButton().setDisable(busy || notification == null
                || notification.readAt().isPresent());
        controls.openNotificationCourseButton().setDisable(busy || !notificationCourseLinkAvailable);
    }

    void setFeedback(String message, FeedbackType type) {
        if (feedbackSessionId != null) {
            sessionFeedback = message == null ? "" : message;
            sessionFeedbackType = type;
            show(controls.feedbackLabel(), false);
            controls.feedbackLabel().setText("");
            refreshSessionFeedback();
            if (!busy && type != FeedbackType.INFO) restoreSessionFocus();
            return;
        }
        setGeneralFeedback(message, type);
    }

    private void setGeneralFeedback(String message, FeedbackType type) {
        var label = controls.feedbackLabel();
        label.setText(message == null ? "" : message);
        label.getStyleClass().removeAll("course-feedback-info", "course-feedback-success",
                "course-feedback-error");
        label.getStyleClass().add(type.cssClass());
        show(label, message != null && !message.isBlank());
    }

    void hideFeedbackIfLoading() {
        if ((feedbackSessionId == null ? controls.feedbackLabel().getText() : sessionFeedback).endsWith("…")) {
            setFeedback("", FeedbackType.INFO);
        }
    }

    String sessionSequence(UUID sessionId) {
        return controls.sessionList().getItems().stream()
                .filter(session -> session.id().equals(sessionId))
                .map(session -> Integer.toString(session.sequenceNumber()))
                .findFirst().orElse("?");
    }

    private void renderTimeline() {
        if (callbacks == null) return;
        ParticipantSessionTimeline.render((VBox) controls.workspace().lookup("#sessionTimeline"),
                controls.sessionList().getItems(), controls.menuList().getItems(),
                controls.attendanceList().getItems(), enrolled, clock, callbacks.attendanceChanged(),
                this::setLocationError);
        ParticipantSessionTimeline.renderAttendanceOnly((VBox) controls.workspace().lookup("#attendanceTimeline"),
                controls.attendanceList().getItems(), clock, callbacks.attendanceChanged());
        refreshSessionFeedback();
    }

    private void refreshSessionFeedback() {
        boolean displayedWithSession = false;
        for (var row : sessionRows().toList()) displayedWithSession |= refreshRowFeedback(row);
        if (feedbackSessionId != null) {
            setGeneralFeedback(displayedWithSession ? "" : sessionFeedback, sessionFeedbackType);
            show(controls.retryButton(), sessionRetry && !displayedWithSession);
        }
    }

    private java.util.stream.Stream<VBox> sessionRows() {
        return List.of("#sessionTimeline", "#attendanceTimeline").stream()
                .map(id -> (VBox) controls.workspace().lookup(id))
                .flatMap(timeline -> timeline.getChildren().stream())
                .filter(VBox.class::isInstance).map(VBox.class::cast);
    }

    private boolean refreshRowFeedback(VBox row) {
        row.getChildren().removeIf(child -> child.getStyleClass().contains("participant-session-feedback"));
        if (!hasSessionFeedback(row)) return false;
        var feedback = new VBox(8);
        feedback.getStyleClass().add("participant-session-feedback");
        feedback.getChildren().add(sessionFeedbackLabel());
        if (sessionRetry) feedback.getChildren().add(sessionRetryButton());
        row.getChildren().add(feedback);
        return ParticipantDetailNavigation.visibleWithin(row, controls.workspace());
    }

    private boolean hasSessionFeedback(VBox row) {
        return feedbackSessionId != null && Objects.equals(feedbackSessionId, row.getUserData())
                && (!sessionFeedback.isBlank() || sessionRetry);
    }

    private Label sessionFeedbackLabel() {
        var label = new Label(sessionFeedback);
        label.setFocusTraversable(true);
        label.setWrapText(true);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        label.setAccessibleText("Esito adesione: " + sessionFeedback);
        label.getStyleClass().addAll("course-feedback", sessionFeedbackType.cssClass());
        show(label, !sessionFeedback.isBlank());
        return label;
    }

    private Button sessionRetryButton() {
        var retry = new Button("Riprova");
        retry.getStyleClass().add("button-secondary");
        retry.setAccessibleText("Riprova il caricamento delle adesioni, senza ripetere il salvataggio");
        retry.setOnAction(event -> {
            sessionFocusPending |= retry.isFocused();
            if (retryAction != null) retryAction.run();
        });
        retry.disableProperty().bind(controls.progressIndicator().visibleProperty());
        return retry;
    }

    private void setLocationError(String message) {
        clearSessionFeedback();
        show(controls.retryButton(), false);
        setGeneralFeedback(message, FeedbackType.ERROR);
    }

    private void restoreSessionFocus() {
        if (!sessionFocusPending) return;
        var expectedSession = feedbackSessionId;
        Platform.runLater(() -> focusCurrentSession(expectedSession));
    }

    private void focusCurrentSession(UUID expectedSession) {
        if (!Objects.equals(feedbackSessionId, expectedSession) || busy) return;
        sessionRows().filter(row -> Objects.equals(expectedSession, row.getUserData()))
                .filter(row -> ParticipantDetailNavigation.visibleWithin(row, controls.workspace()))
                .map(row -> row.lookup(".participant-session-feedback .label"))
                .filter(Objects::nonNull).findFirst().ifPresent(label -> {
                    label.requestFocus(); sessionFocusPending = false;
                });
    }

    private void refreshEnrollmentActions(
            PublishedCourseSummary course, List<EnrollmentSummary> enrollments, boolean busy) {
        if (course == null) {
            controls.enrollButton().setVisible(true);
            controls.enrollButton().setManaged(true);
            controls.enrollButton().setDisable(true);
            show(controls.withdrawButton(), false);
            return;
        }
        var enrollment = enrollments.stream()
                .filter(item -> item.courseId().equals(course.id())).findFirst();
        var active = enrollment.filter(item -> item.status() == EnrollmentStatus.ACTIVE).isPresent();
        var ended = course.hasEnded(java.time.LocalDateTime.now(clock));
        show(controls.enrollButton(), !active);
        show(controls.withdrawButton(), active);
        controls.enrollButton().setDisable(ended || busy);
        controls.withdrawButton().setDisable(ended || busy);
        if (ended) {
            controls.courseMeta().setText(ParticipantWorkspaceRules.courseMeta(course) + "\nCorso concluso");
        }
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    record Callbacks(
            Consumer<PublishedCourseSummary> courseSelected,
            Consumer<EnrollmentSummary> enrollmentSelected,
            Consumer<ParticipantNotification> notificationSelected,
            Runnable courseSearchChanged,
            Runnable categoryChanged,
            BiConsumer<PracticalAttendanceSummary, AttendanceStatus> attendanceChanged,
            java.util.function.BooleanSupplier busy) {
        Callbacks {
            Objects.requireNonNull(courseSelected, "courseSelected");
            Objects.requireNonNull(enrollmentSelected, "enrollmentSelected");
            Objects.requireNonNull(notificationSelected, "notificationSelected");
            Objects.requireNonNull(courseSearchChanged, "courseSearchChanged");
            Objects.requireNonNull(categoryChanged, "categoryChanged");
            Objects.requireNonNull(attendanceChanged, "attendanceChanged");
            Objects.requireNonNull(busy, "busy");
        }
    }

    record Controls(
            ScrollPane rootScroll,
            VBox workspace,
            ProgressIndicator progressIndicator,
            Button accountButton,
            Button logoutButton,
            ToggleButton browseTab,
            ToggleButton mineTab,
            ToggleButton notificationsTab,
            Label feedbackLabel,
            Button retryButton,
            VBox browsePanel,
            Label browseSummary,
            TextField courseSearch,
            ComboBox<CategorySummary> categoryFilter,
            Button clearFiltersButton,
            Label courseCount,
            ListView<PublishedCourseSummary> courseList,
            VBox courseEmptyState,
            VBox courseDetail,
            Label courseCategory,
            Label courseTitle,
            Label courseMeta,
            Button enrollButton,
            Button withdrawButton,
            Label courseDescription,
            ListView<PublishedCourseSessionSummary> sessionList,
            ListView<PublishedSessionRecipeSummary> menuList,
            VBox minePanel,
            ListView<EnrollmentSummary> enrollmentList,
            VBox attendanceEmptyState,
            VBox attendanceDetail,
            Label attendanceCourseTitle,
            Label attendanceCourseMeta,
            Label attendanceWarning,
            ListView<PracticalAttendanceSummary> attendanceList,
            VBox notificationsPanel,
            ListView<ParticipantNotification> notificationList,
            Label unreadCount,
            VBox notificationEmptyState,
            VBox notificationDetail,
            Label notificationTitle,
            Label notificationMeta,
            Label notificationMessage,
            Button markReadButton,
            Button openNotificationCourseButton) {}

    enum Section { BROWSE, MINE, NOTIFICATIONS }

    enum FeedbackType {
        INFO("course-feedback-info"), SUCCESS("course-feedback-success"), ERROR("course-feedback-error");

        private final String cssClass;

        FeedbackType(String cssClass) {
            this.cssClass = cssClass;
        }

        String cssClass() {
            return cssClass;
        }
    }
}
