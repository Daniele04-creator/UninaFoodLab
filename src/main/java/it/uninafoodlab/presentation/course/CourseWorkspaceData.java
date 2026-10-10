package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CourseSessionPolicy.*;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.catalog.RecipeFilter;
import it.uninafoodlab.application.catalog.RecipeSearchScope;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.presentation.common.ResultPager;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

final class CourseWorkspaceData {
    private final Fields fields;
    private final AuthenticatedUser user;
    private final BrowseCatalog browseCatalog;
    private final BrowseCourses browseCourses;
    private final CourseWorkspaceActions actions;
    private final CourseCatalogPane catalogPane;
    private final CourseRecipeMenu recipeMenu;
    private final CourseDetailPane detailPane;
    private final CourseSessionNavigation navigation;
    private final ReactiveFilterGuard filterGuard;
    private final Runnable refreshActions;
    private final CourseCardGrid cardGrid;
    private Runnable retryCatalogRead;

    CourseWorkspaceData(Fields fields, AuthenticatedUser user, BrowseCatalog browseCatalog,
            BrowseCourses browseCourses, CourseWorkspaceActions actions, CourseCatalogPane catalogPane,
            CourseRecipeMenu recipeMenu, CourseDetailPane detailPane, CourseSessionNavigation navigation,
            ReactiveFilterGuard filterGuard, Runnable refreshActions, CourseCardGrid cardGrid) {
        this.fields = fields;
        this.user = user;
        this.browseCatalog = browseCatalog;
        this.browseCourses = browseCourses;
        this.actions = actions;
        this.catalogPane = catalogPane;
        this.recipeMenu = recipeMenu;
        this.detailPane = detailPane;
        this.navigation = navigation;
        this.filterGuard = filterGuard;
        this.refreshActions = refreshActions;
        this.cardGrid = cardGrid;
        cardGrid.setPageLoader(this::loadPage);
        recipeMenu.setPageLoader(this::loadRecipePage);
    }

    private record InitialData(List<CategorySummary> categories, Page<RecipeSummary> recipes,
            CourseCatalogSnapshot catalog) {}

    record Fields(ComboBox<CategorySummary> categoryField,
            ComboBox<CourseCategoryFilter> categoryFilter,
            ListView<CourseSummary> courseList,
            TableView<CourseSessionSummary> sessionTable,
            ComboBox<CourseSessionSummary> recipeSessionField) {}

    void resumePendingReads() {
        navigation.resumeRequestedWorkspace();
        resumeSearch();
    }

    void resumeSearch() {
        catalogPane.resumeSearch();
        recipeMenu.resumeSearch();
    }

    void refreshSelectedCourse() {
        var course = fields.courseList().getSelectionModel().getSelectedItem();
        if (course != null) loadSessions(course);
    }

    void selectCourse(CourseSummary course) {
        recipeMenu.invalidateSavedMenuNames();
        fields.sessionTable().getItems().clear();
        SessionTableSizing.apply(fields.sessionTable(), 0);
        fields.recipeSessionField().getItems().clear();
        fields.recipeSessionField().setValue(null);
        navigation.loadedSessions(null);
        if (course != null) loadSessions(course);
        refreshActions.run();
    }

    void loadInitialData() {
        var filter = catalogPane.filter(Optional.empty());
        actions.runAsync(
                "course-workspace-load",
                () -> new InitialData(
                        browseCatalog.categories(user),
                        browseCatalog.recipes(user, new RecipeFilter(Optional.empty(), "", RecipeSearchScope.MENU),
                                new PageRequest(0, ResultPager.PAGE_SIZE)),
                        loadCatalog(filter, 0)),
                initialData -> {
                    fields.categoryField().getItems().setAll(initialData.categories());
                    fields.categoryFilter().getItems().setAll(CourseCategoryFilter.all());
                    initialData.categories().stream()
                            .map(CourseCategoryFilter::category)
                            .forEach(fields.categoryFilter().getItems()::add);
                    fields.categoryFilter().getSelectionModel().selectFirst();
                    filterGuard.markReady();

                    recipeMenu.setRecipeCatalogItems(initialData.recipes(), 0);
                    cardGrid.acceptPage(initialData.catalog().page(), initialData.catalog().totalCourses());
                    cardGrid.setSessions(initialData.catalog().sessions());
                    var courses = catalogPane.display(initialData.catalog().courses(), initialData.catalog().totalCourses());
                    if (!courses.isEmpty()) {
                        var requestedCourse = navigation.hasRequest()
                                ? courses.stream()
                                        .filter(course -> course.id().equals(navigation.requestedCourseId()))
                                        .findFirst()
                                : Optional.<CourseSummary>empty();
                        if (requestedCourse.isPresent()) {
                            fields.courseList().getSelectionModel().select(requestedCourse.orElseThrow());
                        } else {
                            fields.courseList().getSelectionModel().selectFirst();
                        }
                    }
                },
                "Non è stato possibile caricare corsi e catalogo.");
    }

    void refreshCatalog() {
        loadRecipePage(recipeMenu.page());
    }

    private void loadRecipePage(int requestedPage) {
        var filter = new RecipeFilter(Optional.empty(), recipeMenu.search(), RecipeSearchScope.MENU);
        actions.runAsync("catalog-refresh", () -> {
            var recipes = browseCatalog.recipes(user, filter, new PageRequest(requestedPage, ResultPager.PAGE_SIZE));
            int availablePage = ResultPager.clamp(requestedPage, Math.toIntExact(recipes.totalCount()));
            if (availablePage != requestedPage) {
                recipes = browseCatalog.recipes(user, filter, new PageRequest(availablePage, ResultPager.PAGE_SIZE));
            }
            return new RecipeCatalogPage(recipes, availablePage);
        }, loaded -> {
            recipeMenu.setRecipeCatalogItems(loaded.recipes(), loaded.page());
            refreshActions.run();
        }, "Non è stato possibile aggiornare il catalogo ricette.");
    }

    private record RecipeCatalogPage(Page<RecipeSummary> recipes, int page) {}

    void refreshCategories(CategorySummary created) {
        actions.runAsync(
                "category-refresh",
                () -> browseCatalog.categories(user),
                categories -> {
                    fields.categoryField().getItems().setAll(categories);
                    fields.categoryField().getSelectionModel().select(created);
                    filterGuard.update(() -> {
                        fields.categoryFilter().getItems().setAll(CourseCategoryFilter.all());
                        categories.stream().map(CourseCategoryFilter::category)
                                .forEach(fields.categoryFilter().getItems()::add);
                        fields.categoryFilter().getSelectionModel().select(CourseCategoryFilter.category(created));
                    });

                    actions.showFeedback("Categoria creata e selezionata.", false);
                    if (filterGuard.permitsReload(actions.isBusy())) {
                        catalogPane.resetPagination();
                        loadCourses(null);
                    }
                },
                "Categoria creata, ma non è stato possibile aggiornare il catalogo.");
    }

    void loadCourses(UUID selectCourseId) {
        if (selectCourseId != null) {
            loadCourseTarget(selectCourseId);
        } else {
            loadPage(cardGrid.page());
        }
    }

    void retryCatalogRead() { if (retryCatalogRead != null) retryCatalogRead.run(); }

    private void loadPage(int page) {
        var filter = currentFilter();
        retryCatalogRead = () -> loadPage(page);
        actions.runAsync(
                "course-list-load",
                () -> loadCatalog(filter, page),
                snapshot -> {
                    cardGrid.acceptPage(snapshot.page(), snapshot.totalCourses());
                    cardGrid.setSessions(snapshot.sessions());
                    var loadedCourses = snapshot.courses();
                    var courses = catalogPane.display(loadedCourses, snapshot.totalCourses());
                    if (!courses.isEmpty()) {
                        fields.courseList().getSelectionModel().selectFirst();
                    } else {
                        catalogPane.showEmptyCatalog();
                        fields.sessionTable().getItems().clear();
                        SessionTableSizing.apply(fields.sessionTable(), 0);
                    }
                    refreshActions.run();
                },
                "Non è stato possibile aggiornare l'elenco dei corsi.");
    }

    private CourseFilter currentFilter() {
        var selectedCategory = fields.categoryFilter().getValue();
        var categoryId = selectedCategory == null ? Optional.<UUID>empty() : selectedCategory.categoryId();
        return catalogPane.filter(categoryId);
    }

    private void loadCourseTarget(UUID courseId) {
        var filter = currentFilter();
        int page = cardGrid.page();
        retryCatalogRead = () -> loadCourseTarget(courseId);
        actions.runAsync("course-list-load", () -> new CourseTarget(
                loadCatalog(filter, page), browseCourses.course(user, courseId)), loaded -> {
            fields.courseList().getSelectionModel().clearSelection();
            cardGrid.acceptPage(loaded.catalog().page(), loaded.catalog().totalCourses());
            cardGrid.setSessions(loaded.catalog().sessions());
            catalogPane.display(loaded.catalog().courses(), loaded.catalog().totalCourses());
            if (loaded.course().isEmpty()) {
                navigation.clearUnavailableSessionRequest(courseId);
                return;
            }
            fields.courseList().getSelectionModel().select(loaded.course().orElseThrow());
            if (!navigation.hasRequest()) detailPane.restoreCourseDetailSection(CourseDetailSection.PROGRAM);
        }, "Non è stato possibile aggiornare il corso.");
    }

    private record CourseTarget(CourseCatalogSnapshot catalog, Optional<CourseSummary> course) {}

    void loadSessions(CourseSummary course) {
        if (actions.isBusy()) {
            return;
        }
        recipeMenu.invalidateSavedMenuNames();
        navigation.loadedSessions(null);
        var recipeSessionToRestore = fields.recipeSessionField().getValue();
        actions.runAsync(
                "course-session-load",
                () -> browseCourses.sessions(user, course.id()),
                sessions -> {
                    cardGrid.updateSessions(course.id(), sessions);
                    var visibleSessions = sessionsForDisplay(sessions);
                    navigation.loadedSessions(course.id());
                    fields.sessionTable().getItems().setAll(visibleSessions);
                    SessionTableSizing.apply(fields.sessionTable(), visibleSessions.size());
                    fields.recipeSessionField().getItems().setAll(visibleSessions.stream()
                            .filter(session -> session.mode() == SessionMode.PRACTICAL)
                            .toList());
                    fields.sessionTable().getSelectionModel().clearSelection();
                    if (navigation.hasRequest()
                            && course.id().equals(navigation.requestedCourseId())) {
                        navigation.openRequestedWorkspace();
                        navigation.clearSessionActionsRequest();
                    } else if (detailPane.section() == CourseDetailSection.RECIPES) {
                        recipeSessionSelection(fields.recipeSessionField().getItems(), recipeSessionToRestore)
                                .ifPresentOrElse(
                                        fields.recipeSessionField().getSelectionModel()::select,
                                        () -> {
                                            fields.recipeSessionField().getSelectionModel().clearSelection();
                                            recipeMenu.renderRecipeCatalog();
                                        });
                    }
                    if (fields.sessionTable().getSelectionModel().getSelectedItem() == null && !visibleSessions.isEmpty()) {
                        fields.sessionTable().getSelectionModel().selectFirst();
                    }
                    refreshActions.run();
                },
                "Non è stato possibile caricare le sessioni del corso.");
    }

    private CourseCatalogSnapshot loadCatalog(CourseFilter filter, int pageIndex) {
        var page = browseCourses.courses(user, filter, new PageRequest(pageIndex, ResultPager.PAGE_SIZE));
        int availablePage = ResultPager.clamp(pageIndex, Math.toIntExact(page.totalCount()));
        if (availablePage != pageIndex) {
            page = browseCourses.courses(user, filter, new PageRequest(availablePage, ResultPager.PAGE_SIZE));
        }
        var courseIds = page.items().stream().map(CourseSummary::id).toList();
        return new CourseCatalogSnapshot(page.items(), browseCourses.sessions(user, courseIds),
                availablePage, page.totalCount());
    }

}
