package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

final class CourseSessionSelection {
    private final TableView<CourseSessionSummary> sessionTable;
    private final ComboBox<CourseSessionSummary> recipeSessionField;
    private final CourseScheduleEditor scheduleEditor;
    private final Runnable refreshActions;
    private final Runnable refreshRecipes;
    private boolean synchronizingSession;

    CourseSessionSelection(TableView<CourseSessionSummary> sessionTable,
            ComboBox<CourseSessionSummary> recipeSessionField, CourseScheduleEditor scheduleEditor,
            Runnable refreshActions, Runnable refreshRecipes) {
        this.sessionTable = sessionTable;
        this.recipeSessionField = recipeSessionField;
        this.scheduleEditor = scheduleEditor;
        this.refreshActions = refreshActions;
        this.refreshRecipes = refreshRecipes;
        configureLists();
    }

    private void configureLists() {
        recipeSessionField.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourseSessionSummary session) {
                return session == null ? "" : programSessionName(session) + ", "
                        + session.scheduledAt().format(PROGRAM_DATE_FORMAT);
            }

            @Override
            public CourseSessionSummary fromString(String value) {
                return null;
            }
        });
        recipeSessionField.valueProperty().addListener(
                (observable, previous, session) -> {
                    refreshRecipes.run();
                    if (!synchronizingSession && session != null) {
                        synchronizingSession = true;
                        sessionTable.getSelectionModel().select(session);
                        synchronizingSession = false;
                    }
                    refreshActions.run();
                });
    }

    void selectSession(CourseSessionSummary session) {
        if (session != null) {
            scheduleEditor.populateScheduleEditor(session);
            synchronizeRecipeSelection(session);
        }
        refreshActions.run();
    }

    private void synchronizeRecipeSelection(CourseSessionSummary session) {
        if (synchronizingSession) return;
        synchronizingSession = true;
        if (session.mode() == SessionMode.PRACTICAL) recipeSessionField.getSelectionModel().select(session);
        else recipeSessionField.getSelectionModel().clearSelection();
        synchronizingSession = false;
    }

}
