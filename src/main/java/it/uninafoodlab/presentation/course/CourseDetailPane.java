package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CourseSessionPolicy.*;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.presentation.common.LabIcons;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class CourseDetailPane {
    private final Fields fields;
    private final CourseDetailScroll scroll;
    private final CourseWorkspaceActions actions;
    private final Runnable refreshActions;
    private final Runnable refreshRecipes;
    private final java.util.function.BooleanSupplier archive;
    private final java.util.function.Supplier<CourseWorkspaceController.SessionPlanningAction> planningAction;
    private Runnable sessionReturnAction;
    private CourseDetailSection detailSection = CourseDetailSection.PROGRAM;

    CourseDetailPane(Fields fields, CourseWorkspaceActions actions, Runnable refreshActions,
            Runnable refreshRecipes, java.util.function.BooleanSupplier archive,
            java.util.function.Supplier<CourseWorkspaceController.SessionPlanningAction> planningAction) {
        this.fields = fields;
        this.scroll = new CourseDetailScroll(fields.courseContentScroll());
        this.actions = actions;
        this.refreshActions = refreshActions;
        this.refreshRecipes = refreshRecipes;
        this.archive = archive;
        this.planningAction = planningAction;
    }

    CourseDetailSection section() { return detailSection; }

    record Fields(ListView<CourseSummary> courseList,
            TableView<CourseSessionSummary> sessionTable,
            ComboBox<CourseSessionSummary> recipeSessionField,
            Button sessionReturnButton,
            Button courseReturnButton,
            Label feedbackLabel,
            ToggleButton programTab,
            ToggleButton recipeTab,
            ToggleButton sessionMenuTab,
            ToggleButton sessionParticipantsTab,
            ToggleButton recipeRequirementsButton,
            VBox programPane,
            VBox recipePane,
            HBox coursePageHeader,
            VBox courseSelectorRail,
            VBox courseDetailView,
            VBox courseOverview,
            VBox sessionDetailHeader,
            ScrollPane courseContentScroll,
            javafx.scene.layout.StackPane sessionPlanningHost,
            TextField courseSearchField,
            Label sessionDetailTitle,
            Label sessionDetailContext,
            javafx.scene.layout.FlowPane sessionDetailMetadata,
            Label sessionDetailMode,
            Label sessionDetailStatus,
            Label sessionDetailLocation,
            HBox detailLessonActions,
            Button courseParticipantsButton,
            Button rescheduleButton,
            Button cancelSessionButton) {}

    void setSessionReturnAction(String label, Runnable action) {
        sessionReturnAction = action;
        fields.sessionReturnButton().setText(action == null || label == null ? "Torna al programma" : label);
        fields.sessionReturnButton().setAccessibleText(fields.sessionReturnButton().getText());
    }

    void returnFromSession() {
        if (sessionReturnAction != null) sessionReturnAction.run();
        else showProgram();
    }

    void showCatalog() {
        setEditorVisible(fields.courseSelectorRail(), true);
        setEditorVisible(fields.coursePageHeader(), true);
        setEditorVisible(fields.courseDetailView(), false);
    }

    void showProgram() {
        selectCourseDetailSection(CourseDetailSection.PROGRAM);
    }

    void showRecipes() {
        var selected = fields.sessionTable().getSelectionModel().getSelectedItem();
        if (selected != null && selected.mode() == SessionMode.ONLINE) {
            fields.recipeSessionField().getSelectionModel().clearSelection();
        } else if (selected != null) {
            fields.recipeSessionField().getSelectionModel().select(selected);
        } else {
            selectFirstPracticalSession();
        }
        selectCourseDetailSection(CourseDetailSection.RECIPES);
        refreshActions.run();
    }

    void showSessionParticipants() {
        selectCourseDetailSection(detailSection == CourseDetailSection.ENROLLMENTS
                ? CourseDetailSection.ENROLLMENTS : CourseDetailSection.PARTICIPANTS);
    }

    void showCourseParticipants() {
        selectCourseDetailSection(CourseDetailSection.ENROLLMENTS);
    }

    void selectCourseDetailSection(CourseDetailSection section) {
        fields.feedbackLabel().setManaged(false);
        fields.feedbackLabel().setVisible(false);
        restoreCourseDetailSection(section);
    }

    void restoreCourseDetailSection(CourseDetailSection section) {
        boolean changingSection = detailSection != section;
        detailSection = Objects.requireNonNull(section, "section");
        boolean program = section == CourseDetailSection.PROGRAM;
        boolean menu = section == CourseDetailSection.RECIPES;
        boolean planning = !program && !menu;
        fields.programTab().setSelected(program);
        fields.recipeTab().setSelected(menu);
        fields.sessionMenuTab().setSelected(menu);
        fields.sessionParticipantsTab().setSelected(section == CourseDetailSection.PARTICIPANTS
                || section == CourseDetailSection.ENROLLMENTS);
        fields.recipeRequirementsButton().setSelected(section == CourseDetailSection.REQUIREMENTS);
        setEditorVisible(fields.programPane(), program);
        setEditorVisible(fields.recipePane(), menu);
        setEditorVisible(fields.coursePageHeader(), program);
        setEditorVisible(fields.courseSelectorRail(), false);
        setEditorVisible(fields.courseDetailView(), true);
        setEditorVisible(fields.courseOverview(), program);
        setEditorVisible(fields.sessionDetailHeader(), !program);
        setEditorVisible(fields.courseContentScroll(), !planning);
        setEditorVisible(fields.sessionPlanningHost(), planning);
        updateSessionDetailHeader();
        if (planning) openPlanning(section);

        if (changingSection) focusSection(program);
    }

    private void openPlanning(CourseDetailSection section) {
        var action = planningAction.get();
        var course = fields.courseList().getSelectionModel().getSelectedItem();
        var session = fields.sessionTable().getSelectionModel().getSelectedItem();
        if (action == null || course == null) return;
        if (session == null && section != CourseDetailSection.ENROLLMENTS) return;
        fields.sessionPlanningHost().getChildren().setAll(action.open(course.id(),
                section == CourseDetailSection.ENROLLMENTS ? null : session.id(),
                section != CourseDetailSection.REQUIREMENTS));
    }

    private void focusSection(boolean program) {
        javafx.scene.Node destination = program ? fields.courseReturnButton() : fields.sessionReturnButton();
        if (destination.getScene() != null && destination.isVisible() && !destination.isDisabled()) {
            destination.requestFocus();
        }
    }

    void updateSessionDetailHeader() {
        var course = fields.courseList().getSelectionModel().getSelectedItem();
        var session = fields.sessionTable().getSelectionModel().getSelectedItem();
        scroll.enter(detailSection, course, session);
        boolean enrollments = detailSection == CourseDetailSection.ENROLLMENTS;
        fields.sessionDetailTitle().setText(course == null ? (enrollments ? "Iscritti" : "Sessione") : course.title().value());
        String context = "Seleziona una sessione dal programma.";
        if (enrollments) {
            context = "Iscritti al corso";
        } else if (session != null) {
            var end = session.scheduledAt().atZone(ROME).plusMinutes(session.durationMinutes());
            var time = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            context = "Sessione " + session.sequenceNumber() + ", "
                    + COURSE_DATE_FORMAT.format(session.scheduledAt()) + ", "
                    + time.format(session.scheduledAt()) + "–"
                    + (end.toLocalDate().equals(session.scheduledAt().toLocalDate()) ? "" : COURSE_DATE_FORMAT.format(end) + ", ")
                    + time.format(end);
        }
        fields.sessionDetailContext().setText(context);
        updateSessionMetadata(enrollments ? null : session);
        updateLocationActions(session, enrollments);
        setEditorVisible(fields.sessionMenuTab(), !enrollments);
        setEditorVisible(fields.sessionParticipantsTab(), !enrollments);
        setEditorVisible(fields.recipeRequirementsButton(), !enrollments && session != null
                && session.mode() == SessionMode.PRACTICAL);
        fields.sessionParticipantsTab().setDisable(actions.isBusy() || course == null || planningAction.get() == null);
        fields.sessionMenuTab().setDisable(actions.isBusy() || session == null);
        fields.courseParticipantsButton().setDisable(actions.isBusy() || course == null || planningAction.get() == null);
        var editable = !enrollments && !archive.getAsBoolean();
        setEditorVisible(fields.rescheduleButton(), editable);
        setEditorVisible(fields.cancelSessionButton(), editable);
    }

    private void updateLocationActions(CourseSessionSummary session, boolean enrollments) {
        setEditorVisible(fields.detailLessonActions(), !enrollments && session != null
                && (session.mode() == SessionMode.ONLINE
                    || it.uninafoodlab.presentation.common.SessionLocationLinks.fromVenue(session.venue()).isPresent()));
    }

    private void updateSessionMetadata(CourseSessionSummary session) {
        setEditorVisible(fields.sessionDetailMetadata(), session != null);
        setEditorVisible(fields.sessionDetailLocation(), session != null && session.mode() == SessionMode.PRACTICAL
                && session.venue().isPresent());
        if (session == null) return;
        CourseSessionModeView.update(fields.sessionDetailMode(), session.mode());
        var status = sessionStatusLabel(session);
        fields.sessionDetailStatus().setText(status);
        fields.sessionDetailStatus().getStyleClass().removeAll("session-status-pending", "session-status-confirmed",
                "session-status-cancelled");
        fields.sessionDetailStatus().getStyleClass().add(CourseSessionTable.statusStyle(status));
        fields.sessionDetailLocation().setText(it.uninafoodlab.presentation.common.SessionLocationLinks.fromVenue(session.venue())
                .isPresent() ? "Posizione" : session.venue().orElse(""));
        fields.sessionDetailLocation().setGraphic(LabIcons.icon("map-pin", "dark", 17));
        fields.sessionDetailLocation().setGraphicTextGap(6);
    }

    void selectFirstPracticalSession() {
        recipeSessionSelection(fields.recipeSessionField().getItems(), fields.recipeSessionField().getValue())
                .ifPresentOrElse(
                        fields.recipeSessionField().getSelectionModel()::select,
                        () -> {
                            fields.recipeSessionField().getSelectionModel().clearSelection();
                            refreshRecipes.run();
                        });
    }

    void selectFirstScheduledSession() {
        var selected = fields.sessionTable().getSelectionModel().getSelectedItem();
        if (selected != null && selected.status() == SessionStatus.SCHEDULED) {
            return;
        }
        fields.sessionTable().getItems().stream()
                .filter(session -> session.status() == SessionStatus.SCHEDULED)
                .findFirst()
                .ifPresent(fields.sessionTable().getSelectionModel()::select);
    }
}
