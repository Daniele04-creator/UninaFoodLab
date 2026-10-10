package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.schedule.ScheduleConflictException;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import it.uninafoodlab.presentation.common.UserErrorMessages;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
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

final class CourseWorkspaceCoordinator implements CourseWorkspaceActions {
    private final Fields fields;
    private final Runnable refreshActions;
    private final Runnable resumeNavigation;
    private final Runnable catalogFailure;
    private final Consumer<Boolean> dialogBusy;
    private final java.util.function.Predicate<String> dialogError;
    private CourseWorkspaceData workspaceData;
    private boolean busy;
    private String currentOperation;
    private Runnable readRetry;

    CourseWorkspaceCoordinator(Fields fields, Runnable refreshActions, Runnable resumeNavigation,
            Runnable catalogFailure, Consumer<Boolean> dialogBusy,
            java.util.function.Predicate<String> dialogError) {
        this.fields = fields;
        this.refreshActions = refreshActions;
        this.resumeNavigation = resumeNavigation;
        this.catalogFailure = catalogFailure;
        this.dialogBusy = dialogBusy;
        this.dialogError = dialogError;
    }

    void setWorkspaceData(CourseWorkspaceData workspaceData) { this.workspaceData = Objects.requireNonNull(workspaceData); }
    public boolean isBusy() { return busy; }
    void whenIdle(Runnable action) { if (!busy) action.run(); }
    public void loadCourses(UUID courseId) { workspaceData.loadCourses(courseId); }
    public void loadSessions(CourseSummary course) { workspaceData.loadSessions(course); }
    public void refreshCategories(CategorySummary created) { workspaceData.refreshCategories(created); }

    record Fields(Button createButton,
            Button createCategoryButton,
            ComboBox<CourseCategoryFilter> categoryFilter,
            ToggleButton activeCoursesTab,
            ToggleButton archiveCoursesTab,
            ToggleButton allCoursesTab,
            ListView<CourseSummary> courseList,
            TextField courseSearchField,
            ComboBox<CourseSessionSummary> recipeSessionField,
            ProgressIndicator progressIndicator,
            ScrollPane createCourseView,
            Label createFeedbackLabel,
            Label feedbackLabel,
            VBox courseCatalogView,
            Button openCreateCourseButton,
            TextField titleField,
            VBox createCoursePanel,
            Button cancelCreateCourseButton,
            Button createCourseReturnButton,
            ProgressIndicator createProgressIndicator,
            Label createProgressLabel,
            Button courseRetryButton) {}

    public <T> void runAsync(
            String operationName,
            Callable<T> operation,
            Consumer<T> onSuccess,
            String failureMessage) {
        if (busy) {
            return;
        }
        currentOperation = operationName;
        clearReadRetry();
        setBusy(true);
        FxAsyncExecutor.run(
                "uninafoodlab-" + operationName,
                operation,
                value -> {
                    setBusy(false);
                    clearErrorFeedback();
                    onSuccess.accept(value);
                    resumeNavigation.run();
                },
                failure -> {
                    setBusy(false);
                    if (operationName.equals("catalog-refresh") || operationName.equals("course-workspace-load")) {
                        catalogFailure.run();
                    }
                    readRetry = switch (operationName) {
                        case "course-workspace-load" -> workspaceData::loadInitialData;
                        case "course-list-load" -> workspaceData::retryCatalogRead;
                        case "course-session-load" -> workspaceData::refreshSelectedCourse;
                        case "catalog-refresh" -> workspaceData::refreshCatalog;
                        default -> null;
                    };
                    fields.courseRetryButton().setManaged(readRetry != null);
                    fields.courseRetryButton().setVisible(readRetry != null);
                    showFeedback(
                            failure instanceof ScheduleConflictException
                                    ? UserErrorMessages.validation(failure, failureMessage)
                                    : failureMessage,
                            true);
                    if (operationName.equals("course-create") && fields.createCourseView().isVisible()) {
                        fields.createButton().requestFocus();
                    }
                    workspaceData.resumeSearch();
                });
    }

    void retryRead() {
        if (!busy && readRetry != null) readRetry.run();
    }

    private void clearReadRetry() {
        readRetry = null;
        fields.courseRetryButton().setManaged(false);
        fields.courseRetryButton().setVisible(false);
    }

    private void clearErrorFeedback() {
        for (var label : java.util.List.of(fields.feedbackLabel(), fields.createFeedbackLabel())) {
            if (label.getStyleClass().contains("course-feedback-error")) {
                label.setManaged(false);
                label.setVisible(false);
            }
        }
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        dialogBusy.accept(busy);
        fields.createButton().setDisable(busy);
        fields.createCategoryButton().setDisable(busy);
        fields.categoryFilter().setDisable(busy);
        fields.activeCoursesTab().setDisable(busy);
        fields.archiveCoursesTab().setDisable(busy);
        fields.allCoursesTab().setDisable(busy);
        fields.courseList().setDisable(busy);

        fields.recipeSessionField().setDisable(busy);
        fields.openCreateCourseButton().setDisable(busy);
        fields.createCoursePanel().setDisable(busy);
        fields.cancelCreateCourseButton().setDisable(busy);
        fields.createCourseReturnButton().setDisable(busy);
        fields.courseRetryButton().setDisable(busy);
        fields.progressIndicator().setManaged(busy);
        fields.progressIndicator().setVisible(busy);
        boolean creating = busy && "course-create".equals(currentOperation);
        fields.createProgressIndicator().setManaged(creating);
        fields.createProgressIndicator().setVisible(creating);
        fields.createProgressLabel().setManaged(creating);
        fields.createProgressLabel().setVisible(creating);

        refreshActions.run();
    }

    public void showFeedback(String message, boolean error) {
        if (error && dialogError.test(message)) return;
        var target = fields.createCourseView().isVisible() ? fields.createFeedbackLabel() : fields.feedbackLabel();
        target.setText(message);
        target.setManaged(true);
        target.setVisible(true);
        target.getStyleClass().removeAll(
                "course-feedback-error", "course-feedback-success");
        target.getStyleClass().add(
                error ? "course-feedback-error" : "course-feedback-success");
    }

    public void setCourseFormVisible(boolean visible) {
        if (busy && "course-create".equals(currentOperation)) return;
        fields.createCourseView().setManaged(visible);
        fields.createCourseView().setVisible(visible);
        setCourseCatalogVisible(!visible);
        fields.openCreateCourseButton().setText("Nuovo corso");
        fields.createFeedbackLabel().setManaged(false);
        fields.createFeedbackLabel().setVisible(false);
        if (visible) {
            fields.createCourseView().setVvalue(0);
            fields.titleField().requestFocus();
        } else if (fields.openCreateCourseButton().getScene() != null) {
            fields.openCreateCourseButton().requestFocus();
        }
    }

    private void setCourseCatalogVisible(boolean visible) {
        fields.courseCatalogView().setManaged(visible);
        fields.courseCatalogView().setVisible(visible);
    }

}
