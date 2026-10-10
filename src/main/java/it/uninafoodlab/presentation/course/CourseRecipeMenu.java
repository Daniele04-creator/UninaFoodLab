package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CourseSessionPolicy.*;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.course.AssignRecipeToSession;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.LabIcons;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class CourseRecipeMenu {
    private static final String COURSE_BUTTON_STYLE = "course-button";

    private final Fields fields;
    private final Clock clock;
    private final AuthenticatedUser user;
    private final AssignRecipeToSession assignRecipeToSession;
    private final CourseWorkspaceActions actions;
    private final java.util.function.BooleanSupplier archive;
    private final java.util.function.Supplier<CourseSummary> selectedCourse;
    private final CourseRecipeDetails details;
    private final it.uninafoodlab.application.planning.ManageSessionLifecycle lifecycle;
    private long menuVersion;
    private long savedMenuGeneration;
    private final Map<java.util.UUID, List<String>> savedMenuNames = new HashMap<>();
    private final CourseRecipeCatalog catalog;
    private final CourseAssignedRecipeRows assignedRows;

    CourseRecipeMenu(Fields fields, Clock clock, AuthenticatedUser user,
            AssignRecipeToSession assignRecipeToSession, CourseWorkspaceActions actions,
            java.util.function.BooleanSupplier archive,
            java.util.function.Supplier<CourseSummary> selectedCourse, CourseRecipeDetails details,
            it.uninafoodlab.application.planning.ManageSessionLifecycle lifecycle) {
        this.fields = fields;
        this.clock = clock;
        this.user = user;
        this.assignRecipeToSession = assignRecipeToSession;
        this.actions = actions;
        this.archive = archive;
        this.selectedCourse = selectedCourse;
        this.details = details;
        this.lifecycle = lifecycle;
        this.assignedRows = new CourseAssignedRecipeRows(fields.assignedRecipeRows(), details, this::removeRecipe);
        this.catalog = new CourseRecipeCatalog(new CourseRecipeCatalog.Fields(fields.recipeCatalogRows(),
                fields.recipeResultsLabel(), fields.recipeRetryButton(), fields.recipeSearchField()), actions, this::catalogRow);
    }

    void installPagination(HBox bottomHost) { catalog.installPagination(bottomHost); }
    void setPageLoader(java.util.function.IntConsumer loadPage) { catalog.setPageLoader(loadPage); }
    int page() { return catalog.page(); }
    String search() { return catalog.search(); }
    void markCatalogUnavailable() { catalog.markUnavailable(); }

    record Fields(VBox recipeCatalogRows,
            Label recipeResultsLabel,
            Button recipeRetryButton,
            TextField recipeSearchField,
            ComboBox<CourseSessionSummary> recipeSessionField,
            VBox assignedRecipeRows,
            Label recipeAvailabilityLabel) {}

    void setRecipeCatalogItems(it.uninafoodlab.application.common.Page<RecipeSummary> recipes, int page) {
        catalog.setItems(recipes, page);
    }

    List<String> assignedRecipeNames(CourseSessionSummary session) {
        return CourseWorkspaceView.assignedRecipeNames(session, catalog.items());
    }

    void loadAssignedRecipeNames(CourseSessionSummary session, Consumer<List<String>> onSuccess,
            Runnable onFailure) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        if (session.mode() == SessionMode.ONLINE || session.recipeIds().isEmpty()) {
            onSuccess.accept(List.of());
            return;
        }
        if (lifecycle == null) {
            onSuccess.accept(assignedRecipeNames(session));
            return;
        }
        var cachedNames = savedMenuNames.get(session.id());
        if (cachedNames != null) {
            onSuccess.accept(cachedNames);
            return;
        }
        var generation = savedMenuGeneration;
        it.uninafoodlab.presentation.common.FxAsyncExecutor.run("saved-menu-names",
                () -> lifecycle.menu(user, session.id()), saved -> {
                    if (generation != savedMenuGeneration) return;
                    var names = savedRecipeNames(saved);
                    savedMenuNames.put(session.id(), names);
                    onSuccess.accept(names);
                }, ignored -> {
                    if (generation == savedMenuGeneration) onFailure.run();
                });
    }

    static List<String> savedRecipeNames(
            List<it.uninafoodlab.application.planning.SessionLifecycleRepository.SavedRecipe> savedMenu) {
        Objects.requireNonNull(savedMenu, "savedMenu");
        return savedMenu.stream()
                .map(it.uninafoodlab.application.planning.SessionLifecycleRepository.SavedRecipe::name)
                .toList();
    }

    void invalidateSavedMenuNames() {
        savedMenuGeneration++;
        savedMenuNames.clear();
    }

    void filterRecipeCatalog() { catalog.searchChanged(); }
    void resumeSearch() { catalog.resumeSearch(); }
    void renderRecipeCatalog() { catalog.render(); }

    private boolean canEdit(CourseSessionSummary session) {
        return !actions.isBusy() && catalog.isAvailable() && !archive.getAsBoolean()
                && selectedCourse.get() != null && isRecipeMutable(session, clock);
    }

    private VBox catalogRow(RecipeSummary recipe) {
            var session = fields.recipeSessionField().getValue();
            boolean mutable = canEdit(session);
            var name = new Label(recipe.name());
            name.setWrapText(true);
            name.setMinWidth(0);
            name.getStyleClass().add("field-label");
            var detailsButton = new Button("Ingredienti e dosi");
            detailsButton.getStyleClass().addAll(COURSE_BUTTON_STYLE, "button-quiet");
            detailsButton.setAccessibleText("Ingredienti e dosi di " + recipe.name());
            detailsButton.setOnAction(event -> this.details.show(recipe));
            detailsButton.setDisable(actions.isBusy());
            var assigned = session != null && session.recipeIds().contains(recipe.id());
            var add = new Button(assigned ? "Nel menu" : "Aggiungi");
            add.getStyleClass().addAll(COURSE_BUTTON_STYLE, assigned ? "button-secondary" : "button-primary");
            if (!assigned) add.setGraphic(LabIcons.icon("plus-square", "light", 16));
            add.setAccessibleText((assigned ? "Già nel menu: " : "Aggiungi al menu: ") + recipe.name());
            add.setDisable(!mutable || assigned);
            add.setOnAction(event -> assignRecipe(recipe));
            var rowActions = new javafx.scene.layout.FlowPane(8, 8, detailsButton, add);
            var row = new VBox(6, name, readOnlyValue(recipe.categoryName()), rowActions);
            row.setUserData(recipe.id());
            row.setMinWidth(0);
            row.getStyleClass().add("course-recipe-row");
            return row;
    }

    void assignRecipe(RecipeSummary recipe) {
        var session = fields.recipeSessionField().getValue();
        if (actions.isBusy() || !catalog.isAvailable() || archive.getAsBoolean()) return;
        if (session == null || session.mode() != SessionMode.PRACTICAL) {
            actions.showFeedback("Seleziona una sessione pratica.", true);
            return;
        }
        if (recipe == null) {
            actions.showFeedback("Seleziona una ricetta.", true);
            return;
        }
        if (!isRecipeMutable(session, clock)) {
            actions.showFeedback("Il menu è chiuso: la scadenza delle adesioni è trascorsa.", true);
            return;
        }
        if (session.recipeIds().contains(recipe.id())) {
            actions.showFeedback("Questa ricetta è già associata alla sessione.", true);
            return;
        }
        actions.runAsync(
                "recipe-assignment",
                () -> {
                    assignRecipeToSession.execute(user, session.id(), recipe.id());
                    return session.id();
                },
                ignored -> {
                    invalidateSavedMenuNames();
                    actions.showFeedback("Ricetta aggiunta al menu della sessione pratica.", false);
                    var course = selectedCourse.get();
                    if (course != null) {
                        actions.loadSessions(course);
                    }
                },
                "Non è stato possibile associare la ricetta alla sessione.");
    }

    private void removeRecipe(java.util.UUID recipeId) {
        var session = fields.recipeSessionField().getValue();
        if (session == null || archive.getAsBoolean() || !isRecipeMutable(session, clock)) {
            return;
        }
        actions.runAsync("recipe-removal", () -> {
            assignRecipeToSession.remove(user, session.id(), recipeId);
            return session.id();
        }, ignored -> {
            invalidateSavedMenuNames();
            actions.showFeedback("Ricetta rimossa dal menu.", false);
            var course = selectedCourse.get();
            if (course != null) actions.loadSessions(course);
        }, "Rimozione non riuscita. Con adesioni confermate deve restare almeno una ricetta; il menu deve essere ancora aperto.");
    }

    void renderAssignedRecipes(CourseSessionSummary session, boolean mutable) {
        var version = ++menuVersion;
        assignedRows.clear();
        if (session == null) return;
        if (session.recipeIds().isEmpty()) {
            assignedRows.showMessage("Nessuna ricetta nel menu di questa sessione.");
            return;
        }
        if (lifecycle != null) {
            renderSavedMenu(session, mutable, version);
            return;
        }
        assignedRows.renderCatalog(catalog.items(), session, mutable);
    }

    private void renderSavedMenu(CourseSessionSummary session, boolean mutable, long version) {
            var generation = savedMenuGeneration;
            assignedRows.showMessage("Caricamento del menu salvato…");
            it.uninafoodlab.presentation.common.FxAsyncExecutor.run("saved-menu", () -> lifecycle.menu(user, session.id()), menu -> {
                if (version != menuVersion || generation != savedMenuGeneration) return;
                savedMenuNames.put(session.id(), savedRecipeNames(menu));
                assignedRows.renderSaved(menu, mutable);
            }, failure -> {
                if (version == menuVersion && generation == savedMenuGeneration) {
                    assignedRows.showMessage("Menu non disponibile. Riapri la sessione per aggiornare.");
                }
            });
    }

    void updateRecipeAvailability(CourseSessionSummary session, boolean mutable) {
        if (session == null) {
            fields.recipeAvailabilityLabel().setText("Seleziona una sessione pratica.");
        } else if (!mutable) {
            fields.recipeAvailabilityLabel().setText(session.status() == SessionStatus.CANCELLED
                    ? "La sessione è annullata: la ricetta non può essere modificata."
                    : "Menu chiuso: ricette e fabbisogni non sono più modificabili.");
        } else if (!session.recipeIds().isEmpty()) {
            fields.recipeAvailabilityLabel().setText(
                    "Aggiungi o rimuovi ricette prima della chiusura delle adesioni. Con adesioni confermate deve restarne almeno una.");
        } else {
            fields.recipeAvailabilityLabel().setText("Le adesioni saranno disponibili quando avrai aggiunto almeno una ricetta.");
        }
    }
}
