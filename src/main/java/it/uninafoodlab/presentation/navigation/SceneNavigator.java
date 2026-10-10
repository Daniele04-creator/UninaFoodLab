package it.uninafoodlab.presentation.navigation;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.login.LoginController;
import it.uninafoodlab.presentation.shell.AppShellController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SceneNavigator {

    static final double SHELL_WIDTH = 1142.0;
    static final double SHELL_HEIGHT = 633.0;

    private static final String LOGIN = "/it/uninafoodlab/presentation/login/login.fxml";
    private static final String SHELL = "/it/uninafoodlab/presentation/shell/app-shell.fxml";
    private static final String STYLESHEET =
            "/it/uninafoodlab/presentation/styles/application.css";

    private final Stage stage;
    private final ApplicationRuntime runtime;
    private UnsavedChangesGuard exitGuard;
    private Runnable disposeWorkspace = () -> {};

    public SceneNavigator(Stage stage, ApplicationRuntime runtime) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        setExitContext(() -> false, () -> false, () -> {});
        UnsavedChangesDialog.protectClose(stage, () -> exitGuard, () -> disposeWorkspace.run());
    }

    public void showLogin() {
        var loader = new FXMLLoader(resource(LOGIN));
        loader.setController(new LoginController(runtime.authenticateUser(), runtime.accountLifecycle(), this::showShell));
        disposeWorkspace.run();
        setExitContext(() -> false, () -> false, () -> {});
        show(load(loader), 1120, 720, false);
    }

    private void showShell(AuthenticatedUser user) {
        if (user.role() == it.uninafoodlab.domain.identity.Role.PARTICIPANT) {
            showParticipant(user);
            return;
        }
        var loader = new FXMLLoader(resource(SHELL));
        var controller = new AppShellController(
                user,
                runtime.browseCatalog(),
                runtime.createCategory(),
                runtime.createCourse(),
                runtime.browseCourses(),
                runtime.assignRecipeToSession(),
                runtime.browseNotifications(),
                runtime.manageNotifications(),
                runtime.manageCourseSchedule(),
                runtime.browseChefPlanning(),
                runtime.manageRecipeCatalog(),
                () -> logout(user));
        controller.setSessionLifecycle(runtime.sessionLifecycle());
        controller.setAccountAction(() -> changePassword(user));
        loader.setController(controller);
        var root = load(loader);
        show(root, SHELL_WIDTH, SHELL_HEIGHT, true);
        setExitContext(
                () -> controller.workspaces().anyMatch(it.uninafoodlab.presentation.common.WorkspaceState::hasUnsavedChanges),
                () -> controller.workspaces().anyMatch(it.uninafoodlab.presentation.common.WorkspaceState::isBusy),
                () -> controller.workspaces().forEach(it.uninafoodlab.presentation.common.WorkspaceState::dispose));
    }

    private void showParticipant(AuthenticatedUser user) {
        var loader = new FXMLLoader(resource("/it/uninafoodlab/presentation/participant/participant-workspace.fxml"));
        var controller = new it.uninafoodlab.presentation.participant.ParticipantWorkspaceController(user,
                runtime.browsePublishedCourses(), runtime.browseCatalog(), runtime.browseEnrollments(),
                runtime.manageEnrollment(), runtime.managePracticalAttendance(), runtime.browseNotifications(),
                runtime.manageNotifications());
        controller.setLogoutAction(() -> logout(user));
        controller.setAccountAction(() -> changePassword(user));
        loader.setController(controller);
        show(load(loader), SHELL_WIDTH, SHELL_HEIGHT, true);
        setExitContext(() -> false, controller::isBusy, controller::dispose);
    }

    private void setExitContext(java.util.function.BooleanSupplier changed,
            java.util.function.BooleanSupplier busy, Runnable dispose) {
        exitGuard = new UnsavedChangesGuard(changed,
                () -> busy.getAsBoolean() || stage.getScene() != null && stage.getScene().getRoot().isDisabled(),
                action -> UnsavedChangesDialog.confirm(stage, action), () -> UnsavedChangesDialog.showBusy(stage));
        disposeWorkspace = dispose;
    }

    private void logout(AuthenticatedUser user) {
        if (!exitGuard.allow(UnsavedChangesGuard.Action.LOGOUT)) return;
        var root = stage.getScene().getRoot();
        root.setDisable(true);
        it.uninafoodlab.presentation.common.FxAsyncExecutor.run("logout", () -> {
            runtime.accountLifecycle().logout(user);
            return true;
        }, done -> showLogin(), error -> {
            root.setDisable(false);
            showAccountError("Impossibile revocare la sessione. Verifica la connessione e riprova.");
        });
    }

    private void changePassword(AuthenticatedUser user) {
        if (!exitGuard.allow(UnsavedChangesGuard.Action.PASSWORD)) return;
        it.uninafoodlab.presentation.account.AccountDialogs.showPasswordChange(stage).ifPresent(change -> {
            var root = stage.getScene().getRoot();
            root.setDisable(true);
            it.uninafoodlab.presentation.common.FxAsyncExecutor.run("change-password", () -> {
                try { runtime.accountLifecycle().changePassword(user, change.currentPassword(), change.newPassword()); }
                finally { change.clear(); }
                return true;
            }, done -> showLogin(), error -> {
                root.setDisable(false);
                showAccountError("Cambio password non confermato. Verifica la password attuale e usa almeno 12 caratteri per quella nuova. "
                        + "Se la connessione si è interrotta, controlla l'accesso con la nuova password prima di riprovare.");
            });
        });
    }

    private void showAccountError(String message) {
        var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, message);
        alert.setTitle("Operazione non completata");
        alert.setHeaderText("Controlla l'accesso e riprova");
        it.uninafoodlab.presentation.common.DialogTheme.apply(alert, stage);
        alert.showAndWait();
    }

    private void show(Parent root, double width, double height, boolean maximized) {
        var displayedRoot = new ContentPane(root);
        var scene = new Scene(displayedRoot, width, height);
        scene.getStylesheets().add(resource(STYLESHEET).toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(600);
        stage.setMaximized(maximized);
        stage.show();
    }

    private static Parent load(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load application view", exception);
        }
    }

    private static URL resource(String path) {
        return Objects.requireNonNull(SceneNavigator.class.getResource(path), "Missing resource: " + path);
    }
}
