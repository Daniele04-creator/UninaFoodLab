package it.uninafoodlab.presentation.navigation;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

final class UnsavedChangesGuard {
    enum Action { LOGOUT, CLOSE, PASSWORD }

    private final BooleanSupplier changed;
    private final BooleanSupplier busy;
    private final Predicate<Action> confirmation;
    private final Runnable busyFeedback;

    UnsavedChangesGuard(BooleanSupplier changed, BooleanSupplier busy,
            Predicate<Action> confirmation, Runnable busyFeedback) {
        this.changed = changed;
        this.busy = busy;
        this.confirmation = confirmation;
        this.busyFeedback = busyFeedback;
    }

    boolean allow(Action action) {
        if (busy.getAsBoolean()) {
            busyFeedback.run();
            return false;
        }
        return !changed.getAsBoolean() || confirmation.test(action);
    }
}
