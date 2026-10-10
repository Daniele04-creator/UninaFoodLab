package it.uninafoodlab.infrastructure.course;

import it.uninafoodlab.application.course.CourseCreation;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.application.course.CourseNotReadyException;
import it.uninafoodlab.application.course.CourseRepository;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.SessionPlan;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcCourseRepository implements CourseRepository {

    private static final String CREATE_COURSE =
            "select app.create_course(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String ASSIGN_RECIPE =
            "select app.assign_recipe_to_session(?, ?, ?)";
    private static final String PUBLISH_COURSE =
            "select app.publish_course(?, ?)";

    private final DataSource dataSource;
    private final PublishedCourseQueries publishedCourses;
    private final ChefCourseQueries chefCourses;
    private final CoursePageQueries coursePages;

    public JdbcCourseRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.publishedCourses = new PublishedCourseQueries(dataSource);
        this.chefCourses = new ChefCourseQueries(dataSource);
        this.coursePages = new CoursePageQueries(dataSource);
    }

    @Override
    public UUID create(CourseCreation course) {
        Objects.requireNonNull(course, "course");
        try (var connection = dataSource.getConnection()) {
            try {
                var createdId = insertCourse(connection, course);
                connection.commit();
                return createdId;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new CourseRepositoryException("Unable to create course", exception);
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to create course", exception);
        }
    }

    @Override
    public UUID createAndPublish(CourseCreation course) {
        Objects.requireNonNull(course, "course");
        try (var connection = dataSource.getConnection()) {
            try {
                var createdId = insertCourse(connection, course);
                publish(connection, course.sessionToken(), createdId);
                connection.commit();
                return createdId;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new CourseRepositoryException("Unable to create and publish course", exception);
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to create and publish course", exception);
        }
    }

    @Override
    public List<CourseSummary> findByChef(
            UUID sessionToken, Optional<UUID> categoryId) {
        return findByChef(sessionToken, categoryId, Optional.empty());
    }

    @Override
    public List<CourseSummary> findByChef(UUID sessionToken, Optional<UUID> categoryId, Optional<CourseStatus> status) {
        return chefCourses.findByChef(sessionToken, categoryId, status);
    }

    @Override
    public Page<CourseSummary> findByChef(UUID sessionToken, CourseFilter filter, PageRequest page) {
        return coursePages.byChef(sessionToken, filter, page, null);
    }

    @Override
    public Optional<CourseSummary> findByChef(UUID sessionToken, UUID courseId) {
        return coursePages.byChef(sessionToken, courseId);
    }

    @Override
    public Page<PublishedCourseSummary> findPublished(UUID sessionToken, CourseFilter filter, PageRequest page) {
        return coursePages.published(sessionToken, filter, page, null);
    }

    @Override
    public List<PublishedCourseSummary> findPublishedByIds(UUID sessionToken, List<UUID> courseIds) {
        return coursePages.published(sessionToken, courseIds);
    }

    @Override
    public Map<UUID, List<PublishedCourseSessionSummary>> publishedSessions(UUID sessionToken, List<UUID> courseIds) {
        return publishedCourses.publishedSessions(sessionToken, courseIds);
    }

    @Override
    public List<CourseSessionSummary> sessions(UUID sessionToken, UUID courseId) {
        return chefCourses.sessions(sessionToken, courseId);
    }

    @Override
    public Map<UUID, List<CourseSessionSummary>> sessions(UUID sessionToken, List<UUID> courseIds) {
        return chefCourses.sessions(sessionToken, courseIds);
    }

    @Override
    public void assignRecipe(UUID sessionToken, UUID sessionId, UUID recipeId) {
        changeRecipe(ASSIGN_RECIPE, sessionToken, sessionId, recipeId);
    }

    @Override
    public void removeRecipe(UUID sessionToken, UUID sessionId, UUID recipeId) {
        changeRecipe("select app.remove_session_recipe(?, ?, ?)", sessionToken, sessionId, recipeId);
    }

    private void changeRecipe(String sql, UUID sessionToken, UUID sessionId, UUID recipeId) {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(recipeId, "recipeId");
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, sessionToken);
                statement.setObject(2, sessionId);
                statement.setObject(3, recipeId);
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new CourseRepositoryException("Unable to assign recipe", exception);
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to assign recipe", exception);
        }
    }

    @Override
    public void publish(UUID sessionToken, UUID courseId) {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection()) {
            try {
                publish(connection, sessionToken, courseId);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                if ("23514".equals(exception.getSQLState())) {
                    throw new CourseNotReadyException();
                }
                if ("42501".equals(exception.getSQLState())) {
                    throw new CourseAuthorizationException(
                            "Il corso non è disponibile per questo chef");
                }
                throw new CourseRepositoryException("Unable to publish course", exception);
            }
        } catch (SQLException exception) {
            throw new CourseRepositoryException("Unable to publish course", exception);
        }
    }

    private static void publish(Connection connection, UUID sessionToken, UUID courseId)
            throws SQLException {
        try (var statement = connection.prepareStatement(PUBLISH_COURSE)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            statement.execute();
        }
    }

    @Override
    public List<PublishedCourseSessionSummary> publishedSessions(
            UUID sessionToken, UUID courseId) {
        return publishedCourses.publishedSessions(sessionToken, courseId);
    }

    private static UUID insertCourse(Connection connection, CourseCreation course)
            throws SQLException {
        try (var statement = connection.prepareStatement(CREATE_COURSE)) {
            statement.setObject(1, course.sessionToken());
            statement.setObject(2, course.categoryId());
            statement.setString(3, course.title().value());
            statement.setString(4, course.description());
            statement.setObject(5, course.schedule().startDate());
            statement.setObject(6, course.schedule().startTime());
            statement.setInt(7, course.schedule().recurrenceDays());
            statement.setBigDecimal(8, course.ingredientMargin().value());
            var modes = course.schedule().sessions().stream()
                    .map(session -> session.mode().name())
                    .toArray(String[]::new);
            var durations = course.schedule().sessions().stream()
                    .map(SessionPlan::durationMinutes)
                    .toArray(Integer[]::new);
            var onlineUrls = course.schedule().sessions().stream()
                    .map(session -> session.onlineUrl().map(URI::toString).orElse(null))
                    .toArray(String[]::new);
            var venues = course.schedule().sessions().stream()
                    .map(session -> session.venue().orElse(null))
                    .toArray(String[]::new);
            var capacities = course.schedule().sessions().stream()
                    .map(session -> session.capacity().orElse(null))
                    .toArray(Integer[]::new);
            statement.setArray(9, connection.createArrayOf("text", modes));
            statement.setArray(10, connection.createArrayOf("integer", durations));
            statement.setArray(11, connection.createArrayOf("text", onlineUrls));
            statement.setArray(12, connection.createArrayOf("text", venues));
            statement.setArray(13, connection.createArrayOf("integer", capacities));
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Course creation returned no identifier");
                }
                return result.getObject(1, UUID.class);
            }
        }
    }

    @Override
    public List<PublishedSessionRecipeSummary> publishedMenu(UUID sessionToken, UUID courseId) {
        return publishedCourses.publishedMenu(sessionToken, courseId);
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
