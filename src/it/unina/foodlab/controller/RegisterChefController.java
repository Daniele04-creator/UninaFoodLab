package it.unina.foodlab.controller;

import it.unina.foodlab.dao.ChefDao;
import it.unina.foodlab.model.Chef;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent; 
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

import java.io.PrintWriter;
import java.io.StringWriter;
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
    @FXML private Button registerButton;

   
    private final ChefDao chefDao = new ChefDao();

  
    private static final String EYE_OPEN  = "M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12zm11 4a4 4 0 1 0 0-8 4 4 0 0 0 0 8z";
    private static final String EYE_SLASH = "M2 5l19 14-1.5 2L.5 7 2 5zm3.3 2.4C7.7 6.2 9.7 5 12 5c7 0 11 7 11 7-.7 1.1-1.7 2.3-3 3.3L18.6 13c.3-.6.4-1.2.4-1.9a5 5 0 0 0-5-5c-.7 0-1.3.1-1.9.4L5.3 7.4z";

    
    private static final String REGEX_NOME      = "^[A-Za-zÀ-ÖØ-öø-ÿ'`\\-\\s]{2,50}$";
    private static final String REGEX_COGNOME   = REGEX_NOME;
    private static final String REGEX_CF_CHEF   = "^CH\\d{3}$";
    private static final String REGEX_USERNAME  = "^[A-Za-z0-9_.]{3,20}$";
    private static final int    MIN_PWD_LEN     = 6;

 
    private static final String BG_DARK      = "#1f2937";
    private static final String BG_DARK_2    = "#2b3542";
    private static final String TEXT_LIGHT   = "#e5e7eb";
    private static final String TEXT_MUTED   = "#9aa5b1";
    private static final String BORDER_SOFT  = "rgba(255,255,255,0.10)";

    @FXML
    private void initialize() {
        initPasswordToggle();
        tintDatePickerDark(nascitaPicker);
        makeDatePickerButtonTransparent(nascitaPicker);
        hookOpenOnClick(nascitaPicker);
        clearError();
        setEnterFocusTraversal();

        if (usernameField != null) {
            usernameField.setOnAction(e -> { if (passwordField != null) passwordField.requestFocus(); });
        }
        if (passwordField != null) {
            passwordField.setOnAction(e -> onRegister());
        }
        if (passwordVisibleField != null) {
            passwordVisibleField.setOnAction(e -> onRegister());
        }
    }

    
    @FXML
    private void showCalendarPopup(MouseEvent e) {
        if (nascitaPicker != null) {
            nascitaPicker.show();
            nascitaPicker.requestFocus();
        }
    }

   
 
    @FXML
    public void onRegister() {
        clearError();
        Chef c = getChef();
        if (c == null) return;

        setUiDisabled(true);
        try {
            ChefDao.RegisterOutcome esito = chefDao.register(c);
            switch (esito) {
                case OK -> { showInfo("Registrazione completata", "Chef registrato correttamente."); clearForm(); }
                case DUPLICATE_CF -> { showError("Codice Chef già presente. Scegli un altro codice (es. CH123)."); focusSelect(cfField); }
                case DUPLICATE_USERNAME -> { showError("Username già in uso. Scegline un altro."); focusSelect(usernameField); }
                default -> showError("Registrazione non riuscita. Riprovare.");
            }
        } catch (Throwable ex) {
            showException("Errore durante la registrazione", ex);
        } finally {
            setUiDisabled(false);
        }
    }

    

    private void setUiDisabled(boolean disabled) {
        if (registerButton != null) registerButton.setDisable(disabled);
        if (nomeField != null) nomeField.setDisable(disabled);
        if (cognomeField != null) cognomeField.setDisable(disabled);
        if (cfField != null) cfField.setDisable(disabled);
        if (usernameField != null) usernameField.setDisable(disabled);
        if (passwordField != null) passwordField.setDisable(disabled);
        if (passwordVisibleField != null) passwordVisibleField.setDisable(disabled);
        if (nascitaPicker != null) nascitaPicker.setDisable(disabled);
        if (toggleVisibilityButton != null) toggleVisibilityButton.setDisable(disabled);
    }

    public Chef getChef() {
        clearError();

        String nome     = safeText(nomeField);
        String cognome  = safeText(cognomeField);
        String cf       = safeText(cfField);
        String username = safeText(usernameField);

        String pwd = (passwordField != null && passwordField.getText() != null)
                ? passwordField.getText()
                : (passwordVisibleField != null && passwordVisibleField.getText() != null ? passwordVisibleField.getText() : "");

        LocalDate nasc  = (nascitaPicker == null) ? null : nascitaPicker.getValue();

        String err;

        err = validateRequired(nome, "Inserisci il nome.");
        if (err != null) { showError(err); focusSelect(nomeField); return null; }
        if (!nome.matches(REGEX_NOME)) { showError("Nome non valido."); focusSelect(nomeField); return null; }

        err = validateRequired(cognome, "Inserisci il cognome.");
        if (err != null) { showError(err); focusSelect(cognomeField); return null; }
        if (!cognome.matches(REGEX_COGNOME)) { showError("Cognome non valido."); focusSelect(cognomeField); return null; }

        err = validateRequired(cf, "Inserisci il codice chef.");
        if (err != null) { showError(err); focusSelect(cfField); return null; }
        if (!cf.matches(REGEX_CF_CHEF)) { showError("Codice Chef nel formato CH### (es. CH123)."); focusSelect(cfField); return null; }

        if (nasc == null) { showError("Inserisci la data di nascita."); nascitaPicker.requestFocus(); return null; }
        if (nasc.isAfter(LocalDate.now())) { showError("La data di nascita non può essere futura."); nascitaPicker.requestFocus(); return null; }

        err = validateRequired(username, "Inserisci lo username.");
        if (err != null) { showError(err); focusSelect(usernameField); return null; }
        if (!username.matches(REGEX_USERNAME)) { showError("Username non valido (3-20, lettere/numeri/._)."); focusSelect(usernameField); return null; }

        if (pwd == null || pwd.trim().length() < MIN_PWD_LEN) {
            showError("La password deve avere almeno " + MIN_PWD_LEN + " caratteri.");
            if (passwordField != null) focusSelect(passwordField);
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

    private void initPasswordToggle() {
        if (passwordField == null || passwordVisibleField == null || toggleVisibilityButton == null) return;

        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        setPasswordVisible(false);

        toggleVisibilityButton.setOnMousePressed(e -> toggleVisibilityButton.setOpacity(0.75));
        toggleVisibilityButton.setOnMouseReleased(e -> toggleVisibilityButton.setOpacity(1.0));

        toggleVisibilityButton.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> obs, Boolean was, Boolean showPlain) {
                setPasswordVisible(Boolean.TRUE.equals(showPlain));
            }
        });
    }

    private void setPasswordVisible(boolean show) {
        if (passwordField != null) {
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
        }
        if (passwordVisibleField != null) {
            passwordVisibleField.setVisible(show);
            passwordVisibleField.setManaged(show);
        }
        if (eyeIcon != null) eyeIcon.setContent(show ? EYE_OPEN : EYE_SLASH);
        if (show && passwordVisibleField != null) { passwordVisibleField.requestFocus(); passwordVisibleField.end(); }
        else if (passwordField != null)            { passwordField.requestFocus();        passwordField.end();        }
    }

    private void tintDatePickerDark(DatePicker dp) {
        if (dp == null) return;

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

        dp.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }
                boolean disabled = item.isAfter(LocalDate.now());
                boolean isToday  = item.equals(LocalDate.now());

                String bg = isToday ? BG_DARK_2 : BG_DARK;
                String fg = disabled ? TEXT_MUTED : TEXT_LIGHT;

                setStyle("-fx-background-color:" + bg + "; -fx-background-radius:4;");
                setTextFill(Paint.valueOf(fg));
                setDisable(disabled);
                setOpacity(disabled ? 0.35 : 1.0);
            }
        });

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

    private void makeDatePickerButtonTransparent(DatePicker dp) {
        if (dp == null) return;
        Platform.runLater(() -> {
            var arrowBtn  = dp.lookup(".arrow-button");
            var arrowIcon = dp.lookup(".arrow");
            if (arrowBtn != null) {
                arrowBtn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;");
            }
            if (arrowIcon != null) {
                arrowIcon.setOpacity(0.0);
            }
        });
    }

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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

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

    private void showException(String header, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Errore");
        alert.setHeaderText(header);
        alert.setContentText(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();

        Label label = new Label("Dettagli (stack trace):");
        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);
        alert.getDialogPane().setExpandableContent(expContent);
        alert.getDialogPane().setExpanded(true);
        alert.showAndWait();
    }

    private void focusSelect(TextField tf) { if (tf != null) { tf.requestFocus(); tf.selectAll(); } }
    private static String validateRequired(String v, String m) { return (v == null || v.trim().isEmpty()) ? m : null; }
    private static String safeText(TextField tf) { return (tf == null || tf.getText() == null) ? "" : tf.getText().trim(); }
    private static String capitalize(String s) { if (s == null) return ""; String t = s.trim(); return t.isEmpty()? t : Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase(); }

    private void clearForm() {
        if (nomeField != null) nomeField.clear();
        if (cognomeField != null) cognomeField.clear();
        if (cfField != null) cfField.clear();
        if (usernameField != null) usernameField.clear();
        if (passwordField != null) passwordField.clear();
        if (passwordVisibleField != null) passwordVisibleField.clear();
        if (nascitaPicker != null) nascitaPicker.setValue(null);
        clearError();
        if (nomeField != null) nomeField.requestFocus();
    }

    private void setEnterFocusTraversal() {
        if (nomeField     != null) nomeField.setOnAction(e -> { if (cognomeField  != null) cognomeField.requestFocus(); });
        if (cognomeField  != null) cognomeField.setOnAction(e -> { if (cfField      != null) cfField.requestFocus(); });
        if (cfField       != null) cfField.setOnAction(e ->       { if (nascitaPicker!= null) nascitaPicker.requestFocus(); });
        if (nascitaPicker != null) nascitaPicker.setOnAction(e -> { if (usernameField!= null) usernameField.requestFocus(); });
    }
}
