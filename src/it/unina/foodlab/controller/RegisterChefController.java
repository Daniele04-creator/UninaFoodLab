package it.unina.foodlab.controller;

import it.unina.foodlab.model.Chef;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.shape.SVGPath; //pol

import java.time.LocalDate;

public class RegisterChefController {

    @FXML private TextField nomeField;
    @FXML private TextField cognomeField;
    @FXML private TextField cfField;
    @FXML private TextField usernameField;

    // Password show/hide
    @FXML private PasswordField passwordField;        // campo nascosto
    @FXML private TextField passwordVisibleField;     // campo visibile
    @FXML private ToggleButton toggleVisibilityButton;
    @FXML private SVGPath eyeIcon;

    @FXML private DatePicker nascitaPicker;
    @FXML private Label errorLabel;

    private static final String NAME_REGEX = "^[A-Za-zÀ-ÖØ-öø-ÿ' -]{2,50}$";

    @FXML
    private void initialize() {
        // ---- Error label
        if (errorLabel != null) { errorLabel.setManaged(false); errorLabel.setVisible(false); }

        // ---- DatePicker: solo calendario, niente settimane, niente futuro
        if (nascitaPicker != null) {
            nascitaPicker.setShowWeekNumbers(false);
            nascitaPicker.setEditable(false); // evita editing manuale
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
            // Apri il popup quando ottiene focus o viene cliccato
            nascitaPicker.focusedProperty().addListener((obs, o, focused) -> { if (focused) nascitaPicker.show(); });
            nascitaPicker.setOnMouseClicked(e -> nascitaPicker.show());
        }

        // ---- Password: toggle show/hide
        if (passwordField != null && passwordVisibleField != null) {
            // testo sincronizzato
            passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
            // stato iniziale: nascosto
            updatePasswordVisibility(false, false);

            if (toggleVisibilityButton != null) {
                toggleVisibilityButton.selectedProperty().addListener((obs, old, sel) -> updatePasswordVisibility(sel, true));
            }
        }

        // Enter → focus al prossimo
        setEnterFocusTraversal();
    }

    /** Costruisce il modello Chef. Ritorna null e mostra errore se qualcosa non va. */
    public Chef getChef() {
        clearError();

        String nome = safeText(nomeField);
        String cognome = safeText(cognomeField);
        String cf = safeText(cfField);
        String username = safeText(usernameField);
        String password = (passwordField != null && passwordField.getText() != null) ? passwordField.getText() : "";
        LocalDate nascita = nascitaPicker != null ? nascitaPicker.getValue() : null;

        if (nome.isEmpty() || cognome.isEmpty() || cf.isEmpty()
                || username.isEmpty() || password.isEmpty() || nascita == null) {
            showError("Tutti i campi sono obbligatori.");
            focusFirstEmpty();
            return null;
        }
        if (!nome.matches(NAME_REGEX)) { showError("Nome non valido."); requestFocus(nomeField); return null; }
        if (!cognome.matches(NAME_REGEX)) { showError("Cognome non valido."); requestFocus(cognomeField); return null; }
        if (nascita.isAfter(LocalDate.now())) { showError("Data di nascita nel futuro non ammessa."); nascitaPicker.requestFocus(); return null; }

        if (!cf.matches("^CH\\d{3}$")) {
            showError("Codice fiscale deve iniziare con 'CH' seguito da 3 cifre");
            requestFocus(cfField);
            return null;
        }

        Chef chef = new Chef();
        chef.setNome(capitalize(nome));
        chef.setCognome(capitalize(cognome));
        chef.setCF_Chef(cf);
        chef.setUsername(username);
        chef.setPassword(password);
        chef.setNascita(nascita);
        return chef;
    }

    /* ==================== UI helpers ==================== */

    private void updatePasswordVisibility(boolean show) { updatePasswordVisibility(show, true); }
    private void updatePasswordVisibility(boolean show, boolean focus) {
        if (passwordField == null || passwordVisibleField == null) return;

        // switch visibilità/managed
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);

        // focus & caret
        if (show) {
            if (focus) { passwordVisibleField.requestFocus(); passwordVisibleField.end(); }
            if (eyeIcon != null) {
                // icona "occhio aperto"
                eyeIcon.setContent("M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z");
            }
        } else {
            if (focus) { passwordField.requestFocus(); passwordField.end(); }
            if (eyeIcon != null) {
                // icona "occhio sbarrato"
                eyeIcon.setContent("M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z");
            }
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

    private static String safeText(TextField tf) { return tf == null ? "" : tf.getText().trim(); }
    private static String capitalize(String s) { return (s == null || s.isBlank()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(); }
    private void requestFocus(TextField tf) { if (tf != null) { tf.requestFocus(); tf.selectAll(); } }

    private void focusFirstEmpty() {
        if (isEmpty(nomeField)) { requestFocus(nomeField); return; }
        if (isEmpty(cognomeField)) { requestFocus(cognomeField); return; }
        if (isEmpty(cfField)) { requestFocus(cfField); return; }
        if (isEmpty(usernameField)) { requestFocus(usernameField); return; }
        if (passwordField != null && (passwordField.getText() == null || passwordField.getText().isBlank())) { passwordField.requestFocus(); return; }
        if (nascitaPicker != null && nascitaPicker.getValue() == null) nascitaPicker.requestFocus();
    }
    private boolean isEmpty(TextField tf) { return tf == null || tf.getText() == null || tf.getText().trim().isEmpty(); }

    private void setEnterFocusTraversal() {
        if (nomeField != null) nomeField.setOnAction(e -> cognomeField.requestFocus());
        if (cognomeField != null) cognomeField.setOnAction(e -> cfField.requestFocus());
        if (cfField != null) cfField.setOnAction(e -> usernameField.requestFocus());
        if (usernameField != null) usernameField.setOnAction(e -> { if (passwordField != null) passwordField.requestFocus(); });
        if (passwordField != null) passwordField.setOnAction(e -> { if (nascitaPicker != null) nascitaPicker.requestFocus(); });
    }
}
