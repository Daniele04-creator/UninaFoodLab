package it.uninafoodlab.presentation.login;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.presentation.FxAwait;
import it.uninafoodlab.presentation.FxSnapshot;
import it.uninafoodlab.presentation.navigation.SceneNavigator;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.MenuButton;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.VBox;
import it.uninafoodlab.domain.course.SessionMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

public final class EmptyInstallationUiSmoke {
    private EmptyInstallationUiSmoke() {}

    public static void main(String[] args) throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:18.4")) {
            postgres.start();
            postgres.copyFileToContainer(MountableFile.forHostPath(Path.of("database/bootstrap/001__create_roles.sql")),
                    "/tmp/roles.sql");
            var roles = postgres.execInContainer("psql", "--username=" + postgres.getUsername(),
                    "--dbname=" + postgres.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                    "--set=migrator_password=integration-migrator-only", "--set=app_password=integration-app-only",
                    "--file=/tmp/roles.sql");
            check(roles.getExitCode() == 0, "Isolated database roles created");
            var config = new ApplicationConfig(postgres.getJdbcUrl(), "uninafoodlab_app", "integration-app-only",
                    "uninafoodlab_migrator", "integration-migrator-only", "development");
            try (var runtime = ApplicationRuntime.start(config)) {
                Platform.startup(() -> Platform.setImplicitExit(false));
                try { verifyJourney(runtime); }
                finally { Platform.exit(); }
            }
        }
        System.out.println("PASS: isolated empty PostgreSQL, native registration and visible account logout, first ingredient/category/recipe, online and practical course creation, persisted readback and login");
    }

    private static void verifyJourney(ApplicationRuntime runtime) throws Exception {
        var stage = fx(() -> {
            var window = new Stage();
            new SceneNavigator(window, runtime).showLogin();
            return window;
        });
        FxAwait.until(() -> stage.getScene().lookup("#registerButton") != null, 20);
        register(stage, 0, "partecipante.nuovo", "Partecipante nuovo");
        FxAwait.until(() -> stage.getScene().lookup("#browseTab") != null
                && !visible(stage.getScene(), "progressIndicator"), 20);
        fx(() -> {
            FxSnapshot.save(stage.getScene().getRoot(), "target/first-run/live-first-participant.png");
            logout(stage.getScene());
            return null;
        });
        FxAwait.until(() -> stage.getScene().lookup("#registerButton") != null, 15);
        fx(() -> {
            field(stage.getScene(), "usernameField").setText("partecipante.nuovo");
            field(stage.getScene(), "passwordField").setText("SyntheticChef!2026");
            button(stage.getScene(), "submitButton").fire();
            return null;
        });
        FxAwait.until(() -> stage.getScene().lookup("#browseTab") != null
                && !visible(stage.getScene(), "progressIndicator"), 15);
        fx(() -> { logout(stage.getScene()); return null; });
        FxAwait.until(() -> stage.getScene().lookup("#registerButton") != null, 15);
        register(stage, 1, "chef.nuovo", "Chef nuovo");
        FxAwait.until(() -> stage.getScene().lookup("#shellRoot") != null
                && visible(stage.getScene(), "emptyDayPanel"), 20);
        fx(() -> {
            FxSnapshot.save(stage.getScene().getRoot(), "target/first-run/live-empty-agenda.png");
            button(stage.getScene(), "catalogButton").fire();
            return null;
        });
        FxAwait.until(() -> stage.getScene().lookup("#recipeList") instanceof ListView<?> list
                && list.getItems().isEmpty() && !button(stage.getScene(), "newRecipeButton").isDisabled(), 15);
        fx(() -> {
            FxSnapshot.save(stage.getScene().getRoot(), "target/first-run/live-empty-catalog.png");
            ((ToggleButton) stage.getScene().lookup("#ingredientsTab")).fire();
            button(stage.getScene(), "newIngredientButton").fire();
            field(stage.getScene(), "ingredientName").setText("Farina del laboratorio");
            ((ComboBox<?>) stage.getScene().lookup("#ingredientUnit")).getSelectionModel().selectFirst();
            button(stage.getScene(), "saveIngredientButton").fire();
            return null;
        });
        FxAwait.until(() -> ((ListView<?>) stage.getScene().lookup("#ingredientList")).getItems().size() == 1
                && ((Label) stage.getScene().lookup("#feedback")).getText().startsWith("Ingrediente salvato"), 15);
        fx(() -> {
            FxSnapshot.save(stage.getScene().getRoot(), "target/first-run/live-first-ingredient.png");
            ((ToggleButton) stage.getScene().lookup("#recipesTab")).fire();
            button(stage.getScene(), "newRecipeButton").fire();
            field(stage.getScene(), "recipeName").setText("Prima preparazione");
            ((TextArea) stage.getScene().lookup("#recipeDescription")).setText("Mescola gli ingredienti e prepara l’impasto.");
            return null;
        });
        Platform.runLater(() -> button(stage.getScene(), "newCategoryButton").fire());
        FxAwait.until(() -> categoryDialog() != null, 10);
        fx(() -> {
            var dialog = categoryDialog();
            ((TextField) dialog.lookup("#newCategoryName")).setText("Impasti del laboratorio");
            ((Button) dialog.lookup("#createCategorySubmit")).fire();
            return null;
        });
        FxAwait.until(() -> categoryDialog() == null
                && ((ComboBox<?>) stage.getScene().lookup("#categoryChoice")).getValue() != null, 15);
        fx(() -> {
            check(field(stage.getScene(), "recipeName").getText().equals("Prima preparazione"),
                    "First category does not discard the recipe draft");
            ((ComboBox<?>) stage.getScene().lookup("#lineIngredient")).getSelectionModel().selectFirst();
            field(stage.getScene(), "quantity").setText("100");
            buttonByText((Parent) stage.getScene().lookup("#addIngredientRow"), "Aggiungi").fire();
            button(stage.getScene(), "saveRecipeButton").fire();
            return null;
        });
        FxAwait.until(() -> ((ListView<?>) stage.getScene().lookup("#recipeList")).getItems().size() == 1
                && !button(stage.getScene(), "newRecipeButton").isDisabled(), 15);
        fx(() -> {
            FxSnapshot.save(stage.getScene().getRoot(), "target/first-run/live-first-recipe.png");
            button(stage.getScene(), "coursesButton").fire();
            return null;
        });
        FxAwait.until(() -> stage.getScene().lookup("#courseList") instanceof ListView<?> list
                && list.getItems().isEmpty() && !button(stage.getScene(), "openCreateCourseButton").isDisabled(), 15);
        FxAwait.until(() -> !visible(stage.getScene(), "progressIndicator"), 15);
        fx(() -> {
            FxSnapshot.save(stage.getScene().getRoot(), "target/first-run/live-empty-courses.png");
            button(stage.getScene(), "openCreateCourseButton").fire();
            ((ComboBox<?>) stage.getScene().lookup("#categoryField")).getSelectionModel().selectFirst();
            field(stage.getScene(), "titleField").setText("Primo corso del laboratorio");
            ((TextArea) stage.getScene().lookup("#descriptionField"))
                    .setText("Introduzione online e laboratorio pratico sugli impasti.");
            ((DatePicker) stage.getScene().lookup("#startDateField"))
                    .setValue(LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(14));
            var editors = (javafx.scene.layout.Pane) stage.getScene().lookup("#sessionModeEditor");
            @SuppressWarnings("unchecked")
            var secondMode = (ComboBox<SessionMode>) editors.getChildren().get(1).getUserData();
            secondMode.setValue(SessionMode.PRACTICAL);
            accessibleField(stage.getScene(), "URL HTTPS della sessione 1")
                    .setText("https://example.test/lezione-sintetica");
            accessibleField(stage.getScene(), "Sede o link alla posizione della sessione 2")
                    .setText("https://maps.example.test/locations/laboratorio-sintetico");
            FxSnapshot.save(stage.getScene().getRoot(), "target/first-run/live-create-mixed-course.png");
            button(stage.getScene(), "createButton").fire();
            return null;
        });
        FxAwait.until(() -> stage.getScene().lookup("#courseList") instanceof ListView<?> list
                && list.getItems().size() == 1 && !visible(stage.getScene(), "progressIndicator"), 20);
        var chef = runtime.authenticateUser().execute("chef.nuovo", "SyntheticChef!2026".toCharArray());
        var courses = runtime.browseCourses().courses(chef, Optional.empty());
        check(courses.size() == 1, "GUI creates exactly one persisted course");
        var sessions = runtime.browseCourses().sessions(chef, courses.getFirst().id());
        check(sessions.size() == 2 && sessions.get(0).mode() == SessionMode.ONLINE
                && sessions.get(1).mode() == SessionMode.PRACTICAL,
                "GUI persists both online and practical session modes");
        check(sessions.get(1).venue().orElseThrow()
                        .equals("https://maps.example.test/locations/laboratorio-sintetico"),
                "The practical location link survives GUI submission and the real database round trip");
        runtime.accountLifecycle().logout(chef);
        fx(() -> {
            FxSnapshot.save(stage.getScene().getRoot(), "target/first-run/live-first-mixed-course.png");
            logout(stage.getScene());
            return null;
        });
        FxAwait.until(() -> stage.getScene().lookup("#usernameField") != null, 15);
        fx(() -> {
            field(stage.getScene(), "usernameField").setText("chef.nuovo");
            field(stage.getScene(), "passwordField").setText("SyntheticChef!2026");
            button(stage.getScene(), "submitButton").fire();
            return null;
        });
        FxAwait.until(() -> stage.getScene().lookup("#shellRoot") != null, 15);
        fx(() -> { stage.close(); return null; });
    }

    private static void register(Stage stage, int role, String username, String name) throws Exception {
        Platform.runLater(() -> button(stage.getScene(), "registerButton").fire());
        FxAwait.until(() -> registrationDialog() != null, 10);
        fx(() -> {
            var pane = registrationDialog();
            ((ComboBox<?>) pane.lookup("#registrationRole")).getSelectionModel().select(role);
            ((TextField) pane.lookup("#registrationUsername")).setText(username);
            ((TextField) pane.lookup("#registrationName")).setText(name);
            ((TextField) pane.lookup("#registrationPassword")).setText("SyntheticChef!2026");
            ((TextField) pane.lookup("#registrationConfirmation")).setText("SyntheticChef!2026");
            FxSnapshot.save(pane, "target/first-run/registration-role-" + role + ".png");
            pane.getButtonTypes().stream().filter(type -> type.getButtonData().isDefaultButton())
                    .map(pane::lookupButton).map(Button.class::cast).findFirst().orElseThrow().fire();
            return null;
        });
    }

    private static DialogPane registrationDialog() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window.getScene() != null)
                .map(window -> window.getScene().lookup(".dialog-pane"))
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.lookup("#registrationRole") != null).findFirst().orElse(null);
    }

    private static boolean visible(Scene scene, String id) {
        return scene.lookup("#" + id) != null && scene.lookup("#" + id).isVisible();
    }

    private static TextField accessibleField(Scene scene, String accessibleText) {
        return scene.getRoot().lookupAll(".text-field").stream().filter(TextField.class::isInstance)
                .map(TextField.class::cast).filter(field -> accessibleText.equals(field.getAccessibleText()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing field: " + accessibleText));
    }

    private static void logout(Scene scene) {
        var participantAccount = scene.getRoot().lookupAll(".menu-button").stream()
                .filter(MenuButton.class::isInstance).map(MenuButton.class::cast)
                .filter(menu -> "profileMenu".equals(menu.getId())).findFirst();
        if (participantAccount.isPresent()) {
            var menu = participantAccount.orElseThrow();
            menu.show();
            menu.getItems().stream().filter(item -> "Esci dall’account".equals(item.getText()))
                    .findFirst().orElseThrow().fire();
            menu.hide();
            return;
        }
        var profile = button(scene, "profileButton");
        check(profile.isVisible() && !profile.isDisabled(), "Chef profile remains available for logout");
        profile.fire();
        Window.getWindows().stream().filter(Window::isShowing)
                .filter(javafx.scene.control.ContextMenu.class::isInstance)
                .map(javafx.scene.control.ContextMenu.class::cast)
                .flatMap(menu -> menu.getItems().stream())
                .filter(item -> "Esci dall’account".equals(item.getText()))
                .findFirst().orElseThrow().fire();
    }
    private static DialogPane categoryDialog() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window.getScene() != null)
                .map(window -> window.getScene().lookup(".dialog-pane"))
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.lookup("#newCategoryName") != null).findFirst().orElse(null);
    }
    private static TextField field(Scene scene, String id) { return (TextField) scene.lookup("#" + id); }
    private static Button button(Scene scene, String id) { return (Button) scene.lookup("#" + id); }
    private static Button buttonByText(Parent root, String text) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
