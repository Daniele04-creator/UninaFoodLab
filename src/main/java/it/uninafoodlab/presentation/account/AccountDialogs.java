package it.uninafoodlab.presentation.account;

import it.uninafoodlab.presentation.common.DialogTheme;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import javafx.stage.Window;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import java.util.Arrays;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public final class AccountDialogs {

    private AccountDialogs() {}

    public static Optional<Registration> showRegistration() {
        return showRegistration(null);
    }

    public static Optional<Registration> showRegistration(Window owner) {
        var fields = registrationFields(2);
        var role = new ComboBox<Role>();
        role.setId("registrationRole");
        role.setAccessibleText("Come vuoi usare UninaFoodLab?");
        role.setPromptText("Scegli il tuo ruolo");
        role.getItems().setAll(Role.PARTICIPANT,
                Role.CHEF);
        role.setMaxWidth(Double.MAX_VALUE);
        role.setConverter(new StringConverter<>() {
            @Override public String toString(Role value) {
                if (value == null) return "";
                return value == Role.CHEF ? "Chef" : "Partecipante";
            }
            @Override public Role fromString(String text) {
                throw new UnsupportedOperationException("Role selection is not editable");
            }
        });
        var help = new Label("Partecipante: segui i corsi. Chef: crea e gestisci i tuoi corsi.");
        help.setWrapText(true);
        help.setMaxWidth(450);
        fields.grid().addRow(0, new Label("Ruolo"), role);
        fields.grid().add(help, 0, 1, 2, 1);
        return show(owner, "Crea account", "Scegli come usare UninaFoodLab.", fields.grid(),
                "Crea account", () -> {
                    if (role.getValue() == null) throw new IllegalArgumentException("Scegli il tuo ruolo.");
                    requireRegistration(fields);
                    return new Registration(role.getValue(), fields.username().getText(), fields.name().getText(),
                            fields.password().getText().toCharArray(), fields.confirmation().getText().toCharArray());
                });
    }

    private static void requireRegistration(RegistrationFields fields) {
        try {
            Username.of(fields.username().getText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Scegli uno username di 3–50 caratteri: lettere, numeri, . _ -", exception);
        }
        var name = fields.name().getText().strip();
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("Inserisci un nome da mostrare valido (massimo 100 caratteri).");
        }
        if (fields.password().getText().length() < 12) {
            throw new IllegalArgumentException("La password deve contenere almeno 12 caratteri.");
        }
        requireMatchingPasswords(fields.password(), fields.confirmation());
    }

    public static Optional<PasswordChange> showPasswordChange() {
        return showPasswordChange(null);
    }

    public static Optional<PasswordChange> showPasswordChange(Window owner) {
        var current = new PasswordField();
        var replacement = new PasswordField();
        var confirmation = new PasswordField();
        current.setPromptText("Password attuale");
        replacement.setPromptText("Nuova password (almeno 12 caratteri)");
        confirmation.setPromptText("Ripeti nuova password");
        var grid = grid();
        grid.addRow(0, new Label("Attuale"), current);
        grid.addRow(1, new Label("Nuova"), replacement);
        grid.addRow(2, new Label("Conferma"), confirmation);
        return show(owner, "Cambia password", "La nuova password chiude tutte le sessioni aperte.", grid,
                "Aggiorna password", () -> {
                    if (current.getText().isEmpty()) {
                        current.requestFocus();
                        throw new IllegalArgumentException("Inserisci la password attuale.");
                    }
                    if (current.getText().length() < 12) {
                        current.requestFocus();
                        throw new IllegalArgumentException("La password attuale deve contenere almeno 12 caratteri.");
                    }
                    if (replacement.getText().isEmpty()) {
                        replacement.requestFocus();
                        throw new IllegalArgumentException("Inserisci la nuova password.");
                    }
                    if (replacement.getText().length() < 12) {
                        replacement.requestFocus();
                        throw new IllegalArgumentException("La nuova password deve contenere almeno 12 caratteri.");
                    }
                    if (confirmation.getText().isEmpty()) {
                        confirmation.requestFocus();
                        throw new IllegalArgumentException("Ripeti la nuova password.");
                    }
                    if (!replacement.getText().equals(confirmation.getText())) {
                        confirmation.requestFocus();
                        throw new IllegalArgumentException("Le password non coincidono.");
                    }
                    return new PasswordChange(current.getText().toCharArray(), replacement.getText().toCharArray());
                });
    }

    private static RegistrationFields registrationFields(int firstRow) {
        var username = new TextField();
        username.setId("registrationUsername");
        username.setPromptText("solo lettere minuscole, numeri, . _ -");
        var name = new TextField();
        name.setId("registrationName");
        name.setPromptText("Nome e cognome");
        var password = new PasswordField();
        password.setId("registrationPassword");
        password.setPromptText("Almeno 12 caratteri");
        var confirmation = new PasswordField();
        confirmation.setId("registrationConfirmation");
        confirmation.setPromptText("Ripeti password");
        var grid = grid();
        grid.addRow(firstRow, new Label("Username"), username);
        grid.addRow(firstRow + 1, new Label("Nome"), name);
        grid.addRow(firstRow + 2, new Label("Password"), password);
        grid.addRow(firstRow + 3, new Label("Conferma"), confirmation);
        return new RegistrationFields(grid, username, name, password, confirmation);
    }

    private static <T> Optional<T> show(Window owner, String title, String header, GridPane grid, String confirm,
            ResultFactory<T> factory) {
        var dialog = new Dialog<T>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        var save = new ButtonType(confirm, ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        DialogTheme.apply(dialog, owner);
        DialogTheme.primary(dialog, save);
        dialog.getDialogPane().setPrefWidth(540);
        dialog.getDialogPane().getStyleClass().add("account-dialog");
        dialog.getDialogPane().getStylesheets().add(AccountDialogs.class.getResource("account.css").toExternalForm());
        var validation = new Label();
        validation.getStyleClass().add("account-dialog-validation");
        validation.setAccessibleText("Errore nei dati dell'account");
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);
        dialog.getDialogPane().setContent(accountForm(title, header, grid, validation));
        var result = new java.util.concurrent.atomic.AtomicReference<T>();
        var saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                result.set(factory.create());
                validation.setVisible(false);
                validation.setManaged(false);
            } catch (IllegalArgumentException exception) {
                validation.setText(validationMessage(exception));
                validation.setVisible(true);
                validation.setManaged(true);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == save ? result.get() : null);
        return dialog.showAndWait();
    }

    private static javafx.scene.control.ScrollPane accountForm(String title, String header, GridPane grid, Label validation) {
        var logo = it.uninafoodlab.presentation.common.LabBrand.logo(180);
        var heading = new Label(title);
        heading.getStyleClass().add("account-heading");
        var introduction = new Label(header); introduction.setWrapText(true);
        introduction.getStyleClass().add("account-introduction");
        var form = new VBox(14, logo, heading, introduction, validation);
        var rows = new java.util.TreeMap<Integer, java.util.List<javafx.scene.Node>>();
        for (var node : java.util.List.copyOf(grid.getChildren())) {
            rows.computeIfAbsent(java.util.Objects.requireNonNullElse(GridPane.getRowIndex(node), 0), ignored -> new java.util.ArrayList<>()).add(node);
        }
        grid.getChildren().clear();
        for (var nodes : rows.values()) {
            var field = new VBox(6);
            associateLabel(nodes);
            for (var node : nodes) {
                if (node instanceof javafx.scene.control.Control control) control.setMaxWidth(Double.MAX_VALUE);
                field.getChildren().add(node);
            }
            form.getChildren().add(field);
        }
        var scroll = new javafx.scene.control.ScrollPane(form); scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(title.equals("Crea account") ? 580 : 440);
        return scroll;
    }

    private static void associateLabel(java.util.List<javafx.scene.Node> nodes) {
        var editor = nodes.stream().filter(node -> node instanceof javafx.scene.control.TextInputControl
                || node instanceof ComboBox<?>).findFirst();
        editor.ifPresent(control -> nodes.stream().filter(Label.class::isInstance).map(Label.class::cast)
                .forEach(label -> label.setLabelFor(control)));
    }

    private static void requireMatchingPasswords(PasswordField password, PasswordField confirmation) {
        if (!password.getText().equals(confirmation.getText())) {
            throw new IllegalArgumentException("Le password non coincidono.");
        }
    }

    private static String validationMessage(IllegalArgumentException exception) {
        return it.uninafoodlab.presentation.common.UserErrorMessages.validation(exception, "Controlla i dati inseriti.");
    }

    private static GridPane grid() {
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.getStyleClass().add("account-form");
        var labels = new ColumnConstraints(130);
        var editors = new ColumnConstraints(300, 320, Double.MAX_VALUE);
        editors.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, editors);
        return grid;
    }

    public record Registration(Role role, String username, String displayName, char[] password, char[] confirmation) {
        public Registration { password = password.clone(); confirmation = confirmation.clone(); }
        @Override public char[] password() { return password.clone(); }
        @Override public char[] confirmation() { return confirmation.clone(); }
        public void clear() { Arrays.fill(password, '\0'); Arrays.fill(confirmation, '\0'); }
    }

    public record PasswordChange(char[] currentPassword, char[] newPassword) {
        public PasswordChange { currentPassword = currentPassword.clone(); newPassword = newPassword.clone(); }
        @Override public char[] currentPassword() { return currentPassword.clone(); }
        @Override public char[] newPassword() { return newPassword.clone(); }
        public void clear() { Arrays.fill(currentPassword, '\0'); Arrays.fill(newPassword, '\0'); }
    }

    private record RegistrationFields(
            GridPane grid, TextField username, TextField name, PasswordField password, PasswordField confirmation) {}

    @FunctionalInterface
    private interface ResultFactory<T> { T create(); }
}
