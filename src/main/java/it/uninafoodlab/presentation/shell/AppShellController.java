package it.uninafoodlab.presentation.shell;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.CreateCategory;
import it.uninafoodlab.application.catalog.ManageRecipeCatalog;
import it.uninafoodlab.presentation.catalog.CatalogWorkspaceController;
import it.uninafoodlab.presentation.common.LabIcons;
import it.uninafoodlab.presentation.common.ProfileMenus;
import it.uninafoodlab.application.course.AssignRecipeToSession;
import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CreateCourse;
import it.uninafoodlab.application.notification.BrowseNotifications;
import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.application.planning.BrowseChefPlanning;
import it.uninafoodlab.application.schedule.ManageCourseSchedule;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.presentation.course.CourseWorkspaceController;
import it.uninafoodlab.presentation.dashboard.DashboardWorkspaceController;
import it.uninafoodlab.presentation.notification.NotificationWorkspaceController;
import it.uninafoodlab.presentation.planning.PlanningWorkspaceController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public final class AppShellController {

    private static final String COURSE_WORKSPACE =
            "/it/uninafoodlab/presentation/course/course-workspace.fxml";
    private static final String DASHBOARD_WORKSPACE =
            "/it/uninafoodlab/presentation/dashboard/dashboard-workspace.fxml";
    private static final String NOTIFICATION_WORKSPACE =
            "/it/uninafoodlab/presentation/notification/notification-workspace.fxml";
    private static final String PLANNING_WORKSPACE =
            "/it/uninafoodlab/presentation/planning/planning-workspace.fxml";

    private final AuthenticatedUser user;
    private final BrowseCatalog browseCatalog;
    private final CreateCategory createCategory;
    private final ManageRecipeCatalog manageRecipeCatalog;
    private final CreateCourse createCourse;
    private final BrowseCourses browseCourses;
    private final AssignRecipeToSession assignRecipeToSession;
    private final BrowseNotifications browseNotifications;
    private final ManageNotifications manageNotifications;
    private final ManageCourseSchedule manageCourseSchedule;
    private final BrowseChefPlanning browseChefPlanning;
    private final Runnable logoutAction;
    private it.uninafoodlab.application.planning.ManageSessionLifecycle sessionLifecycle;
    private Runnable accountAction = () -> {};

    public void setSessionLifecycle(it.uninafoodlab.application.planning.ManageSessionLifecycle lifecycle) {
        sessionLifecycle = lifecycle;
    }

    public void setAccountAction(Runnable action) { accountAction = action; }

    @FXML private void openAccount() { accountAction.run(); }

    @FXML private void openProfile() {
        ProfileMenus.show(profileButton, user, this::openAccount, this::logout);
    }

    @FXML private Button dashboardButton;
    @FXML private Button coursesButton;
    @FXML private Button notificationsButton;
    @FXML private Button catalogButton;
    @FXML private Button monthlyReportButton;
    @FXML private Label profileName;
    @FXML private Label profileChevron;
    @FXML private Button profileButton;
    @FXML private Label brandTitle;
    @FXML private StackPane contentHost;
    @FXML private javafx.scene.layout.BorderPane shellRoot;
    @FXML private javafx.scene.layout.GridPane shellHeader;
    @FXML private javafx.scene.layout.HBox navigationBar;
    @FXML private javafx.scene.layout.HBox profileActions;

    private Node dashboardWorkspace;
    private Node courseWorkspace;
    private Node notificationWorkspace;
    private Node planningWorkspace;
    private Node catalogWorkspace;
    private Node embeddedPlanningWorkspace;
    private PlanningWorkspaceController embeddedPlanningController;
    private CatalogWorkspaceController catalogWorkspaceController;
    private DashboardWorkspaceController dashboardWorkspaceController;
    private CourseWorkspaceController courseWorkspaceController;
    private NotificationWorkspaceController notificationWorkspaceController;
    private PlanningWorkspaceController planningWorkspaceController;
    private Node shoppingListWorkspace;
    private PlanningWorkspaceController shoppingListController;
    private Runnable shoppingListRefresh;
    private Button activeSection;
    private ShellNavigation navigation;

    public AppShellController(
            AuthenticatedUser user,
            BrowseCatalog browseCatalog,
            CreateCategory createCategory,
            CreateCourse createCourse,
            BrowseCourses browseCourses,
            AssignRecipeToSession assignRecipeToSession,
            BrowseNotifications browseNotifications,
            ManageNotifications manageNotifications,
            ManageCourseSchedule manageCourseSchedule,
            BrowseChefPlanning browseChefPlanning,
            ManageRecipeCatalog manageRecipeCatalog,
            Runnable logoutAction) {
        this.user = Objects.requireNonNull(user, "user");
        if (user.role() != Role.CHEF) {
            throw new SecurityException("Desktop access is restricted to chefs");
        }
        this.browseCatalog = Objects.requireNonNull(browseCatalog, "browseCatalog");
        this.createCategory = Objects.requireNonNull(createCategory, "createCategory");
        this.manageRecipeCatalog = Objects.requireNonNull(manageRecipeCatalog, "manageRecipeCatalog");
        this.createCourse = Objects.requireNonNull(createCourse, "createCourse");
        this.browseCourses = Objects.requireNonNull(browseCourses, "browseCourses");
        this.assignRecipeToSession = Objects.requireNonNull(
                assignRecipeToSession, "assignRecipeToSession");
        this.browseNotifications = Objects.requireNonNull(browseNotifications, "browseNotifications");
        this.manageNotifications = Objects.requireNonNull(manageNotifications, "manageNotifications");
        this.manageCourseSchedule = Objects.requireNonNull(manageCourseSchedule, "manageCourseSchedule");
        this.browseChefPlanning = Objects.requireNonNull(browseChefPlanning, "browseChefPlanning");
        this.logoutAction = Objects.requireNonNull(logoutAction, "logoutAction");
    }

    @FXML
    private void initialize() {
        navigation = new ShellNavigation(contentHost, List.of(
                new ShellNavigation.Item(dashboardButton, "calendar"),
                new ShellNavigation.Item(coursesButton, "book-open"),
                new ShellNavigation.Item(notificationsButton, "message-square"),
                new ShellNavigation.Item(catalogButton, "book-open"),
                new ShellNavigation.Item(monthlyReportButton, "bar-chart-2")));
        brandTitle.setText("");
        brandTitle.setAccessibleText("UninaFoodLab");
        var logo = it.uninafoodlab.presentation.common.LabBrand.logo(174);
        brandTitle.setGraphic(logo);
        profileName.setText(user.displayName());
        profileChevron.setGraphic(LabIcons.icon("chevron-down", "dark", 14));
        profileButton.setAccessibleText("Account di " + user.displayName() + ": password e uscita");
        coursesButton.setText("Corsi");
        shellHeader.widthProperty().addListener((observable, before, width) ->
                ShellHeaderLayout.arrange(width.doubleValue(), shellHeader, navigationBar, profileActions));
        ShellHeaderLayout.arrange(0, shellHeader, navigationBar, profileActions);
        showDashboard();
    }

    @FXML
    private void showDashboard() {
        if (dashboardWorkspace == null) {
            dashboardWorkspace = loadDashboardWorkspace();
        }
        dashboardWorkspaceController.activate();
        showContent(dashboardButton, dashboardWorkspace);
    }

    @FXML
    private void showCourses() {
        if (courseWorkspace == null) {
            courseWorkspace = loadCourseWorkspace();
        }
        courseWorkspaceController.showCourseIndex();
        showContent(coursesButton, courseWorkspace);
    }

    @FXML
    private void showCatalog() {
        if (catalogWorkspace == null) {
            var loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(
                    "/it/uninafoodlab/presentation/catalog/catalog-workspace.fxml")));
            catalogWorkspaceController = new CatalogWorkspaceController(user, browseCatalog, manageRecipeCatalog);
            catalogWorkspaceController.setCreateCategory(createCategory);
            loader.setController(catalogWorkspaceController);
            catalogWorkspace = load(loader, "catalog workspace");
        } else {
            catalogWorkspaceController.activate();
        }
        showContent(catalogButton, catalogWorkspace);
    }

    private void showSessionActions(UUID courseId, UUID sessionId) {
        showCourseSessionWorkspace(
                controller -> controller.showSessionActions(courseId, sessionId));
    }

    private void showRecipeAssignment(UUID courseId, UUID sessionId) {
        showCourseSessionWorkspace(
                controller -> controller.showSessionActions(courseId, sessionId));
    }

    private void showCourseSessionWorkspace(Consumer<CourseWorkspaceController> navigation) {
        var origin = activeSection;
        if (courseWorkspace == null) {
            courseWorkspace = loadCourseWorkspace();
        }
        if (courseWorkspaceController != null) {
            navigation.accept(courseWorkspaceController);
            if (origin == dashboardButton) {
                courseWorkspaceController.setSessionReturnAction("Torna all’agenda", this::showDashboard);
            } else if (contentHost.getChildren().contains(shoppingListWorkspace)) {
                courseWorkspaceController.setSessionReturnAction("Torna alla lista della spesa",
                        shoppingListRefresh);
            } else {
                courseWorkspaceController.setSessionReturnAction(null, null);
            }
        }
        showContent(origin == dashboardButton ? origin : coursesButton,
                courseWorkspace);
    }

    @FXML
    private void showNotifications() {
        if (notificationWorkspace == null) {
            notificationWorkspace = loadNotificationWorkspace();
        }
        showContent(notificationsButton, notificationWorkspace);
    }

    private void composeForCourse(UUID courseId) {
        showNotifications();
        notificationWorkspaceController.composeForCourse(courseId);
    }

    private void showShoppingList(it.uninafoodlab.application.course.CourseSummary course) {
        if (shoppingListWorkspace == null) {
            var loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(PLANNING_WORKSPACE)));
            shoppingListController = new PlanningWorkspaceController(user, browseCourses, browseChefPlanning);
            shoppingListController.setSessionLifecycle(sessionLifecycle);
            shoppingListController.setSessionNavigation(this::showSessionActions);
            loader.setController(shoppingListController);
            shoppingListWorkspace = load(loader, "course shopping list");
        }
        shoppingListRefresh = () -> showShoppingList(course);
        shoppingListController.showCourseShoppingList(course.id(), course.title().value(), () -> {
            showCourses();
            courseWorkspaceController.showCourse(course.id());
        });
        showContent(coursesButton, shoppingListWorkspace);
    }

    private Node embeddedSessionPlanning(UUID courseId, UUID sessionId, boolean participants) {
        if (embeddedPlanningWorkspace == null) {
            var loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource(PLANNING_WORKSPACE)));
            embeddedPlanningController = new PlanningWorkspaceController(user, browseCourses, browseChefPlanning);
            embeddedPlanningController.setSessionLifecycle(sessionLifecycle);
            loader.setController(embeddedPlanningController);
            embeddedPlanningWorkspace = load(loader, "session planning workspace");
        }
        embeddedPlanningController.showEmbeddedSession(courseId, sessionId, participants);
        return embeddedPlanningWorkspace;
    }

    @FXML
    private void showMonthlyReport() {
        if (planningWorkspace == null) planningWorkspace = loadPlanningWorkspace();
        planningWorkspaceController.showMonthlyReport();
        showContent(monthlyReportButton, planningWorkspace);
    }

    @FXML
    private void logout() {
        logoutAction.run();
    }

    public java.util.stream.Stream<it.uninafoodlab.presentation.common.WorkspaceState> workspaces() {
        return java.util.stream.Stream.<it.uninafoodlab.presentation.common.WorkspaceState>of(
                courseWorkspaceController, catalogWorkspaceController, notificationWorkspaceController,
                dashboardWorkspaceController, planningWorkspaceController,
                shoppingListController, embeddedPlanningController)
                .filter(Objects::nonNull);
    }

    private Node loadCourseWorkspace() {
        var resource = Objects.requireNonNull(getClass().getResource(COURSE_WORKSPACE),
                "Missing course workspace");
        var loader = new FXMLLoader(resource);
        courseWorkspaceController = new CourseWorkspaceController(
                user,
                browseCatalog,
                createCategory,
                createCourse,
                browseCourses,
                assignRecipeToSession,
                manageCourseSchedule);
        courseWorkspaceController.setCommunicationAction(this::composeForCourse);
        courseWorkspaceController.setShoppingListAction(this::showShoppingList);
        courseWorkspaceController.setSessionPlanningAction(this::embeddedSessionPlanning);
        courseWorkspaceController.setRecipeCatalog(manageRecipeCatalog);
        courseWorkspaceController.setSessionLifecycle(sessionLifecycle);
        loader.setController(courseWorkspaceController);
        try {
            return loader.load();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load course workspace", exception);
        }
    }

    private Node loadDashboardWorkspace() {
        var loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource(DASHBOARD_WORKSPACE), "Missing dashboard workspace"));
        dashboardWorkspaceController = new DashboardWorkspaceController(
                user, browseCourses,
                this::showCourses, this::showSessionActions,
                this::showRecipeAssignment);
        dashboardWorkspaceController.setCreateCourseAction(() -> {
            showCourses();
            courseWorkspaceController.showCourseCreation();
        });
        loader.setController(dashboardWorkspaceController);
        return load(loader, "dashboard workspace");
    }

    private Node loadNotificationWorkspace() {
        var loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource(NOTIFICATION_WORKSPACE), "Missing notification workspace"));
        notificationWorkspaceController = new NotificationWorkspaceController(
                user, browseNotifications, manageNotifications, browseCourses);
        loader.setController(notificationWorkspaceController);
        return load(loader, "notification workspace");
    }

    private Node loadPlanningWorkspace() {
        var loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource(PLANNING_WORKSPACE), "Missing planning workspace"));
        planningWorkspaceController = new PlanningWorkspaceController(user, browseCourses, browseChefPlanning);
        planningWorkspaceController.setSessionLifecycle(sessionLifecycle);
        planningWorkspaceController.setSessionNavigation(this::showSessionActions);
        loader.setController(planningWorkspaceController);
        return load(loader, "planning workspace");
    }

    private static Node load(FXMLLoader loader, String description) {
        try {
            return loader.load();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load " + description, exception);
        }
    }

    private void showContent(Button selected, Node content) {
        activeSection = selected;
        if (notificationWorkspaceController != null) {
            if (content == notificationWorkspace) {
                notificationWorkspaceController.activate();
            } else {
                notificationWorkspaceController.deactivate();
            }
        }
        navigation.show(selected, content);
    }
}
