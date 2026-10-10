package it.uninafoodlab.application.enrollment;

import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseFilter;
import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository {

    void enroll(UUID sessionToken, UUID courseId);

    void withdraw(UUID sessionToken, UUID courseId);

    void setAttendance(UUID sessionToken, UUID sessionId, AttendanceStatus status);

    Page<EnrollmentSummary> findByParticipant(UUID sessionToken, CourseFilter filter, PageRequest page);

    List<EnrollmentSummary> findByParticipant(UUID sessionToken, List<UUID> courseIds);

    List<AttendanceCommitment> commitments(UUID sessionToken);

    List<PracticalAttendanceSummary> practicalSessions(UUID sessionToken, UUID courseId);
}
