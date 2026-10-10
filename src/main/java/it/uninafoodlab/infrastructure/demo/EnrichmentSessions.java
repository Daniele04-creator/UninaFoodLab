package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.RECIPES;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.id;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

final class EnrichmentSessions {
    private EnrichmentSessions() {}

    static void insertThree(Connection connection, LocalDate reference, int courseIndex,
                            LocalDate start, int hour) throws SQLException {
        for (int sequence = 0; sequence < 3; sequence++) {
            insert(connection, reference, courseIndex, sequence,
                    start.plusDays(sequence * 7L).atTime(hour, 0));
        }
    }

    private static void insert(Connection connection, LocalDate reference, int courseIndex,
                               int sequence, LocalDateTime scheduled) throws SQLException {
        var session = id("session-" + courseIndex + "-" + sequence);
        boolean online = sequence == 1;
        boolean cancelled = courseIndex == 7;
        saveSession(connection, courseIndex, sequence, scheduled, session);
        if (!online && !cancelled) {
            addRecipes(connection, session, courseIndex, sequence);
            if (courseIndex < 4) insertAttendance(connection, session, courseIndex, reference);
        }
    }

    private static void saveSession(Connection connection, int courseIndex, int sequence,
                                    LocalDateTime scheduled, UUID session) throws SQLException {
        boolean online = sequence == 1;
        boolean cancelled = courseIndex == 7;
        execute(connection, """
                INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,mode,status,
                    duration_minutes,online_url,venue,capacity,attendance_closes_at)
                VALUES (?,?,?,?,?,?, ?,?,?,?,?)
                """, session, id("course-" + courseIndex), sequence + 1, scheduled, online ? "ONLINE" : "PRACTICAL",
                cancelled ? "CANCELLED" : "SCHEDULED", online ? 60 : 120,
                online ? "https://example.org/uninafoodlab/laboratorio-" + courseIndex : null,
                online ? null : "Laboratorio Partenope · Napoli", online ? null : 12,
                online ? null : scheduled.minusMinutes(1));
    }

    private static void addRecipes(Connection connection, UUID session, int courseIndex, int sequence)
            throws SQLException {
        int recipeCount = sequence == 0 ? 2 : 1;
        for (int recipe = 0; recipe < recipeCount; recipe++) {
            execute(connection, "INSERT INTO app.session_recipe(session_id,recipe_id,portions_per_participant) VALUES (?,?,1)",
                    session, RECIPES[(courseIndex + sequence + recipe) % RECIPES.length]);
        }
    }

    private static void insertAttendance(Connection connection, UUID session, int courseIndex, LocalDate date)
            throws SQLException {
        for (int participant = 0; participant < 7; participant++) {
            execute(connection, """
                    INSERT INTO app.practical_attendance(enrollment_id,session_id,status,updated_at)
                    VALUES (?,?,?,?)
                    """, id("enrollment-" + courseIndex + "-" + participant), session,
                    participant < 5 ? "CONFIRMED" : "DECLINED",
                    date.minusDays(1).atTime(10, 0).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime());
        }
    }
}
