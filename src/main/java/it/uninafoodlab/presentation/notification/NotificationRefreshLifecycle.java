package it.uninafoodlab.presentation.notification;

final class NotificationRefreshLifecycle {

    private boolean active;
    private boolean disposed;
    private boolean followUpRequested;

    void activate() {
        if (!disposed) {
            active = true;
        }
    }

    void deactivate() {
        active = false;
        followUpRequested = false;
    }

    void dispose() {
        active = false;
        followUpRequested = false;
        disposed = true;
    }

    void requestFollowUp() {
        if (active) {
            followUpRequested = true;
        }
    }

    boolean consumeFollowUp() {
        var requested = active && followUpRequested;
        followUpRequested = false;
        return requested;
    }

    boolean isActive() {
        return active;
    }
}
