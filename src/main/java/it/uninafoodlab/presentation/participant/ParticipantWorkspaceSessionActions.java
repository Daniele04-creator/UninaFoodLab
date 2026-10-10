package it.uninafoodlab.presentation.participant;

import java.util.Objects;


final class ParticipantWorkspaceSessionActions {

    private ParticipantWorkspaceSessionActions() {}

    static void invoke(Runnable action, Runnable fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (action == null) {
            fallback.run();
        } else {
            action.run();
        }
    }
}
