package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;


final class ParticipantWorkspaceAsync {

    private final Hooks hooks;
    private Runnable retryRead;
    private String refreshConfirmation;

    ParticipantWorkspaceAsync(Hooks hooks) {
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    <T> void read(
            String operationName,
            String loadingMessage,
            Callable<T> operation,
            Consumer<T> onSuccess,
            String failureMessage,
            Runnable onFailure) {
        Objects.requireNonNull(onFailure, "onFailure");
        var confirmation = refreshConfirmation;
        var version = hooks.nextVersion().getAsLong();
        retryRead = null;
        hooks.busy().accept(true);
        showLoadingFeedback(loadingMessage, confirmation);
        hooks.retry().accept(false);
        FxAsyncExecutor.run(operationName, operation,
                value -> {
                    if (hooks.stale().test(version)) {
                        return;
                    }
                    hooks.busy().accept(false);
                    withConfirmation(confirmation, () -> onSuccess.accept(value));
                    if (hooks.current().test(version)) {
                        finishFeedback(confirmation);
                    }
                },
                failure -> {
                    if (hooks.stale().test(version)) {
                        return;
                    }
                    hooks.busy().accept(false);
                    retryRead = () -> withConfirmation(confirmation, () -> read(operationName,
                            loadingMessage, operation, onSuccess, failureMessage, onFailure));
                    showReadFailure(failureMessage, confirmation);
                    hooks.retry().accept(true);
                    onFailure.run();
                });
    }

    private void showLoadingFeedback(String loadingMessage, String confirmation) {
        hooks.feedback().accept(confirmation == null ? loadingMessage
                : confirmation + " Aggiornamento dei dati…", confirmation == null
                ? ParticipantWorkspaceView.FeedbackType.INFO : ParticipantWorkspaceView.FeedbackType.SUCCESS);
    }

    private void showReadFailure(String failureMessage, String confirmation) {
        hooks.feedback().accept(confirmation == null ? failureMessage
                : confirmation + " I dati non sono stati aggiornati. " + failureMessage,
                ParticipantWorkspaceView.FeedbackType.ERROR);
    }

    private void finishFeedback(String confirmation) {
        if (confirmation == null) hooks.hideLoadingFeedback().run();
        else hooks.feedback().accept(confirmation, ParticipantWorkspaceView.FeedbackType.SUCCESS);
    }

    void retry() {
        if (retryRead != null) retryRead.run();
    }

    void invalidateRead() {
        hooks.nextVersion().getAsLong();
        retryRead = null;
        hooks.retry().accept(false);
        hooks.busy().accept(false);
        finishFeedback(refreshConfirmation);
    }

    void contextChanged() {
        invalidateRead();
        if (refreshConfirmation == null) {
            hooks.feedback().accept("", ParticipantWorkspaceView.FeedbackType.INFO);
        }
    }

    void refreshAfterWrite(String confirmation, Runnable refresh) {
        hooks.feedback().accept(confirmation, ParticipantWorkspaceView.FeedbackType.SUCCESS);
        withConfirmation(confirmation, refresh);
    }


    private void withConfirmation(String confirmation, Runnable action) {
        var previous = refreshConfirmation;
        refreshConfirmation = confirmation;
        try {
            action.run();
        } finally {
            refreshConfirmation = previous;
        }
    }

    <T> void write(
            String operationName,
            Callable<T> operation,
            Runnable onSuccess,
            String failureMessage) {
        var version = hooks.nextVersion().getAsLong();
        retryRead = null;
        hooks.busy().accept(true);
        hooks.feedback().accept("Salvataggio in corso…", ParticipantWorkspaceView.FeedbackType.INFO);
        hooks.retry().accept(false);
        FxAsyncExecutor.run(operationName, operation,
                ignored -> {
                    if (!hooks.stale().test(version)) {
                        hooks.busy().accept(false);
                        onSuccess.run();
                    }
                },
                failure -> {
                    if (!hooks.stale().test(version)) {
                        hooks.busy().accept(false);
                        hooks.feedback().accept(failureMessage, ParticipantWorkspaceView.FeedbackType.ERROR);
                    }
                });
    }

    record Hooks(
            LongSupplier nextVersion,
            LongPredicate stale,
            LongPredicate current,
            Consumer<Boolean> busy,
            BiConsumer<String, ParticipantWorkspaceView.FeedbackType> feedback,
            Consumer<Boolean> retry,
            Runnable hideLoadingFeedback) {
        Hooks {
            Objects.requireNonNull(nextVersion, "nextVersion");
            Objects.requireNonNull(stale, "stale");
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(busy, "busy");
            Objects.requireNonNull(feedback, "feedback");
            Objects.requireNonNull(retry, "retry");
            Objects.requireNonNull(hideLoadingFeedback, "hideLoadingFeedback");
        }
    }
}
