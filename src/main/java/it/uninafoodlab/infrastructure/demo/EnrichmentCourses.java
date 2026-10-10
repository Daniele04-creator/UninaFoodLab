package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.CATEGORIES;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.COURSE_INDICES;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.CHEF;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.TITLES;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.id;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

final class EnrichmentCourses {
    private EnrichmentCourses() {}

    static void insertAll(Connection connection, LocalDate reference) throws SQLException {
        for (int index : COURSE_INDICES) insert(connection, reference, index);
    }

    private static void insert(Connection connection, LocalDate reference, int index) throws SQLException {
        var course = id("course-" + index);
        var start = EnrichmentFixture.start(reference, index);
        boolean cancelled = index == 7;
        int preferredHour = EnrichmentFixture.preferredHour(index);
        int hour = cancelled ? preferredHour : EnrichmentSchedule.freeHour(connection, start, preferredHour);
        saveCourse(connection, course, index, start, hour);
        insertEnrollments(connection, course, index, start);
        EnrichmentSessions.insertThree(connection, reference, index, start, hour);
        if (index >= 4 && index <= 6) {
            execute(connection, "UPDATE app.course SET status='COMPLETED' WHERE id=?", course);
        }
        EnrichmentNotifications.insertBoth(connection, course, index, start);
    }

    private static void saveCourse(Connection connection, UUID course, int index, LocalDate start,
                                   int hour) throws SQLException {
        execute(connection, """
                INSERT INTO app.course(id,chef_profile_id,category_id,title,description,start_date,
                    start_time,recurrence_days,session_count,status,ingredient_margin)
                VALUES (?,?,?,?,?, ?,?::time,7,3,?,0)
                """, course, CHEF, CATEGORIES[index], TITLES[index],
                "Tre incontri di cucina con preparazioni per una persona, ingredienti misurati e tecniche da ripetere a casa. "
                    + "Il programma combina un incontro online e due laboratori pratici; le ricette restano consultabili nella scheda della sessione.",
                start, hour + ":00", index == 7 ? "CANCELLED" : "PUBLISHED");
    }

    private static void insertEnrollments(Connection connection, UUID course, int index, LocalDate start)
            throws SQLException {
        for (int participant = 0; participant < 8; participant++) {
            execute(connection, """
                    INSERT INTO app.enrollment(id,participant_profile_id,course_id,status,enrolled_at)
                    VALUES (?,?,?,'ACTIVE',?)
                    """, id("enrollment-" + index + "-" + participant),
                    PresentationDemoCourses.id("participant-" + ((participant + index * 2) % 18)),
                    course, start.minusDays(14).atTime(10, 0).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime());
        }
    }
}
