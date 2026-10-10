package it.uninafoodlab.presentation.course;

final class ReactiveFilterGuard {

    private boolean ready;
    private boolean adjusting;

    void markReady() {
        ready = true;
    }

    void update(Runnable change) {
        adjusting = true;
        try {
            change.run();
        } finally {
            adjusting = false;
        }
    }

    boolean permitsReload(boolean busy) {
        return ready && !adjusting && !busy;
    }
}
