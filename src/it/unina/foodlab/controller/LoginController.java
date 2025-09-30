package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ChefDao;
import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.AppSession;
import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import javafx.geometry.Rectangle2D;
import javafx.stage.Modality;
import javafx.stage.Screen;

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

    @FXML
    private void initialize() {
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        updatePasswordVisibility(false, false);

        toggleVisibilityButton.selectedProperty().addListener(
                (obs, oldVal, selected) -> updatePasswordVisibility(selected)
        );

        loginButton.setOnAction(e -> handleLogin());
        usernameField.setOnAction(e -> getActivePasswordField().requestFocus());
        passwordField.setOnAction(e -> handleLogin());
        passwordVisibleField.setOnAction(e -> handleLogin());
        registerButton.setOnAction(e -> onRegister());
    }

    /** chiamata da Main dopo il load dell'FXML */
    public void setStage(Stage stage) { this.stage = stage; }

    public void requestInitialFocus() {
        Platform.runLater(() -> usernameField.requestFocus());
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = getActivePasswordField().getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Inserisci username e password");
            shake(card);
            return;
        }

        setBusy(true);

        // Non lanciare eccezioni, gestiamo l'errore internamente
        boolean success = false;
        String errorMessage = "";

        try {
            ChefDao chefDao = new ChefDao();
            if (!chefDao.authenticate(username, password)) {
                errorMessage = "Username o password errati";
            } else {
                Chef chef = chefDao.findByUsername(username);
                if (chef == null || chef.getCF_Chef() == null || chef.getCF_Chef().isBlank()) {
                    errorMessage = "Chef non trovato dopo l'autenticazione";
                } else {
                    // Login OK
                    AppSession.setCfChef(chef.getCF_Chef().trim());

                    CorsoDao corsoDao = new CorsoDao(chef.getCF_Chef());
                    SessioneDao sessioneDao = new SessioneDao(chef.getCF_Chef());

                    FXMLLoader ldr = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/corsi.fxml"));
                    Parent root = ldr.load();
                    CorsiPanelController controller = ldr.getController();
                    controller.setDaos(corsoDao, sessioneDao);

                    String displayName = chef.getNome() != null
                            ? (chef.getNome() + " " + (chef.getCognome() == null ? "" : chef.getCognome())).trim()
                            : chef.getCF_Chef();

                    if (displayName.isBlank()) displayName = chef.getCF_Chef();

                    if (stage != null) {
                        Scene scene = new Scene(root, 1000, 640);
                        stage.setTitle("UninaFoodLab - Corsi di " + displayName);
                        stage.setScene(scene);
                        stage.show();
                        enforceFullScreenLook(stage);
                    }
                    success = true;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            errorMessage = "Errore durante il login: " + ex.getMessage();
        } finally {
            setBusy(false);
        }

        if (!success) {
            showError(errorMessage);
            shake(card);
        }
    }

    


    private void enforceFullScreenLook(Stage stage) {
        Platform.runLater(() -> {
            try {
                // scegli lo screen in cui è attualmente la finestra (multi-monitor friendly)
                Screen screen = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())
                                      .stream().findFirst().orElse(Screen.getPrimary());
                Rectangle2D vb = screen.getVisualBounds();

                stage.setX(vb.getMinX());
                stage.setY(vb.getMinY());
                stage.setWidth(vb.getWidth());
                stage.setHeight(vb.getHeight());

                stage.toFront();
                stage.requestFocus();

                // forza refresh del window manager
                stage.setAlwaysOnTop(true);
                PauseTransition pt = new PauseTransition(Duration.millis(120));
                pt.setOnFinished(ev -> stage.setAlwaysOnTop(false));
                pt.play();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }


    private TextField getActivePasswordField() {
        return passwordVisibleField.isVisible() ? passwordVisibleField : passwordField;
    }

    private void updatePasswordVisibility(boolean show) { updatePasswordVisibility(show, true); }

    private void updatePasswordVisibility(boolean show, boolean focus) {
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);

        if (show) {
            if (focus) { passwordVisibleField.requestFocus(); passwordVisibleField.end(); }
            eyeIcon.setContent("M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z");
        } else {
            if (focus) { passwordField.requestFocus(); passwordField.end(); }
            eyeIcon.setContent("M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z");
        }
    }

    private void showError(String message) {
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
        TranslateTransition t1 = new TranslateTransition(Duration.millis(60), target); t1.setFromX(0);  t1.setToX(-8);
        TranslateTransition t2 = new TranslateTransition(Duration.millis(60), target); t2.setFromX(-8); t2.setToX(8);
        TranslateTransition t3 = new TranslateTransition(Duration.millis(60), target); t3.setFromX(8);  t3.setToX(-4);
        TranslateTransition t4 = new TranslateTransition(Duration.millis(60), target); t4.setFromX(-4); t4.setToX(0);
        new SequentialTransition(t1, t2, t3, t4).play();
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        spinner.setVisible(busy);
        spinner.setManaged(busy);
        loginButton.setText(busy ? "Verifica..." : "Login");
    }

    /**
     * Autentica e carica la vista Corsi da FXML,
     * poi passa i DAO al CorsiPanelController.
     */
    private LoginResult authenticate(String username, String password) {
        try {
            ChefDao chefDao = new ChefDao();
            if (!chefDao.authenticate(username, password)) 
                throw new RuntimeException("Credenziali errate");

            Chef chef = chefDao.findByUsername(username);
            if (chef == null || chef.getCF_Chef() == null || chef.getCF_Chef().isBlank())
                throw new RuntimeException("Chef non trovato dopo l'autenticazione.");

            String cfChef = chef.getCF_Chef().trim();
            AppSession.setCfChef(cfChef);

            CorsoDao corsoDao = new CorsoDao(cfChef);
            SessioneDao sessioneDao = new SessioneDao(cfChef);

            // Carica l'FXML della vista corsi
            java.net.URL fxml = getClass().getResource("/it/unina/foodlab/ui/corsi.fxml");
            if (fxml == null) {
                throw new RuntimeException("FXML non trovato sul classpath: /it/unina/foodlab/ui/corsi.fxml");
            }

            FXMLLoader ldr = new FXMLLoader(fxml);
            Parent root;
            try {
                root = ldr.load();
            } catch (Exception loadEx) {
                loadEx.printStackTrace();
                Throwable cause = loadEx;
                while (cause.getCause() != null) cause = cause.getCause();
                throw new RuntimeException("Errore caricando corsi.fxml: " + cause.getMessage(), loadEx);
            }

            CorsiPanelController controller = ldr.getController();
            if (controller == null) {
                throw new RuntimeException("Controller nullo. Verifica fx:controller in corsi.fxml");
            }
            controller.setDaos(corsoDao, sessioneDao);

            // Calcolo il displayName dello chef
            String displayName = chef.getNome() != null
                    ? (chef.getNome() + " " + (chef.getCognome() == null ? "" : chef.getCognome())).trim()
                    : cfChef;
            if (displayName.isBlank()) displayName = cfChef;

            // Imposto il fade-in sulla root prima di restituirla
            root.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(600), root);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
            fadeIn.play();

            return new LoginResult(root, displayName);

        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    
    @FXML
    private void onRegister() {
        try {
            // Costruisci il percorso assoluto al file FXML della registrazione
            java.io.File fxmlFile = new java.io.File("src/it/unina/foodlab/ui/RegisterChefDialog.fxml");
            if (!fxmlFile.exists()) {
                showError("File FXML non trovato: " + fxmlFile.getAbsolutePath());
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlFile.toURI().toURL());
            StackPane registerRoot = loader.load();
            RegisterChefController controller = loader.getController();

            // Ottieni lo stage e la scena corrente
            Stage stage = (Stage) loginButton.getScene().getWindow();
            Scene scene = stage.getScene();

            // Sostituisci il root della scena con la view di registrazione
            scene.setRoot(registerRoot);

            // Configura pulsante Registrati
            Button registerBtn = (Button) registerRoot.lookup("#registerButton");
            if (registerBtn != null) {
                registerBtn.setOnAction(event -> {
                    Chef chef = controller.getChef();
                    if (chef == null) {
                        controller.showError("Compila tutti i campi correttamente");
                    } else {
                        try {
                            new ChefDao().register(chef);

                            // Torna alla schermata di login
                            java.io.File loginFile = new java.io.File("src/it/unina/foodlab/ui/LoginFrame.fxml");
                            FXMLLoader loginLoader = new FXMLLoader(loginFile.toURI().toURL());
                            StackPane loginRoot = loginLoader.load();
                            scene.setRoot(loginRoot);

                        } catch (Exception ex) {
                            controller.showError("Errore durante la registrazione: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                });
            }
        // Configura pulsante Annulla (torna alla scena di login)
        Button cancelBtn = (Button) registerRoot.lookup("#cancelButton");
        if (cancelBtn != null) {
            cancelBtn.setOnAction(event -> {
                try {
                    java.io.File loginFxml = new java.io.File("src/it/unina/foodlab/ui/LoginFrame.fxml");
                    FXMLLoader loginLoader = new FXMLLoader(loginFxml.toURI().toURL());
                    StackPane loginRoot = loginLoader.load();
                    scene.setRoot(loginRoot);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError("Errore caricando Login.fxml: " + ex.getMessage());
                }
            });
        }

    } catch (Exception ex) {
        ex.printStackTrace();
        showError("Errore apertura registrazione: " + ex.getMessage());
    }
}








    public static class LoginResult {
        private final Parent root;
        private final String chefDisplayName;
        public LoginResult(Parent root, String chefDisplayName) { this.root = root; this.chefDisplayName = chefDisplayName; }
        public Parent getRoot() { return root; }
        public String getChefDisplayName() { return chefDisplayName; }
    }
}