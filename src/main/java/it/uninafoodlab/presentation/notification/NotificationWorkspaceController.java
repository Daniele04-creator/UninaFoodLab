package it.uninafoodlab.presentation.notification;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.notification.BrowseNotifications;
import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

public final class NotificationWorkspaceController implements it.uninafoodlab.presentation.common.WorkspaceState {

    private final AuthenticatedUser user;
    private final BrowseNotifications browseNotifications;
    private final ManageNotifications manageNotifications;
    private final BrowseCourses browseCourses;

    @FXML private VBox composeCard;
    @FXML private VBox composerActionBar;
    @FXML private FlowPane composerActions;
    @FXML private HBox historyPanel;
    @FXML private VBox notificationArchive;
    @FXML private VBox notificationDetail;
    @FXML private Button composeButton;
    @FXML private Button backButton;
    @FXML private Label workspaceTitleLabel;
    @FXML private ComboBox<CourseTarget> courseTargetField;
    @FXML private TextField titleField;
    @FXML private TextArea messageField;
    @FXML private Label titleFeedbackLabel;
    @FXML private Label messageFeedbackLabel;
    @FXML private Button sendButton;
    @FXML private ListView<NotificationView> notificationList;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label feedbackLabel;
    @FXML private Button historyRetryButton;
    @FXML private Button recipientRetryButton;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailCourseLabel;
    @FXML private Label detailDateLabel;
    @FXML private Label detailMessageLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label sentBadge;
    @FXML private Label recipientSummaryLabel;
    @FXML private Label recipientFooterLabel;
    @FXML private ComboBox<HistoryCourse> historyCourseField;
    @FXML private TextField historySearchField;

    private boolean busy;
    private boolean refreshing;
    private boolean chefDataLoaded;
    private NotificationHistoryView historyView;
    private NotificationComposerView composerView;
    private NotificationDetailNavigation detailNavigation;
    private NotificationRecipients recipients;
    private UUID requestedCourse;
    private UUID createdNotificationId;
    private Runnable failedHistoryLoad;
    private boolean updatingCourseTargets;
    private static final String COURSE_ID_PARAMETER = "courseId";
    private static final String GENERAL_HISTORY = "Comunicazioni generali";
    private Timeline notificationRefresh;
    private final NotificationRefreshLifecycle notificationRefreshLifecycle =
            new NotificationRefreshLifecycle();

    public NotificationWorkspaceController(
            AuthenticatedUser user,
            BrowseNotifications browseNotifications,
            ManageNotifications manageNotifications,
            BrowseCourses browseCourses) {
        this.user = Objects.requireNonNull(user, "user");
        this.browseNotifications = Objects.requireNonNull(browseNotifications, "browseNotifications");
        this.manageNotifications = Objects.requireNonNull(manageNotifications, "manageNotifications");
        this.browseCourses = Objects.requireNonNull(browseCourses, "browseCourses");
    }

    @FXML
    private void initialize() {
        it.uninafoodlab.presentation.common.TextAreaNavigation.install(messageField);
        composerActionBar.visibleProperty().bind(composeCard.visibleProperty());
        composerActionBar.managedProperty().bind(composeCard.managedProperty());
        composerActions.maxWidthProperty().bind(composeCard.widthProperty());
        recipients = new NotificationRecipients(user, manageNotifications, courseTargetField,
                recipientRetryButton, recipientSummaryLabel, this::updateActions);
        composerView = new NotificationComposerView(composeCard, historyPanel, backButton, composeButton,
                workspaceTitleLabel, courseTargetField, titleField, messageField, titleFeedbackLabel,
                messageFeedbackLabel, sendButton, recipientFooterLabel,
                this::showFeedback, this::refreshRecipientCount, this::updateActions);
        composeButton.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon("edit", "light", 18));
        historyPanel.getStyleClass().add("notification-chef-history");
        backButton.setGraphic(it.uninafoodlab.presentation.common.LabIcons.back(16));
        notificationArchive.setMinWidth(0);
        notificationArchive.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(notificationArchive, javafx.scene.layout.Priority.ALWAYS);
        historyPanel.minHeightProperty().bind(Bindings.when(notificationDetail.visibleProperty())
                .then(javafx.scene.layout.Region.USE_COMPUTED_SIZE).otherwise(0.0));
        composeCard.setManaged(false);
        composeCard.setVisible(false);
        courseTargetField.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseTarget target) {
                return target == null ? "" : target.label();
            }

            @Override
            public CourseTarget fromString(String text) {
                return null;
            }
        });
        courseTargetField.valueProperty().addListener((observable, previous, target) -> {
            if (!updatingCourseTargets) {
                composerView.restoreTargetDraft(target);
                refreshRecipientCount();
            }
        });
        historyView = new NotificationHistoryView(notificationList, historyCourseField, historySearchField,
                detailTitleLabel, detailCourseLabel, detailDateLabel, detailMessageLabel, detailStatusLabel,
                sentBadge, this::showFeedback, this::updateActions, ignored -> detailNavigation.open());
        detailNavigation = new NotificationDetailNavigation(notificationArchive, notificationDetail,
                workspaceTitleLabel, backButton, composeButton, historyView::restoreFocus);
        notificationRefresh = new Timeline(new KeyFrame(Duration.seconds(5), ignored -> {
            if (notificationRefreshLifecycle.isActive()) {
                refreshWorkspace();
            }
        }));
        notificationRefresh.setCycleCount(javafx.animation.Animation.INDEFINITE);
        updateActions();
    }

    @FXML
    private void toggleComposer() {
        if (detailNavigation.isOpen()) {
            detailNavigation.close();
            return;
        }
        setComposerOpen(!composeCard.isVisible());
    }

    private void setComposerOpen(boolean open) {
        if (detailNavigation.isOpen()) detailNavigation.close();
        backButton.setText("Torna alle comunicazioni");
        composerView.setOpen(open);
    }

    public void composeForCourse(UUID courseId) {
        requestedCourse = Objects.requireNonNull(courseId, COURSE_ID_PARAMETER);
        if (chefDataLoaded) {
            composerView.selectCourse(requestedCourse);
            requestedCourse = null;
        }
        setComposerOpen(true);
    }

    @FXML
    private void refreshRecipientCount() {
        recipients.refresh();
    }

    @FXML
    private void sendNotification() {
        if (busy) return;
        try {
            var target = recipients.requiredTarget();
            var draft = NotificationDraft.of(titleField.getText(), messageField.getText());
            setBusy(true);
            FxAsyncExecutor.run(
                    "notification-send",
                    () -> manageNotifications.send(user, target.courseId(), draft),
                    notificationId -> {
                        createdNotificationId = notificationId;
                        titleField.clear();
                        messageField.clear();
                        setComposerOpen(false);
                        courseTargetField.setValue(null);
                        showFeedback("Comunicazione inviata. Disponibile nell’app per i destinatari.", false);
                        setBusy(false);
                        refreshHistory();
                    },
                    failure -> fail("Non è stato possibile inviare la comunicazione."));
        } catch (IllegalArgumentException exception) {
            showFeedback(it.uninafoodlab.presentation.common.UserErrorMessages.validation(exception,
                    "Controlla oggetto e messaggio della comunicazione."), true);
        }
    }

    private void loadChefData() {
        setBusy(true);
        FxAsyncExecutor.run(
                "notification-workspace-load",
                () -> new WorkspaceSnapshot(
                        browseCourses.courses(user, Optional.empty()),
                        chefHistory()),
                snapshot -> {
                    updatingCourseTargets = true;
                    try {
                        composerView.replaceCourses(snapshot.courses(), requestedCourse);
                        requestedCourse = null;
                    } finally {
                        updatingCourseTargets = false;
                    }
                    composerView.restoreTargetDraft(courseTargetField.getValue());
                    refreshRecipientCount();
                    replaceHistory(snapshot.history());
                    chefDataLoaded = true;
                    setBusy(false);
                },
                failure -> {
                    fail("Non è stato possibile caricare le comunicazioni. La bozza è conservata.");
                    offerHistoryRetry(this::loadChefData);
                });
    }

    private void refreshHistory() {
        if (!notificationRefreshLifecycle.isActive()) {
            return;
        }
        if (refreshing) {
            notificationRefreshLifecycle.requestFollowUp();
            return;
        }
        refreshing = true;
        historyRetryButton.setVisible(false);
        historyRetryButton.setManaged(false);
        FxAsyncExecutor.run(
                "notification-history-load",
                this::chefHistory,
                history -> {
                    replaceHistory(history);
                    refreshing = false;
                    runPendingRefresh();
                },
                failure -> {
                    refreshing = false;
                    showFeedback("Non è stato possibile aggiornare le comunicazioni. Filtri e bozza sono conservati.", true);
                    offerHistoryRetry(this::refreshHistory);
                    runPendingRefresh();
                });
    }

    private void runPendingRefresh() {
        if (notificationRefreshLifecycle.consumeFollowUp()) {
            refreshHistory();
        }
    }

    private void refreshWorkspace() {
        if (busy || refreshing) {
            return;
        }
        if (!chefDataLoaded) {
            loadChefData();
        } else {
            refreshHistory();
        }
    }

    private List<NotificationView> chefHistory() {
        return browseNotifications.forChef(user).stream()
                .map(item -> new NotificationView(
                        item.id(), item.title(), item.message(),
                        item.courseTitle(), item.createdAt(),
                        item.recipientCount() + (item.recipientCount() == 1 ? " destinatario" : " destinatari"),
                        item.courseId(), item.courseStartDate()))
                .toList();
    }

    private void setBusy(boolean loading) {
        busy = loading;
        progressIndicator.setManaged(loading);
        progressIndicator.setVisible(loading);
        updateActions();
    }

    private void updateActions() {
        composerView.updateActions(busy, recipients.hasRecipients(), recipients.countLabel());
        recipientRetryButton.setDisable(busy);
        historyRetryButton.setDisable(busy || refreshing);
    }

    private void replaceHistory(List<NotificationView> history) {
        if (failedHistoryLoad != null) {
            feedbackLabel.setVisible(false);
            feedbackLabel.setManaged(false);
        }
        historyRetryButton.setVisible(false);
        historyRetryButton.setManaged(false);
        failedHistoryLoad = null;
        historyView.replaceHistory(history, createdNotificationId);
        if (history.stream().anyMatch(item -> item.id().equals(createdNotificationId))) {
            createdNotificationId = null;
        }
    }

    public void activate() {
        notificationRefreshLifecycle.activate();
        if (!notificationRefreshLifecycle.isActive()) {
            return;
        }
        chefDataLoaded = false;
        refreshWorkspace();
        if (notificationRefresh != null) {
            notificationRefresh.playFromStart();
        }
    }

    public void deactivate() {
        notificationRefreshLifecycle.deactivate();
        if (notificationRefresh != null) {
            notificationRefresh.stop();
        }
    }

    public boolean hasUnsavedChanges() {
        return composerView != null && composerView.hasUnsavedChanges();
    }

    public boolean isBusy() {
        return busy;
    }

    public void dispose() {
        notificationRefreshLifecycle.dispose();
        if (notificationRefresh != null) {
            notificationRefresh.stop();
        }
    }

    private void fail(String message) {
        setBusy(false);
        showFeedback(message, true);
    }

    private void offerHistoryRetry(Runnable retry) {
        failedHistoryLoad = retry;
        historyRetryButton.setVisible(true);
        historyRetryButton.setManaged(true);
        updateActions();
    }

    @FXML
    private void retryHistoryLoad() {
        var retry = failedHistoryLoad;
        if (retry == null || busy || refreshing) return;
        historyRetryButton.setVisible(false);
        historyRetryButton.setManaged(false);
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
        retry.run();
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
        feedbackLabel.getStyleClass().removeAll("course-feedback-error", "course-feedback-success");
        feedbackLabel.getStyleClass().add(error ? "course-feedback-error" : "course-feedback-success");
    }

    public record CourseTarget(Optional<UUID> courseId, String label) {
        public CourseTarget {
            Objects.requireNonNull(courseId, COURSE_ID_PARAMETER);
            label = Objects.requireNonNull(label, "label").strip();
        }

        static CourseTarget allCourses() {
            return new CourseTarget(Optional.empty(), "Tutti i miei corsi");
        }

        static CourseTarget course(CourseSummary course) {
            return new CourseTarget(Optional.of(course.id()), course.title().value() + ", "
                    + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(course.startDate()));
        }
    }

    private record WorkspaceSnapshot(List<CourseSummary> courses, List<NotificationView> history) {}

    public record NotificationView(
            UUID id,
            String title,
            String message,
            Optional<String> courseTitle,
            Instant createdAt,
            String status,
            Optional<UUID> courseId,
            Optional<java.time.LocalDate> courseStartDate) {
        public NotificationView(UUID id, String title, String message, Optional<String> courseTitle,
                Instant createdAt, String status) {
            this(id, title, message, courseTitle, createdAt, status, Optional.empty(), Optional.empty());
        }
        public String courseLabel() {
            return courseTitle.orElse("Tutti i corsi") + courseStartDate
                    .map(date -> ", " + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(date)).orElse("");
        }
        public HistoryCourse historyCourse() {
            return new HistoryCourse(false, courseId, courseTitle.isEmpty() ? GENERAL_HISTORY : courseLabel());
        }
    }

    public record HistoryCourse(boolean all, Optional<UUID> courseId, String label) {
        public HistoryCourse {
            Objects.requireNonNull(courseId, COURSE_ID_PARAMETER);
            Objects.requireNonNull(label, "label");
        }
        boolean matches(NotificationView item) {
            return all || courseId.equals(item.courseId());
        }
        @Override public String toString() { return label; }
        @Override public boolean equals(Object other) {
            return other instanceof HistoryCourse choice && all == choice.all && courseId.equals(choice.courseId);
        }
        @Override public int hashCode() { return Objects.hash(all, courseId); }
    }
}
