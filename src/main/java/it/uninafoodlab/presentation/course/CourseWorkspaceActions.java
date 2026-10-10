package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.course.CourseSummary;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

interface CourseWorkspaceActions {
    <T> void runAsync(String operationName, Callable<T> operation, Consumer<T> onSuccess, String failureMessage);
    boolean isBusy();
    void setBusy(boolean busy);
    void showFeedback(String message, boolean error);
    void setCourseFormVisible(boolean visible);
    void loadCourses(UUID courseId);
    void loadSessions(CourseSummary course);
    void refreshCategories(CategorySummary created);
}
