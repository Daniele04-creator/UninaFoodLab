package it.uninafoodlab.presentation.navigation;

import it.uninafoodlab.presentation.FxSnapshot;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

public final class UnsavedChangesUiSmoke {
    private UnsavedChangesUiSmoke() {}

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var test = new FutureTask<Void>(() -> {
                verifyGuard();
                return null;
            });
            Platform.runLater(test);
            test.get(30, TimeUnit.SECONDS);
            System.out.println("PASS: native exit confirmation, safe default, cancel/close retention, explicit consent, busy protection and clean exit");
        } finally { Platform.exit(); }
    }

    private static void verifyGuard() {
        var field = new TextField("Bozza non salvata");
        var stage = new Stage();
        stage.setScene(new Scene(new VBox(field), 1000, 600));
        stage.show();
        field.requestFocus();
        var changed = new AtomicBoolean(true);
        var busy = new AtomicBoolean(false);
        var disposed = new AtomicInteger();
        var guard = new UnsavedChangesGuard(changed::get, busy::get,
                action -> UnsavedChangesDialog.confirm(stage, action), () -> UnsavedChangesDialog.showBusy(stage));
        UnsavedChangesDialog.protectClose(stage, () -> guard, disposed::incrementAndGet);

        respond(false, true);
        var close = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        stage.getOnCloseRequest().handle(close);
        check(close.isConsumed() && disposed.get() == 0, "Cancelled close must retain the workspace and lifecycle");
        check(field.getText().equals("Bozza non salvata") && field.isFocused(), "Cancellation must preserve text and focus");

        for (var action : new UnsavedChangesGuard.Action[]{UnsavedChangesGuard.Action.LOGOUT,
                UnsavedChangesGuard.Action.PASSWORD}) {
            respond(false, false);
            check(!guard.allow(action), "Cancelled account action must not leave the workspace");
        }
        Platform.runLater(() -> Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                .findFirst().orElseThrow().hide());
        check(!guard.allow(UnsavedChangesGuard.Action.CLOSE), "Closing the confirmation must cancel departure");

        respond(true, false);
        close = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        stage.getOnCloseRequest().handle(close);
        check(!close.isConsumed() && disposed.get() == 1, "Explicit discard must allow departure and dispose once");
        check(changed.get(), "Consent must not clear drafts before successful departure");

        busy.set(true);
        Platform.runLater(() -> {
            var pane = dialogPane(stage);
            ((Button) pane.lookupButton(pane.getButtonTypes().getFirst())).fire();
        });
        check(!guard.allow(UnsavedChangesGuard.Action.LOGOUT), "Busy operations must block exit");
        busy.set(false);
        changed.set(false);
        check(guard.allow(UnsavedChangesGuard.Action.CLOSE), "Clean workspace must leave without a dialog");
        stage.close();
    }

    public static void verifyWorkspaceExit(java.util.function.BooleanSupplier busy, boolean allowed) {
        var busyFeedback = new AtomicInteger();
        var guard = new UnsavedChangesGuard(() -> false, busy,
                action -> { throw new AssertionError("Read-only workspaces have no draft confirmation"); },
                busyFeedback::incrementAndGet);
        for (var action : UnsavedChangesGuard.Action.values()) {
            check(guard.allow(action) == allowed, "Workspace operation must protect " + action);
        }
        check(busyFeedback.get() == (allowed ? 0 : UnsavedChangesGuard.Action.values().length),
                "Busy workspace must explain why departure is blocked");
    }

    private static void respond(boolean accept, boolean capture) {
        Platform.runLater(() -> {
            var pane = dialogPane(null);
            var keep = pane.getButtonTypes().stream()
                    .filter(type -> type.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE).findFirst().orElseThrow();
            var proceed = pane.getButtonTypes().stream()
                    .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
            check(((Button) pane.lookupButton(keep)).isDefaultButton(), "Keep editing must be the default");
            check(!((Button) pane.lookupButton(proceed)).isDefaultButton(), "Discard must require explicit consent");
            if (capture) {
                try { FxSnapshot.save(pane, "target/native-match/unsaved-exit-confirmation.png"); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            }
            ((Button) pane.lookupButton(accept ? proceed : keep)).fire();
        });
    }

    private static DialogPane dialogPane(Stage owner) {
        return Window.getWindows().stream().filter(window -> window != owner && window.isShowing())
                .map(window -> window.getScene().getRoot().lookup(".dialog-pane"))
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
