package it.unina.foodlab.controller;

import it.unina.foodlab.model.Chef;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

import java.time.LocalDate;

public class RegisterChefController {

    /* ====== FXML ====== */
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

    /* ====== Icone occhio ====== */
    private static final String EYE_OPEN  = "M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z";
    private static final String EYE_SLASH = "M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z";

    /* ====== Regole semplici ====== */
    private static final String NAME_REGEX = "^[A-Za-zÀ-ÖØ-öø-ÿ' -]{2,50}$";
    private static final int    MIN_PWD    = 6;

    /* ====== Palette scura coerente ====== */
    private static final String BG_DARK      = "#1f2937"; // popup/card
    private static final String BG_DARK_2    = "#2b3542"; // oggi
    private static final String TEXT_LIGHT   = "#e5e7eb";
    private static final String TEXT_MUTED   = "#9aa5b1";
    private static final String ACCENT_GREEN = "#10b981";
    private static final String BORDER_SOFT  = "rgba(255,255,255,0.10)";

    @FXML
    private void initialize() {
        // Toggle mostra/nascondi password (occhio senza riquadro)
        initPasswordToggle();

        // DatePicker scuro + popup scuro + celle scure
        tintDatePickerDark(nascitaPicker);

        // Rendi trasparente il bottoncino del DatePicker (a destra)
        makeDatePickerButtonTransparent(nascitaPicker);

        // Aggancia apri-popup su click (sia sul DatePicker sia sul suo editor)
        hookOpenOnClick(nascitaPicker);

        clearError();
        setEnterFocusTraversal();
    }

    /** Costruisce il modello; se errore ritorna null e mostra messaggio. */
    public Chef getChef() {
        clearError();

        String nome    = safeText(nomeField);
        String cognome = safeText(cognomeField);
        String cf      = safeText(cfField);
        String user    = safeText(usernameField);
        String pwd     = passwordField.getText() == null ? "" : passwordField.getText();
        LocalDate nasc = nascitaPicker.getValue();

        if (nome.isEmpty() || cognome.isEmpty() || cf.isEmpty()
                || user.isEmpty() || pwd.isEmpty() || nasc == null) {
            showError("Tutti i campi sono obbligatori.");
            focusFirstEmpty();
            return null;
        }
        if (!nome.matches(NAME_REGEX))      { showError("Nome non valido.");     nomeField.requestFocus(); nomeField.selectAll(); return null; }
        if (!cognome.matches(NAME_REGEX))   { showError("Cognome non valido.");  cognomeField.requestFocus(); cognomeField.selectAll(); return null; }
        if (nasc.isAfter(LocalDate.now()))  { showError("Data di nascita futura non ammessa."); nascitaPicker.requestFocus(); return null; }
        if (!cf.matches("^CH\\d{3}$"))      { showError("Codice Chef nel formato CH### (es. CH123)."); cfField.requestFocus(); cfField.selectAll(); return null; }
        if (pwd.length() < MIN_PWD)         { showError("La password deve avere almeno " + MIN_PWD + " caratteri."); passwordField.requestFocus(); passwordField.selectAll(); return null; }

        Chef c = new Chef();
        c.setNome(capitalize(nome));
        c.setCognome(capitalize(cognome));
        c.setCF_Chef(cf);
        c.setUsername(user);
        c.setPassword(pwd);           // Nota: in produzione andrebbe hashata
        c.setNascita(nasc);
        return c;
    }

    /* ==================== Toggle occhio ==================== */

    private void initPasswordToggle() {
        if (passwordField == null || passwordVisibleField == null || toggleVisibilityButton == null) return;

        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        setPasswordVisible(false);

        toggleVisibilityButton.setOnMousePressed(e -> toggleVisibilityButton.setOpacity(0.75));
        toggleVisibilityButton.setOnMouseReleased(e -> toggleVisibilityButton.setOpacity(1.0));

        toggleVisibilityButton.selectedProperty().addListener((obs, was, showPlain) -> setPasswordVisible(Boolean.TRUE.equals(showPlain)));
    }

    private void setPasswordVisible(boolean show) {
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
        passwordVisibleField.setVisible(show);
        passwordVisibleField.setManaged(show);
        if (eyeIcon != null) eyeIcon.setContent(show ? EYE_OPEN : EYE_SLASH);
        if (show) { passwordVisibleField.requestFocus(); passwordVisibleField.end(); }
        else      { passwordField.requestFocus();        passwordField.end();        }
    }

    /* ==================== DatePicker dark + bottone trasparente ==================== */

    /** Applica tema scuro al DatePicker: editor + popup + celle. */
    private void tintDatePickerDark(DatePicker dp) {
        if (dp == null) return;

        // Editor scuro (niente barra bianca)
        dp.setEditable(false);
        dp.setStyle("-fx-background-color: transparent;" +
                "-fx-control-inner-background: transparent;" +
                "-fx-text-fill:" + TEXT_LIGHT + ";" +
                "-fx-prompt-text-fill: rgba(255,255,255,0.60);" +
                "-fx-background-insets: 0;" +
                "-fx-border-color: transparent;");
        if (dp.getEditor() != null) {
            dp.getEditor().setStyle("-fx-background-color: transparent;" +
                    "-fx-control-inner-background: transparent;" +
                    "-fx-text-fill:" + TEXT_LIGHT + ";" +
                    "-fx-prompt-text-fill: rgba(255,255,255,0.60);" +
                    "-fx-background-insets: 0;" +
                    "-fx-border-color: transparent;");
        }

        // Celle giorno scure
     // Celle giorno scure + FUTURO DISABILITATO
        dp.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                boolean disabled = item.isAfter(LocalDate.now());   // <--- BLOCCA FUTURO
                boolean isToday  = item.equals(LocalDate.now());

                String bg = isToday ? BG_DARK_2 : BG_DARK;
                String fg = disabled ? TEXT_MUTED : TEXT_LIGHT;

                // stile base scuro
                setStyle("-fx-background-color:" + bg + "; -fx-background-radius:4;");

                // testo
                setTextFill(Paint.valueOf(fg));

                // disabilita interazione + aspetto attenuato per le date future
                setDisable(disabled);
                setOpacity(disabled ? 0.35 : 1.0);                  // <--- TRASPARENZA FUTURO
            }
        });


        // Popup scuro al momento dell'apertura
        dp.setOnShowing(ev -> Platform.runLater(() -> {
            var popup = dp.lookup(".date-picker-popup");
            if (popup != null) {
                popup.setStyle("-fx-background-color:" + BG_DARK + ";" +
                               "-fx-text-fill:" + TEXT_LIGHT + ";" +
                               "-fx-background-radius:8;" +
                               "-fx-border-color:" + BORDER_SOFT + ";" +
                               "-fx-border-radius:8;");
            }
            var header = dp.lookup(".month-year-pane");
            if (header != null) header.setStyle("-fx-background-color:" + BG_DARK + "; -fx-text-fill:" + TEXT_LIGHT + ";");

            var leftBtn  = dp.lookup(".left-button");
            var rightBtn = dp.lookup(".right-button");
            if (leftBtn  != null) leftBtn.setStyle("-fx-background-color: transparent; -fx-text-fill:" + TEXT_LIGHT + ";");
            if (rightBtn != null) rightBtn.setStyle("-fx-background-color: transparent; -fx-text-fill:" + TEXT_LIGHT + ";");

            var grid = dp.lookup(".calendar-grid");
            if (grid != null) grid.setStyle("-fx-background-color:" + BG_DARK + "; -fx-text-fill:" + TEXT_LIGHT + ";");
        }));
    }

    /** Rende il bottone bianco del DatePicker invisibile, ma lascia il click sul campo per aprire il popup. */
    private void makeDatePickerButtonTransparent(DatePicker dp) {
        if (dp == null) return;
        Platform.runLater(() -> {
            var arrowBtn  = dp.lookup(".arrow-button");
            var arrowIcon = dp.lookup(".arrow");
            if (arrowBtn != null) {
                // TRASPARENTE ma clic ancora abilitato (niente mouseTransparent!)
                arrowBtn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;");
            }
            if (arrowIcon != null) {
                arrowIcon.setOpacity(0.0); // nasconde l'iconcina
            }
        });
    }

    /** Aggancia l’apertura del popup al click sul DatePicker e sul suo editor, e anche ai tasti Invio/Spazio. */
    private void hookOpenOnClick(DatePicker dp) {
        if (dp == null) return;
        dp.setOnMouseClicked(e -> dp.show());
        if (dp.getEditor() != null) {
            dp.getEditor().setOnMouseClicked(e -> dp.show());
            dp.getEditor().setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) dp.show();
            });
        }
    }

    /** handler per l’FXML: clic sul campo -> apri popup calendario (fallback) */
    @FXML
    private void showCalendarPopup() {
        if (nascitaPicker != null) nascitaPicker.show();
    }

    /* ==================== UI helpers / validazione ==================== */

    public void showError(String msg) {
        if (errorLabel != null) {
            errorLabel.setText(msg);
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
        if (isEmpty(nomeField))     { nomeField.requestFocus(); nomeField.selectAll(); return; }
        if (isEmpty(cognomeField))  { cognomeField.requestFocus(); cognomeField.selectAll(); return; }
        if (isEmpty(cfField))       { cfField.requestFocus(); cfField.selectAll(); return; }
        if (nascitaPicker.getValue() == null) { nascitaPicker.requestFocus(); return; }
        if (isEmpty(usernameField)) { usernameField.requestFocus(); usernameField.selectAll(); return; }
        if (passwordField.getText() == null || passwordField.getText().trim().isEmpty()) {
            passwordField.requestFocus(); passwordField.selectAll();
        }
    }

    private boolean isEmpty(TextField tf) {
        return tf == null || tf.getText() == null || tf.getText().trim().isEmpty();
    }

    private static String safeText(TextField tf) {
        return (tf == null || tf.getText() == null) ? "" : tf.getText().trim();
    }

    private static String capitalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return t;
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }

    private void setEnterFocusTraversal() {
        if (nomeField     != null) nomeField.setOnAction(e -> { if (cognomeField  != null) cognomeField.requestFocus(); });
        if (cognomeField  != null) cognomeField.setOnAction(e -> { if (cfField      != null) cfField.requestFocus(); });
        if (cfField       != null) cfField.setOnAction(e ->       { if (nascitaPicker!= null) nascitaPicker.requestFocus(); });
        if (nascitaPicker != null) nascitaPicker.setOnAction(e -> { if (usernameField!= null) usernameField.requestFocus(); });
        if (usernameField != null) usernameField.setOnAction(e -> { if (passwordField!= null) passwordField.requestFocus(); });
    }
}
