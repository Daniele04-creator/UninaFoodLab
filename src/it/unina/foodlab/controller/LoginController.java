package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ChefDao;
import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.AppSession;
import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.PauseTransition;
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

    /* ====================== LIFECYCLE ====================== */

    /** Chiamata dal Main subito dopo il load dell'FXML */
    public void setStage(Stage stage) { this.stage = stage; }

    public void requestInitialFocus() {
        Platform.runLater(() -> {
            if (usernameField != null) usernameField.requestFocus();
        });
    }

    @FXML
    private void initialize() {
        // toggle password
        if (passwordVisibleField != null && passwordField != null) {
            passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
            updatePasswordVisibility(false, false);

            if (toggleVisibilityButton != null) {
                toggleVisibilityButton.selectedProperty().addListener(
                        (obs, oldVal, sel) -> updatePasswordVisibility(sel)
                );
            }
        }

        // error label off
        if (errorLabel != null) {
            errorLabel.setManaged(false);
            errorLabel.setVisible(false);
        }

        // azioni
        if (loginButton != null) loginButton.setOnAction(e -> handleLogin());
        if (registerButton != null) registerButton.setOnAction(e -> onRegister());

        if (usernameField != null) usernameField.setOnAction(e -> getActivePasswordField().requestFocus());
        if (passwordField != null) passwordField.setOnAction(e -> handleLogin());
        if (passwordVisibleField != null) passwordVisibleField.setOnAction(e -> handleLogin());
    }

    /* ====================== ACTIONS ====================== */

    private void handleLogin() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = getActivePasswordField().getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Inserisci username e password.");
            shake(card);
            return;
        }

        setBusy(true);
        String errorMessage = null;

        try {
            ChefDao chefDao = new ChefDao();
            if (!chefDao.authenticate(username, password)) {
                errorMessage = "Username o password errati.";
            } else {
                Chef chef = chefDao.findByUsername(username);
                if (chef == null || chef.getCF_Chef() == null || chef.getCF_Chef().isBlank()) {
                    errorMessage = "Chef non trovato dopo l'autenticazione.";
                } else {
                    // Sessione applicativa
                    String cf = chef.getCF_Chef().trim();
                    AppSession.setCfChef(cf);

                    CorsoDao corsoDao = new CorsoDao(cf);
                    SessioneDao sessioneDao = new SessioneDao(cf);

                    // Carica corsi.fxml dal classpath
                    URL fxmlUrl = getClass().getResource("/it/unina/foodlab/ui/corsi.fxml");
                    if (fxmlUrl == null) throw new RuntimeException("FXML mancante: /it/unina/foodlab/ui/corsi.fxml");

                    FXMLLoader ldr = new FXMLLoader(fxmlUrl);
                    Parent root = ldr.load();

                    CorsiPanelController controller = ldr.getController();
                    if (controller == null) throw new RuntimeException("Controller nullo in corsi.fxml");
                    controller.setDaos(corsoDao, sessioneDao);

                    // Nome finestra
                    String displayName = chef.getNome() != null
                            ? (chef.getNome() + " " + (chef.getCognome() == null ? "" : chef.getCognome())).trim()
                            : cf;
                    if (displayName.isBlank()) displayName = cf;

                    // Mostra scena
                    Stage st = resolveStage();
                    if (st == null) throw new RuntimeException("Stage non disponibile");

                    Scene scene = new Scene(root, 1000, 640);
                    root.setOpacity(0); // fade-in gradevole
                    st.setTitle("UninaFoodLab - Corsi di " + displayName);
                    st.setScene(scene);
                    st.show();
                    enforceFullScreenLook(st);

                    FadeTransition fadeIn = new FadeTransition(Duration.millis(450), root);
                    fadeIn.setFromValue(0);
                    fadeIn.setToValue(1);
                    fadeIn.play();

                    return; // SUCCESS: esco senza mostrare errore
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            errorMessage = "Errore durante il login: " + ex.getMessage();
        } finally {
            setBusy(false);
        }

        // Se arrivo qui, c'è stato un errore
        if (errorMessage != null) {
            showError(errorMessage);
            shake(card);
        }
    }

   @FXML
private void onRegister() {
    try {
        // Carica la view di registrazione dal classpath
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/RegisterChefDialog.fxml"));
        StackPane registerRoot = loader.load();
        RegisterChefController regCtrl = loader.getController();

        Stage st = resolveStage();
        if (st == null) {
            showError("Stage non disponibile.");
            return;
        }

        // Cambia root OR crea la scena se assente, senza usare variabili catturate nelle lambda
        Scene current = st.getScene();
        if (current == null) {
            st.setScene(new Scene(registerRoot, 720, 520));
        } else {
            current.setRoot(registerRoot);
        }

        // --- Bottone "Registrati" ---
        Button registerBtn = (Button) registerRoot.lookup("#registerButton");
        if (registerBtn != null) {
            registerBtn.setOnAction(event -> {
                Chef chef = regCtrl.getChef();
                if (chef == null) return; // errore già mostrato dal controller

                try {
                    new ChefDao().register(chef);

                    // Ricarica il Login e reimposta lo Stage sul NUOVO controller (niente cattura di 'scene')
                    FXMLLoader loginLdr = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/LoginFrame.fxml"));
                    StackPane loginRoot = loginLdr.load();
                    LoginController newLogin = loginLdr.getController();
                    newLogin.setStage(st);
                    newLogin.requestInitialFocus();

                    Scene sc = st.getScene();            // rileggo la scena al momento del click
                    if (sc == null) {
                        st.setScene(new Scene(loginRoot, 720, 520));
                    } else {
                        sc.setRoot(loginRoot);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    regCtrl.showError("Errore durante la registrazione: " + ex.getMessage());
                }
            });
        }

        // --- Bottone "Annulla" ---
        Button cancelBtn = (Button) registerRoot.lookup("#cancelButton");
        if (cancelBtn != null) {
            cancelBtn.setOnAction(event -> {
                try {
                    FXMLLoader loginLdr = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/LoginFrame.fxml"));
                    StackPane loginRoot = loginLdr.load();
                    LoginController newLogin = loginLdr.getController();
                    newLogin.setStage(st);
                    newLogin.requestInitialFocus();

                    Scene sc = st.getScene();            // rileggo la scena qui, niente cattura
                    if (sc == null) {
                        st.setScene(new Scene(loginRoot, 720, 520));
                    } else {
                        sc.setRoot(loginRoot);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError("Errore caricando LoginFrame.fxml: " + ex.getMessage());
                }
            });
        }

    } catch (Exception ex) {
        ex.printStackTrace();
        showError("Errore apertura registrazione: " + ex.getMessage());
    }
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
        return (passwordVisibleField != null && passwordVisibleField.isVisible())
                ? passwordVisibleField : passwordField;
    }

    private void updatePasswordVisibility(boolean show) { updatePasswordVisibility(show, true); }

    private void updatePasswordVisibility(boolean show, boolean focus) {
        if (passwordField == null || passwordVisibleField == null) return;

        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);

        if (show) {
            if (focus) { passwordVisibleField.requestFocus(); passwordVisibleField.end(); }
            if (eyeIcon != null)
                eyeIcon.setContent("M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z");
        } else {
            if (focus) { passwordField.requestFocus(); passwordField.end(); }
            if (eyeIcon != null)
                eyeIcon.setContent("M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z");
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

                st.toFront();
                st.requestFocus();

                st.setAlwaysOnTop(true);
                PauseTransition pt = new PauseTransition(Duration.millis(120));
                pt.setOnFinished(ev -> st.setAlwaysOnTop(false));
                pt.play();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }
}
