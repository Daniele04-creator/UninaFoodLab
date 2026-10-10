package it.uninafoodlab.presentation.login;

import it.uninafoodlab.application.auth.AuthenticateUser;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

public final class LoginRetryUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var calls = new AtomicInteger();
        var outcome = new AtomicInteger();
        var receivedPassword = new AtomicReference<char[]>();
        var authenticated = new AtomicReference<AuthenticatedUser>();
        var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("smoke.chef"), "Chef sintetico", Role.CHEF);
        var controller = new LoginController(new AuthenticateUser((username, password) -> {
            calls.incrementAndGet();
            receivedPassword.set(password);
            return switch (outcome.get()) {
                case 0 -> Optional.empty();
                case 1 -> throw new IllegalStateException("jdbc:private-host synthetic-secret");
                default -> Optional.of(actor);
            };
        }), authenticated::set);
        try {
            var scene = fx(() -> {
                var loader = new FXMLLoader(LoginRetryUiSmoke.class.getResource("login.fxml"));
                loader.setController(controller);
                var root = (Parent) loader.load();
                var view = new Scene(root, 1000, 600);
                view.getStylesheets().add(LoginRetryUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css").toExternalForm());
                var stage = new Stage();
                stage.setScene(view);
                stage.show();
                return view;
            });
            captureWidths(scene);
            verifyRevealFocus(scene);
            fx(() -> {
                snapshot(scene, "login-clean.png");
                var scroll = (ScrollPane) scene.lookup(".access-scroll");
                var viewport = (Region) scroll.lookup(".viewport");
                var card = (Region) scene.lookup(".login-card");
                var backdrop = (Color) viewport.getBackground().getFills().getFirst().getFill();
                var surface = (Color) card.getBackground().getFills().getFirst().getFill();
                var edge = (Color) card.getBorder().getStrokes().getFirst().getTopStroke();
                check(backdrop.getBrightness() < surface.getBrightness()
                                && edge.getBrightness() < surface.getBrightness() - 0.1,
                        "Login surface has a visible border against its restrained backdrop");
                var formBounds = scene.lookup(".login-card").localToScene(
                        scene.lookup(".login-card").getBoundsInLocal());
                check(formBounds.getMinY() >= 0 && formBounds.getMaxY() <= scene.getHeight(),
                        "Entire login form fits the minimum window height");
                field(scene, "usernameField").setText("   ");
                field(scene, "passwordField").setText("synthetic-secret");
                submit(scene).fire();
                check(!submit(scene).isDisabled(), "Missing username stays synchronous");
                check(error(scene).getText().equals("Inserisci lo username per accedere."), "Useful missing username message");
                check(scene.getFocusOwner() == field(scene, "usernameField"), "Missing username receives focus");
                check(field(scene, "passwordField").getText().equals("synthetic-secret"), "Missing username retains password");
                check(calls.get() == 0, "Missing username makes no authentication call");
                snapshot(scene, "studio-login-blank-username.png");
                field(scene, "usernameField").setText("smoke.chef");
                field(scene, "passwordField").clear();
                var reveal = (ToggleButton) scene.lookup("#revealPasswordButton");
                reveal.fire();
                submit(scene).fire();
                check(error(scene).getText().equals("Inserisci la password per accedere."), "Useful missing password message");
                check(scene.getFocusOwner() == field(scene, "visiblePasswordField"), "Visible empty password receives focus");
                check(!submit(scene).isDisabled() && calls.get() == 0, "Missing password makes no asynchronous attempt");
                reveal.fire();
                submit(scene).fire();
                check(scene.getFocusOwner() == field(scene, "passwordField"), "Hidden empty password receives focus");
                attempt(scene);
                return null;
            });
            await(() -> !submit(scene).isDisabled());
            fx(() -> {
                check(error(scene).getText().equals("Username o password non validi"), "Credentials get the generic authentication message");
                verifyFailure(scene, receivedPassword.get());
                check(calls.get() == 1, "No automatic credentials replay");
                snapshot(scene, "studio-login-invalid-credentials.png");
                outcome.set(1);
                attempt(scene);
                return null;
            });
            await(() -> !submit(scene).isDisabled());
            fx(() -> {
                check(error(scene).getText().equals("Accesso temporaneamente non disponibile. Riprova tra poco."), "Service failure offers useful recovery");
                verifyFailure(scene, receivedPassword.get());
                check(calls.get() == 2, "No automatic service replay");
                snapshot(scene, "studio-login-service-error.png");
                outcome.set(2);
                attempt(scene);
                return null;
            });
            await(() -> authenticated.get() != null);
            fx(() -> {
                check(authenticated.get().equals(actor), "Explicit retry authenticates successfully");
                check(calls.get() == 3 && !submit(scene).isDisabled(), "Exactly three explicit attempts");
                check(!error(scene).isVisible(), "Successful retry clears stale error");
                checkWiped(receivedPassword.get());
                return null;
            });
            System.out.println("PASS: login local validation, focus, safe credential/service errors, password wiping and explicit retry");
        } finally {
            Platform.exit();
        }
    }

    private static void verifyRevealFocus(Scene scene) throws Exception {
        fx(() -> {
            var hidden = field(scene, "passwordField");
            var visible = field(scene, "visiblePasswordField");
            var reveal = (ToggleButton)scene.lookup("#revealPasswordButton");
            hidden.setText("synthetic-password");hidden.requestFocus();hidden.positionCaret(5);
            reveal.fire();
            check(visible.isVisible() && !hidden.isVisible(), "Reveal switches to the visible editor");
            check(scene.getFocusOwner() == visible && visible.getCaretPosition() == 5, "Reveal preserves editing focus and caret");
            check(((Label)scene.lookup("#passwordLabel")).getLabelFor() == visible, "Password label follows the visible editor");
            check(reveal.getAccessibleText().equals("Nascondi password"), "Reveal exposes its current accessible action");
            snapshot(scene, "login-password-visible.png");
            visible.positionCaret(8);reveal.fire();
            check(hidden.isVisible() && !visible.isVisible(), "Hide restores the password editor");
            check(scene.getFocusOwner() == hidden && hidden.getCaretPosition() == 8, "Hide preserves editing focus and caret");
            check(((Label)scene.lookup("#passwordLabel")).getLabelFor() == hidden, "Password label follows the hidden editor");
            check(hidden.getText().equals("synthetic-password"), "Reveal does not alter credentials");
            reveal.setDisable(true);
            scene.getRoot().applyCss();
            check(reveal.getBackground().getFills().stream().allMatch(fill ->
                    fill.getFill() instanceof javafx.scene.paint.Color color && color.getOpacity() == 0),
                    "Disabled reveal keeps a transparent background during login");
            snapshot(scene, "login-password-disabled.png");
            reveal.setDisable(false);
            snapshot(scene, "login-password-hidden.png");hidden.clear();return null;
        });
    }

    private static void captureWidths(Scene scene) throws Exception {
        var stage = (Stage)scene.getWindow();
        for (int width : new int[]{1000, 1440}) {
            fx(() -> {stage.setWidth(width + stage.getWidth() - scene.getWidth());return null;});
            await(() -> Math.abs(scene.getWidth() - width) < 2);
            fx(() -> {scene.getRoot().applyCss();scene.getRoot().layout();snapshot(scene,"login-" + width + ".png");return null;});
        }
        fx(() -> {stage.setWidth(1000 + stage.getWidth() - scene.getWidth());return null;});
        await(() -> Math.abs(scene.getWidth() - 1000) < 2);
    }

    private static void attempt(Scene scene) {
        field(scene, "passwordField").setText("synthetic-secret");
        submit(scene).fire();
        check(submit(scene).isDisabled(), "Authentication disables duplicate submits");
        check(field(scene, "passwordField").getText().isEmpty(), "Actual attempt clears password immediately");
        check(field(scene, "visiblePasswordField").getText().isEmpty(), "Actual attempt clears revealed password");
    }

    private static void verifyFailure(Scene scene, char[] password) {
        check(error(scene).isVisible() && error(scene).isManaged(), "Failure is visible");
        check(!error(scene).getText().contains("synthetic-secret") && !error(scene).getText().contains("jdbc:"), "Error never exposes secrets or service details");
        check(field(scene, "usernameField").getText().equals("smoke.chef"), "Failure retains username");
        check(!field(scene, "usernameField").isDisabled() && !field(scene, "passwordField").isDisabled(), "Failure reenables form");
        check(scene.getFocusOwner() == field(scene, "passwordField"), "Failure focuses cleared password");
        checkWiped(password);
    }

    private static void checkWiped(char[] password) {
        for (char value : password) check(value == '\0', "Use case wipes password buffer");
    }

    private static void snapshot(Scene scene, String filename) throws Exception {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        var rendered = scene.snapshot(null);
        int width = (int) rendered.getWidth();
        int height = (int) rendered.getHeight();
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var pixels = rendered.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, pixels.getArgb(x, y));
        }
        var target = Path.of("target", filename);
        Files.createDirectories(target.getParent());
        ImageIO.write(image, "png", target.toFile());
    }

    private static TextField field(Scene scene, String id) { return (TextField) scene.lookup("#" + id); }
    private static Button submit(Scene scene) { return (Button) scene.lookup("#submitButton"); }
    private static Label error(Scene scene) { return (Label) scene.lookup("#errorLabel"); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        it.uninafoodlab.presentation.FxAwait.until(condition, 10);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
