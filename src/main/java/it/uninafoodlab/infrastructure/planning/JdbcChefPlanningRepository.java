package it.uninafoodlab.infrastructure.planning;

import it.uninafoodlab.application.planning.ChefPlanningRepository;
import it.uninafoodlab.application.planning.CourseParticipant;
import it.uninafoodlab.application.planning.CoursePlanningExport;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.application.planning.MonthlyReport;
import it.uninafoodlab.application.planning.SessionParticipant;
import it.uninafoodlab.application.planning.SessionPreparation;
import it.uninafoodlab.application.planning.SessionPlanningSnapshot;
import it.uninafoodlab.application.planning.RecipePreparation;
import it.uninafoodlab.application.planning.IngredientContribution;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.sql.SQLException;
import java.sql.Connection;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcChefPlanningRepository implements ChefPlanningRepository {
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String SESSION_ID = "sessionId";

    private static final String PREPARATION = """
            select confirmed_participants, pending_participants, declined_participants,
                   ingredient_margin, recipe_id, recipe_name, base_servings,
                   portions_per_participant, ingredient_id, ingredient_name, unit,
                   contribution_quantity, required_quantity
              from app.session_preparation(?, ?)
            """;

    private static final String REQUIREMENTS = """
            select ingredient_name, unit, confirmed_participants, required_quantity
              from app.list_session_ingredient_requirements(?, ?)
            """;
    private static final String COURSE_SESSIONS = """
            select session_id, sequence_number, scheduled_at, mode, status, duration_minutes, recipe_count
              from app.list_course_sessions(?, ?)
            """;
    private static final String PARTICIPANTS = """
            select participant_name, enrollment_status, attendance_status
              from app.list_session_participants(?, ?)
            """;
    private static final String COURSE_PARTICIPANTS = """
            select participant_name, enrollment_status
              from app.list_course_participants(?, ?)
            """;
    private static final String REPORT = """
            select total_courses, online_sessions, practical_sessions,
                   average_recipes, minimum_recipes, maximum_recipes
              from app.chef_monthly_report(?, ?, ?)
            """;

    private final DataSource dataSource;

    public JdbcChefPlanningRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public SessionPreparation sessionPreparation(UUID sessionToken, UUID sessionId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(sessionId, SESSION_ID);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(PREPARATION)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, sessionId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Session preparation returned no context");
                }
                int confirmed = result.getInt("confirmed_participants");
                int pending = result.getInt("pending_participants");
                int declined = result.getInt("declined_participants");
                var margin = result.getBigDecimal("ingredient_margin");
                var recipes = new LinkedHashMap<UUID, RecipePreparation>();
                var contributions = new LinkedHashMap<UUID, List<IngredientContribution>>();
                var totals = new LinkedHashMap<UUID, IngredientRequirement>();
                do {
                    var recipeId = result.getObject("recipe_id", UUID.class);
                    if (recipeId == null) {
                        continue;
                    }
                    if (!recipes.containsKey(recipeId)) {
                        recipes.put(recipeId, new RecipePreparation(recipeId,
                                result.getString("recipe_name"), result.getInt("base_servings"),
                                result.getBigDecimal("portions_per_participant"), List.of()));
                        contributions.put(recipeId, new ArrayList<>());
                    }
                    var ingredientId = result.getObject("ingredient_id", UUID.class);
                    if (ingredientId != null) {
                        var name = result.getString("ingredient_name");
                        var unit = MeasurementUnit.valueOf(result.getString("unit"));
                        contributions.get(recipeId).add(new IngredientContribution(ingredientId,
                                name, unit, result.getBigDecimal("contribution_quantity")));
                        totals.putIfAbsent(ingredientId, new IngredientRequirement(name, unit,
                                confirmed, result.getBigDecimal("required_quantity")));
                    }
                } while (result.next());
                var completeRecipes = recipes.values().stream()
                        .map(recipe -> new RecipePreparation(recipe.recipeId(), recipe.recipeName(),
                                recipe.baseServings(), recipe.portionsPerParticipant(),
                                contributions.get(recipe.recipeId())))
                        .toList();
                var orderedTotals = totals.values().stream()
                        .sorted(java.util.Comparator.comparing(IngredientRequirement::ingredientName)
                                .thenComparing(value -> value.unit().name()))
                        .toList();
                return new SessionPreparation(sessionId, confirmed, pending, declined,
                        margin, completeRecipes, orderedTotals);
            }
        } catch (SQLException exception) {
            throw failure("Unable to load session preparation", exception);
        }
    }

    @Override
    public List<IngredientRequirement> requirements(UUID sessionToken, UUID sessionId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(sessionId, SESSION_ID);
        try (var connection = dataSource.getConnection()) {
            return requirements(connection, sessionToken, sessionId);
        } catch (SQLException exception) {
            throw failure("Unable to load ingredient requirements", exception);
        }
    }

    @Override
    public List<SessionPlanningSnapshot> exportSnapshots(UUID sessionToken, List<UUID> sessionIds) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        var requestedIds = List.copyOf(Objects.requireNonNull(sessionIds, "sessionIds"));
        try (var connection = dataSource.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                var snapshots = SessionPlanningQueries.snapshots(connection, sessionToken, requestedIds);
                connection.commit();
                return List.copyOf(snapshots);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw failure("Unable to load planning export snapshot", exception);
        }
    }

    @Override
    public CoursePlanningExport courseExport(UUID sessionToken, UUID courseId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Rome"));
                var sessions = courseSessions(connection, sessionToken, courseId).stream()
                        .filter(session -> session.mode() == SessionMode.PRACTICAL)
                        .filter(session -> session.status() != SessionStatus.CANCELLED)
                        .filter(session -> session.scheduledAt().isAfter(now))
                        .toList();
                var snapshots = SessionPlanningQueries.snapshots(connection, sessionToken,
                        sessions.stream().map(CourseSessionSummary::id).toList());
                connection.commit();
                return new CoursePlanningExport(sessions, snapshots);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw failure("Unable to load course planning export snapshot", exception);
        }
    }

    private static List<CourseSessionSummary> courseSessions(Connection connection, UUID sessionToken, UUID courseId)
            throws SQLException {
        try (var statement = connection.prepareStatement(COURSE_SESSIONS)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                var sessions = new ArrayList<CourseSessionSummary>();
                while (result.next()) sessions.add(new CourseSessionSummary(
                        result.getObject("session_id", UUID.class), result.getInt("sequence_number"),
                        result.getObject("scheduled_at", java.time.LocalDateTime.class),
                        SessionMode.valueOf(result.getString("mode")), SessionStatus.valueOf(result.getString("status")),
                        result.getInt("duration_minutes"), java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), result.getInt("recipe_count")));
                return List.copyOf(sessions);
            }
        }
    }

    private static List<IngredientRequirement> requirements(Connection connection, UUID sessionToken, UUID sessionId)
            throws SQLException {
        try (var statement = connection.prepareStatement(REQUIREMENTS)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, sessionId);
            try (var result = statement.executeQuery()) {
                var requirements = new ArrayList<IngredientRequirement>();
                while (result.next()) requirements.add(new IngredientRequirement(
                        result.getString("ingredient_name"), MeasurementUnit.valueOf(result.getString("unit")),
                        result.getInt("confirmed_participants"), result.getBigDecimal("required_quantity")));
                return List.copyOf(requirements);
            }
        }
    }

    @Override
    public List<SessionParticipant> participants(UUID sessionToken, UUID sessionId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(sessionId, SESSION_ID);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(PARTICIPANTS)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, sessionId);
            try (var result = statement.executeQuery()) {
                var participants = new ArrayList<SessionParticipant>();
                while (result.next()) {
                    participants.add(new SessionParticipant(
                            result.getString("participant_name"),
                            EnrollmentStatus.valueOf(result.getString("enrollment_status")),
                            Optional.ofNullable(result.getString("attendance_status"))
                                    .map(AttendanceStatus::valueOf)));
                }
                return List.copyOf(participants);
            }
        } catch (SQLException exception) {
            throw failure("Unable to load session participants", exception);
        }
    }

    @Override
    public List<CourseParticipant> courseParticipants(UUID sessionToken, UUID courseId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(COURSE_PARTICIPANTS)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                var participants = new ArrayList<CourseParticipant>();
                while (result.next()) {
                    participants.add(new CourseParticipant(
                            result.getString("participant_name"),
                            EnrollmentStatus.valueOf(result.getString("enrollment_status"))));
                }
                return List.copyOf(participants);
            }
        } catch (SQLException exception) {
            throw failure("Unable to load course participants", exception);
        }
    }

    @Override
    public MonthlyReport monthlyReport(UUID sessionToken, YearMonth month) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(month, "month");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(REPORT)) {
            statement.setObject(1, sessionToken);
            statement.setInt(2, month.getYear());
            statement.setInt(3, month.getMonthValue());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Monthly report returned no row");
                }
                return new MonthlyReport(
                        month,
                        result.getLong("total_courses"),
                        result.getLong("online_sessions"),
                        result.getLong("practical_sessions"),
                        Optional.ofNullable(result.getBigDecimal("average_recipes")),
                        Optional.ofNullable(result.getObject("minimum_recipes", Integer.class)),
                        Optional.ofNullable(result.getObject("maximum_recipes", Integer.class)));
            }
        } catch (SQLException exception) {
            throw failure("Unable to load monthly report", exception);
        }
    }

    private static ChefPlanningRepositoryException failure(String message, SQLException cause) {
        return new ChefPlanningRepositoryException(message, cause);
    }
}
