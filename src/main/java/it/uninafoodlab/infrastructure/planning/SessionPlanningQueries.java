package it.uninafoodlab.infrastructure.planning;

import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.application.planning.SessionPlanningSnapshot;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

final class SessionPlanningQueries {
    private static final String SNAPSHOTS = """
            select requested.session_id, requested.position, context.scheduled_at,
                   context.revision, context.state, context.provenance,
                   requirements.ingredient_name, requirements.unit,
                   requirements.confirmed_participants, requirements.required_quantity
              from unnest(?::uuid[]) with ordinality requested(session_id, position)
              left join lateral app.session_planning_export_context(?, requested.session_id) context on true
              left join lateral app.list_session_ingredient_requirements(?, requested.session_id) requirements on true
             order by requested.position, requirements.ingredient_name, requirements.unit
            """;

    private SessionPlanningQueries() {}

    static List<SessionPlanningSnapshot> snapshots(Connection connection, UUID sessionToken, List<UUID> sessionIds)
            throws SQLException {
        if (sessionIds.isEmpty()) return List.of();
        var requestedIds = connection.createArrayOf("uuid", sessionIds.toArray(UUID[]::new));
        try (var statement = connection.prepareStatement(SNAPSHOTS)) {
            statement.setArray(1, requestedIds);
            statement.setObject(2, sessionToken);
            statement.setObject(3, sessionToken);
            try (var rows = statement.executeQuery()) {
                return readSnapshots(rows);
            }
        } finally {
            requestedIds.free();
        }
    }

    private static List<SessionPlanningSnapshot> readSnapshots(ResultSet rows) throws SQLException {
        var snapshotsByPosition = new LinkedHashMap<Long, SessionPlanningSnapshot>();
        var requirementsByPosition = new LinkedHashMap<Long, List<IngredientRequirement>>();
        while (rows.next()) {
            long position = rows.getLong("position");
            if (!snapshotsByPosition.containsKey(position)) {
                var scheduledAt = rows.getObject("scheduled_at", LocalDateTime.class);
                if (scheduledAt == null) throw new SQLException("Session planning export returned no context");
                snapshotsByPosition.put(position, new SessionPlanningSnapshot(
                        rows.getObject("session_id", UUID.class), scheduledAt,
                        rows.getInt("revision"), rows.getString("state"), rows.getString("provenance"), List.of()));
                requirementsByPosition.put(position, new ArrayList<>());
            }
            if (rows.getString("ingredient_name") != null) {
                requirementsByPosition.get(position).add(new IngredientRequirement(
                        rows.getString("ingredient_name"), MeasurementUnit.valueOf(rows.getString("unit")),
                        rows.getInt("confirmed_participants"), rows.getBigDecimal("required_quantity")));
            }
        }
        return snapshotsByPosition.entrySet().stream().map(entry -> {
            var snapshot = entry.getValue();
            return new SessionPlanningSnapshot(snapshot.sessionId(), snapshot.scheduledAt(), snapshot.revision(),
                    snapshot.state(), snapshot.provenance(), requirementsByPosition.get(entry.getKey()));
        }).toList();
    }
}
