package it.unina.foodlab.controller;

import it.unina.foodlab.model.Chef;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.time.LocalDate;

public class RegisterChefController {

    @FXML private TextField nomeField;
    @FXML private TextField cognomeField;
    @FXML private TextField cfField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private DatePicker nascitaPicker;
    @FXML private Label errorLabel;

    @FXML
    private void initialize() {
        if (errorLabel != null) errorLabel.setVisible(false);
    }

    /** Restituisce un oggetto Chef creato dai campi compilati, o null se ci sono errori */
    private static final String NAME_REGEX = "^[A-Za-zÀ-ÖØ-öø-ÿ' -]{2,50}$";

    public Chef getChef() {
        String nome = safeText(nomeField);
        String cognome = safeText(cognomeField);
        String cf = safeText(cfField);
        String username = safeText(usernameField);
        String password = passwordField != null ? passwordField.getText() : "";
        LocalDate nascita = nascitaPicker != null ? nascitaPicker.getValue() : null;

        if (nome.isEmpty() || cognome.isEmpty() || cf.isEmpty()
                || username.isEmpty() || password.isEmpty() || nascita == null) {
            showError("Tutti i campi sono obbligatori");
            return null;
        }

        if (!nome.matches(NAME_REGEX)) {
            showError("Nome non valido: almeno 2 lettere, solo caratteri alfabetici, spazi, apostrofi o trattini.");
            return null;
        }
        if (!cognome.matches(NAME_REGEX)) {
            showError("Cognome non valido: almeno 2 lettere, solo caratteri alfabetici, spazi, apostrofi o trattini.");
            return null;
        }

        // opzionale: età minima
        if (nascita.isAfter(LocalDate.now())) {
            showError("Data di nascita non può essere nel futuro.");
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

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    public void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setStyle("-fx-text-fill: #bf616a;");
            errorLabel.setVisible(true);
        }
    }

    private static String safeText(TextField tf) {
        return tf == null ? "" : tf.getText().trim();
    }
}