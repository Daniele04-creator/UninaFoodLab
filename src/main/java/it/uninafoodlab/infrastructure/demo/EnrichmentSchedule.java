package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.scalar;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.CHEF;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

final class EnrichmentSchedule {
    private EnrichmentSchedule() {}

    static int freeHour(Connection connection, LocalDate start, int preferredHour) throws SQLException {
        for (int hour : new int[]{preferredHour, 9, 12, 15, 18}) {
            if (allThreeSessionsFree(connection, start, hour)) return hour;
        }
        throw new IllegalStateException("Nessun orario libero per i tre incontri demo dal " + start
                + ": nessun dato aggiunto.");
    }

    private static boolean allThreeSessionsFree(Connection connection, LocalDate start, int hour) throws SQLException {
        for (int sequence = 0; sequence < 3; sequence++) {
            var scheduled = start.plusDays(sequence * 7L).atTime(hour, 0);
            if (overlapsAnotherSession(connection, scheduled, sequence == 1 ? 60 : 120)) return false;
        }
        return true;
    }

    private static boolean overlapsAnotherSession(Connection connection, LocalDateTime scheduled,
                                                   int duration) throws SQLException {
        return scalar(connection, """
                SELECT count(*) FROM app.course_session s JOIN app.course c ON c.id=s.course_id
                WHERE c.chef_profile_id=? AND s.status='SCHEDULED'
                    AND s.scheduled_at < ?::timestamp + make_interval(mins=>?)
                    AND ?::timestamp < s.scheduled_at + make_interval(mins=>s.duration_minutes)
                """, CHEF, scheduled, duration, scheduled) > 0;
    }
}
