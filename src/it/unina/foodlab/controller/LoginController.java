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
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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

public class LoginController {

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

    // DAO come dipendenze del controller (evito new ripetuti)
    private final ChefDao chefDao = new ChefDao();

    // SVG icons (evito stringhe duplicate)
    private static final String EYE_OPEN_ICON =
            "M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z";
    private static final String EYE_SLASH_ICON =
            "M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z";

    /* ====================== LIFECYCLE ====================== */

    /** Chiamata dal Main subito dopo il load dell'FXML */
    public void setStage(Stage stage) { this.stage = stage; }

    public void requestInitialFocus() {
        if (usernameField != null) {
            Platform.runLater(new Runnable() {
                @Override public void run() { usernameField.requestFocus(); }
            });
        }
    }

    @FXML
    private void initialize() {
        initPasswordToggle();
        hideError();

        if (loginButton != null) {
            loginButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) { handleLogin(); }
            });
        }
        if (registerButton != null) {
            registerButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) { onRegister(); }
            });
        }
        if (usernameField != null && passwordField != null && passwordVisibleField != null) {
            usernameField.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) {
                    getActivePasswordField().requestFocus();
                }
            });
            passwordField.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) { handleLogin(); }
            });
            passwordVisibleField.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) { handleLogin(); }
            });
        }
    }

    private void initPasswordToggle() {
        if (passwordVisibleField == null || passwordField == null) return;

        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        updatePasswordVisibility(false, false);

        if (toggleVisibilityButton != null) {
            toggleVisibilityButton.selectedProperty().addListener((obs, oldVal, sel) -> {
                updatePasswordVisibility(sel, true);
            });
        }
    }

    /* ====================== ACTIONS ====================== */

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
                if (chef == null || chef.getCF_Chef() == null || chef.getCF_Chef().trim().isEmpty()) {
                    errorMessage = "Chef non trovato dopo l'autenticazione.";
                } else {
                    // Sessione applicativa
                    String cf = chef.getCF_Chef().trim();
                    AppSession.setCfChef(cf);

                    // Prepara DAOs per la view successiva
                    CorsoDao corsoDao = new CorsoDao(cf);
                    SessioneDao sessioneDao = new SessioneDao(cf);

                    // Carica e mostra la view dei corsi
                    showCorsiScene(chef, corsoDao, sessioneDao);
                    return; // success
                }
            }
        } catch (Exception ex) {
            // Log minimale in console, messaggio pulito in UI
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

    @FXML
    private void onRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getResource("/it/unina/foodlab/ui/RegisterChefDialog.fxml"));
            StackPane registerRoot = loader.load();
            final RegisterChefController regCtrl = loader.getController();

            final Stage st = resolveStage();
            if (st == null) {
                showError("Stage non disponibile.");
                return;
            }

            Scene current = st.getScene();
            if (current == null) {
                st.setScene(new Scene(registerRoot, 720, 520));
            } else {
                current.setRoot(registerRoot);
            }

            Button registerBtn = (Button) registerRoot.lookup("#registerButton");
            if (registerBtn != null) {
                registerBtn.setOnAction(new EventHandler<ActionEvent>() {
                    @Override public void handle(ActionEvent event) {
                        Chef chef = regCtrl.getChef();
                        if (chef == null) return; // errori già mostrati nella form
                        try {
                            new ChefDao().register(chef);
                            // Torna al login
                            loadLoginScene(st);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            regCtrl.showError("Errore durante la registrazione. Controlla i dati e riprova.");
                        }
                    }
                });
            }

            Button cancelBtn = (Button) registerRoot.lookup("#cancelButton");
            if (cancelBtn != null) {
                cancelBtn.setOnAction(new EventHandler<ActionEvent>() {
                    @Override public void handle(ActionEvent event) {
                        try {
                            loadLoginScene(st);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            showError("Errore caricando la schermata di login.");
                        }
                    }
                });
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            showError("Impossibile aprire la registrazione.");
        }
    }

    /* ====================== NAV HELPERS ====================== */

    private void showCorsiScene(Chef chef, CorsoDao corsoDao, SessioneDao sessioneDao) throws IOException {
        URL fxmlUrl = getResource("/it/unina/foodlab/ui/corsi.fxml");
        if (fxmlUrl == null) throw new IOException("FXML mancante: /it/unina/foodlab/ui/corsi.fxml");

        FXMLLoader ldr = new FXMLLoader(fxmlUrl);
        Parent root = ldr.load();

        CorsiPanelController controller = ldr.getController();
        if (controller == null) throw new IOException("Controller nullo in corsi.fxml");
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
        StackPane loginRoot = loginLdr.load();
        LoginController newLogin = loginLdr.getController();
        newLogin.setStage(st);
        newLogin.requestInitialFocus();

        Scene sc = st.getScene();
        if (sc == null) {
            st.setScene(new Scene(loginRoot, 720, 520));
        } else {
            sc.setRoot(loginRoot);
        }
    }

    private String buildDisplayName(Chef c) {
        if (c == null) return "";
        String nome = c.getNome() == null ? "" : c.getNome().trim();
        String cognome = c.getCognome() == null ? "" : c.getCognome().trim();
        String full = (nome + " " + cognome).trim();
        if (!full.isEmpty()) return full;
        String cf = c.getCF_Chef();
        return cf == null ? "" : cf.trim();
    }

    /* ====================== UI HELPERS ====================== */

    private Stage resolveStage() {
        if (stage != null) return stage;
        if (loginButton != null && loginButton.getScene() != null) {
            return (Stage) loginButton.getScene().getWindow();
        }
        return null;
    }

    private TextField getActivePasswordField() {
        if (passwordVisibleField != null && passwordVisibleField.isVisible()) {
            return passwordVisibleField;
        }
        return passwordField;
    }

    private void updatePasswordVisibility(boolean show, boolean focus) {
        if (passwordField == null || passwordVisibleField == null) return;

        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);

        if (show) {
            if (focus) { passwordVisibleField.requestFocus(); passwordVisibleField.end(); }
            if (eyeIcon != null) eyeIcon.setContent(EYE_OPEN_ICON);
        } else {
            if (focus) { passwordField.requestFocus(); passwordField.end(); }
            if (eyeIcon != null) eyeIcon.setContent(EYE_SLASH_ICON);
        }
    }

    private void setBusy(boolean busy) {
        if (loginButton != null) loginButton.setDisable(busy);
        if (spinner != null) {
            spinner.setVisible(busy);
            spinner.setManaged(busy);
        }
        if (loginButton != null) loginButton.setText(busy ? "Verifica..." : "Login");
    }

    private void showError(String message) {
        if (errorLabel == null) return;
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        errorLabel.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), errorLabel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
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
        Platform.runLater(new Runnable() {
            @Override public void run() {
                try {
                    Screen screen = Screen.getScreensForRectangle(
                            st.getX(), st.getY(), st.getWidth(), st.getHeight()
                    ).stream().findFirst().orElse(Screen.getPrimary());
                    Rectangle2D vb = screen.getVisualBounds();

                    st.setX(vb.getMinX());
                    st.setY(vb.getMinY());
                    st.setWidth(vb.getWidth());
                    st.setHeight(vb.getHeight());

                    st.toFront();
                    st.requestFocus();

                    st.setAlwaysOnTop(true);
                    PauseTransition pt = new PauseTransition(Duration.millis(120));
                    pt.setOnFinished(ev -> st.setAlwaysOnTop(false));
                    pt.play();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    /* ====================== UTILS ====================== */

    private static String safeText(TextField tf) {
        if (tf == null || tf.getText() == null) return "";
        return tf.getText().trim();
    }

    private static URL getResource(String path) {
        return LoginController.class.getResource(path);
    }
}
