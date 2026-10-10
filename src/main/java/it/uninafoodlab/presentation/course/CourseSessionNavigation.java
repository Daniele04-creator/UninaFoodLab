package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import java.util.Objects;
import java.util.UUID;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

final class CourseSessionNavigation {
    private final ListView<CourseSummary> courseList;
    private final TableView<CourseSessionSummary> sessionTable;
    private final ComboBox<CourseSessionSummary> recipeSessionField;
    private final CourseDetailPane detailPane;
    private final CourseWorkspaceActions actions;
    private boolean sessionActionsRequested;
    private UUID sessionActionsCourseId;
    private UUID sessionActionsSessionId;
    private UUID loadedSessionsCourseId;
    private CourseDetailSection requestedCourseDetailSection = CourseDetailSection.PROGRAM;

    CourseSessionNavigation(ListView<CourseSummary> courseList, TableView<CourseSessionSummary> sessionTable,
            ComboBox<CourseSessionSummary> recipeSessionField, CourseDetailPane detailPane, CourseWorkspaceActions actions) {
        this.courseList = courseList;
        this.sessionTable = sessionTable;
        this.recipeSessionField = recipeSessionField;
        this.detailPane = detailPane;
        this.actions = actions;
    }

    boolean hasRequest() { return sessionActionsRequested; }
    UUID requestedCourseId() { return sessionActionsCourseId; }
    void loadedSessions(UUID courseId) { loadedSessionsCourseId = courseId; }

    void showCourse(UUID courseId) {
        sessionActionsCourseId = Objects.requireNonNull(courseId, "courseId");
        sessionActionsSessionId = null;
        requestedCourseDetailSection = CourseDetailSection.PROGRAM;
        sessionActionsRequested = true;
        actions.setCourseFormVisible(false);
        resumeRequestedWorkspace();
    }

    void showRequestedWorkspace(
            UUID courseId, UUID sessionId, CourseDetailSection requestedSection) {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(sessionId, "sessionId");
        requestedCourseDetailSection = Objects.requireNonNull(requestedSection, "requestedSection");
        sessionActionsRequested = true;
        sessionActionsCourseId = courseId;
        sessionActionsSessionId = sessionId;
        actions.setCourseFormVisible(false);
        resumeRequestedWorkspace();
    }

    void resumeRequestedWorkspace() {
        if (!sessionActionsRequested || actions.isBusy() || courseList == null) {
            return;
        }
        var target = courseList.getItems().stream()
                .filter(course -> course.id().equals(sessionActionsCourseId))
                .findFirst();
        if (target.isPresent()) {
            var selected = courseList.getSelectionModel().getSelectedItem();
            var sameCourse = selected != null && selected.id().equals(sessionActionsCourseId);
            if (!sameCourse) {
                courseList.getSelectionModel().select(target.orElseThrow());
                return;
            }
            if (sessionActionsCourseId.equals(loadedSessionsCourseId)) {
                openRequestedWorkspace();
                clearSessionActionsRequest();
            } else {
                actions.loadSessions(target.orElseThrow());
            }
            return;
        }
        actions.loadCourses(sessionActionsCourseId);
    }

    void openCalendarWorkspace() {
        detailPane.restoreCourseDetailSection(CourseDetailSection.PROGRAM);
        if (!selectRequestedSession() && !sessionActionsRequested) {
            detailPane.selectFirstScheduledSession();
        }
    }

    void openRequestedWorkspace() {
        if (requestedCourseDetailSection == CourseDetailSection.PROGRAM) {
            openCalendarWorkspace();
            return;
        }
        selectRequestedSession();
        selectRequestedRecipeSession();
        detailPane.restoreCourseDetailSection(requestedCourseDetailSection);
    }

    boolean selectRequestedSession() {
        if (sessionActionsSessionId == null) {
            return false;
        }
        var target = sessionTable.getItems().stream()
                .filter(session -> session.id().equals(sessionActionsSessionId))
                .findFirst();
        target.ifPresent(sessionTable.getSelectionModel()::select);
        return target.isPresent();
    }

    void selectRequestedRecipeSession() {
        recipeSessionField.getItems().stream()
                .filter(session -> session.id().equals(sessionActionsSessionId))
                .findFirst()
                .ifPresent(recipeSessionField.getSelectionModel()::select);
    }

    void clearSessionActionsRequest() {
        sessionActionsRequested = false;
        sessionActionsCourseId = null;
        sessionActionsSessionId = null;
        requestedCourseDetailSection = CourseDetailSection.PROGRAM;
    }

    void clearUnavailableSessionRequest(UUID requestedCourseId) {
        if (sessionActionsRequested
                && requestedCourseId != null
                && requestedCourseId.equals(sessionActionsCourseId)) {
            clearSessionActionsRequest();
            actions.showFeedback("Il corso o la sessione richiesta non è più disponibile.", true);
        }
    }

    void refreshSelectedCourse(String message) {
        actions.showFeedback(message, false);
        var course = courseList.getSelectionModel().getSelectedItem();
        if (course != null) {
            var selected = sessionTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                sessionActionsRequested = true;
                sessionActionsCourseId = course.id();
                sessionActionsSessionId = selected.id();
                requestedCourseDetailSection = detailPane.section();
            }
            actions.loadCourses(course.id());
        }
    }

}
