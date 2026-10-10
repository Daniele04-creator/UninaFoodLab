package it.uninafoodlab.infrastructure.course;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary.IngredientDose;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

final class PublishedCourseQueries {
    private static final String SESSION_TOKEN = "sessionToken";

    private static final String SELECT_PUBLISHED_SESSIONS = """
            SELECT session_id, sequence_number, scheduled_at, mode,
                   duration_minutes, online_url, venue, capacity, attendance_closes_at, status
              FROM app.published_course_sessions_batch(?, ARRAY[?]::uuid[])
             WHERE session_id IS NOT NULL
            """;
    private final DataSource dataSource;

    PublishedCourseQueries(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    List<PublishedCourseSessionSummary> publishedSessions(
            UUID sessionToken, UUID courseId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SELECT_PUBLISHED_SESSIONS)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                var sessions = new ArrayList<PublishedCourseSessionSummary>();
                while (result.next()) {
                    sessions.add(mapPublishedSession(result));
                }
                return List.copyOf(sessions);
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to load published sessions", exception);
        }
    }

    Map<UUID, List<PublishedCourseSessionSummary>> publishedSessions(UUID sessionToken, List<UUID> courseIds) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        var requestedIds = List.copyOf(courseIds).stream().distinct().toList();
        if (requestedIds.size() > 100) throw new IllegalArgumentException("At most one hundred course IDs required");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT * FROM app.published_course_sessions_batch(?, ?)")) {
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
            throw new CourseRepositoryException("Unable to load published course sessions", exception);
        }
    }

    private static Map<UUID, List<PublishedCourseSessionSummary>> readSessions(ResultSet result) throws SQLException {
        var sessions = new LinkedHashMap<UUID, List<PublishedCourseSessionSummary>>();
        while (result.next()) {
            var courseId = result.getObject("course_id", UUID.class);
            var courseSessions = sessions.computeIfAbsent(courseId, ignored -> new ArrayList<>());
            if (result.getObject("session_id", UUID.class) != null) courseSessions.add(mapPublishedSession(result));
        }
        sessions.replaceAll((courseId, courseSessions) -> List.copyOf(courseSessions));
        return Map.copyOf(sessions);
    }

    List<PublishedSessionRecipeSummary> publishedMenu(UUID sessionToken, UUID courseId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "select session_id, recipe_id, name, description, base_servings, portions_per_participant, "
                        + "ingredient_name, quantity, unit from app.list_published_session_menu_doses(?,?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                var menu = new LinkedHashMap<MenuKey, PublishedSessionRecipeSummary>();
                var doses = new LinkedHashMap<MenuKey, List<IngredientDose>>();
                while (result.next()) {
                    var key = new MenuKey(result.getObject("session_id", UUID.class),
                            result.getObject("recipe_id", UUID.class));
                    if (!menu.containsKey(key)) {
                        menu.put(key, new PublishedSessionRecipeSummary(key.sessionId(), key.recipeId(),
                                result.getString("name"), result.getString("description"),
                                result.getInt("base_servings"), result.getBigDecimal("portions_per_participant")));
                    }
                    var ingredients = doses.computeIfAbsent(key, ignored -> new ArrayList<>());
                    if (result.getString("ingredient_name") != null) {
                        ingredients.add(new IngredientDose(result.getString("ingredient_name"),
                                result.getBigDecimal("quantity"), MeasurementUnit.valueOf(result.getString("unit"))));
                    }
                }
                return menu.entrySet().stream().map(entry -> {
                    var item = entry.getValue();
                    return new PublishedSessionRecipeSummary(item.sessionId(), item.recipeId(), item.name(),
                            item.description(), item.baseServings(), item.portionsPerParticipant(), doses.get(entry.getKey()));
                }).toList();
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to load published session menu", exception);
        }
    }

    private record MenuKey(UUID sessionId, UUID recipeId) {}

    static PublishedCourseSummary mapPublishedCourse(ResultSet result)
            throws SQLException {
        return new PublishedCourseSummary(
                result.getObject("course_id", UUID.class),
                result.getObject("category_id", UUID.class),
                result.getString("category_name"),
                result.getString("chef_name"),
                CourseTitle.of(result.getString("title")),
                result.getString("description"),
                result.getObject("start_date", LocalDate.class),
                result.getObject("start_time", LocalTime.class),
                result.getInt("session_count"),
                result.getObject("last_session_ends_at", LocalDateTime.class),
                CourseStatus.valueOf(result.getString("status")));
    }

    private static PublishedCourseSessionSummary mapPublishedSession(ResultSet result)
            throws SQLException {
        return new PublishedCourseSessionSummary(
                result.getObject("session_id", UUID.class),
                result.getInt("sequence_number"),
                result.getObject("scheduled_at", LocalDateTime.class),
                SessionMode.valueOf(result.getString("mode")),
                result.getInt("duration_minutes"),
                optionalUri(result.getString("online_url")),
                Optional.ofNullable(result.getString("venue")),
                Optional.ofNullable(result.getObject("capacity", Integer.class)),
                Optional.ofNullable(result.getObject("attendance_closes_at", LocalDateTime.class)),
                SessionStatus.valueOf(result.getString("status")));
    }

    private static Optional<URI> optionalUri(String value) {
        return value == null ? Optional.empty() : Optional.of(URI.create(value));
    }
}
