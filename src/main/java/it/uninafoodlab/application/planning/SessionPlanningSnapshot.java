package it.uninafoodlab.application.planning;

import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessionPlanningSnapshot(UUID sessionId, LocalDateTime scheduledAt, int revision, String state,
        String provenance, List<IngredientRequirement> requirements) {
    public SessionPlanningSnapshot {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        state = Objects.requireNonNull(state, "state").strip();
        provenance = Objects.requireNonNull(provenance, "provenance").strip();
        requirements = List.copyOf(requirements);
        if (revision < 0 || state.isEmpty() || provenance.isEmpty()) {
            throw new IllegalArgumentException("Session planning snapshot is invalid");
        }
    }
}
