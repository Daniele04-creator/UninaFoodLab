package it.uninafoodlab.infrastructure.schedule;

import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.application.schedule.NewSessionInput;
import it.uninafoodlab.application.schedule.ScheduleRepository;
import it.uninafoodlab.application.schedule.ScheduleConflictException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcScheduleRepository implements ScheduleRepository {
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String DRAFT = "draft";

    private static final String RESCHEDULE =
            "select app.reschedule_course_session(?, ?, ?, ?, ?, ?, ?)";
    private static final String ADD =
            "select app.add_course_session(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String CANCEL =
            "select app.cancel_course_session(?, ?, ?, ?)";

    private final DataSource dataSource;

    public JdbcScheduleRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void reschedule(
            UUID sessionToken, UUID sessionId, LocalDateTime scheduledAt,
            Optional<LocalDateTime> attendanceClosesAt, Optional<String> location,
            NotificationDraft draft) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(attendanceClosesAt, "attendanceClosesAt");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(draft, DRAFT);
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(RESCHEDULE)) {
                statement.setObject(1, sessionToken);
                statement.setObject(2, sessionId);
                statement.setObject(3, scheduledAt);
                if (attendanceClosesAt.isPresent()) {
                    statement.setObject(4, attendanceClosesAt.orElseThrow());
                } else {
                    statement.setNull(4, Types.TIMESTAMP);
                }
                statement.setString(5, location.orElse(null));
                statement.setString(6, draft.title());
                statement.setString(7, draft.message());
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw failure("Unable to reschedule course session", exception);
            }
        } catch (SQLException exception) {
            throw failure("Unable to reschedule course session", exception);
        }
    }

    @Override
    public UUID add(
            UUID sessionToken, UUID courseId, NewSessionInput input,
            NotificationDraft draft) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(draft, DRAFT);
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(ADD)) {
                statement.setObject(1, sessionToken);
                statement.setObject(2, courseId);
                statement.setObject(3, input.scheduledAt());
                statement.setString(4, input.mode().name());
                statement.setInt(5, input.durationMinutes());
                statement.setString(6, input.onlineUrl().map(Object::toString).orElse(null));
                statement.setString(7, input.venue().orElse(null));
                statement.setObject(8, input.capacity().orElse(null), Types.INTEGER);
                statement.setObject(9, input.attendanceClosesAt().orElse(null), Types.TIMESTAMP);
                statement.setString(10, draft.title());
                statement.setString(11, draft.message());
                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Session creation returned no identifier");
                    }
                    var id = result.getObject(1, UUID.class);
                    connection.commit();
                    return id;
                }
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw failure("Unable to add course session", exception);
            }
        } catch (SQLException exception) {
            throw failure("Unable to add course session", exception);
        }
    }

    @Override
    public void cancel(UUID sessionToken, UUID sessionId, NotificationDraft draft) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(draft, DRAFT);
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(CANCEL)) {
                statement.setObject(1, sessionToken);
                statement.setObject(2, sessionId);
                statement.setString(3, draft.title());
                statement.setString(4, draft.message());
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw failure("Unable to cancel course session", exception);
            }
        } catch (SQLException exception) {
            throw failure("Unable to cancel course session", exception);
        }
    }

    private static RuntimeException failure(String message, SQLException cause) {
        if ("23P01".equals(cause.getSQLState())) {
            return new ScheduleConflictException();
        }
        return new ScheduleRepositoryException(message, cause);
    }

    private static void rollback(Connection connection, SQLException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }
}
