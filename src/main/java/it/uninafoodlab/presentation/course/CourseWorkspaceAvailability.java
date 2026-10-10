package it.uninafoodlab.presentation.course;


import static it.uninafoodlab.presentation.course.CourseSessionPolicy.isRecipeMutable;
import static it.uninafoodlab.presentation.course.CoursePresentation.setEditorVisible;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.time.Clock;
import java.util.function.BooleanSupplier;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.VBox;

final class CourseWorkspaceAvailability {
    record Fields(TableView<CourseSessionSummary> sessionTable, ListView<CourseSummary> courseList,
            Label recipeCourseContext, VBox sessionCommandBar, Button rescheduleButton,
            Button cancelSessionButton, Button addSessionButton, Label archiveReadOnlyLabel,
            Button communicateCourseButton, ToggleButton programTab, ToggleButton recipeTab,
            ComboBox<CourseSessionSummary> recipeSessionField, Label recipeTaskDescription,
            Label assignedRecipesLabel, VBox recipeAddPanel, ColumnConstraints menuReadColumn,
            ColumnConstraints menuEditColumn, Label recipeSessionContext, ToggleButton recipeRequirementsButton,
            Label recipeAvailabilityLabel) {}

    private final Fields fields;
    private final Clock clock;
    private final CourseCatalogPane catalog;
    private final CourseRecipeMenu recipes;
    private final CourseDetailPane details;
    private final CourseSessionAgenda agenda;
    private final CourseSessionDetails sessionDetails;
    private final BooleanSupplier busy;
    private final BooleanSupplier hasCommunication;
    private final BooleanSupplier hasPlanning;
    private final BooleanSupplier hasRequirements;

    CourseWorkspaceAvailability(Fields fields, Clock clock, CourseCatalogPane catalog, CourseRecipeMenu recipes,
            CourseDetailPane details, CourseSessionAgenda agenda, CourseSessionDetails sessionDetails,
            BooleanSupplier busy, BooleanSupplier hasCommunication,
            BooleanSupplier hasPlanning, BooleanSupplier hasRequirements) {
        this.fields = fields;
        this.clock = clock;
        this.catalog = catalog;
        this.recipes = recipes;
        this.details = details;
        this.agenda = agenda;
        this.sessionDetails = sessionDetails;
        this.busy = busy;
        this.hasCommunication = hasCommunication;
        this.hasPlanning = hasPlanning;
        this.hasRequirements = hasRequirements;
    }

    void update() {
        var session = fields.sessionTable().getSelectionModel().getSelectedItem();
        var course = fields.courseList().getSelectionModel().getSelectedItem();
        boolean working = busy.getAsBoolean();
        boolean archived = catalog.isArchive();
        updateCourseActions(course, working, archived);
        var recipeSession = session != null && session.mode() == SessionMode.ONLINE
                ? null : fields.recipeSessionField().getValue();
        boolean mutable = isRecipeMutable(recipeSession, clock);
        updateMenuLayout(recipeSession, session, archived, mutable);
        sessionDetails.update(session, working);
        updateMenuActions(recipeSession, working, archived, mutable);
        if (archived && course != null) {
            fields.recipeAvailabilityLabel().setText("Corso passato: ricette disponibili in sola lettura.");
        }
        updateScheduleActions(course, session, working, archived);
        details.updateSessionDetailHeader();
        agenda.render(working);
    }

    private void updateCourseActions(CourseSummary course, boolean working, boolean archived) {
        fields.recipeCourseContext().setText(course == null ? "" : course.title().value());
        setEditorVisible(fields.sessionCommandBar(), true);
        setEditorVisible(fields.rescheduleButton(), !archived);
        setEditorVisible(fields.cancelSessionButton(), !archived);
        setEditorVisible(fields.addSessionButton(), !archived);
        setEditorVisible(fields.archiveReadOnlyLabel(), archived);
        boolean unavailable = working || course == null;
        fields.communicateCourseButton().setDisable(unavailable || !hasCommunication.getAsBoolean());
        fields.programTab().setDisable(unavailable);
        fields.recipeTab().setDisable(unavailable);
    }

    private void updateMenuLayout(CourseSessionSummary recipeSession, CourseSessionSummary session,
            boolean archived, boolean mutable) {
        boolean online = session != null && session.mode() == SessionMode.ONLINE;
        setEditorVisible(fields.recipeTaskDescription(), !online);
        setEditorVisible(fields.recipeAvailabilityLabel(), !online);
        fields.recipeTaskDescription().setText(!archived && mutable
                ? "Componi il menu prima della chiusura delle adesioni."
                : "Consulta le ricette della sessione.");
        fields.assignedRecipesLabel().setText(menuHeading(recipeSession, session));
        boolean editable = !archived && recipeSession != null && mutable;
        setEditorVisible(fields.recipeAddPanel(), editable);
        fields.menuReadColumn().setPercentWidth(editable ? 58 : 100);
        fields.menuEditColumn().setPercentWidth(editable ? 42 : 0);
        setEditorVisible(fields.communicateCourseButton(), !archived);
    }

    private static String menuHeading(CourseSessionSummary recipeSession, CourseSessionSummary session) {
        if (recipeSession != null) return "Ricette nel menu";
        if (session != null && session.mode() == SessionMode.ONLINE) {
            return "Questa sessione online non prevede un menu.";
        }
        return "Nessun menu selezionato";
    }

    private void updateMenuActions(CourseSessionSummary session, boolean working, boolean archived, boolean mutable) {
        fields.recipeSessionContext().setText(session == null ? "" : (it.uninafoodlab.presentation.common.SessionLocationLinks
                .fromVenue(session.venue()).isPresent() ? "Posizione" : session.venue().orElse(""))
                + session.capacity().map(value -> ", " + value + " posti").orElse(""));
        boolean requirementsAvailable = (hasPlanning.getAsBoolean() || hasRequirements.getAsBoolean()) && session != null;
        setEditorVisible(fields.recipeRequirementsButton(), requirementsAvailable);
        fields.recipeRequirementsButton().setDisable(working);
        recipes.renderRecipeCatalog();
        recipes.renderAssignedRecipes(session, !working && !archived && mutable);
        fields.recipeSessionField().setDisable(working);
        recipes.updateRecipeAvailability(session, mutable);
    }

    private void updateScheduleActions(CourseSummary course, CourseSessionSummary session,
            boolean working, boolean archived) {
        boolean courseEditable = editableCourse(course);
        boolean scheduled = session != null && session.status() == SessionStatus.SCHEDULED;
        boolean unavailable = working || archived || !courseEditable;
        fields.rescheduleButton().setDisable(unavailable || !scheduled);
        fields.cancelSessionButton().setDisable(unavailable || !scheduled);
        fields.addSessionButton().setDisable(unavailable);
    }

    private static boolean editableCourse(CourseSummary course) {
        return course != null && (course.status() == CourseStatus.PUBLISHED || course.status() == CourseStatus.DRAFT);
    }
}
