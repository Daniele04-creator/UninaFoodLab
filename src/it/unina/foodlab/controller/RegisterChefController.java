package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ChefDao;
import it.unina.foodlab.model.Chef;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import java.io.IOException;
import java.time.LocalDate;

public class RegisterChefController {

    @FXML private TextField nomeField;
    @FXML private TextField cognomeField;
    @FXML private TextField cfField;
    @FXML private TextField usernameField;
    @FXML private TextField passwordField;
    @FXML private DatePicker nascitaPicker;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;

    private final ChefDao chefDao = new ChefDao();
    private Stage stage;

    private static final String REGEX_NOME     = "^[A-Za-zÀ-ÖØ-öø-ÿ'`\\-\\s]{2,50}$";
    private static final String REGEX_COGNOME  = REGEX_NOME;
    private static final String REGEX_CF_CHEF  = "^CH\\d{3}$";
    private static final String REGEX_USERNAME = "^[A-Za-z0-9_.]{3,20}$";
    private static final int    MIN_PWD_LEN    = 6;


    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void showCalendarPopup(MouseEvent e) {
        nascitaPicker.show();
        nascitaPicker.requestFocus();
    }

    @FXML
    private void goNextFromNome() {
        cognomeField.requestFocus();
    }

    @FXML
    private void goNextFromCognome() {
        cfField.requestFocus();
    }

    @FXML
    private void goNextFromCf() {
        nascitaPicker.requestFocus();
    }

    @FXML
    private void goNextFromNascita() {
        usernameField.requestFocus();
    }

    @FXML
    public void onRegister() {
        

        Chef chef = getChef();
        if (chef == null) {
            return;
        }

        setUiDisabled(true);
        try {
            ChefDao.RegisterOutcome esito = chefDao.register(chef);

            switch (esito) {
                case OK -> {
                    showInfo("Registrazione completata", "Chef registrato correttamente.");
                }
                case DUPLICATE_CF -> {
                    showError("Codice Chef già presente. Scegli un altro codice (es. CH123).");
                }
                case DUPLICATE_USERNAME -> {
                    showError("Username già in uso. Scegline un altro.");
                }
                default -> showError("Registrazione non riuscita. Riprovare.");
            }
        } catch (Exception ex) {
        	ex.printStackTrace(); 
            showError("Errore durante la registrazione. Riprova.");
        } finally {
            setUiDisabled(false);
        }
    }

    @FXML
    private void onCancel() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/it/unina/foodlab/ui/LoginFrame.fxml"));
            Parent root = loader.load();

            LoginController login = loader.getController();
            login.setStage(stage);

            Stage st = stage;
            Scene scene = st.getScene();
            if (scene == null) {
                st.setScene(new Scene(root, 700, 540));
            } else {
                scene.setRoot(root);
            }
        } catch (IOException e) {
            showError("Errore nel caricamento della schermata di login.");
        }
    }

    private void setUiDisabled(boolean disabled) {
        registerButton.setDisable(disabled);
        nomeField.setDisable(disabled);
        cognomeField.setDisable(disabled);
        cfField.setDisable(disabled);
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        nascitaPicker.setDisable(disabled);
    }

    private Chef getChef() {
        String nome = safeText(nomeField);
        String cognome = safeText(cognomeField);
        String cf = safeText(cfField);
        String username = safeText(usernameField);
        String pwd = safeText(passwordField);
        LocalDate nasc = nascitaPicker.getValue();

        String err = validateRequired(nome, "Inserisci il nome.");
        if (err != null) {
            showError(err);
            nomeField.requestFocus();
            return null;
        }
        if (!nome.matches(REGEX_NOME)) {
            showError("Nome non valido.");
            nomeField.requestFocus();
            return null;
        }

        err = validateRequired(cognome, "Inserisci il cognome.");
        if (err != null) {
            showError(err);
            cognomeField.requestFocus();
            return null;
        }
        if (!cognome.matches(REGEX_COGNOME)) {
            showError("Cognome non valido.");
            cognomeField.requestFocus();
            return null;
        }

        err = validateRequired(cf, "Inserisci il codice chef.");
        if (err != null) {
            showError(err);
            cfField.requestFocus();
            return null;
        }
        if (!cf.matches(REGEX_CF_CHEF)) {
            showError("Codice Chef nel formato CH### (es. CH123).");
            cfField.requestFocus();
            return null;
        }

        if (nasc == null) {
            showError("Inserisci la data di nascita.");
            nascitaPicker.requestFocus();
            return null;
        }
        if (nasc.isAfter(LocalDate.now())) {
            showError("La data di nascita non può essere futura.");
            nascitaPicker.requestFocus();
            return null;
        }

        err = validateRequired(username, "Inserisci lo username.");
        if (err != null) {
            showError(err);
            usernameField.requestFocus();
            return null;
        }
        if (!username.matches(REGEX_USERNAME)) {
            showError("Username non valido (3-20, lettere/numeri/._).");
            usernameField.requestFocus();
            return null;
        }

        if (pwd.length() < MIN_PWD_LEN) {
            showError("La password deve avere almeno " + MIN_PWD_LEN + " caratteri.");
            passwordField.requestFocus();
            return null;
        }

        Chef c = new Chef();
        c.setNome(capitalize(nome));
        c.setCognome(capitalize(cognome));
        c.setCF_Chef(cf);
        c.setNascita(nasc);
        c.setUsername(username);
        c.setPassword(pwd);
        return c;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(
                getClass().getResource("/it/unina/foodlab/util/dark-theme.css").toExternalForm()
        );
        pane.getStyleClass().add("dark-alert");

        alert.showAndWait();
    }

    public void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }


    private static String validateRequired(String v, String m) {
        return (v == null || v.trim().isEmpty()) ? m : null;
    }

    private static String safeText(TextField tf) {
        return (tf == null || tf.getText() == null) ? "" : tf.getText().trim();
    }

    private static String capitalize(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }
}
