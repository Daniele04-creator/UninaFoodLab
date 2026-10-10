package it.uninafoodlab.presentation.shell;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.application.course.*;
import it.uninafoodlab.application.notification.*;
import it.uninafoodlab.application.planning.*;
import it.uninafoodlab.application.schedule.*;
import it.uninafoodlab.domain.course.*;
import it.uninafoodlab.domain.identity.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.TraversalDirection;
import javafx.scene.control.*;
import javafx.stage.Stage;

public final class SessionNavigationUiSmoke {
    private static final List<UUID> requirementRequests = new CopyOnWriteArrayList<>();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID OTHER_COURSE_ID = UUID.randomUUID();
    private static final UUID SECOND_PRACTICAL_ID = UUID.randomUUID();
    private static final UUID PAST_SESSION_ID = UUID.randomUUID();
    private static final UUID CANCELLED_SESSION_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID ONLINE_SESSION_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(2);

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        Stage[] stage = new Stage[1];
        try {
            var chef = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("smoke.chef"), "Chef sintetico", Role.CHEF);
            var courses = repository(CourseRepository.class);
            var catalog = repository(CatalogRepository.class);
            var notices = repository(NotificationRepository.class);
            var logoutCalls = new java.util.concurrent.atomic.AtomicInteger();
            var accountCalls = new java.util.concurrent.atomic.AtomicInteger();
            var shell = new AppShellController(chef, new BrowseCatalog(catalog), new CreateCategory(catalog),
                    new CreateCourse(courses), new BrowseCourses(courses), new AssignRecipeToSession(courses),
                    new BrowseNotifications(notices), new ManageNotifications(notices),
                    new ManageCourseSchedule(repository(ScheduleRepository.class)),
                    new BrowseChefPlanning(repository(ChefPlanningRepository.class)),
                    new ManageRecipeCatalog(repository(RecipeCatalogManagementRepository.class)), logoutCalls::incrementAndGet);
            shell.setAccountAction(accountCalls::incrementAndGet);
            fx(() -> {
                var loader = new FXMLLoader(SessionNavigationUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/shell/app-shell.fxml"));
                loader.setController(shell);
                Parent root = loader.load();
                stage[0] = new Stage();
                stage[0].setScene(new Scene(root, 1000, 600));
                stage[0].getScene().getStylesheets().add(SessionNavigationUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                stage[0].show();
                stage[0].toFront();
                stage[0].requestFocus();
                return null;
            });
            await(() -> stage[0].isFocused());
            var agenda = fx(() -> field(shell, "dashboardWorkspaceController"));
            await(() -> !node(agenda, "progressIndicator").isVisible());
            if (List.of(args).contains("exit")) {
                openPlanningWorkspaces(shell);
                verifyPlanningExitState(shell);
                System.out.println("PASS: all three planning workspaces block exit while printing and release it after cancellation");
                return;
            }
            var brand = fx(() -> (Label) field(shell, "brandTitle"));
            check(fx(() -> !(brand.getParent() instanceof Button) && !brand.isFocusTraversable()),
                    "The static brand must not be clickable or a keyboard tab stop");
            var profileButton = fx(() -> (Button) stage[0].getScene().getRoot().lookup(".profile-button"));
            check(fx(() -> profileButton.lookup(".lab-avatar") == null
                            && profileButton.lookup(".profile-role") == null
                            && profileButton.lookup(".profile-chevron") != null),
                    "Profile entry shows the account name and chevron without avatar or repeated role");
            focusByKeyboard(stage[0], profileButton);
            check(fx(() -> profileButton.isFocused() && profileButton.isFocusVisible()),
                    "A generic Button shows visible focus after keyboard traversal");
            var keyboardMenu = fx(() -> {
                profileButton.fire();
                return javafx.stage.Window.getWindows().stream()
                        .filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                        .filter(ContextMenu::isShowing).findFirst().orElseThrow();
            });
            await(keyboardMenu::isFocused);
            fx(() -> {
                javafx.event.Event.fireEvent(keyboardMenu.getSkin().getNode(),
                        new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                                "", "", javafx.scene.input.KeyCode.ESCAPE, false, false, false, false));
                return null;
            });
            await(() -> !keyboardMenu.isShowing());
            check(fx(() -> profileButton.isFocused() && profileButton.isFocusVisible()),
                    "Escape returns keyboard-visible focus to the profile button");
            fx(() -> { profileButton.requestFocus(); return null; });
            check(fx(() -> profileButton.isFocused() && !profileButton.isFocusVisible()),
                    "A generic Button does not request a visible focus ring after pointer-style focus");
            var pointerMenu = fx(() -> {
                profileButton.fire();
                return javafx.stage.Window.getWindows().stream()
                        .filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                        .filter(ContextMenu::isShowing).findFirst().orElseThrow();
            });
            fx(() -> { pointerMenu.hide(); return null; });
            check(fx(() -> profileButton.isFocused() && !profileButton.isFocusVisible()),
                    "Pointer-style menu dismissal does not create a keyboard focus ring");
            fx(() -> {
                stage[0].getScene().getRoot().applyCss();
                stage[0].getScene().getRoot().layout();
                checkHeaderLayout(shell);
                for (var name : List.of("dashboardButton", "coursesButton", "catalogButton",
                        "notificationsButton", "monthlyReportButton")) {
                    var nav = button(shell, name);
                    var bounds = nav.localToScene(nav.getBoundsInLocal());
                    check(bounds.getMinX() >= 0 && bounds.getMaxX() <= stage[0].getScene().getWidth(),
                            "All navigation actions fit at the minimum width: " + name);
                    check(bounds.getMaxY() <= 130, "Task navigation stays above the full-width workspace");
                }
                check(((javafx.scene.layout.StackPane)field(shell, "contentHost")).getWidth() >= 980,
                        "Workbench gives the content the full viewport width");
                var accelerators = stage[0].getScene().getAccelerators();
                check(!accelerators.containsKey(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F1)),
                        "F1 must not register an application shortcut");
                for (var key : List.of(javafx.scene.input.KeyCode.DIGIT1, javafx.scene.input.KeyCode.DIGIT2,
                        javafx.scene.input.KeyCode.DIGIT3, javafx.scene.input.KeyCode.DIGIT4,
                        javafx.scene.input.KeyCode.DIGIT5, javafx.scene.input.KeyCode.DIGIT6)) {
                    check(!accelerators.containsKey(new javafx.scene.input.KeyCodeCombination(key,
                            javafx.scene.input.KeyCombination.ALT_DOWN)), "Section shortcuts must be removed");
                }
                check(stage[0].getScene().getRoot().lookup("#helpButton") == null,
                        "Shell must not expose the removed quick guide");
                return null;
            });
            fx(() -> { invoke(agenda, "selectDay", new Class<?>[]{LocalDate.class}, DAY); return null; });
            await(() -> !((javafx.scene.layout.VBox) field(agenda, "daySessions")).getChildren().isEmpty());
            var selectedDayLabel = fx(() -> (Label) field(agenda, "selectedDayLabel"));
            String dayLabel = fx(selectedDayLabel::getText);
            captureShell(stage[0], 1440);
            captureShell(stage[0], 1000);
            fx(() -> { invoke(shell, "openProfile", new Class<?>[]{}); return null; });
            await(() -> javafx.stage.Window.getWindows().stream()
                    .filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                    .anyMatch(menu -> menu.isShowing() && menu.getScene().getWidth() > 0));
            fx(() -> {
                var menu = javafx.stage.Window.getWindows().stream()
                        .filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                        .filter(ContextMenu::isShowing).findFirst().orElseThrow();
                check(menu.getItems().stream().map(MenuItem::getText)
                                .filter(Objects::nonNull).toList()
                                .containsAll(List.of("Cambia password", "Esci dall’account")),
                        "The profile menu preserves password and logout actions");
                check(menu.isAutoHide() && menu.isHideOnEscape(),
                        "The profile menu closes after outside clicks and Escape");
                check(menu.getItems().size() == 3,
                        "The compact profile has only identity and two actions, without a separator");
                check(menu.getSkin().getNode().getLayoutBounds().getWidth() <= 228,
                        "The profile menu retains the compact mockup width");
                var logoutRow = menu.getScene().getRoot().lookupAll(".menu-item .label").stream()
                        .filter(Label.class::isInstance).map(Label.class::cast)
                        .filter(label -> "Esci dall’account".equals(label.getText())).findFirst().orElseThrow();
                check(javafx.scene.paint.Color.web("#173946").equals(logoutRow.getTextFill()),
                        "Profile actions share the same readable ink color as the mockup");
                check(menu.getScene().getRoot().lookup(".profile-menu-identity") != null,
                        "The compact menu identifies the signed-in account");
                var identity = (javafx.scene.layout.VBox) menu.getScene().getRoot()
                        .lookup(".profile-menu-identity");
                check(identity.getChildren().size() == 1
                                && identity.getChildren().getFirst() instanceof Label username
                                && username.getText().equals(chef.username().value()),
                        "The menu identifies the username once without repeated display name, role or dot");
                check(menu.getItems().getFirst() instanceof CustomMenuItem
                                && menu.getItems().getFirst().isDisable(),
                        "The identity header is informational and cannot be selected as an action");
                var header = (javafx.scene.layout.Region) menu.getScene().getRoot().lookup(".profile-menu-header");
                check(header != null, "The identity header has a styleable menu row");
                header.applyCss();
                check(menu.getScene().getStylesheets().stream().anyMatch(sheet -> sheet.endsWith("/profile.css")),
                        "The profile popup retains its stylesheet after attaching to the owner scene");
                check(header.getOpacity() > 0.99,
                        "The informational account header remains fully legible when disabled");
                check(header.getBackground() != null && !header.getBackground().getFills().isEmpty()
                                && javafx.scene.paint.Color.WHITE.equals(
                                        header.getBackground().getFills().getFirst().getFill()),
                        "The account header uses the same white surface as the menu in the actual popup skin");
                it.uninafoodlab.presentation.FxSnapshot.save(menu.getScene().getRoot(),
                        "target/ui-polish/profile-menu.png");
                menu.getItems().stream().filter(item -> "Cambia password".equals(item.getText()))
                        .findFirst().orElseThrow().fire();
                check(accountCalls.get() == 1 && !menu.isShowing(),
                        "Password action closes the menu and opens account management");
                invoke(shell, "openProfile", new Class<?>[]{});
                var reopened = javafx.stage.Window.getWindows().stream()
                        .filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                        .filter(ContextMenu::isShowing).findFirst().orElseThrow();
                reopened.getItems().stream().filter(item -> "Esci dall’account".equals(item.getText()))
                        .findFirst().orElseThrow().fire();
                check(logoutCalls.get() == 1 && !reopened.isShowing(),
                        "Logout action closes the menu and signs out");
                return null;
            });
            fx(() -> { ((Button) node(agenda, "daySessions").lookup(".agenda-open-session")).fire(); return null; });
            awaitCourse(shell);
            check(fx(() -> ((Label) content(shell).lookup("#sessionDetailTitle")).getText().equals("Corso sintetico")
                    && ((Label) content(shell).lookup("#sessionDetailContext")).getText().contains("Sessione 1")
                    && ((Label) content(shell).lookup("#sessionDetailContext")).getText().contains("18:00–20:00")),
                    "Session opened from Agenda names its real course and shows the session and time interval");
            fx(() -> {
                it.uninafoodlab.presentation.FxSnapshot.save(stage[0].getScene().getRoot(),
                        "target/ui-polish/chef-session-detail.png");
                return null;
            });
            check(fx(() -> selected(shell, "dashboardButton")), "Session opened from Agenda must keep Agenda selected");
            fireReturn(shell, "Torna all’agenda");
            await(() -> !node(agenda, "progressIndicator").isVisible());
            check(fx(() -> selected(shell, "dashboardButton") && content(shell) == field(shell, "dashboardWorkspace")
                    && dayLabel.equals(((Label) field(agenda, "selectedDayLabel")).getText())),
                    "Return must restore the Agenda workspace and selected future day");

            fx(() -> {
                invoke(shell, "showRecipeAssignment", new Class<?>[]{UUID.class, UUID.class}, COURSE_ID, SESSION_ID);
                return null;
            });
            awaitCourse(shell);
            check(fx(() -> selected(shell, "dashboardButton")
                    && ((ToggleButton) content(shell).lookup("#sessionMenuTab")).isSelected()),
                    "Recipe navigation must open the session menu and preserve the Agenda origin");
            fireReturn(shell, "Torna all’agenda");
            await(() -> !node(agenda, "progressIndicator").isVisible());

            var coursesButton = fx(() -> button(shell, "coursesButton"));
            focusByKeyboard(stage[0], coursesButton);
            check(fx(() -> coursesButton.isFocused() && coursesButton.isFocusVisible()),
                    "Corsi receives visible focus through keyboard traversal");
            fx(() -> { coursesButton.fire(); return null; });
            check(fx(() -> coursesButton.isFocused() && coursesButton.isFocusVisible()),
                    "Changing section preserves keyboard-visible focus on Corsi");
            awaitCourse(shell);
            fx(() -> {
                check(content(shell).getScene().getRoot().lookup("#reportsButton") == null,
                        "Fabbisogni must not duplicate Agenda in primary navigation");
                requirementRequests.clear();
                ((Button) content(shell).lookup("#shoppingListButton")).fire();
                return null;
            });
            var planning = fx(() -> field(shell, "shoppingListController"));
            await(() -> !node(planning, "progressIndicator").isVisible());
            check(fx(() -> selected(shell, "coursesButton")
                    && ((Label) field(planning, "pageTitle")).getText().equals("Lista della spesa")
                    && ((Label) field(planning, "pageDescription")).getText().contains("Corso sintetico")
                    && !node(planning, "sessionFilters").isVisible()),
                    "Shopping list keeps Corsi selected and the course context without redundant filters");
            check(new HashSet<>(requirementRequests).equals(Set.of(SESSION_ID, SECOND_PRACTICAL_ID))
                    && requirementRequests.size() == 2,
                    "Shopping list loads only future practical sessions of the selected course");
            check(fx(() -> ((IngredientRequirement) ((TableView<?>) field(planning, "requirementTable"))
                            .getItems().getFirst()).requiredQuantity().compareTo(new java.math.BigDecimal("200")) == 0),
                    "Shopping list sums the ingredient quantities across future practical sessions");
            fx(() -> {
                var warning = (javafx.scene.layout.VBox) field(planning, "missingMenusBox");
                warning.getChildren().stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).findFirst().orElseThrow().fire();
                return null;
            });
            awaitCourse(shell);
            fireReturn(shell, "Torna alla lista della spesa");
            await(() -> !node(planning, "progressIndicator").isVisible());
            check(fx(() -> content(shell) == field(shell, "shoppingListWorkspace")),
                    "Missing-menu navigation returns to the same refreshed shopping list");
            fx(() -> { button(planning, "courseReturnButton").fire(); return null; });
            check(fx(() -> content(shell) == field(shell, "courseWorkspace")
                    && ((CourseSummary) ((ListView<?>) content(shell).lookup("#courseList"))
                            .getSelectionModel().getSelectedItem()).id().equals(COURSE_ID)),
                    "Return restores the same course");
            check(fx(() -> !node(field(shell, "courseWorkspaceController"), "sessionDetailHeader").isVisible()),
                    "Global Corsi entry must restore the program instead of the previous session detail");
            fx(() -> {
                var list = (ListView<CourseSummary>) content(shell).lookup("#courseList");
                list.getSelectionModel().select(list.getItems().stream()
                        .filter(course -> course.id().equals(OTHER_COURSE_ID)).findFirst().orElseThrow());
                return null;
            });
            awaitCourse(shell);
            fx(() -> {
                requirementRequests.clear();
                ((Button) content(shell).lookup("#shoppingListButton")).fire();
                return null;
            });
            await(() -> !node(planning, "progressIndicator").isVisible());
            check(fx(() -> ((Label) field(planning, "pageDescription")).getText().equals("Solo teoria")
                    && ((TableView<?>) field(planning, "requirementTable")).getItems().isEmpty()
                    && ((Label) ((TableView<?>) field(planning, "requirementTable")).getPlaceholder())
                            .getText().contains("Nessuna sessione pratica futura")),
                    "An online-only second course clears the previous amounts and explains the empty list");
            check(requirementRequests.isEmpty(), "No requirements request for an online-only course");
            fx(() -> { button(planning, "courseReturnButton").fire(); return null; });
            fx(() -> { invoke(shell, "showSessionActions", new Class<?>[]{UUID.class, UUID.class}, COURSE_ID, SESSION_ID); return null; });
            awaitCourse(shell);
            fireReturn(shell, "Torna al programma");
            check(fx(() -> selected(shell, "coursesButton") && content(shell) == field(shell, "courseWorkspace")
                    && !node(field(shell, "courseWorkspaceController"), "sessionDetailHeader").isVisible()),
                    "Normal Corsi entry must clear the previous origin and return to its program");
            fx(() -> {
                invoke(shell, "embeddedSessionPlanning", new Class<?>[]{UUID.class, UUID.class, boolean.class},
                        COURSE_ID, ONLINE_SESSION_ID, true);
                return null;
            });
            var embedded = fx(() -> field(shell, "embeddedPlanningController"));
            await(() -> !node(embedded, "progressIndicator").isVisible());
            check(fx(() -> !((TableColumn<?, ?>) field(embedded, "attendanceColumn")).isVisible()),
                    "Online session participants must use course enrollment, without practical attendance");
            fx(() -> {
                invoke(shell, "embeddedSessionPlanning", new Class<?>[]{UUID.class, UUID.class, boolean.class},
                        COURSE_ID, SESSION_ID, true);
                return null;
            });
            await(() -> !node(embedded, "progressIndicator").isVisible());
            var attendanceColumn = fx(() -> (TableColumn<?, ?>) field(embedded, "attendanceColumn"));
            check(fx(attendanceColumn::isVisible),
                    "Switching back to a practical session must restore its attendance column");
            System.out.println("PASS: shell/course shopping list aggregates future practical sessions, preserves context and return, clears another course; Agenda and embedded participants remain usable");
        } finally {
            fx(() -> { if (stage[0] != null) stage[0].close(); return null; });
            Platform.exit();
        }
    }

    private static void openPlanningWorkspaces(AppShellController shell) throws Exception {
        var course = new BrowseCourses(repository(CourseRepository.class)).courses(
                new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        Username.of("smoke.chef"), "Chef sintetico", Role.CHEF), Optional.empty()).getFirst();
        fx(() -> {
            invoke(shell, "showMonthlyReport", new Class<?>[]{});
            invoke(shell, "showShoppingList", new Class<?>[]{CourseSummary.class}, course);
            invoke(shell, "embeddedSessionPlanning", new Class<?>[]{UUID.class, UUID.class, boolean.class},
                    COURSE_ID, SESSION_ID, false);
            return null;
        });
    }

    private static void verifyPlanningExitState(AppShellController shell) throws Exception {
        for (var name : List.of("planningWorkspaceController", "shoppingListController", "embeddedPlanningController")) {
            await(() -> !node(field(shell, name), "progressIndicator").isVisible());
        }
        for (var name : List.of("planningWorkspaceController", "shoppingListController", "embeddedPlanningController")) {
            var workspace = fx(() -> field(shell, name));
            await(() -> !node(workspace, "progressIndicator").isVisible());
            fx(() -> {
                check(shell.workspaces().anyMatch(item -> item == workspace),
                        "Exit protection must include " + name);
                check(!shell.workspaces().anyMatch(it.uninafoodlab.presentation.common.WorkspaceState::isBusy),
                        "Completed reads must allow exit");
                var progress = (it.uninafoodlab.presentation.planning.ShoppingListPrinter.PrintingProgress) field(workspace, "feedback");
                progress.printingStarted();
                check(shell.workspaces().anyMatch(it.uninafoodlab.presentation.common.WorkspaceState::isBusy),
                        "Background printing must block close and logout for " + name);
                it.uninafoodlab.presentation.navigation.UnsavedChangesUiSmoke.verifyWorkspaceExit(
                        () -> shell.workspaces().anyMatch(it.uninafoodlab.presentation.common.WorkspaceState::isBusy), false);
                check(!shell.workspaces().anyMatch(it.uninafoodlab.presentation.common.WorkspaceState::hasUnsavedChanges),
                        "Read-only planning must not require draft confirmation");
                progress.printingCancelled();
                check(!shell.workspaces().anyMatch(it.uninafoodlab.presentation.common.WorkspaceState::isBusy),
                        "Cancelled printing must release exit protection");
                it.uninafoodlab.presentation.navigation.UnsavedChangesUiSmoke.verifyWorkspaceExit(
                        () -> shell.workspaces().anyMatch(it.uninafoodlab.presentation.common.WorkspaceState::isBusy), true);
                return null;
            });
        }
    }

    private static void captureShell(Stage stage, int width) throws Exception {
        fx(() -> {
            stage.setWidth(width + stage.getWidth() - stage.getScene().getWidth());
            stage.setHeight(900 + stage.getHeight() - stage.getScene().getHeight());
            return null;
        });
        await(() -> Math.abs(stage.getScene().getWidth() - width) < 1);
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            for (var section : List.of("dashboardButton", "coursesButton", "catalogButton",
                    "notificationsButton", "monthlyReportButton")) {
                var destination = (Button) stage.getScene().lookup("#" + section);
                var renderedText = (javafx.scene.text.Text) destination.lookup(".text");
                check(renderedText.getText().equals(destination.getText()),
                        "Navigation labels must remain complete at " + width + "px: " + destination.getText());
            }
            it.uninafoodlab.presentation.FxSnapshot.save(stage.getScene().getRoot(),
                    "target/native-match/shell-" + width + ".png");
            return null;
        });
    }

    private static void checkHeaderLayout(Object shell) {
        var root = (javafx.scene.layout.BorderPane) field(shell, "shellRoot");
        var header = (javafx.scene.layout.Region) root.getTop();
        var brand = (Label) field(shell, "brandTitle");
        var navigation = button(shell, "dashboardButton");
        header.resize(1440, 200);
        header.applyCss();
        header.resize(1440, header.prefHeight(1440));
        header.layout();
        var brandBounds = brand.localToScene(brand.getBoundsInLocal());
        var navBounds = navigation.localToScene(navigation.getBoundsInLocal());
        check(navBounds.getMinY() < brandBounds.getMaxY()
                        && navBounds.getMaxY() > brandBounds.getMinY(),
                "Wide header shares one row for brand and task navigation");
        var report = button(shell, "monthlyReportButton");
        var profile = (Label) field(shell, "profileName");
        check(report.localToScene(report.getBoundsInLocal()).getMaxX()
                        < profile.localToScene(profile.getBoundsInLocal()).getMinX(),
                "Wide navigation does not overlap the Chef profile");
        header.resize(800, 200);
        header.applyCss();
        header.resize(800, header.prefHeight(800));
        header.layout();
        check(navigation.localToScene(navigation.getBoundsInLocal()).getMinY()
                        >= brand.localToScene(brand.getBoundsInLocal()).getMaxY(),
                "Narrow header moves navigation below the brand");
        root.requestLayout();
        root.layout();
    }

    private static void awaitCourse(Object shell) throws Exception {
        await(() -> field(shell, "courseWorkspaceController") != null
                && !node(field(shell, "courseWorkspaceController"), "progressIndicator").isVisible());
    }

    private static void focusByKeyboard(Stage stage, Node target) throws Exception {
        fx(() -> { stage.requestFocus(); return null; });
        await(stage::isFocused);
        fx(() -> {
            var scene = stage.getScene();
            var current = scene.getFocusOwner();
            if (current == null) current = scene.getRoot();
            for (var attempt = 0; attempt < 128; attempt++) {
                if (target.isFocused() && target.isFocusVisible()) return null;
                if (!current.requestFocusTraversal(TraversalDirection.NEXT)) break;
                current = scene.getFocusOwner();
                if (current == null) current = scene.getRoot();
            }
            throw new AssertionError("Keyboard focus traversal did not reach " + target);
        });
    }

    private static void fireReturn(Object shell, String text) throws Exception {
        fx(() -> {
            var header = (Parent) field(field(shell, "courseWorkspaceController"), "sessionDetailHeader");
            var button = descendants(header).stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(item -> text.equals(item.getText())).findFirst().orElseThrow(
                            () -> new AssertionError("Missing return action: " + text));
            check(button.isVisible() && !button.isDisabled(), "Return action must be usable: " + text);
            button.fire();
            return null;
        });
    }

    private static List<Node> descendants(Parent parent) {
        var nodes = new ArrayList<Node>();
        for (var child : parent.getChildrenUnmodifiable()) {
            nodes.add(child);
            if (child instanceof Parent nested) nodes.addAll(descendants(nested));
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (type == CourseRepository.class && method.getName().equals("findByChef")) {
                var courses = courseFixtures();
                if (args[1] instanceof UUID id) return courses.stream().filter(course -> course.id().equals(id)).findFirst();
                if (args[1] instanceof CourseFilter) {
                    var page = (it.uninafoodlab.application.common.PageRequest) args[2];
                    return new it.uninafoodlab.application.common.Page<>(
                            courses.stream().skip(page.offset()).limit(page.size()).toList(), courses.size());
                }
                return courses;
            }
            if (type == CourseRepository.class && method.getName().equals("sessions")) {
                if (args[1] instanceof List<?> ids) return ids.stream().map(UUID.class::cast)
                        .collect(java.util.stream.Collectors.toMap(id -> id, SessionNavigationUiSmoke::sessionFixtures));
                return sessionFixtures((UUID) args[1]);
            }
            if (type == ChefPlanningRepository.class && method.getName().equals("sessionPreparation")) {
                return new SessionPreparation(SESSION_ID, 0, java.math.BigDecimal.ZERO, List.of(), List.of());
            }
            if (type == ChefPlanningRepository.class && method.getName().equals("requirements")) {
                requirementRequests.add((UUID) args[1]);
                return List.of(new IngredientRequirement("Farina", MeasurementUnit.GRAM, 2, new java.math.BigDecimal("100")));
            }
            if (type == ChefPlanningRepository.class && method.getName().equals("exportSnapshots")) {
                return ((List<UUID>) args[1]).stream().map(id -> {
                    requirementRequests.add(id);
                    return new SessionPlanningSnapshot(id, DAY.atTime(18, 0), 0, "PRELIMINARY", "CAPTURED",
                            List.of(new IngredientRequirement("Farina", MeasurementUnit.GRAM, 2,
                                    new java.math.BigDecimal("100"))));
                }).toList();
            }
            if (type == ChefPlanningRepository.class && method.getName().equals("courseExport")) {
                if (OTHER_COURSE_ID.equals(args[1])) return new CoursePlanningExport(List.of(), List.of());
                var sessions = List.of(practical(SECOND_PRACTICAL_ID, DAY.plusDays(1), SessionStatus.SCHEDULED),
                        new CourseSessionSummary(SESSION_ID, 1, DAY.atTime(18, 0), SessionMode.PRACTICAL,
                                SessionStatus.SCHEDULED, 120, Optional.empty(), Optional.of("Laboratorio sintetico"),
                                Optional.of(12), Optional.of(DAY.minusDays(1).atTime(18, 0)), Optional.empty(), 0));
                var snapshots = sessions.stream().map(session -> {
                    requirementRequests.add(session.id());
                    return new SessionPlanningSnapshot(session.id(), session.scheduledAt(), 0,
                            "PRELIMINARY", "CAPTURED", List.of(new IngredientRequirement("Farina", MeasurementUnit.GRAM,
                                    2, new java.math.BigDecimal("100"))));
                }).toList();
                return new CoursePlanningExport(sessions, snapshots);
            }
            if (method.getReturnType() == List.class) return List.of();
            if (method.getReturnType() == it.uninafoodlab.application.common.Page.class) {
                return new it.uninafoodlab.application.common.Page<>(List.of(), 0);
            }
            if (method.getReturnType() == Map.class) return Map.of();
            if (method.getReturnType() == Optional.class) return Optional.empty();
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == boolean.class) return false;
            throw new AssertionError("Unexpected repository call: " + method);
        });
    }

    private static List<CourseSummary> courseFixtures() {
        return List.of(new CourseSummary(COURSE_ID, UUID.randomUUID(), "Cucina", CourseTitle.of("Corso sintetico"),
                "Preparazione in laboratorio", DAY, LocalTime.of(18, 0), 1, 2,
                Optional.of(DAY.plusDays(1).atTime(20, 0)), CourseStatus.PUBLISHED, IngredientMargin.zero()),
                new CourseSummary(OTHER_COURSE_ID, UUID.randomUUID(), "Cucina", CourseTitle.of("Solo teoria"),
                        "Corso online", DAY, LocalTime.of(18, 0), 1, 1,
                        Optional.of(DAY.plusDays(1).atTime(20, 0)), CourseStatus.PUBLISHED, IngredientMargin.zero()));
    }

    private static List<CourseSessionSummary> sessionFixtures(UUID courseId) {
        if (OTHER_COURSE_ID.equals(courseId)) return List.of(new CourseSessionSummary(
                ONLINE_SESSION_ID, 1, DAY.atTime(18, 0), SessionMode.ONLINE, SessionStatus.SCHEDULED,
                120, Optional.of(java.net.URI.create("https://example.org/lesson")),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0));
        return List.of(practical(SECOND_PRACTICAL_ID, DAY.plusDays(1), SessionStatus.SCHEDULED),
                practical(PAST_SESSION_ID, DAY.minusDays(5), SessionStatus.COMPLETED),
                practical(CANCELLED_SESSION_ID, DAY.plusDays(1), SessionStatus.CANCELLED),
                new CourseSessionSummary(SESSION_ID, 1, DAY.atTime(18, 0), SessionMode.PRACTICAL,
                        SessionStatus.SCHEDULED, 120, Optional.empty(), Optional.of("Laboratorio sintetico"),
                        Optional.of(12), Optional.of(DAY.minusDays(1).atTime(18, 0)), Optional.empty(), 0),
                new CourseSessionSummary(ONLINE_SESSION_ID, 2, DAY.plusDays(1).atTime(18, 0), SessionMode.ONLINE,
                        SessionStatus.SCHEDULED, 120, Optional.of(java.net.URI.create("https://example.org/lesson")),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0));
    }

    private static CourseSessionSummary practical(UUID id, LocalDate date, SessionStatus status) {
        return new CourseSessionSummary(id, 3, date.atTime(18, 0), SessionMode.PRACTICAL, status, 120,
                Optional.empty(), Optional.of("Laboratorio"), Optional.of(12),
                Optional.of(date.minusDays(1).atTime(18, 0)), Optional.empty(), 0);
    }

    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void invoke(Object target, String name, Class<?>[] types, Object... args) {
        try { var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(target, args); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Node node(Object target, String name) { return (Node) field(target, name); }
    private static Button button(Object target, String name) { return (Button) field(target, name); }
    private static boolean selected(Object shell, String name) {
        var sections = List.of("dashboardButton", "coursesButton", "notificationsButton",
                "catalogButton", "monthlyReportButton");
        long selectedCount = sections.stream().map(section -> button(shell, section))
                .filter(item -> item.getStyleClass().contains("navigation-button-selected")).count();
        return selectedCount == 1 && button(shell, name).getGraphic() != null
                && button(shell, name).getStyleClass().contains("navigation-button-selected");
    }
    private static Node content(Object shell) { return ((javafx.scene.layout.StackPane) field(shell, "contentHost")).getChildren().getFirst(); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        it.uninafoodlab.presentation.FxAwait.until(condition::getAsBoolean, 15);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
