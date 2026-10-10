package it.uninafoodlab.presentation.common;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;

public final class FxAsyncExecutor {

    private FxAsyncExecutor() {}

    public static <T> void run(
            String operationName,
            Callable<T> operation,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {
        Objects.requireNonNull(operationName, "operationName");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        var task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        Thread.ofVirtual().name(operationName).start(task);
    }
}
