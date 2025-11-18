package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ChefDao;
import it.unina.foodlab.dao.CorsoDao;
import it.unina.foodlab.dao.SessioneDao;
import it.unina.foodlab.model.Chef;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class LoginController {

    @FXML private VBox card;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private ToggleButton toggleVisibilityButton;
    @FXML private SVGPath eyeIcon;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private ProgressIndicator spinner;

    private final ChefDao chefDao = new ChefDao();
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
    	 if (passwordVisibleField == null || passwordField == null || toggleVisibilityButton == null) {
             return;
         }

         passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
         setPasswordVisible(false);
         updateEyeIcon(false);

         toggleVisibilityButton.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
             setPasswordVisible(isSelected);
             updateEyeIcon(isSelected);
         });

         toggleVisibilityButton.setOpacity(0.95);
         toggleVisibilityButton.setOnMousePressed(e -> toggleVisibilityButton.setOpacity(0.75));
         toggleVisibilityButton.setOnMouseReleased(e -> toggleVisibilityButton.setOpacity(0.95));
    }

    @FXML
    private void handleUsernameEnter() {
        TextField active = getActivePasswordField();
        if (active != null) {
            active.requestFocus();
        }
    }

    @FXML
    private void handlePasswordEnter() {
        onLogin();
    }


    private void updateEyeIcon(boolean visible) {
        if (eyeIcon == null) {
            return;
        }
        if (visible) {
            eyeIcon.setContent("M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z");
        } else {
            eyeIcon.setContent("M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z");
        }
    }

    private TextField getActivePasswordField() {
        if (toggleVisibilityButton != null && toggleVisibilityButton.isSelected()) {
            return passwordVisibleField;
        }
        return passwordField;
    }

    private void setPasswordVisible(boolean visible) {
        if (passwordField == null || passwordVisibleField == null) {
            return;
        }
        if (visible) {
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
        } else {
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
        }
    }


    @FXML
    private void onLogin() {
        String username = safeText(usernameField);
        TextField pwdField = getActivePasswordField();
        String password = pwdField == null ? "" : safeText(pwdField);

        if (username.isEmpty() || password.isEmpty()) {
            showError("Inserisci username e password.");
            shake(card);
            return;
        }

        setBusy(true);

        try {
            boolean authenticated = chefDao.authenticate(username, password);
            if (!authenticated) {
                showError("Username o password errati.");
                shake(card);
                return;
            }
            Chef chef = chefDao.findByUsername(username);
            String cf = chef.getCF_Chef().trim();

            CorsoDao corsoDao = new CorsoDao(cf);
            SessioneDao sessioneDao = new SessioneDao(cf);

            showCorsiScene(chef, corsoDao, sessioneDao);
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Errore durante il login. Riprova.");
            shake(card);
        } finally {
            setBusy(false);
        }
    }

    @FXML
    private void onRegister() {

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/RegisterChefDialog.fxml"));
            Parent root = loader.load();

            RegisterChefController regCtrl = loader.getController();
            regCtrl.setStage(stage);

            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, 720, 520);
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
        } catch (IOException e) {
            e.printStackTrace();
            showError("Impossibile aprire la registrazione.");
        }
    }

    private void showCorsiScene(Chef chef, CorsoDao corsoDao, SessioneDao sessioneDao) throws IOException {
    URL fxml = getClass().getResource("/it/unina/foodlab/ui/Corsi.fxml");
    if (fxml == null) {
        throw new IOException("FXML mancante: /it/unina/foodlab/ui/Corsi.fxml");
    }

    FXMLLoader loader = new FXMLLoader(fxml);
    Parent root = loader.load();

    CorsiPanelController controller = loader.getController();
    if (controller == null) {
        throw new IOException("Controller nullo in Corsi.fxml");
    }

    controller.setDaos(corsoDao, sessioneDao);

    Stage st = stage;

    Scene scene = new Scene(root, 1000, 640);
    st.setTitle("UninaFoodLab - Corsi di " + chef.getNome() + " " + chef.getCognome());
    st.setScene(scene);
    st.show();

    enforceFullScreenLook(st);
}



    private void setBusy(boolean busy) {
        if (loginButton != null) {
            loginButton.setDisable(busy);
            loginButton.setText(busy ? "Verifica..." : "Login");
        }
        if (spinner != null) {
            spinner.setVisible(busy);
            spinner.setManaged(busy);
        }
    }

    private void showError(String message) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        errorLabel.setOpacity(0.0);

        FadeTransition ft = new FadeTransition(Duration.millis(180), errorLabel);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    private void shake(VBox target) {
        if (target == null) {
            return;
        }

        TranslateTransition t1 = new TranslateTransition(Duration.millis(60), target);
        t1.setFromX(0);
        t1.setToX(-8);

        TranslateTransition t2 = new TranslateTransition(Duration.millis(60), target);
        t2.setFromX(-8);
        t2.setToX(8);

        TranslateTransition t3 = new TranslateTransition(Duration.millis(60), target);
        t3.setFromX(8);
        t3.setToX(-4);

        TranslateTransition t4 = new TranslateTransition(Duration.millis(60), target);
        t4.setFromX(-4);
        t4.setToX(0);

        new SequentialTransition(t1, t2, t3, t4).play();
    }

    private void enforceFullScreenLook(Stage stage) {
        if (stage == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                List<Screen> screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
                Screen screen = (screens == null || screens.isEmpty()) ? Screen.getPrimary() : screens.get(0);

                Rectangle2D bounds = screen.getVisualBounds();
                stage.setX(bounds.getMinX());
                stage.setY(bounds.getMinY());
                stage.setWidth(bounds.getWidth());
                stage.setHeight(bounds.getHeight());
                stage.toFront();
                stage.requestFocus();
                stage.setAlwaysOnTop(true);

                PauseTransition pt = new PauseTransition(Duration.millis(120));
                pt.setOnFinished(e -> stage.setAlwaysOnTop(false));
                pt.play();
            } catch (Exception ignore) {
            }
        });
    }

    private static String safeText(TextField tf) {
        if (tf == null || tf.getText() == null) {
            return "";
        }
        return tf.getText().trim();
    }
    
}
