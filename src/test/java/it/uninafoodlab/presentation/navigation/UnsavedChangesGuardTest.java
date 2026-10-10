package it.uninafoodlab.presentation.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UnsavedChangesGuardTest {
    @Test
    void cancellingAnyDepartureRetainsTheDirtyWorkspace() {
        var changed = new AtomicBoolean(true);
        var prompts = new AtomicInteger();
        var guard = new UnsavedChangesGuard(changed::get, () -> false, action -> {
            prompts.incrementAndGet();
            return false;
        }, () -> {});

        for (var action : UnsavedChangesGuard.Action.values()) assertThat(guard.allow(action)).isFalse();
        assertThat(prompts).hasValue(3);
        assertThat(changed).isTrue();
    }

    @Test
    void unchangedAndRevertedWorkspacesLeaveWithoutPrompting() {
        var changed = new AtomicBoolean(false);
        var prompts = new AtomicInteger();
        var guard = new UnsavedChangesGuard(changed::get, () -> false, action -> {
            prompts.incrementAndGet();
            return true;
        }, () -> {});

        assertThat(guard.allow(UnsavedChangesGuard.Action.CLOSE)).isTrue();
        changed.set(true);
        assertThat(guard.allow(UnsavedChangesGuard.Action.LOGOUT)).isTrue();
        changed.set(false);
        assertThat(guard.allow(UnsavedChangesGuard.Action.PASSWORD)).isTrue();
        assertThat(prompts).hasValue(1);
    }

    @Test
    void runningOperationsBlockDepartureBeforeAnyDiscardConfirmation() {
        var prompts = new AtomicInteger();
        var busyMessages = new AtomicInteger();
        var guard = new UnsavedChangesGuard(() -> true, () -> true, action -> {
            prompts.incrementAndGet();
            return true;
        }, busyMessages::incrementAndGet);

        assertThat(guard.allow(UnsavedChangesGuard.Action.CLOSE)).isFalse();
        assertThat(prompts).hasValue(0);
        assertThat(busyMessages).hasValue(1);
    }

    @Test
    void consentDoesNotEraseDraftsBeforeTheDepartureActuallySucceeds() {
        var changed = new AtomicBoolean(true);
        var guard = new UnsavedChangesGuard(changed::get, () -> false, action -> true, () -> {});

        assertThat(guard.allow(UnsavedChangesGuard.Action.PASSWORD)).isTrue();
        assertThat(changed).isTrue();
    }
}
