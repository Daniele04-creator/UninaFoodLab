package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;
import static it.uninafoodlab.infrastructure.demo.DemoSql.scalar;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.CHEF;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.id;

import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

final class EnrichmentParticipant {
    static final String MARKER = "presentation_demo_explorer:v1";
    static final String USERNAME = "demo.esploratore";
    private static final String DEMO_PASSWORD = "MartaRossi!2026";

    private EnrichmentParticipant() {}

    static boolean ensure(Connection connection) throws SQLException {
        if (scalar(connection, "SELECT count(*) FROM app.schema_metadata WHERE component=?", MARKER) == 1) {
            return false;
        }
        requireCoursesAndFreeIdentity(connection);
        insertAccount(connection);
        for (int course : EnrichmentFixture.COURSE_INDICES) insertEnrollment(connection, course);
        addNotificationRecipients(connection);
        execute(connection, "INSERT INTO app.schema_metadata(component) VALUES (?)", MARKER);
        return true;
    }

    private static void requireCoursesAndFreeIdentity(Connection connection) throws SQLException {
        if (scalar(connection, "SELECT count(*) FROM app.chef_profile WHERE id=?", CHEF) != 1
                || scalar(connection, "SELECT count(*) FROM app.course WHERE id=? AND status='PUBLISHED'",
                        id("course-2")) != 1) {
            throw new IllegalStateException("Corsi della demo arricchita assenti o modificati: Partecipante non aggiunto.");
        }
        if (scalar(connection, "SELECT count(*) FROM app.account WHERE id=? OR username=?",
                id("explorer-account"), USERNAME) != 0
                || scalar(connection, "SELECT count(*) FROM app.participant_profile WHERE id=?",
                        id("explorer-profile")) != 0) {
            throw new IllegalStateException("Identità sintetica già presente senza marcatura: nessun dato sovrascritto.");
        }
    }

    private static void insertAccount(Connection connection) throws SQLException {
        var password = DEMO_PASSWORD.toCharArray();
        String hash;
        try {
            hash = new Pbkdf2PasswordHasher().hash(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        execute(connection, "INSERT INTO app.account(id,username,password_hash,role,active) VALUES (?,?,?,'PARTICIPANT',true)",
                id("explorer-account"), USERNAME, hash);
        execute(connection, "INSERT INTO app.participant_profile(id,account_id,display_name) VALUES (?,?,?)",
                id("explorer-profile"), id("explorer-account"), "Giulia Esploratrice");
    }

    private static void insertEnrollment(Connection connection, int course) throws SQLException {
        var enrollment = id("explorer-enrollment-" + course);
        execute(connection, """
                INSERT INTO app.enrollment(id,participant_profile_id,course_id,status,enrolled_at)
                VALUES (?,?,?,'ACTIVE',clock_timestamp())
                """, enrollment, id("explorer-profile"), id("course-" + course));
        if (course == 0 || course == 3) addResponseIfOpen(connection, enrollment, course, 0, "CONFIRMED");
        if (course == 2) addResponseIfOpen(connection, enrollment, course, 0, "DECLINED");
        if (course == 0) addResponseIfOpen(connection, enrollment, course, 2, "DECLINED");
        if (course == 1 || course == 3) addResponseIfOpen(connection, enrollment, course, 2, "CONFIRMED");
    }

    private static void addResponseIfOpen(Connection connection, java.util.UUID enrollment,
                                          int course, int sequence, String status) throws SQLException {
        execute(connection, """
                INSERT INTO app.practical_attendance(enrollment_id,session_id,status,updated_at)
                SELECT ?,s.id,?,clock_timestamp() FROM app.course_session s
                WHERE s.id=? AND s.mode='PRACTICAL' AND s.status='SCHEDULED'
                    AND s.attendance_closes_at > clock_timestamp() AT TIME ZONE 'Europe/Rome'
                """, enrollment, status, id("session-" + course + "-" + sequence));
    }

    private static void addNotificationRecipients(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO app.notification_recipient(notification_id,participant_profile_id)
                SELECT n.id,? FROM app.notification n WHERE n.course_id=?
                """, id("explorer-profile"), id("course-2"));
    }
}
