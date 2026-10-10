package it.uninafoodlab.infrastructure.notification;

import it.uninafoodlab.application.notification.ChefNotification;
import it.uninafoodlab.application.notification.NotificationDraft;
import it.uninafoodlab.application.notification.NotificationReason;
import it.uninafoodlab.application.notification.NotificationRepository;
import it.uninafoodlab.application.notification.ParticipantNotification;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcNotificationRepository implements NotificationRepository {
    private static final String SESSION_TOKEN = "sessionToken";

    private static final String SEND = "select app.send_course_notification(?, ?, ?, ?)";
    private static final String MARK_READ = "select app.mark_notification_read(?, ?)";
    private static final String PARTICIPANT_HISTORY = """
            select notification_id, title, message, reason, course_title, created_at, read_at, course_id
              from app.list_participant_notifications(?)
            """;
    private static final String CHEF_HISTORY = """
            select notification_id, title, message, reason, course_title, created_at,
                   recipient_count, course_id, course_start_date
              from app.list_chef_notifications_with_course_context(?)
            """;

    private final DataSource dataSource;

    public JdbcNotificationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public long countRecipients(UUID sessionToken, Optional<UUID> courseId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("select app.count_notification_recipients(?, ?)")) {
            statement.setObject(1, sessionToken);
            if (courseId.isPresent()) {
                statement.setObject(2, courseId.orElseThrow());
            } else {
                statement.setNull(2, Types.OTHER);
            }
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Recipient count returned no result");
                }
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw failure("Unable to count notification recipients", exception);
        }
    }

    @Override
    public UUID send(UUID sessionToken, Optional<UUID> courseId, NotificationDraft draft) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(draft, "draft");
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(SEND)) {
                statement.setObject(1, sessionToken);
                if (courseId.isPresent()) {
                    statement.setObject(2, courseId.orElseThrow());
                } else {
                    statement.setNull(2, Types.OTHER);
                }
                statement.setString(3, draft.title());
                statement.setString(4, draft.message());
                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Notification creation returned no identifier");
                    }
                    var id = result.getObject(1, UUID.class);
                    connection.commit();
                    return id;
                }
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw failure("Unable to send notification", exception);
            }
        } catch (SQLException exception) {
            throw failure("Unable to send notification", exception);
        }
    }

    @Override
    public void markRead(UUID sessionToken, UUID notificationId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(notificationId, "notificationId");
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(MARK_READ)) {
                statement.setObject(1, sessionToken);
                statement.setObject(2, notificationId);
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw failure("Unable to mark notification as read", exception);
            }
        } catch (SQLException exception) {
            throw failure("Unable to mark notification as read", exception);
        }
    }

    @Override
    public List<ParticipantNotification> findForParticipant(UUID sessionToken) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(PARTICIPANT_HISTORY)) {
            statement.setObject(1, sessionToken);
            try (var result = statement.executeQuery()) {
                var history = new ArrayList<ParticipantNotification>();
                while (result.next()) {
                    history.add(mapParticipant(result));
                }
                return List.copyOf(history);
            }
        } catch (SQLException exception) {
            throw failure("Unable to load participant notifications", exception);
        }
    }

    @Override
    public List<ChefNotification> findForChef(UUID sessionToken) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(CHEF_HISTORY)) {
            statement.setObject(1, sessionToken);
            try (var result = statement.executeQuery()) {
                var history = new ArrayList<ChefNotification>();
                while (result.next()) {
                    history.add(mapChef(result));
                }
                return List.copyOf(history);
            }
        } catch (SQLException exception) {
            throw failure("Unable to load Chef notifications", exception);
        }
    }

    private static ParticipantNotification mapParticipant(ResultSet result) throws SQLException {
        return new ParticipantNotification(
                result.getObject("notification_id", UUID.class),
                result.getString("title"),
                result.getString("message"),
                NotificationReason.valueOf(result.getString("reason")),
                Optional.ofNullable(result.getString("course_title")),
                result.getObject("created_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(result.getObject("read_at", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant),
                Optional.ofNullable(result.getObject("course_id", UUID.class)));
    }

    private static ChefNotification mapChef(ResultSet result) throws SQLException {
        return new ChefNotification(
                result.getObject("notification_id", UUID.class),
                result.getString("title"),
                result.getString("message"),
                NotificationReason.valueOf(result.getString("reason")),
                Optional.ofNullable(result.getString("course_title")),
                result.getObject("created_at", OffsetDateTime.class).toInstant(),
                result.getLong("recipient_count"),
                Optional.ofNullable(result.getObject("course_id", UUID.class)),
                Optional.ofNullable(result.getObject("course_start_date", java.time.LocalDate.class)));
    }

    private static NotificationRepositoryException failure(String message, SQLException cause) {
        return new NotificationRepositoryException(message, cause);
    }

    private static void rollback(Connection connection, SQLException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }
}
