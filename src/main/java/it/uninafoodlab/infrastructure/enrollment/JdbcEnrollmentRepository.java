package it.uninafoodlab.infrastructure.enrollment;

import it.uninafoodlab.application.enrollment.AttendanceUnavailableException;
import it.uninafoodlab.application.enrollment.AttendanceCommitment;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.enrollment.EnrollmentRepository;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcEnrollmentRepository implements EnrollmentRepository {
    private static final String SESSION_TOKEN = "sessionToken";

    private static final String ENROLL = "select app.enroll_in_course(?, ?)";
    private static final String WITHDRAW = "select app.withdraw_from_course(?, ?)";
    private static final String SET_ATTENDANCE =
            "select app.set_practical_attendance(?, ?, ?)";
    private static final String SELECT_PRACTICAL_SESSIONS = """
            select session_id, sequence_number, scheduled_at, duration_minutes,
                   venue, capacity, confirmed_count, remaining_places,
                   attendance_closes_at, attendance_status, recipe_count
              from app.list_enrollment_practical_sessions(?, ?)
            """;
    private static final String SELECT_ENROLLMENT_PAGE =
            "select * from app.list_participant_enrollments_page(?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_ENROLLMENTS_BY_IDS =
            "select * from app.list_participant_enrollments_by_ids(?, ?)";
    private static final String SELECT_COMMITMENTS =
            "select * from app.list_participant_commitments(?)";

    private final DataSource dataSource;

    public JdbcEnrollmentRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void enroll(UUID sessionToken, UUID courseId) {
        executeCourseMutation(ENROLL, sessionToken, courseId, "Unable to enroll in course");
    }

    @Override
    public void withdraw(UUID sessionToken, UUID courseId) {
        executeCourseMutation(
                WITHDRAW, sessionToken, courseId, "Unable to withdraw from course");
    }

    @Override
    public void setAttendance(
            UUID sessionToken, UUID sessionId, AttendanceStatus status) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(status, "status");
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(SET_ATTENDANCE)) {
                statement.setObject(1, sessionToken);
                statement.setObject(2, sessionId);
                statement.setString(3, status.name());
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                if ("23514".equals(exception.getSQLState())) {
                    throw new AttendanceUnavailableException();
                }
                throw new EnrollmentRepositoryException(
                        "Unable to update practical attendance", exception);
            }
        } catch (SQLException exception) {
            throw new EnrollmentRepositoryException(
                    "Unable to update practical attendance", exception);
        }
    }

    @Override
    public Page<EnrollmentSummary> findByParticipant(
            UUID sessionToken, CourseFilter filter, PageRequest page) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(page, "page");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SELECT_ENROLLMENT_PAGE)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, filter.categoryId().orElse(null));
            statement.setString(3, filter.search());
            statement.setString(4, filter.period().name());
            statement.setObject(5, filter.now());
            statement.setInt(6, page.size());
            statement.setInt(7, page.offset());
            try (var result = statement.executeQuery()) {
                var enrollments = new ArrayList<EnrollmentSummary>();
                long totalCount = 0;
                while (result.next()) {
                    totalCount = result.getLong("total_count");
                    if (result.getObject("course_id") != null) {
                        enrollments.add(mapEnrollment(result));
                    }
                }
                return new Page<>(enrollments, totalCount);
            }
        } catch (SQLException exception) {
            throw new EnrollmentRepositoryException("Unable to load enrollment page", exception);
        }
    }

    @Override
    public List<EnrollmentSummary> findByParticipant(UUID sessionToken, List<UUID> courseIds) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        var selectedIds = List.copyOf(courseIds);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SELECT_ENROLLMENTS_BY_IDS)) {
            var ids = connection.createArrayOf("uuid", selectedIds.toArray(UUID[]::new));
            try {
                statement.setObject(1, sessionToken);
                statement.setArray(2, ids);
                try (var result = statement.executeQuery()) {
                    var enrollments = new ArrayList<EnrollmentSummary>();
                    while (result.next()) {
                        enrollments.add(mapEnrollment(result));
                    }
                    return List.copyOf(enrollments);
                }
            } finally {
                ids.free();
            }
        } catch (SQLException exception) {
            throw new EnrollmentRepositoryException("Unable to load selected enrollments", exception);
        }
    }

    @Override
    public List<AttendanceCommitment> commitments(UUID sessionToken) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SELECT_COMMITMENTS)) {
            statement.setObject(1, sessionToken);
            try (var result = statement.executeQuery()) {
                var commitments = new ArrayList<AttendanceCommitment>();
                while (result.next()) {
                    commitments.add(new AttendanceCommitment(
                            result.getObject("course_id", UUID.class),
                            CourseTitle.of(result.getString("course_title")),
                            result.getObject("scheduled_at", java.time.LocalDateTime.class),
                            result.getInt("duration_minutes")));
                }
                return List.copyOf(commitments);
            }
        } catch (SQLException exception) {
            throw new EnrollmentRepositoryException("Unable to load attendance commitments", exception);
        }
    }

    @Override
    public List<PracticalAttendanceSummary> practicalSessions(
            UUID sessionToken, UUID courseId) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SELECT_PRACTICAL_SESSIONS)) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, courseId);
            try (var result = statement.executeQuery()) {
                var sessions = new ArrayList<PracticalAttendanceSummary>();
                while (result.next()) {
                    sessions.add(mapPracticalSession(result));
                }
                return List.copyOf(sessions);
            }
        } catch (SQLException exception) {
            throw new EnrollmentRepositoryException(
                    "Unable to load practical attendance", exception);
        }
    }

    private void executeCourseMutation(
            String sql, UUID sessionToken, UUID courseId, String failureMessage) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(courseId, "courseId");
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, sessionToken);
                statement.setObject(2, courseId);
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new EnrollmentRepositoryException(failureMessage, exception);
            }
        } catch (SQLException exception) {
            throw new EnrollmentRepositoryException(failureMessage, exception);
        }
    }

    private static EnrollmentSummary mapEnrollment(ResultSet result) throws SQLException {
        return new EnrollmentSummary(
                result.getObject("course_id", UUID.class),
                CourseTitle.of(result.getString("title")),
                result.getString("category_name"),
                result.getString("chef_name"),
                result.getObject("start_date", java.time.LocalDate.class),
                EnrollmentStatus.valueOf(result.getString("enrollment_status")),
                result.getObject("enrolled_at", OffsetDateTime.class).toInstant());
    }

    private static PracticalAttendanceSummary mapPracticalSession(ResultSet result)
            throws SQLException {
        return new PracticalAttendanceSummary(
                result.getObject("session_id", UUID.class),
                result.getInt("sequence_number"),
                result.getObject("scheduled_at", java.time.LocalDateTime.class),
                result.getInt("duration_minutes"),
                result.getString("venue"),
                result.getInt("capacity"),
                result.getInt("confirmed_count"),
                result.getInt("remaining_places"),
                result.getObject("attendance_closes_at", java.time.LocalDateTime.class),
                Optional.ofNullable(result.getString("attendance_status"))
                        .map(AttendanceStatus::valueOf),
                result.getInt("recipe_count"));
    }

    private static void rollback(Connection connection, SQLException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }
}
