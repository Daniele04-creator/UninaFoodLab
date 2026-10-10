package it.uninafoodlab.presentation.account;

import it.uninafoodlab.presentation.FxAwait;
import it.uninafoodlab.presentation.FxSnapshot;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class AccountDialogsUiSmoke {

    private AccountDialogsUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            Platform.runLater(AccountDialogs::showRegistration);
            FxAwait.until(() -> dialogWindow() != null, 10);
            fx(() -> {
                var window = dialogWindow();
                check(window != null, "Registration dialog opens");
                var pane = (DialogPane) window.getScene().lookup(".dialog-pane");
                check(pane != null, "Registration dialog has a dialog pane");
                check(pane.lookup("#registrationRole") instanceof javafx.scene.control.ComboBox<?>,
                        "Registration lets the user choose Chef or Participant without an invitation");
                check(pane.getStylesheets().stream().anyMatch(value -> value.endsWith("dialogs.css")),
                        "Account dialog loads the shared dialog theme");
                check(saveButton(pane).getStyleClass().contains("button-primary"),
                        "Account confirmation exposes the primary action");
                var buttonBar = (javafx.scene.control.ButtonBar) pane.lookup(".button-bar");
                check(buttonBar != null && "+CO".equals(buttonBar.getButtonOrder()),
                        "Dialog button order keeps cancellation before confirmation");
                var cancel = pane.lookupButton(javafx.scene.control.ButtonType.CANCEL);
                check(cancel.localToScene(cancel.getBoundsInLocal()).getMinX()
                                < saveButton(pane).localToScene(saveButton(pane).getBoundsInLocal()).getMinX(),
                        "Confirmation is positioned to the right of cancellation");
                check(pane.getContent().lookup(".scroll-pane") instanceof javafx.scene.control.ScrollPane,
                        "Account form remains scrollable at small window heights");
                var fields = textFields(pane);
                check(fields.size() == 4, "Registration exposes only identity and password fields");
                check(fields.stream().map(GridPane::getRowIndex).distinct().count() == 4,
                        "Registration fields preserve distinct validation identities");
                pane.applyCss(); pane.layout();
                for (var index = 0; index < fields.size(); index++) {
                    var bounds = fields.get(index).localToScene(fields.get(index).getBoundsInLocal());
                    check(bounds.getWidth() >= 300 && bounds.getHeight() > 0, "Every editor is wide and visible");
                    for (var next = index + 1; next < fields.size(); next++) {
                        var other = fields.get(next).localToScene(fields.get(next).getBoundsInLocal());
                        check(!bounds.intersects(other), "Editable fields do not overlap");
                    }
                }
                assertProfessionalForm(pane);
                FxSnapshot.save(pane, "target/ui-polish/account-registration.png");
                saveButton(pane).fire();
                check(window.isShowing(), "Missing role keeps the dialog open");
                var validation = (Label) pane.lookup(".account-dialog-validation");
                check(validation != null && validation.isVisible() && !validation.getText().isBlank(),
                        "Missing role is shown as inline validation feedback");
                if (window instanceof javafx.stage.Stage stage) stage.sizeToScene();
                FxSnapshot.save(pane, "target/ui-polish/account-validation.png");
                var roles = (javafx.scene.control.ComboBox<?>) pane.lookup("#registrationRole");
                roles.getSelectionModel().select(1);
                fieldAtRow(fields, 2).setText("chef.nuovo");
                fieldAtRow(fields, 3).setText("Chef nuovo");
                fieldAtRow(fields, 4).setText("SyntheticChef!2026");
                fieldAtRow(fields, 5).setText("diversa");
                saveButton(pane).fire();
                assertInlineValidation(window, pane, "Mismatched registration passwords preserve the form");
                check(fieldAtRow(fields, 2).getText().equals("chef.nuovo"), "Username survives validation");
                fieldAtRow(fields, 5).setText("SyntheticChef!2026");
                saveButton(pane).fire();
                check(!window.isShowing(), "Valid Chef registration requires no code");
                return null;
            });
            verifyPasswordChangeMismatchThenCorrection();
            System.out.println("PASS: Account dialogs keep invalid input inline and accept corrected passwords.");
        } finally {
            Platform.exit();
        }
    }

    private static Window dialogWindow() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(window -> window.getScene() != null)
                .filter(window -> window.getScene().lookup(".dialog-pane") != null)
                .findFirst().orElse(null);
    }

    private static ButtonBase saveButton(DialogPane pane) {
        return pane.getButtonTypes().stream()
                .filter(type -> type.getButtonData().isDefaultButton())
                .map(pane::lookupButton)
                .map(ButtonBase.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Account dialog must expose a save button"));
    }

    private static void verifyPasswordChangeMismatchThenCorrection() throws Exception {
        Platform.runLater(AccountDialogs::showPasswordChange);
        FxAwait.until(() -> dialogWindow() != null, 10);
        var window = fx(() -> {
            var currentWindow = dialogWindow();
            var pane = pane(currentWindow);
            var fields = passwordFields(pane);
            assertProfessionalForm(pane);
            FxSnapshot.save(pane, "target/ui-polish/account-password-change.png");
            saveButton(pane).fire();
            assertInlineValidation(currentWindow, pane, "Empty password change remains editable");
            check(fields.get(0).isFocused(), "Empty current password receives focus");
            fields.get(0).setText("password-attuale");
            saveButton(pane).fire();
            assertInlineValidation(currentWindow, pane, "Missing new password remains editable");
            check(fields.get(1).isFocused(), "Missing new password receives focus");
            fields.get(1).setText("breve");
            fields.get(2).setText("breve");
            saveButton(pane).fire();
            assertInlineValidation(currentWindow, pane, "Short new password remains editable");
            check(fields.get(1).isFocused(), "Short new password receives focus");
            fields.get(1).setText("nuova-password");
            fields.get(2).setText("conferma-diversa");
            saveButton(pane).fire();
            assertInlineValidation(currentWindow, pane, "Password change with mismatched passwords remains open");
            check(fields.get(2).isFocused(), "Mismatched confirmation receives focus");
            fields.get(2).setText("nuova-password");
            saveButton(pane).fire();
            return currentWindow;
        });
        FxAwait.until(() -> !window.isShowing(), 10);
    }

    private static void assertProfessionalForm(DialogPane pane) {
        check(pane.getContent() instanceof javafx.scene.control.ScrollPane, "Account form is a single scrollable column");
        check(pane.lookup(".account-photo") == null, "Account form has no decorative photograph");
        var labels = pane.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast).toList();
        var copy = labels.stream().map(Label::getText).collect(java.util.stream.Collectors.joining(" ")).toLowerCase();
        check(!copy.contains("scripts/") && !copy.contains("manage-accounts") && !copy.contains("installazione") && !copy.contains("demo"),
                "Account copy contains no implementation instructions");
        for (var input : textFields(pane)) {
            check(labels.stream().anyMatch(label -> label.getLabelFor() == input), "Each account editor has an associated label");
        }
    }

    private static DialogPane pane(Window window) {
        var pane = (DialogPane) window.getScene().lookup(".dialog-pane");
        check(pane != null, "Account dialog has a dialog pane");
        return pane;
    }

    private static java.util.List<TextInputControl> textFields(DialogPane pane) {
        return pane.getContent().lookupAll(".text-input").stream()
                .filter(TextInputControl.class::isInstance)
                .map(TextInputControl.class::cast)
                .toList();
    }

    private static java.util.List<javafx.scene.control.PasswordField> passwordFields(DialogPane pane) {
        return textFields(pane).stream()
                .filter(javafx.scene.control.PasswordField.class::isInstance)
                .map(javafx.scene.control.PasswordField.class::cast)
                .sorted(java.util.Comparator.comparingInt(AccountDialogsUiSmoke::gridRow))
                .toList();
    }

    private static int gridRow(TextInputControl field) {
        var row = GridPane.getRowIndex(field);
        return row == null ? 0 : row;
    }

    private static void assertInlineValidation(Window window, DialogPane pane, String message) {
        check(window.isShowing(), message);
        var validation = (Label) pane.lookup(".account-dialog-validation");
        check(validation != null && validation.isVisible() && !validation.getText().isBlank(),
                message + " and shows inline feedback");
    }

    private static TextInputControl fieldAtRow(java.util.List<TextInputControl> fields, int row) {
        return fields.stream()
                .filter(field -> Integer.valueOf(row).equals(GridPane.getRowIndex(field)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected editable field at grid row " + row));
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
