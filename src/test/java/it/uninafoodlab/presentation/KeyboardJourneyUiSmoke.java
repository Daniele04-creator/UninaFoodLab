package it.uninafoodlab.presentation;

import it.uninafoodlab.application.account.*;
import it.uninafoodlab.application.auth.AuthenticateUser;
import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.*;
import it.uninafoodlab.application.enrollment.*;
import it.uninafoodlab.application.notification.*;
import it.uninafoodlab.application.planning.*;
import it.uninafoodlab.application.schedule.*;
import it.uninafoodlab.application.security.PasswordHasher;
import it.uninafoodlab.domain.identity.*;
import it.uninafoodlab.domain.course.*;
import it.uninafoodlab.presentation.account.AccountDialogs;
import it.uninafoodlab.presentation.catalog.CatalogWorkspaceController;
import it.uninafoodlab.presentation.course.CourseWorkspaceController;
import it.uninafoodlab.presentation.login.LoginController;
import it.uninafoodlab.presentation.notification.NotificationWorkspaceController;
import it.uninafoodlab.presentation.participant.ParticipantWorkspaceController;
import it.uninafoodlab.presentation.shell.AppShellController;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.robot.Robot;
import javafx.stage.*;


public final class KeyboardJourneyUiSmoke {
    private static final int MAX_TABS = 120;
    private static final List<String> evidence = new ArrayList<>();
    private static Robot robot;
    private static boolean diagnose;
    private static final List<String> inputTrace = new CopyOnWriteArrayList<>();
    private static Path activationFile;
    private static boolean activationCheckpointUsed;

    public static void main(String[] args) throws Exception {
        var activationArguments = Arrays.stream(args).filter(argument -> argument.startsWith("--activation-file=")).toList();
        check(activationArguments.size() <= 1, "Specify at most one activation file");
        if (!activationArguments.isEmpty()) {
            String rawPath = activationArguments.getFirst().substring("--activation-file=".length());
            check(!rawPath.isBlank(), "Activation file path must not be empty");
            activationFile = Path.of(rawPath).toAbsolutePath().normalize();
            check(!Files.exists(activationFile), "Activation file must not exist before the keyboard gate starts: " + activationFile);
        }
        Platform.startup(() -> Platform.setImplicitExit(false));
        robot = fx(Robot::new);
        diagnose = Arrays.asList(args).contains("--diagnose");
        try {
            for (int width : new int[]{1000, 1440}) {
                int height = width == 1000 ? 600 : 900;
                if (Arrays.asList(args).contains("--chef-only")) { chef(width, height); continue; }
                login(width, height);
                if (Arrays.asList(args).contains("--login-only")) continue;
                chef(width, height);
                participant(width, height);
                catalog(width, height);
                notification(width, height);
            }
            System.out.println("PASS KeyboardJourneyUiSmoke: OS keyboard events, focus, clipping and account dialogs; "
                    + evidence.size() + " bounded journeys at 1000x600 and 1440x900");
        } catch (Exception | Error failure) {
            Throwable cause = failure;
            while (cause.getCause() != null) cause = cause.getCause();
            evidence.add("FAIL " + cause);
            throw failure;
        } finally {
            String reportName = Arrays.asList(args).contains("--login-only") ? "keyboard-login-handshake.txt"
                    : Arrays.asList(args).contains("--chef-only") ? "keyboard-chef-handshake.txt" : "keyboard-journeys.txt";
            Path report = Path.of("target/ui-polish/" + reportName);
            Files.createDirectories(report.getParent());
            Files.write(report, evidence);
            if (diagnose) Files.write(Path.of("target/ui-polish/keyboard-input-trace.txt"), inputTrace);
            fx(() -> { for (var window : List.copyOf(Window.getWindows())) window.hide(); return null; });
            Platform.exit();
        }
    }

    private static void login(int width, int height) throws Exception {
        var controller = new LoginController(new AuthenticateUser((name, password) -> Optional.empty()),
                new AccountLifecycle(repository(AccountLifecycleRepository.class), repository(PasswordHasher.class)), user -> {});
        var stage = show(controller, "login/login.fxml", width, height);
        try {
            journey(stage, "login-hidden-" + width, nodes(stage, "usernameField", "passwordField",
                    "revealPasswordButton", "submitButton", "registerButton"));
            fx(() -> { ((ToggleButton) stage.getScene().lookup("#revealPasswordButton")).fire(); return null; });
            journey(stage, "login-revealed-" + width, nodes(stage, "usernameField", "visiblePasswordField",
                    "revealPasswordButton", "submitButton", "registerButton"));
            dialog(stage, true, width);
            dialog(stage, false, width);
        } finally { close(stage); }
    }

    private static void chef(int width, int height) throws Exception {
        var courses = repository(CourseRepository.class);
        var catalog = repository(CatalogRepository.class);
        var notices = repository(NotificationRepository.class);
        var controller = new AppShellController(user(Role.CHEF), new BrowseCatalog(catalog), new CreateCategory(catalog),
                new CreateCourse(courses), new BrowseCourses(courses), new AssignRecipeToSession(courses),
                new BrowseNotifications(notices), new ManageNotifications(notices),
                new ManageCourseSchedule(repository(ScheduleRepository.class)),
                new BrowseChefPlanning(repository(ChefPlanningRepository.class)),
                new ManageRecipeCatalog(repository(RecipeCatalogManagementRepository.class)), () -> {});
        var stage = show(controller, "shell/app-shell.fxml", width, height);
        try {
            await(() -> !((Node) field(field(controller, "dashboardWorkspaceController"), "progressIndicator")).isVisible());
            journey(stage, "chef-navigation-" + width, nodes(stage, "dashboardButton", "coursesButton",
                    "catalogButton", "notificationsButton", "monthlyReportButton", "profileButton"));
            profile(stage, stage.getScene().lookup("#profileButton"), "chef-profile-" + width);
            fx(() -> { ((Button) stage.getScene().lookup("#coursesButton")).fire(); return null; });
            await(() -> field(controller, "courseWorkspaceController") != null
                    && !((CourseWorkspaceController) field(controller, "courseWorkspaceController")).isBusy());
            check(fx(() -> ((ListView<?>) stage.getScene().lookup("#courseList")).getItems().size() == 1),
                    "Chef courses must render the synthetic page before the creation journey");
            fx(() -> { ((CourseWorkspaceController) field(controller, "courseWorkspaceController")).showCourseCreation(); return null; });
            journey(stage, "course-form-" + width, nodes(stage, "titleField", "descriptionField", "categoryField",
                    "createCourseReturnButton", "cancelCreateCourseButton", "createButton"));
            multiline(stage, (TextArea) stage.getScene().lookup("#descriptionField"));
        } finally { close(stage); }
    }

    private static void participant(int width, int height) throws Exception {
        var enrollments = repository(EnrollmentRepository.class);
        var notices = repository(NotificationRepository.class);
        var controller = new ParticipantWorkspaceController(user(Role.PARTICIPANT),
                new BrowsePublishedCourses(repository(CourseRepository.class)), new BrowseCatalog(repository(CatalogRepository.class)),
                new BrowseEnrollments(enrollments), new ManageEnrollment(enrollments),
                new ManagePracticalAttendance(enrollments), new BrowseNotifications(notices), new ManageNotifications(notices));
        var stage = show(controller, "participant/participant-workspace.fxml", width, height);
        try {
            await(() -> !controller.isBusy());
            journey(stage, "participant-navigation-" + width, nodes(stage, "browseTab", "mineTab", "notificationsTab",
                    "profileMenu", "courseSearch", "categoryFilter", "clearFiltersButton"));
            profile(stage, stage.getScene().lookup("#profileMenu"), "participant-profile-" + width);
        } finally { close(stage); }
    }

    private static void catalog(int width, int height) throws Exception {
        var controller = new CatalogWorkspaceController(user(Role.CHEF), new BrowseCatalog(repository(CatalogRepository.class)),
                new ManageRecipeCatalog(repository(RecipeCatalogManagementRepository.class)));
        controller.setCreateCategory(new CreateCategory(repository(CatalogRepository.class)));
        var stage = show(controller, "catalog/catalog-workspace.fxml", width, height);
        try {
            await(() -> !controller.isBusy());
            fx(() -> { ((Button) stage.getScene().lookup("#newRecipeButton")).fire(); return null; });
            journey(stage, "recipe-form-" + width, nodes(stage, "recipeName", "recipeDescription", "categoryChoice",
                    "lineIngredientSearch", "quantity", "saveRecipeButton", "backToRecipesButton"));
            multiline(stage, (TextArea) stage.getScene().lookup("#recipeDescription"));
            fx(() -> { ((Button) stage.getScene().lookup("#backToRecipesButton")).fire();
                ((ToggleButton) stage.getScene().lookup("#ingredientsTab")).fire();
                ((Button) stage.getScene().lookup("#newIngredientButton")).fire(); return null; });
            journey(stage, "ingredient-form-" + width, nodes(stage, "ingredientName", "ingredientUnit",
                    "saveIngredientButton", "cancelIngredientButton"));
        } finally { close(stage); }
    }

    private static void notification(int width, int height) throws Exception {
        var notices = repository(NotificationRepository.class);
        var controller = new NotificationWorkspaceController(user(Role.CHEF), new BrowseNotifications(notices),
                new ManageNotifications(notices), new BrowseCourses(repository(CourseRepository.class)));
        var stage = show(controller, "notification/notification-workspace.fxml", width, height);
        try {
            fx(() -> { controller.activate(); return null; });
            await(() -> !controller.isBusy() && ((ComboBox<?>) stage.getScene().lookup("#courseTargetField")).getItems().size() > 1);
            fx(() -> {
                ((Button) stage.getScene().lookup("#composeButton")).fire();
                ((ComboBox<?>) stage.getScene().lookup("#courseTargetField")).getSelectionModel().selectFirst();
                ((TextField) stage.getScene().lookup("#titleField")).setText("Materiali per il laboratorio");
                ((TextArea) stage.getScene().lookup("#messageField")).setText("Porta il grembiule per la sessione pratica.");
                return null;
            });
            await(() -> !stage.getScene().lookup("#sendButton").isDisabled());
            journey(stage, "notification-form-" + width, nodes(stage, "courseTargetField", "titleField", "messageField",
                    "backButton", "sendButton"));
            multiline(stage, (TextArea) stage.getScene().lookup("#messageField"));
        } finally { fx(() -> { controller.dispose(); return null; }); close(stage); }
    }

    private static void multiline(Stage stage, TextArea field) throws Exception {
        reach(stage, field);
        var before = fx(field::getText);
        press(stage, KeyCode.ENTER, false);
        activate(stage);
        check(fx(() -> field.getText().length() == before.length() + 1 && field.getText().contains("\n")),
                "Enter must retain native multiline editing in " + describe(field));
        fx(() -> { field.setText(before); return null; });
    }

    private static void dialog(Stage owner, boolean registration, int width) throws Exception {
        Platform.runLater(() -> {
            if (registration) AccountDialogs.showRegistration(owner);
            else AccountDialogs.showPasswordChange(owner);
        });
        await(() -> Window.getWindows().stream().anyMatch(window -> window.isShowing()
                && window.getScene().getRoot() instanceof DialogPane));
        var dialogStage = fx(() -> (Stage) Window.getWindows().stream().filter(window -> window.isShowing()
                && window.getScene().getRoot() instanceof DialogPane).findFirst().orElseThrow());
        try {
            var targets = fx(() -> descendants(dialogStage.getScene().getRoot()).stream()
                    .filter(node -> node instanceof TextInputControl || node instanceof ComboBox<?> || node instanceof Button)
                    .filter(KeyboardJourneyUiSmoke::usable).toList());
            check(targets.size() >= (registration ? 7 : 5), "All account inputs and actions exist");
            journey(dialogStage, (registration ? "registration-" : "password-change-") + width, targets);
            press(dialogStage, KeyCode.ESCAPE, false);
            await(() -> !dialogStage.isShowing());
        } finally { if (fx(dialogStage::isShowing)) close(dialogStage); }
        await(owner::isFocused);
    }

    private static void profile(Stage stage, Node target, String name) throws Exception {
        reach(stage, target);
        press(stage, KeyCode.SPACE, false);
        await(() -> Window.getWindows().stream().filter(ContextMenu.class::isInstance)
                .map(ContextMenu.class::cast).anyMatch(ContextMenu::isShowing));
        var popup = fx(() -> Window.getWindows().stream().filter(ContextMenu.class::isInstance)
                .map(ContextMenu.class::cast).filter(ContextMenu::isShowing).findFirst().orElseThrow());
        await(popup::isFocused);
        press(popup, KeyCode.DOWN, false);
        activate(popup);
        fx(() -> { FxSnapshot.save(popup.getScene().getRoot(), "target/ui-polish/keyboard-" + name + ".png"); return null; });
        press(popup, KeyCode.ESCAPE, false);
        await(() -> !popup.isShowing());
        activate(stage);
        check(fx(() -> stage.getScene().getFocusOwner() == target && target.isFocusVisible()),
                name + ": Escape must restore visible keyboard focus to profile");
        evidence.add(name + " PASS Space opens, Down selects, Escape closes and restores focus");
    }

    private static void journey(Stage stage, String name, List<Node> targets) throws Exception {
        activate(stage);
        assertLoaded(stage, name);
        for (boolean reverse : new boolean[]{false, true}) {
            var originalText = fx(() -> targets.stream().filter(TextArea.class::isInstance).map(TextArea.class::cast)
                    .collect(java.util.stream.Collectors.toMap(area -> area, TextArea::getText)));
            var remaining = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
            remaining.addAll(targets);
            var trace = new ArrayList<String>();
            int tabs = 0;
            while (!remaining.isEmpty() && tabs++ < MAX_TABS) {
                press(stage, KeyCode.TAB, reverse);
                fx(() -> {
                    Node focus = stage.getScene().getFocusOwner();
                    if (!stage.isFocused() || focus == null || !focus.isFocused() || !focus.isFocusVisible())
                        FxSnapshot.save(stage.getScene().getRoot(), "target/ui-polish/keyboard-failure-" + name
                                + (reverse ? "-reverse" : "-forward") + ".png");
                    check(stage.isFocused(), name + ": expected window must own native focus before inspecting keyboard outline");
                    check(focus != null && usable(focus), name + ": keyboard focus enters an invisible or disabled node: " + focus);
                    check(focus.isFocused(), name + ": scene focus owner must hold actual input focus");
                    double fraction = visibleFraction(focus);
                    check(fraction > 0, name + ": keyboard focus is fully clipped outside its viewport: " + describe(focus));
                    trace.add(describe(focus));
                    if (remaining.remove(focus)) {
                        check(focus.isFocusVisible(), name + ": keyboard focus has no native focus-visible state: " + describe(focus));
                        check(fraction >= 0.95, name + ": focused input or action is clipped by its viewport (visible="
                                + fraction + "): " + describe(focus));
                        FxSnapshot.save(stage.getScene().getRoot(), "target/ui-polish/keyboard-" + name
                                + (reverse ? "-reverse-" : "-forward-") + trace.size() + ".png");
                    }
                    return null;
                });
            }
            check(remaining.isEmpty(), name + " " + (reverse ? "Shift+Tab" : "Tab") + " did not reach "
                    + remaining.stream().map(KeyboardJourneyUiSmoke::describe).toList() + "; trace=" + trace);
            fx(() -> { originalText.forEach((area, before) -> check(before.equals(area.getText()),
                    name + ": Tab must traverse without editing multiline text")); return null; });
            assertLoaded(stage, name);
            evidence.add(name + " " + (reverse ? "Shift+Tab" : "Tab") + " PASS " + tabs + "/" + MAX_TABS
                    + " key presses; targets=" + targets.stream().map(KeyboardJourneyUiSmoke::describe).toList());
        }
    }

    private static void assertLoaded(Stage stage, String name) throws Exception {
        fx(() -> {
            for (var id : List.of("retryButton", "courseRetryButton", "historyRetryButton", "recipientRetryButton")) {
                var retry = stage.getScene().lookup("#" + id);
                check(retry == null || !usable(retry), name + ": a failed read exposes " + id);
            }
            for (var node : stage.getScene().getRoot().lookupAll(".course-feedback-error")) {
                if (node instanceof Label label && usable(label)) {
                    check(label.getText().isBlank(), name + ": load feedback reports " + label.getText());
                }
            }
            return null;
        });
    }

    private static void reach(Stage stage, Node target) throws Exception {
        activate(stage);
        for (int attempts = 0; attempts < MAX_TABS; attempts++) {
            press(stage, KeyCode.TAB, false);
            if (fx(() -> stage.getScene().getFocusOwner() == target)) return;
        }
        throw new AssertionError("Real Tab could not reach " + describe(target));
    }

    private static void press(Window expected, KeyCode key, boolean shift) throws Exception {
        activate(expected);
        var delivered = new CountDownLatch(1);
        var receivedScene = new java.util.concurrent.atomic.AtomicReference<Scene>();
        EventHandler<KeyEvent> observe = event -> {
            if (diagnose) inputTrace.add("KEY " + event.getCode() + " shift=" + event.isShiftDown()
                    + " current=" + describe(((Scene) event.getSource()).getFocusOwner()));
            if (event.getCode() == key) { receivedScene.compareAndSet(null, (Scene) event.getSource()); delivered.countDown(); }
        };
        var scenes = fx(() -> {
            var windows = Window.getWindows().stream().filter(Window::isShowing).toList();
            check(expected.isShowing() && expected.isFocused(), "Expected test window must own desktop keyboard focus");
            var views = windows.stream().map(Window::getScene).filter(Objects::nonNull).distinct().toList();
            views.forEach(scene -> scene.addEventFilter(KeyEvent.KEY_PRESSED, observe));
            if (shift) robot.keyPress(KeyCode.SHIFT);
            robot.keyPress(key); robot.keyRelease(key);
            if (shift) robot.keyRelease(KeyCode.SHIFT);
            return views;
        });
        try {
            check(delivered.await(3, TimeUnit.SECONDS), "OS key did not arrive in a test scene: " + key);
            check(receivedScene.get() == expected.getScene(), "OS key was delivered to another scene; keyboard journey is invalid");
            pulses();
        } finally {
            fx(() -> { scenes.forEach(scene -> scene.removeEventFilter(KeyEvent.KEY_PRESSED, observe)); return null; });
        }
        if (key == KeyCode.TAB) activate(expected);
    }

    private static Stage show(Object controller, String resource, int width, int height) throws Exception {
        var result = fx(() -> {
            var loader = new FXMLLoader(KeyboardJourneyUiSmoke.class.getResource("/it/uninafoodlab/presentation/" + resource));
            loader.setController(controller);
            var scene = new Scene(loader.<Parent>load(), width, height);
            scene.getStylesheets().add(KeyboardJourneyUiSmoke.class.getResource("/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
            var stage = new Stage(); stage.setTitle("UninaFoodLab keyboard gate");
            stage.setScene(scene); stage.show(); stage.toFront(); stage.requestFocus();
            if (diagnose) {
                var monitored = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
                scene.focusOwnerProperty().addListener((observable, before, after) -> {
                    inputTrace.add("FOCUS " + (before == null ? "null" : describe(before)) + " -> " + describe(after));
                    if (after != null && monitored.add(after)) after.focusVisibleProperty().addListener((property, old, visible) -> {
                        inputTrace.add("VISIBLE " + describe(after) + "=" + visible);
                        if (!visible) inputTrace.add(Arrays.stream(Thread.currentThread().getStackTrace())
                                .skip(2).limit(22).map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining("\n")));
                    });
                });
                stage.focusedProperty().addListener((observable, before, after) -> inputTrace.add("WINDOW=" + after));
                scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                        event -> inputTrace.add("MOUSE_PRESS " + event.getTarget()));
            }
            return stage;
        });
        awaitActivationCheckpoint(result);
        return result;
    }

    private static void awaitActivationCheckpoint(Stage stage) throws Exception {
        if (activationFile == null || activationCheckpointUsed) return;
        activationCheckpointUsed = true;
        var ready = fx(() -> "READY KeyboardJourneyUiSmoke: title=" + stage.getTitle()
                + "; scene=" + (int) stage.getScene().getWidth() + "x" + (int) stage.getScene().getHeight()
                + "; activationFile=" + activationFile);
        System.out.println(ready);
        System.out.flush();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (!Files.exists(activationFile)) {
            check(System.nanoTime() < deadline, "Native window activation checkpoint timed out after 45 seconds: " + activationFile);
            Thread.sleep(50);
        }
    }

    private static void activate(Window window) throws Exception {
        fx(() -> {
            if (!window.isFocused()) {
                if (window instanceof Stage stage) stage.toFront();
                window.requestFocus();
            }
            return null;
        });
        await(window::isFocused);

        pulses();
    }
    private static void pulses() throws Exception {
        var settled = new CountDownLatch(1);
        var timer = fx(() -> {
            var animation = new AnimationTimer() {
                private int frames;
                @Override public void handle(long now) {
                    if (++frames == 2) { stop(); settled.countDown(); }
                }
            };
            animation.start(); Platform.requestNextPulse(); return animation;
        });
        try { check(settled.await(3, TimeUnit.SECONDS), "Native focus/layout did not settle within two pulses"); }
        finally { fx(() -> { timer.stop(); return null; }); }
    }
    private static void close(Stage stage) throws Exception { fx(() -> { stage.close(); return null; }); }
    private static List<Node> nodes(Stage stage, String... ids) throws Exception {
        return fx(() -> Arrays.stream(ids).map(id -> {
            var node = stage.getScene().lookup("#" + id);
            check(node != null && usable(node), "Required usable keyboard target missing: " + id);
            return node;
        }).toList());
    }
    private static boolean usable(Node node) {
        for (Node current = node; current != null; current = current.getParent())
            if (!current.isVisible() || current.isDisabled() || current.getOpacity() <= 0) return false;
        return node.getScene() != null;
    }
    private static double visibleFraction(Node node) {
        var bounds = node.localToScene(node.getBoundsInLocal());
        double left = Math.max(0, bounds.getMinX()), top = Math.max(0, bounds.getMinY());
        double right = Math.min(node.getScene().getWidth(), bounds.getMaxX());
        double bottom = Math.min(node.getScene().getHeight(), bounds.getMaxY());
        for (Node ancestor = node.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getClip() == null) continue;
            var clip = ancestor.localToScene(ancestor.getClip().getBoundsInParent());
            left = Math.max(left, clip.getMinX()); top = Math.max(top, clip.getMinY());
            right = Math.min(right, clip.getMaxX()); bottom = Math.min(bottom, clip.getMaxY());
        }
        double full = bounds.getWidth() * bounds.getHeight();
        return full <= 0 ? 0 : Math.max(0, right - left) * Math.max(0, bottom - top) / full;
    }
    private static List<Node> descendants(Parent parent) {
        var result = new ArrayList<Node>();
        for (var node : parent.getChildrenUnmodifiable()) {
            result.add(node); if (node instanceof Parent nested) result.addAll(descendants(nested));
        }
        return result;
    }
    private static String describe(Node node) {
        if (node == null) return "null";
        return node.getClass().getSimpleName() + "#" + Objects.toString(node.getId(), "")
                + (node instanceof ButtonBase button ? "(" + button.getText() + ")" : "");
    }
    private static AuthenticatedUser user(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("keyboard.synthetic"), "Utente sintetico", role);
    }
    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type) {
        var courses = List.of(new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Cucina", CourseTitle.of("Pasta fresca"),
                "Preparazione in laboratorio", LocalDate.now().plusDays(2), LocalTime.of(18, 0), 7, 2,
                Optional.of(LocalDateTime.now().plusDays(9)), CourseStatus.PUBLISHED, IngredientMargin.zero()));
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (type == CourseRepository.class && method.getName().equals("findByChef")) {
                if (args[1] instanceof UUID id) return courses.stream().filter(course -> course.id().equals(id)).findFirst();
                if (args[1] instanceof CourseFilter) {
                    var page = (PageRequest) args[2];
                    return new Page<>(courses.stream().skip(page.offset()).limit(page.size()).toList(), courses.size());
                }
                return courses;
            }
            if (type == NotificationRepository.class && method.getName().equals("countRecipients")) return 1L;
            if (method.getReturnType() == List.class) return List.of();
            if (method.getReturnType() == Page.class) return new Page<>(List.of(), 0);
            if (method.getReturnType() == Map.class) return Map.of();
            if (method.getReturnType() == Optional.class) return Optional.empty();
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == boolean.class) return false;
            throw new AssertionError("Unexpected repository mutation: " + method);
        });
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static <T> T fx(Callable<T> operation) throws Exception {
        var task = new FutureTask<T>(operation); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier predicate) throws Exception { FxAwait.until(predicate::getAsBoolean, 15); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
