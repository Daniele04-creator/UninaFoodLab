package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ChefDao;
import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.AppSession;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public class LoginController {

    // UI
    @FXML private VBox card;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private ToggleButton toggleVisibilityButton;
    @FXML private SVGPath eyeIcon;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private ProgressIndicator spinner;

    private Stage stage;

    // DAO
    private final ChefDao chefDao = new ChefDao();

    // SVG icone (open/closed)
    private static final String EYE_OPEN  = "M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z";
    private static final String EYE_SLASH = "M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z";

    /** Chiamata dal Main subito dopo il load dell'FXML */
    public void setStage(Stage stage) { this.stage = stage; }

    public void requestInitialFocus() {
        Platform.runLater(() -> {
            if (usernameField != null) usernameField.requestFocus();
        });
    }

    @FXML
    private void initialize() {
        initPasswordToggle();
        hideError();

        if (loginButton != null)   loginButton.setOnAction(e -> handleLogin());
        if (registerButton != null) registerButton.setOnAction(e -> onRegister());

        if (usernameField != null) {
            usernameField.setOnAction(e -> getActivePasswordField().requestFocus());
        }
        if (passwordField != null) passwordField.setOnAction(e -> handleLogin());
        if (passwordVisibleField != null) passwordVisibleField.setOnAction(e -> handleLogin());
    }

    // === Toggle mostra/nascondi password (solo icona, nessun riquadro) ===
    private void initPasswordToggle() {
        if (passwordVisibleField == null || passwordField == null || toggleVisibilityButton == null) return;

        // tieni in sync i due campi
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());

        // stato iniziale: nascosta
        setPasswordVisible(false);

        toggleVisibilityButton.selectedProperty().addListener((obs, was, sel) -> {
            setPasswordVisible(sel);
            toggleVisibilityButton.setOpacity(0.95);
        });
        toggleVisibilityButton.setOnMousePressed(e -> toggleVisibilityButton.setOpacity(0.75));
        toggleVisibilityButton.setOnMouseReleased(e -> toggleVisibilityButton.setOpacity(0.95));
    }

    private void setPasswordVisible(boolean show) {
        if (show) {
            // mostra in chiaro
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            if (eyeIcon != null) eyeIcon.setContent(EYE_OPEN);
            if (toggleVisibilityButton.getTooltip() != null)
                toggleVisibilityButton.getTooltip().setText("Nascondi password");
        } else {
            // nascondi
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            if (eyeIcon != null) eyeIcon.setContent(EYE_SLASH);
            if (toggleVisibilityButton.getTooltip() != null)
                toggleVisibilityButton.getTooltip().setText("Mostra password");
        }
    }

    // === Login ===
    private void handleLogin() {
        String username = safeText(usernameField);
        String password = getActivePasswordField().getText();

        if (username.isEmpty() || password == null || password.trim().isEmpty()) {
            showError("Inserisci username e password.");
            shake(card);
            return;
        }

        setBusy(true);
        String errorMessage = null;

        try {
            boolean ok = chefDao.authenticate(username, password);
            if (!ok) {
                errorMessage = "Username o password errati.";
            } else {
                Chef chef = chefDao.findByUsername(username);
                if (chef == null || safeText(chef.getCF_Chef()).isEmpty()) {
                    errorMessage = "Chef non trovato dopo l'autenticazione.";
                } else {
                    // sessione
                    String cf = chef.getCF_Chef().trim();
                    AppSession.setCfChef(cf);

                    // DAOs per la schermata successiva
                    CorsoDao corsoDao = new CorsoDao(cf);
                    SessioneDao sessioneDao = new SessioneDao(cf);

                    showCorsiScene(chef, corsoDao, sessioneDao);
                    return; // ok
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            errorMessage = "Errore durante il login. Riprova.";
        } finally {
            setBusy(false);
        }

        if (errorMessage != null) {
            showError(errorMessage);
            shake(card);
        }
    }

   private void onRegister() {
    try {
        FXMLLoader loader = new FXMLLoader(getResource("/it/unina/foodlab/ui/RegisterChefDialog.fxml"));
        Parent registerRoot = loader.load(); // <-- Parent, NON StackPane
        RegisterChefController regCtrl = loader.getController();

        Stage st = resolveStage();
        if (st == null) { showError("Stage non disponibile."); return; }

        Scene current = st.getScene();
        if (current == null) st.setScene(new Scene(registerRoot, 720, 520));
        else current.setRoot(registerRoot);

        // Bottone "Registrati"
        Button registerBtn = (Button) registerRoot.lookup("#registerButton");
        if (registerBtn != null) {
            registerBtn.setOnAction(ev -> {
                Chef chef = regCtrl.getChef();
                if (chef == null) return;
                try {
                    new ChefDao().register(chef);
                    loadLoginScene(st); // torna al login
                } catch (Exception ex) {
                    ex.printStackTrace();
                    regCtrl.showError("Errore durante la registrazione. Controlla i dati e riprova.");
                }
            });
        }

        // Bottone "Annulla"
        Button cancelBtn = (Button) registerRoot.lookup("#cancelButton");
        if (cancelBtn != null) cancelBtn.setOnAction(ev -> {
            try { loadLoginScene(st); }
            catch (Exception ex) { ex.printStackTrace(); showError("Errore caricando il login."); }
        });

    } catch (IOException ex) {
        ex.printStackTrace();
        showError("Impossibile aprire la registrazione.");
    }
}


    // === Navigazione ===
    private void showCorsiScene(Chef chef, CorsoDao corsoDao, SessioneDao sessioneDao) throws IOException {
        URL fxmlUrl = getResource("/it/unina/foodlab/ui/Corsi.fxml");
        if (fxmlUrl == null) throw new IOException("FXML mancante: /it/unina/foodlab/ui/corsi.fxml");

        FXMLLoader ldr = new FXMLLoader(fxmlUrl);
        Parent root = ldr.load();
        CorsiPanelController controller = ldr.getController();
        Objects.requireNonNull(controller, "Controller nullo in corsi.fxml");
        controller.setDaos(corsoDao, sessioneDao);

        String displayName = buildDisplayName(chef);
        Stage st = resolveStage();
        if (st == null) throw new IOException("Stage non disponibile");

        Scene scene = new Scene(root, 1000, 640);
        st.setTitle("UninaFoodLab - Corsi di " + displayName);
        st.setScene(scene);
        st.show();

        enforceFullScreenLook(st);
    }

    private void loadLoginScene(Stage st) throws IOException {
    FXMLLoader loginLdr = new FXMLLoader(getResource("/it/unina/foodlab/ui/LoginFrame.fxml"));
    Parent loginRoot = loginLdr.load(); // <-- Parent, NON StackPane
    LoginController newLogin = loginLdr.getController();
    newLogin.setStage(st);
    newLogin.requestInitialFocus();

    Scene sc = st.getScene();
    if (sc == null) st.setScene(new Scene(loginRoot, 720, 520));
    else sc.setRoot(loginRoot);
}


    // === UI helpers ===
    private Stage resolveStage() {
        if (stage != null) return stage;
        if (loginButton != null && loginButton.getScene() != null)
            return (Stage) loginButton.getScene().getWindow();
        return null;
    }

    private TextField getActivePasswordField() {
        return (passwordVisibleField != null && passwordVisibleField.isVisible())
                ? passwordVisibleField : passwordField;
    }

    private void setBusy(boolean busy) {
        if (loginButton != null) loginButton.setDisable(busy);
        if (spinner != null) { spinner.setVisible(busy); spinner.setManaged(busy); }
        if (loginButton != null) loginButton.setText(busy ? "Verifica..." : "Login");
    }

    private void showError(String message) {
        if (errorLabel == null) return;
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        errorLabel.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(180), errorLabel);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void hideError() {
        if (errorLabel == null) return;
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private void shake(VBox target) {
        if (target == null) return;
        TranslateTransition t1 = new TranslateTransition(Duration.millis(60), target); t1.setFromX(0);  t1.setToX(-8);
        TranslateTransition t2 = new TranslateTransition(Duration.millis(60), target); t2.setFromX(-8); t2.setToX(8);
        TranslateTransition t3 = new TranslateTransition(Duration.millis(60), target); t3.setFromX(8);  t3.setToX(-4);
        TranslateTransition t4 = new TranslateTransition(Duration.millis(60), target); t4.setFromX(-4); t4.setToX(0);
        new SequentialTransition(t1, t2, t3, t4).play();
    }

    private void enforceFullScreenLook(Stage st) {
        Platform.runLater(() -> {
            try {
                Screen screen = Screen.getScreensForRectangle(
                        st.getX(), st.getY(), st.getWidth(), st.getHeight()
                ).stream().findFirst().orElse(Screen.getPrimary());
                Rectangle2D vb = screen.getVisualBounds();
                st.setX(vb.getMinX());
                st.setY(vb.getMinY());
                st.setWidth(vb.getWidth());
                st.setHeight(vb.getHeight());
                st.toFront(); st.requestFocus();
                st.setAlwaysOnTop(true);
                PauseTransition pt = new PauseTransition(Duration.millis(120));
                pt.setOnFinished(ev -> st.setAlwaysOnTop(false));
                pt.play();
            } catch (Exception ex) { ex.printStackTrace(); }
        });
    }

    // === Utils ===
    private static String safeText(TextField tf) {
        if (tf == null || tf.getText() == null) return "";
        return tf.getText().trim();
    }

    private static String safeText(String s) { return s == null ? "" : s.trim(); }

    private String buildDisplayName(Chef c) {
        if (c == null) return "";
        String nome = safeText(c.getNome());
        String cogn = safeText(c.getCognome());
        String full = (nome + " " + cogn).trim();
        if (!full.isEmpty()) return full;
        return safeText(c.getCF_Chef());
    }

    private static URL getResource(String path) {
        return LoginController.class.getResource(path);
    }
}
