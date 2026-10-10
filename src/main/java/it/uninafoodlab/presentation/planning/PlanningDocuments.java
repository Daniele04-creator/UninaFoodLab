package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.planning.SessionPlanningSnapshot;
import java.util.List;
import javafx.stage.Window;

final class PlanningDocuments {
    private PlanningDocuments() {}

    static String exportContext(List<CourseSessionSummary> sessions, List<SessionPlanningSnapshot> snapshots) {
        var snapshotsBySessionId = new java.util.HashMap<java.util.UUID, SessionPlanningSnapshot>();
        snapshots.forEach(snapshot -> snapshotsBySessionId.putIfAbsent(snapshot.sessionId(), snapshot));
        return String.join("; ", sessions.stream().map(session -> {
            var snapshot = snapshotsBySessionId.get(session.id());
            if (snapshot == null) throw new IllegalStateException("Missing session planning export snapshot");
            return sessionContext(snapshot);
        }).toList());
    }

    static String sessionContext(SessionPlanningSnapshot snapshot) {
        return snapshot.scheduledAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu, HH:mm"))
                + ", " + PlanningLabels.stateLabel(snapshot.state());
    }

    static void print(Window owner, boolean busy, String context, List<IngredientRequirement> rows,
            ShoppingListPrinter.PrintingProgress progress) {
        if (busy || rows.isEmpty()) return;
        ShoppingListPrinter.print(owner, context, List.copyOf(rows), progress);
    }

}
