package it.uninafoodlab.presentation.common;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.TraversalDirection;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class ResultPagerUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var stage = new AtomicReference<Stage>();
        var pager = new AtomicReference<ResultPager>();
        var anchor = new AtomicReference<Button>();
        try {
            fx(() -> {
                var source = new Button("Prima della paginazione");
                var resultPager = new ResultPager(page -> {
                    pager.get().setDisable(true);
                    Platform.runLater(() -> {
                        pager.get().update(page, 25, "risultati");
                        pager.get().setDisable(false);
                    });
                });
                pager.set(resultPager);
                anchor.set(source);
                resultPager.update(1, 25, "risultati");
                var window = new Stage();
                window.setScene(new Scene(new VBox(source, resultPager), 1000, 250));
                window.show();
                window.requestFocus();
                stage.set(window);
            });
            fx(() -> {
                anchor.get().requestFocus();
                anchor.get().requestFocusTraversal(TraversalDirection.NEXT);
                stage.get().getScene().getFocusOwner().requestFocusTraversal(TraversalDirection.NEXT);
                var next = button(pager.get(), "Pagina successiva");
                check(next.isFocused() && next.isFocusVisible(), "Next must have visible keyboard focus before loading");
                next.fire();
            });
            fx(() -> {});
            fx(() -> {
                var previous = button(pager.get(), "Pagina precedente");
                check(previous.isFocused(), "Async last page must move focus to enabled Previous");
                check(previous.isFocusVisible(), "Restored keyboard focus must remain visible");
                previous.fire();
            });
            fx(() -> {});
            fx(() -> button(pager.get(), "Pagina precedente").fire());
            fx(() -> {});
            fx(() -> {
                var next = button(pager.get(), "Pagina successiva");
                check(next.isFocused() && next.isFocusVisible(), "Async first page must restore visible focus to Next");
                next.fire();
                anchor.get().fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0,
                        MouseButton.PRIMARY, 1, false, false, false, false,
                        true, false, false, false, false, true, null));
                anchor.get().requestFocus();
            });
            fx(() -> {});
            fx(() -> check(anchor.get().isFocused(), "Loading must preserve focus moved outside the pager"));
            System.out.println("RESULT_PAGER_ASYNC_FOCUS_OK");
        } finally {
            fx(() -> { if (stage.get() != null) stage.get().close(); });
            Platform.exit();
        }
    }

    private static Button button(ResultPager pager, String accessibleText) {
        return pager.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> accessibleText.equals(button.getAccessibleText())).findFirst().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void fx(Runnable action) throws Exception {
        var completed = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
            finally { completed.countDown(); }
        });
        if (!completed.await(10, TimeUnit.SECONDS)) throw new AssertionError("FX action timed out");
        if (failure.get() instanceof AssertionError assertion) throw assertion;
        if (failure.get() != null) throw new RuntimeException(failure.get());
    }
}
