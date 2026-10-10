package it.uninafoodlab.infrastructure.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

final class ChefCourseQueries {
    private static final String STATUS = "status";

    private static final String SELECT_COURSES = """
            SELECT course_id, category_id, category_name, title, description,
                   start_date, start_time, recurrence_days, session_count,
                   last_session_ends_at, status,
                   ingredient_margin
              FROM app.list_chef_courses(?, ?, ?)
            """;
    private static final String SELECT_SESSIONS = """
            SELECT session_id, sequence_number, scheduled_at, mode, status,
                   duration_minutes, online_url, venue, capacity, attendance_closes_at, completed_at,
                   recipe_ids, recipe_count
              FROM app.list_course_sessions(?, ?)
            """;
    private final DataSource dataSource;

    ChefCourseQueries(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    List<CourseSummary> findByChef(
            UUID sessionToken,
            Optional<UUID> categoryId,
            Optional<CourseStatus> status) {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(status, STATUS);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SELECT_COURSES)) {
            statement.setObject(1, sessionToken);
            if (categoryId.isPresent()) {
                statement.setObject(2, categoryId.orElseThrow());
            } else {
                statement.setNull(2, Types.OTHER);
            }
            if (status.isPresent()) {
                statement.setString(3, status.orElseThrow().name());
            } else {
                statement.setNull(3, Types.VARCHAR);
            }
            try (var result = statement.executeQuery()) {
                var courses = new ArrayList<CourseSummary>();
                while (result.next()) {
                    courses.add(mapCourse(result));
                }
                return List.copyOf(courses);
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to load courses", exception);
        }
    }

    List<CourseSessionSummary> sessions(UUID sessionToken, UUID courseId) {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SELECT_SESSIONS)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                var sessions = new ArrayList<CourseSessionSummary>();
                while (result.next()) {
                    sessions.add(mapSession(result));
                }
                return List.copyOf(sessions);
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to load course sessions", exception);
        }
    }

    Map<UUID, List<CourseSessionSummary>> sessions(UUID sessionToken, List<UUID> courseIds) {
        Objects.requireNonNull(sessionToken, "sessionToken");
        var requestedIds = List.copyOf(courseIds).stream().distinct().toList();
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        SELECT * FROM app.chef_course_sessions_batch(?, ?)
                        """)) {
            var ids = connection.createArrayOf("uuid", requestedIds.toArray(UUID[]::new));
            try {
                statement.setObject(1, sessionToken);
                statement.setArray(2, ids);
                try (var result = statement.executeQuery()) {
                    return readSessions(result);
                }
            } finally {
                ids.free();
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to load course sessions", exception);
        }
    }

    private static Map<UUID, List<CourseSessionSummary>> readSessions(ResultSet result) throws SQLException {
        var sessions = new LinkedHashMap<UUID, List<CourseSessionSummary>>();
        while (result.next()) {
            var courseId = result.getObject("course_id", UUID.class);
            var courseSessions = sessions.computeIfAbsent(courseId, ignored -> new ArrayList<>());
            if (result.getObject("session_id", UUID.class) != null) {
                courseSessions.add(mapSession(result));
            }
        }
        sessions.replaceAll((courseId, courseSessions) -> List.copyOf(courseSessions));
        return Map.copyOf(sessions);
    }

    static CourseSummary mapCourse(ResultSet result) throws SQLException {
        return new CourseSummary(
                result.getObject("course_id", UUID.class),
                result.getObject("category_id", UUID.class),
                result.getString("category_name"),
                CourseTitle.of(result.getString("title")),
                result.getString("description"),
                result.getObject("start_date", LocalDate.class),
                result.getObject("start_time", LocalTime.class),
                result.getInt("recurrence_days"),
                result.getInt("session_count"),
                Optional.ofNullable(result.getObject("last_session_ends_at", LocalDateTime.class)),
                CourseStatus.valueOf(result.getString(STATUS)),
                IngredientMargin.of(result.getBigDecimal("ingredient_margin")));
    }

    private static CourseSessionSummary mapSession(ResultSet result) throws SQLException {
        return new CourseSessionSummary(
                result.getObject("session_id", UUID.class),
                result.getInt("sequence_number"),
                result.getObject("scheduled_at", LocalDateTime.class),
                SessionMode.valueOf(result.getString("mode")),
                SessionStatus.valueOf(result.getString(STATUS)),
                result.getInt("duration_minutes"),
                optionalUri(result.getString("online_url")),
                Optional.ofNullable(result.getString("venue")),
                Optional.ofNullable(result.getObject("capacity", Integer.class)),
                Optional.ofNullable(result.getObject("attendance_closes_at", LocalDateTime.class)),
                Optional.ofNullable(result.getObject("completed_at", LocalDateTime.class)),
                recipeIds(result),
                result.getInt("recipe_count"));
    }

    private static List<UUID> recipeIds(ResultSet result) throws SQLException {
        var array = result.getArray("recipe_ids");
        try {
            return java.util.Arrays.stream((Object[]) array.getArray())
                    .map(value -> UUID.fromString(value.toString())).toList();
        } finally {
            array.free();
        }
    }

    private static Optional<URI> optionalUri(String value) {
        return value == null ? Optional.empty() : Optional.of(URI.create(value));
    }
}
