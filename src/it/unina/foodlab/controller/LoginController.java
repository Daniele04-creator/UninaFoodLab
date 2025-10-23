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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

/**
 * Controller del Login.
 * - Nessun CSS esterno: tutto via FXML inline.
 * - Niente stream/lambda: handler e listener espliciti.
 * - Logica invariata, UX più robuste.
 */
public class LoginController {

    /* =================== Costanti =================== */

    private static final String FXML_LOGIN     = "/it/unina/foodlab/ui/LoginFrame.fxml";
    private static final String FXML_REGISTRAZ = "/it/unina/foodlab/ui/RegisterChefDialog.fxml";
    private static final String FXML_CORSI     = "/it/unina/foodlab/ui/Corsi.fxml";

    private static final String MSG_INSERISCI_CRED = "Inserisci username e password.";
    private static final String MSG_ERR_LOGIN      = "Username o password errati.";
    private static final String MSG_ERR_AUTENT     = "Errore durante il login. Riprova.";
    private static final String MSG_CHEF_MANCANTE  = "Chef non trovato dopo l'autenticazione.";
    private static final String MSG_STAGE_NULL     = "Stage non disponibile.";
    private static final String MSG_OPEN_REG_ERR   = "Impossibile aprire la registrazione.";
    private static final String TITLE_APP          = "UninaFoodLab - Corsi di ";

    // SVG occhio (aperto/chiuso)
    private static final String EYE_OPEN  = "M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z";
    private static final String EYE_SLASH = "M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z";

    /* =================== UI =================== */

    @FXML private VBox card;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField; // quando "mostra" è attivo
    @FXML private ToggleButton toggleVisibilityButton;
    @FXML private SVGPath eyeIcon;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private ProgressIndicator spinner;

    /* =================== Altri membri =================== */

    private final ChefDao chefDao = new ChefDao();
    private Stage stage;

    /* =================== API pubbliche =================== */

    public void setStage(Stage stage) { this.stage = stage; }

    public void requestInitialFocus() {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                if (usernameField != null) usernameField.requestFocus();
            }
        });
    }

    /* =================== Ciclo di vita =================== */

    @FXML
    private void initialize() {
        initPasswordToggle();
        hideError();
        bindActions();
    }

    /* =================== Inizializzazioni =================== */

    private void bindActions() {
        if (loginButton != null) {
            loginButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) { onLogin(); }
            });
        }

        if (registerButton != null) {
            registerButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) { onRegister(); }
            });
        }

        if (usernameField != null) {
            usernameField.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) {
                    TextField active = getActivePasswordField();
                    if (active != null) active.requestFocus();
                }
            });
        }

        if (passwordField != null) {
            passwordField.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) { onLogin(); }
            });
        }
        if (passwordVisibleField != null) {
            passwordVisibleField.setOnAction(new EventHandler<ActionEvent>() {
                @Override public void handle(ActionEvent event) { onLogin(); }
            });
        }
    }

    /** Configura il toggle "mostra/nascondi" password SENZA lambda. */
    private void initPasswordToggle() {
        if (passwordVisibleField == null || passwordField == null || toggleVisibilityButton == null) return;

        // Sincronizza i due campi
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());

        // Stato iniziale: nascosta
        setPasswordVisible(false);

        // Listener selectedProperty
        toggleVisibilityButton.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> obs, Boolean was, Boolean selected) {
                setPasswordVisible(Boolean.TRUE.equals(selected));
                toggleVisibilityButton.setOpacity(0.95);
            }
        });

        // Effetto pressione (tattile) senza lambda
        toggleVisibilityButton.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent event) { toggleVisibilityButton.setOpacity(0.75); }
        });
        toggleVisibilityButton.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent event) { toggleVisibilityButton.setOpacity(0.95); }
        });
    }

    private void setPasswordVisible(boolean show) {
        if (passwordField == null || passwordVisibleField == null) return;

        if (show) {
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            if (eyeIcon != null) eyeIcon.setContent(EYE_OPEN);
            if (toggleVisibilityButton != null && toggleVisibilityButton.getTooltip() != null) {
                toggleVisibilityButton.getTooltip().setText("Nascondi password");
            }
        } else {
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            if (eyeIcon != null) eyeIcon.setContent(EYE_SLASH);
            if (toggleVisibilityButton != null && toggleVisibilityButton.getTooltip() != null) {
                toggleVisibilityButton.getTooltip().setText("Mostra password");
            }
        }
    }

    /* =================== Azioni =================== */

    private void onLogin() {
        String username = safeText(usernameField);
        TextField pwdField = getActivePasswordField();
        String password = (pwdField == null) ? "" : safeText(pwdField);

        if (username.length() == 0 || password.length() == 0) {
            showError(MSG_INSERISCI_CRED);
            shake(card);
            return;
        }

        setBusy(true);
        String err = null;

        try {
            boolean ok = chefDao.authenticate(username, password);
            if (!ok) {
                err = MSG_ERR_LOGIN;
            } else {
                Chef chef = chefDao.findByUsername(username);
                if (chef == null || safeText(chef.getCF_Chef()).length() == 0) {
                    err = MSG_CHEF_MANCANTE;
                } else {
                    String cf = chef.getCF_Chef().trim();
                    AppSession.setCfChef(cf);

                    CorsoDao corsoDao = new CorsoDao(cf);
                    SessioneDao sessioneDao = new SessioneDao(cf);

                    showCorsiScene(chef, corsoDao, sessioneDao);
                    return; // successo
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();

            err = MSG_ERR_AUTENT;
        } finally {
            setBusy(false);
        }

        if (err != null) {
            showError(err);
            shake(card);
        }
    }

    private void onRegister() {
        try {
            FXMLLoader ldr = new FXMLLoader(getResource(FXML_REGISTRAZ));
            Parent registerRoot = ldr.load();

            final Stage st = resolveStage();
            if (st == null) { showError(MSG_STAGE_NULL); return; }

            Scene current = st.getScene();
            if (current == null) st.setScene(new Scene(registerRoot, 720, 520));
            else current.setRoot(registerRoot);

            final Button registerBtn = (Button) registerRoot.lookup("#registerButton");
            final Button cancelBtn   = (Button) registerRoot.lookup("#cancelButton");
            final RegisterChefController regCtrl = ldr.getController();

            if (registerBtn != null) {
                registerBtn.setOnAction(new EventHandler<ActionEvent>() {
                    @Override public void handle(ActionEvent event) {
                        Chef chef = regCtrl.getChef();
                        if (chef == null) return;

                        try {
                            ChefDao dao = new ChefDao();
                            ChefDao.RegisterOutcome out = dao.register(chef);

                            if (out == ChefDao.RegisterOutcome.OK) {
                                try { loadLoginScene(st); }
                                catch (IOException e) { regCtrl.showError("Registrazione completata, ma errore nel ritorno al login."); }
                            } else if (out == ChefDao.RegisterOutcome.DUPLICATE_CF) {
                                regCtrl.showError("Esiste già un utente registrato con questo codice fiscale.");
                            } else if (out == ChefDao.RegisterOutcome.DUPLICATE_USERNAME) {
                                regCtrl.showError("Questo username è già in uso. Scegline un altro.");
                            } else {
                                regCtrl.showError("Errore durante la registrazione. Controlla i dati e riprova.");
                            }

                        } catch (Exception ex) {
                        	ex.printStackTrace();
                            regCtrl.showError("Errore durante la registrazione. Controlla i dati e riprova.");
                        }
                    }
                });
            }

            if (cancelBtn != null) {
                cancelBtn.setOnAction(new EventHandler<ActionEvent>() {
                    @Override public void handle(ActionEvent event) {
                        try { loadLoginScene(st); }
                        catch (IOException e) { showError("Errore caricando il login."); }
                    }
                });
            }

        } catch (IOException ex) {
        	 ex.printStackTrace();
            showError(MSG_OPEN_REG_ERR);
        }
    }

    /* =================== Navigazione =================== */

    private void showCorsiScene(Chef chef, CorsoDao corsoDao, SessioneDao sessioneDao) throws IOException {
        URL fxml = getResource(FXML_CORSI);
        if (fxml == null) throw new IOException("FXML mancante: " + FXML_CORSI);

        FXMLLoader ldr = new FXMLLoader(fxml);
        Parent root = ldr.load();
        CorsiPanelController controller = ldr.getController();
        Objects.requireNonNull(controller, "Controller nullo in Corsi.fxml");
        controller.setDaos(corsoDao, sessioneDao);

        Stage st = resolveStage();
        if (st == null) throw new IOException("Stage non disponibile");

        Scene scene = new Scene(root, 1000, 640);
        st.setTitle(TITLE_APP + buildDisplayName(chef));
        st.setScene(scene);
        st.show();

        enforceFullScreenLook(st);
    }

    private void loadLoginScene(Stage st) throws IOException {
        FXMLLoader ldr = new FXMLLoader(getResource(FXML_LOGIN));
        Parent root = ldr.load();

        LoginController ctrl = ldr.getController();
        ctrl.setStage(st);
        ctrl.requestInitialFocus();

        Scene sc = st.getScene();
        if (sc == null) st.setScene(new Scene(root, 720, 520));
        else sc.setRoot(root);
    }

    /* =================== Helpers UI =================== */

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
        errorLabel.setOpacity(0.0);

        FadeTransition ft = new FadeTransition(Duration.millis(180), errorLabel);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    private void hideError() {
        if (errorLabel == null) return;
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private void shake(VBox target) {
        if (target == null) return;

        TranslateTransition t1 = new TranslateTransition(Duration.millis(60), target);
        t1.setFromX(0);  t1.setToX(-8);
        TranslateTransition t2 = new TranslateTransition(Duration.millis(60), target);
        t2.setFromX(-8); t2.setToX(8);
        TranslateTransition t3 = new TranslateTransition(Duration.millis(60), target);
        t3.setFromX(8);  t3.setToX(-4);
        TranslateTransition t4 = new TranslateTransition(Duration.millis(60), target);
        t4.setFromX(-4); t4.setToX(0);

        new SequentialTransition(t1, t2, t3, t4).play();
    }

    private void enforceFullScreenLook(final Stage st) {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                try {
                    List<Screen> list = Screen.getScreensForRectangle(st.getX(), st.getY(), st.getWidth(), st.getHeight());
                    Screen screen = (list == null || list.isEmpty()) ? Screen.getPrimary() : list.get(0);

                    Rectangle2D vb = screen.getVisualBounds();
                    st.setX(vb.getMinX());
                    st.setY(vb.getMinY());
                    st.setWidth(vb.getWidth());
                    st.setHeight(vb.getHeight());
                    st.toFront();
                    st.requestFocus();

                    st.setAlwaysOnTop(true);
                    PauseTransition pt = new PauseTransition(Duration.millis(120));
                    pt.setOnFinished(new EventHandler<ActionEvent>() {
                        @Override public void handle(ActionEvent event) {
                            st.setAlwaysOnTop(false);
                        }
                    });
                    pt.play();
                } catch (Exception ignore) { }
            }
        });
    }

    /* =================== Utils =================== */

    private static String safeText(TextField tf) {
        if (tf == null || tf.getText() == null) return "";
        return tf.getText().trim();
    }

    private static String safeText(String s) { return (s == null) ? "" : s.trim(); }

    private String buildDisplayName(Chef c) {
        if (c == null) return "";
        String nome = safeText(c.getNome());
        String cogn = safeText(c.getCognome());
        String full = (nome + " " + cogn).trim();
        return (full.length() > 0) ? full : safeText(c.getCF_Chef());
    }

    private static URL getResource(String path) {
        return LoginController.class.getResource(path);
    }
}
