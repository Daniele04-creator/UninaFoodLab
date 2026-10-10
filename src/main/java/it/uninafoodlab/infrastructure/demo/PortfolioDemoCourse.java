package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

final class PortfolioDemoCourse {
    private static final UUID CHEF = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID RECIPE_ONE = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID RECIPE_TWO = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final String DESCRIPTION = "Scenario sintetico locale rinnovabile per il portfolio.";

    private PortfolioDemoCourse() {}

    static UUID id(LocalDate date, String part) {
        return UUID.nameUUIDFromBytes(("uninafoodlab:portfolio-demo:v1:" + date + ":" + part)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String part(boolean past) {
        return past ? "past" : "future";
    }

    private static LocalDate startDate(LocalDate date, boolean past) {
        return past ? date.minusMonths(1).withDayOfMonth(8) : date.plusDays(7);
    }

    static boolean exists(Connection connection, UUID course) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM app.course WHERE id = ?")) {
            query.setObject(1, course);
            try (var rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    static void insert(Connection connection, LocalDate date, boolean past) throws SQLException {
        var course = id(date, part(past));
        var expected = courseFields(date, past);
        execute(connection, """
                INSERT INTO app.course (id, chef_profile_id, category_id, title, description,
                    start_date, start_time, recurrence_days, session_count, status, ingredient_margin)
                VALUES (?, ?, ?, ?, ?, ?, TIME '18:00', 7, 2, 'PUBLISHED', 0)
                """, course, CHEF, CATEGORY, expected.title(), DESCRIPTION, expected.startDate());
        for (int sequence = 1; sequence <= 2; sequence++) {
            insertSession(connection, course, sessionFields(date, past, sequence));
        }
    }

    private static void insertSession(Connection connection, UUID course, SessionFields session) throws SQLException {
        execute(connection, """
                INSERT INTO app.course_session (id, course_id, sequence_number, scheduled_at,
                    mode, status, duration_minutes, online_url, venue, capacity, attendance_closes_at)
                VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 90, ?, ?, ?, ?)
                """, session.id(), course, session.sequence(), session.scheduledAt(), session.mode(),
                session.onlineUrl(), session.venue(), session.capacity(), session.attendanceClosesAt());
        for (var recipe : session.recipes()) {
            execute(connection, "INSERT INTO app.session_recipe (session_id, recipe_id) VALUES (?, ?)",
                    session.id(), recipe);
        }
    }

    static boolean matches(Connection connection, LocalDate date, boolean past) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT title, start_date, chef_profile_id, status, session_count, category_id,
                       start_time, recurrence_days, ingredient_margin, description FROM app.course WHERE id = ?
                """)) {
            query.setObject(1, id(date, part(past)));
            try (var rows = query.executeQuery()) {
                if (!rows.next() || !courseFields(date, past).equals(readCourse(rows))) {
                    return false;
                }
            }
        }
        return sessionsMatch(connection, date, past);
    }

    private static boolean sessionsMatch(Connection connection, LocalDate date, boolean past) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT s.id, s.scheduled_at, s.mode, s.status, s.duration_minutes,
                       array_agg(sr.recipe_id ORDER BY sr.recipe_id) FILTER (WHERE sr.recipe_id IS NOT NULL),
                       s.online_url, s.venue, s.capacity, s.attendance_closes_at, s.sequence_number
                FROM app.course_session s LEFT JOIN app.session_recipe sr ON sr.session_id = s.id
                WHERE s.course_id = ? GROUP BY s.id ORDER BY s.sequence_number
                """)) {
            query.setObject(1, id(date, part(past)));
            try (var rows = query.executeQuery()) {
                for (int sequence = 1; sequence <= 2; sequence++) {
                    if (!rows.next() || !sessionFields(date, past, sequence).equals(readSession(rows))) {
                        return false;
                    }
                }
                return !rows.next();
            }
        }
    }

    private static CourseFields courseFields(LocalDate date, boolean past) {
        var title = (past ? "Demo archivio · " : "Demo laboratorio · ") + date;
        return new CourseFields(title, startDate(date, past), CHEF, "PUBLISHED", 2, CATEGORY,
                LocalTime.of(18, 0), 7, 0, DESCRIPTION);
    }

    private static SessionFields sessionFields(LocalDate date, boolean past, int sequence) {
        var id = id(date, part(past) + "-" + sequence);
        var scheduledAt = startDate(date, past).plusDays((sequence - 1) * 7L).atTime(18, 0);
        if (!past && sequence == 2) {
            return new SessionFields(id, scheduledAt, "ONLINE", "SCHEDULED", 90,
                    "https://example.org/uninafoodlab-demo", null, null, null, sequence, List.of());
        }
        var recipes = past && sequence == 1 ? List.of(RECIPE_ONE) : List.of(RECIPE_ONE, RECIPE_TWO);
        return new SessionFields(id, scheduledAt, "PRACTICAL", "SCHEDULED", 90,
                null, "Laboratorio demo", 12, scheduledAt.minusDays(1), sequence, recipes);
    }

    private static CourseFields readCourse(ResultSet rows) throws SQLException {
        return new CourseFields(rows.getString(1), rows.getObject(2, LocalDate.class),
                rows.getObject(3, UUID.class), rows.getString(4), rows.getInt(5),
                rows.getObject(6, UUID.class), rows.getObject(7, LocalTime.class),
                rows.getInt(8), rows.getBigDecimal(9).signum(), rows.getString(10));
    }

    private static SessionFields readSession(ResultSet rows) throws SQLException {
        return new SessionFields(rows.getObject(1, UUID.class), rows.getObject(2, LocalDateTime.class),
                rows.getString(3), rows.getString(4), rows.getInt(5), rows.getString(7),
                rows.getString(8), rows.getObject(9, Integer.class), rows.getObject(10, LocalDateTime.class),
                rows.getInt(11), readRecipes(rows));
    }

    private static List<UUID> readRecipes(ResultSet rows) throws SQLException {
        var recipes = rows.getArray(6);
        if (recipes == null) {
            return List.of();
        }
        try {
            return List.of((UUID[]) recipes.getArray());
        } finally {
            recipes.free();
        }
    }

    private record CourseFields(String title, LocalDate startDate, UUID chefId, String status, int sessionCount,
                                UUID categoryId, LocalTime startTime, int recurrenceDays, int marginSign, String description) {}

    private record SessionFields(UUID id, LocalDateTime scheduledAt, String mode, String status, int durationMinutes,
                                 String onlineUrl, String venue, Integer capacity, LocalDateTime attendanceClosesAt,
                                 int sequence, List<UUID> recipes) {}
}
