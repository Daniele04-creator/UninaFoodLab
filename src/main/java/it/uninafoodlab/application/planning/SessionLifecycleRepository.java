package it.uninafoodlab.application.planning;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface SessionLifecycleRepository {
    record State(int revision, String status, String provenance) {}
    record SavedRecipe(UUID id, String name, String description, String doses) {}
    record ReportRow(UUID sessionId, String courseTitle, LocalDateTime scheduledAt,
            String mode, String state, Integer recipeCount) {}

    State state(UUID sessionToken, UUID sessionId);
    List<SavedRecipe> menu(UUID sessionToken, UUID sessionId);
    List<ReportRow> report(UUID sessionToken, YearMonth month);
    void refresh(UUID sessionToken, UUID sessionId);
}
