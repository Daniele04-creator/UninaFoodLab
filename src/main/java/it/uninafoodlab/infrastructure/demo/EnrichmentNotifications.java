package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.CHEF;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.id;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

final class EnrichmentNotifications {
    private EnrichmentNotifications() {}

    static void insertBoth(Connection connection, UUID course, int courseIndex, LocalDate start) throws SQLException {
        insert(connection, course, courseIndex, 0, start);
        insert(connection, course, courseIndex, 1, start);
    }

    private static void insert(Connection connection, UUID course, int courseIndex,
                               int notice, LocalDate start) throws SQLException {
        var notification = id("notification-" + courseIndex + "-" + notice);
        boolean cancellation = courseIndex == 7 && notice == 1;
        execute(connection, """
                INSERT INTO app.notification(id,chef_profile_id,course_id,title,message,reason,created_at)
                VALUES (?,?,?,?,?,?,?)
                """, notification, CHEF, course, title(courseIndex, notice),
                cancellation ? "Il laboratorio è stato annullato. Le sessioni non si svolgeranno; consulta gli altri corsi in calendario."
                        : "Il percorso comprende due pratiche e un incontro online. Consulta le ricette associate a ogni sessione e conferma la partecipazione alle pratiche entro la scadenza indicata.",
                cancellation ? "SESSION_CANCELLED" : "GENERAL",
                start.minusDays(10 - notice * 2L).atTime(12, 0).atZone(ZoneId.of("Europe/Rome")).toOffsetDateTime());
        execute(connection, """
                INSERT INTO app.notification_recipient(notification_id,participant_profile_id)
                SELECT ?,participant_profile_id FROM app.enrollment WHERE course_id=?
                """, notification, course);
    }

    private static String title(int courseIndex, int notice) {
        if (courseIndex == 7) return notice == 0 ? "Programma del laboratorio" : "Laboratorio annullato";
        return notice == 0 ? "Programma dei tre incontri" : "Ingredienti e organizzazione";
    }
}
