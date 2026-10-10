package it.uninafoodlab.presentation.login;

import it.uninafoodlab.application.auth.AuthenticateUser;
import it.uninafoodlab.application.auth.InvalidCredentialsException;
import it.uninafoodlab.application.account.AccountLifecycle;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.account.AccountDialogs;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class LoginController {

    private final AuthenticateUser authenticateUser;
    private final AccountLifecycle accountLifecycle;
    private final Consumer<AuthenticatedUser> onAuthenticated;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private javafx.scene.control.ToggleButton revealPasswordButton;
    @FXML private Button submitButton;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label errorLabel;
    @FXML private Label brandTitle;
    @FXML private Label passwordLabel;
    @FXML private VBox accountActions;

    public LoginController(
            AuthenticateUser authenticateUser, Consumer<AuthenticatedUser> onAuthenticated) {
        this(authenticateUser, null, onAuthenticated);
    }

    public LoginController(
            AuthenticateUser authenticateUser, AccountLifecycle accountLifecycle,
            Consumer<AuthenticatedUser> onAuthenticated) {
        this.authenticateUser = Objects.requireNonNull(authenticateUser, "authenticateUser");
        this.accountLifecycle = accountLifecycle;
        this.onAuthenticated = Objects.requireNonNull(onAuthenticated, "onAuthenticated");
    }

    @FXML
    private void initialize() {
        brandTitle.setText("");
        brandTitle.setAccessibleText("UninaFoodLab");
        var logo = it.uninafoodlab.presentation.common.LabBrand.logo(190);
        logo.fitWidthProperty().bind(javafx.beans.binding.Bindings.min(190, brandTitle.widthProperty()));
        brandTitle.setGraphic(logo);
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        visiblePasswordField.visibleProperty().bind(revealPasswordButton.selectedProperty());
        visiblePasswordField.managedProperty().bind(visiblePasswordField.visibleProperty());
        passwordField.visibleProperty().bind(revealPasswordButton.selectedProperty().not());
        passwordField.managedProperty().bind(passwordField.visibleProperty());
        togglePassword();
        accountActions.setManaged(accountLifecycle != null);
        accountActions.setVisible(accountLifecycle != null);
        usernameField.requestFocus();
    }

    @FXML
    private void togglePassword() {
        var revealed = revealPasswordButton.isSelected();
        var previous = revealed ? passwordField : visiblePasswordField;
        var current = revealed ? visiblePasswordField : passwordField;
        var caret = previous.getCaretPosition();
        passwordLabel.setLabelFor(current);
        current.requestFocus();
        current.positionCaret(caret);
        revealPasswordButton.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon(
                revealed ? "eye-off" : "eye", "dark", 22));
        revealPasswordButton.getTooltip().setText(revealed ? "Nascondi password" : "Mostra password");
        revealPasswordButton.setAccessibleText(revealed ? "Nascondi password" : "Mostra password");
    }

    @FXML
    private void submit() {
        if (submitButton.isDisabled()) {
            return;
        }
        hideError();
        var username = usernameField.getText();
        if (username == null || username.isBlank()) {
            showError("Inserisci lo username per accedere.");
            usernameField.requestFocus();
            return;
        }
        if (passwordField.getText().isEmpty()) {
            showError("Inserisci la password per accedere.");
            (revealPasswordButton.isSelected() ? visiblePasswordField : passwordField).requestFocus();
            return;
        }
        var password = passwordField.getText().toCharArray();
        passwordField.clear();
        revealPasswordButton.setSelected(false);
        togglePassword();
        setLoading(true);

        FxAsyncExecutor.run(
                "uninafoodlab-authentication",
                () -> authenticateUser.execute(username, password),
                authenticated -> {
                    setLoading(false);
                    onAuthenticated.accept(authenticated);
                },
                failure -> {
                    setLoading(false);
                    showError(failure instanceof InvalidCredentialsException
                            ? "Username o password non validi"
                            : "Accesso temporaneamente non disponibile. Riprova tra poco.");
                    passwordField.requestFocus();
                });
    }

    @FXML
    private void registerAccount() {
        if (accountLifecycle == null) return;
        try {
            AccountDialogs.showRegistration(usernameField.getScene().getWindow()).ifPresent(request -> {
                var password = request.password();
                var confirmation = request.confirmation();
                request.clear();
                if (!Arrays.equals(password, confirmation)) {
                    Arrays.fill(password, '\0');
                    Arrays.fill(confirmation, '\0');
                    showError("Le password non coincidono.");
                    return;
                }
                Arrays.fill(confirmation, '\0');
                performAccountAction(() -> request.role() == it.uninafoodlab.domain.identity.Role.CHEF
                        ? accountLifecycle.registerChef(request.username(), request.displayName(), password)
                        : accountLifecycle.registerParticipant(request.username(), request.displayName(), password));
            });
        } catch (IllegalArgumentException exception) {
            showError("Inserisci dati di registrazione validi.");
        }
    }

    private void performAccountAction(java.util.concurrent.Callable<AuthenticatedUser> action) {
        hideError();
        setLoading(true);
        FxAsyncExecutor.run("uninafoodlab-account", action,
                authenticated -> {
                    setLoading(false);
                    onAuthenticated.accept(authenticated);
                }, failure -> {
                    setLoading(false);
                    showError(it.uninafoodlab.presentation.common.UserErrorMessages.validation(failure,
                            "Operazione account non disponibile. Riprova più tardi."));
                });
    }

    private void setLoading(boolean loading) {
        submitButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        visiblePasswordField.setDisable(loading);
        revealPasswordButton.setDisable(loading);
        accountActions.setDisable(loading);
        progressIndicator.setManaged(loading);
        progressIndicator.setVisible(loading);
    }

    private void hideError() {
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private void showError(String message) {
        showFeedback(message, "login-error", "Errore: ");
    }

    private void showFeedback(String message, String style, String announcement) {
        errorLabel.getStyleClass().removeAll("login-error", "login-success");
        errorLabel.getStyleClass().add(style);
        errorLabel.setAccessibleText(announcement + message);
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }
}
