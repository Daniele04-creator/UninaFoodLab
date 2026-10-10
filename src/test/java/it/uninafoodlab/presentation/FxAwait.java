package it.uninafoodlab.presentation;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;


public final class FxAwait {
    private FxAwait() {}

    public static void until(Callable<Boolean> condition, long timeoutSeconds) throws Exception {
        if (Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Await must run outside the JavaFX thread");
        }
        var completed = new CompletableFuture<Void>();
        var timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                try {
                    if (condition.call()) {
                        stop();
                        completed.complete(null);
                    }
                } catch (Exception | AssertionError failure) {
                    stop();
                    completed.completeExceptionally(failure);
                }
            }
        };
        Platform.runLater(timer::start);
        try {
            completed.get(timeoutSeconds, TimeUnit.SECONDS);
        } finally {
            Platform.runLater(timer::stop);
        }
    }
}
