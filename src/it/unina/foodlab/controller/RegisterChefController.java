package it.unina.foodlab.controller;

import it.unina.foodlab.model.Chef;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.shape.SVGPath;

import java.time.LocalDate;

public class RegisterChefController {

    @FXML private TextField nomeField;
    @FXML private TextField cognomeField;
    @FXML private TextField cfField;
    @FXML private TextField usernameField;

    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private ToggleButton toggleVisibilityButton;
    @FXML private SVGPath eyeIcon;

    @FXML private DatePicker nascitaPicker;
    @FXML private Label errorLabel;

    private static final String NAME_REGEX = "^[A-Za-zÀ-ÖØ-öø-ÿ' -]{2,50}$";
    private static final String EYE_OPEN_ICON =
            "M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z";
    private static final String EYE_SLASH_ICON =
            "M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z";

    @FXML
    private void initialize() {
        initErrorLabel();
        initDatePicker();
        initPasswordToggle();
        initEnterTraversal();
    }

    /** Costruisce il modello Chef. Ritorna null e mostra errore se qualcosa non va. */
    public Chef getChef() {
        clearError();

        String nome = safeText(nomeField);
        String cognome = safeText(cognomeField);
        String cf = safeText(cfField);
        String username = safeText(usernameField);
        String password = passwordField != null && passwordField.getText() != null ? passwordField.getText() : "";
        LocalDate nascita = nascitaPicker != null ? nascitaPicker.getValue() : null;

        if (nome.isEmpty() || cognome.isEmpty() || cf.isEmpty()
                || username.isEmpty() || password.isEmpty() || nascita == null) {
            showError("Tutti i campi sono obbligatori.");
            focusFirstEmpty();
            return null;
        }
        if (!nome.matches(NAME_REGEX)) { showError("Nome non valido."); requestFocus(nomeField); return null; }
        if (!cognome.matches(NAME_REGEX)) { showError("Cognome non valido."); requestFocus(cognomeField); return null; }
        if (nascita.isAfter(LocalDate.now())) { showError("Data di nascita futura non ammessa."); nascitaPicker.requestFocus(); return null; }
        if (!cf.matches("^CH\\d{3}$")) { showError("CF deve iniziare con 'CH' seguito da 3 cifre."); requestFocus(cfField); return null; }

        Chef chef = new Chef();
        chef.setNome(capitalize(nome));
        chef.setCognome(capitalize(cognome));
        chef.setCF_Chef(cf);
        chef.setUsername(username);
        chef.setPassword(password); // Nota: in produzione andrebbe hashata
        chef.setNascita(nascita);
        return chef;
    }

    /* ==================== INIT HELPERS ==================== */

    private void initErrorLabel() {
        if (errorLabel != null) { errorLabel.setManaged(false); errorLabel.setVisible(false); }
    }

    private void initDatePicker() {
        if (nascitaPicker == null) return;
        nascitaPicker.setShowWeekNumbers(false);
        nascitaPicker.setEditable(false);
        nascitaPicker.setPromptText("Data di nascita");
        nascitaPicker.setDayCellFactory(dp -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setDisable(false); setStyle(""); return; }
                boolean future = item.isAfter(LocalDate.now());
                setDisable(future);
                setStyle(future ? "-fx-opacity:0.4;" : "");
            }
        });
        nascitaPicker.focusedProperty().addListener((obs, o, focused) -> { if (focused) nascitaPicker.show(); });
        nascitaPicker.setOnMouseClicked(e -> nascitaPicker.show());
    }

    private void initPasswordToggle() {
        if (passwordField == null || passwordVisibleField == null) return;

        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        updatePasswordVisibility(false, false);

        if (toggleVisibilityButton != null) {
            toggleVisibilityButton.selectedProperty().addListener((obs, old, sel) -> updatePasswordVisibility(sel, true));
        }
    }

    private void initEnterTraversal() {
        if (nomeField != null && cognomeField != null) {
            nomeField.setOnAction(new EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(ActionEvent event) { cognomeField.requestFocus(); }
            });
        }
        if (cognomeField != null && cfField != null) {
            cognomeField.setOnAction(new EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(ActionEvent event) { cfField.requestFocus(); }
            });
        }
        if (cfField != null && usernameField != null) {
            cfField.setOnAction(new EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(ActionEvent event) { usernameField.requestFocus(); }
            });
        }
        if (usernameField != null && passwordField != null) {
            usernameField.setOnAction(new EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(ActionEvent event) { passwordField.requestFocus(); }
            });
        }
        if (passwordField != null && nascitaPicker != null) {
            passwordField.setOnAction(new EventHandler<javafx.event.ActionEvent>() {
                @Override public void handle(ActionEvent event) { nascitaPicker.requestFocus(); }
            });
        }
    }

    /* ==================== UI helpers ==================== */

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

    public void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setStyle("-fx-text-fill:#bf616a;");
            errorLabel.setManaged(true);
            errorLabel.setVisible(true);
        }
    }

    public void clearError() {
        if (errorLabel != null) {
            errorLabel.setText("");
            errorLabel.setManaged(false);
            errorLabel.setVisible(false);
        }
    }

    private void focusFirstEmpty() {
        if (isEmpty(nomeField)) { requestFocus(nomeField); return; }
        if (isEmpty(cognomeField)) { requestFocus(cognomeField); return; }
        if (isEmpty(cfField)) { requestFocus(cfField); return; }
        if (isEmpty(usernameField)) { requestFocus(usernameField); return; }
        if (passwordField != null && (passwordField.getText() == null || passwordField.getText().trim().isEmpty())) {
            passwordField.requestFocus(); return;
        }
        if (nascitaPicker != null && nascitaPicker.getValue() == null) nascitaPicker.requestFocus();
    }

    private static boolean isEmpty(TextField tf) {
        return tf == null || tf.getText() == null || tf.getText().trim().isEmpty();
    }

    private static String safeText(TextField tf) {
        return tf == null || tf.getText() == null ? "" : tf.getText().trim();
    }

    private static String capitalize(String s) {
        if (s == null || s.trim().isEmpty()) return s;
        String t = s.trim();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }

    private void requestFocus(TextField tf) {
        if (tf != null) { tf.requestFocus(); tf.selectAll(); }
    }
}
