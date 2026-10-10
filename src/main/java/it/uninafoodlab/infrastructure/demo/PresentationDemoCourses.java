package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

final class PresentationDemoCourses {
    private static final String DEMO_TIME_ZONE = "Europe/Rome";
    static final int[] COURSE_INDICES = {2, 4, 5};

    private static final UUID CHEF = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String[][] COURSES = {
        {"Sapori di casa: cucina campana", "Cucina campana", "Gnocchi alla sorrentina", "Parmigiana di melanzane", "Pasta e patate con provola"},
        {"Mani in pasta: pizza e focaccia", "Panificazione", "Pizza margherita", "Focaccia al rosmarino"},
        {"Dolci da condividere", "Pasticceria", "Crostata di limone", "Torta caprese"},
        {"La tavola vegetale di stagione", "Cucina vegetariana", "Insalata di ceci e verdure", "Risotto ai funghi"},
        {"Il Mediterraneo nel piatto", "Cucina mediterranea", "Spaghetti alle vongole", "Orata al cartoccio"},
        {"La sfoglia fatta a mano", "Pasta fresca", "Ravioli ricotta e spinaci", "Gnocchi alla sorrentina"}
    };
    private static final String[] DESCRIPTIONS = {
        "Un percorso nei sapori della cucina campana: impasti di patate, sughi di pomodoro, frittura delle melanzane e mantecatura della pasta. Ogni incontro alterna dimostrazione, preparazione individuale e degustazione ragionata. Ingredienti pesati per una persona e attrezzature sono inclusi.",
        "Dall'idratazione alla cottura: impasto, pieghe, fermentazione e stesura di pizza e focaccia. Per osservare tutte le fasi nella durata del laboratorio, Marta alterna impasti maturati prima dell'incontro e impasti personali dei partecipanti, da completare a casa seguendo i tempi indicati. Ogni persona prepara una porzione e confronta consistenza e alveolatura.",
        "Frolla, crema al limone e impasti al cioccolato: quattro incontri dedicati alla precisione dei pesi, alla gestione delle temperature e alla cottura dei dolci. Si lavora in postazioni individuali con assaggi guidati e indicazioni per raffreddamento e conservazione. Ingredienti e stampi sono disponibili in laboratorio.",
        "Legumi, ortaggi e cereali diventano piatti completi attraverso tostatura, cotture controllate e mantecatura. Impariamo a gestire consistenze e stagionalità preparando un'insalata di ceci e un risotto ai funghi. Ogni partecipante cucina una porzione, confronta il risultato e riceve indicazioni per ripetere il piatto a casa.",
        "Un laboratorio dedicato ai prodotti del Mediterraneo: pulizia del pesce, controllo dei molluschi, cottura al cartoccio e gestione del fondo per la pasta. Le preparazioni sono individuali e la degustazione mette a confronto profumi, consistenze e tempi di cottura. Ingredienti, utensili e postazioni sono predisposti dalla scuola.",
        "Farina, uova e patate: studiamo consistenza degli impasti, riposo, stesura e chiusura dei ravioli. Le prove con gnocchi e pasta ripiena aiutano a riconoscere umidità e spessore corretti. Ogni partecipante prepara la propria porzione, prova la cottura e impara a organizzare il lavoro e conservare la pasta fresca."
    };

    private PresentationDemoCourses() {}

    static UUID id(String value) {
        return UUID.nameUUIDFromBytes(("uninafoodlab:presentation:v2:" + value).getBytes(StandardCharsets.UTF_8));
    }

    static void insert(Connection connection, LocalDate date) throws SQLException {

        for (int index : COURSE_INDICES) {
            insertCourse(connection, date, index);
        }
    }

    private static void insertCourse(Connection connection, LocalDate date, int index) throws SQLException {
        var courseDefinition = COURSES[index];
        var start = startDate(date, index);
        int hour = switch (index) {
            case 0 -> 15;
            case 1 -> 19;
            default -> 17;
        };
        var course = id("course-" + index);
        execute(connection, """
                INSERT INTO app.course(id,chef_profile_id,category_id,title,description,start_date,
                    start_time,recurrence_days,session_count,status,ingredient_margin)
                SELECT ?,?,id,?,?,?,?::time,7,4,'PUBLISHED',0 FROM app.category WHERE name=?
                """, course, CHEF, courseDefinition[0], DESCRIPTIONS[index], start, hour + ":00", courseDefinition[1]);
        insertEnrollments(connection, index, course, start);
        for (int sessionIndex = 0; sessionIndex < 4; sessionIndex++) {
            insertSession(connection, date, index, sessionIndex, start.plusDays(sessionIndex * 7L).atTime(hour, 0));
        }
        insertNotifications(connection, index, course, start);
    }

    private static LocalDate startDate(LocalDate date, int index) {
        if (index < 2) return date;
        if (index < 4) return date.plusDays(index + 1L);
        return date.minusMonths(index - 3L).withDayOfMonth(3);
    }

    private static void insertEnrollments(Connection connection, int index, UUID course, LocalDate start)
            throws SQLException {
        for (int participant = 0; participant < 14; participant++) {
            execute(connection, """
                    INSERT INTO app.enrollment(id,participant_profile_id,course_id,status,enrolled_at,withdrawn_at)
                    VALUES (?,?,?,'ACTIVE',?,NULL)
                    """, id("enrollment-" + index + "-" + participant),
                    id("participant-" + ((participant + index * 2) % 18)), course,
                    start.minusDays(14).atTime(10, 0).atZone(ZoneId.of(DEMO_TIME_ZONE)).toOffsetDateTime());
        }
    }

    private static void insertSession(Connection connection, LocalDate date, int courseIndex,
                                      int sessionIndex, LocalDateTime scheduled) throws SQLException {
        if (sessionIndex == 1) {
            insertOnlineSession(connection, courseIndex, sessionIndex, scheduled);
            return;
        }
        var session = id("session-" + courseIndex + "-" + sessionIndex);
        var course = id("course-" + courseIndex);
        boolean cancelled = courseIndex == 3 && sessionIndex == 3;
        var venue = courseIndex % 2 == 0 ? "Laboratorio Vesuvio · Napoli" : "Cucina del Porto · Napoli";
        execute(connection, """
                INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,mode,status,
                    duration_minutes,online_url,venue,capacity,attendance_closes_at)
                VALUES (?,?,?,?,'PRACTICAL',?,150,NULL,?,14,?)
                """, session, course, sessionIndex + 1, scheduled,
                cancelled ? "CANCELLED" : "SCHEDULED", venue, scheduled.minusDays(1));
        boolean empty = courseIndex == 2 && sessionIndex == 3;
        if (empty) return;
        insertRecipes(connection, session, courseIndex, sessionIndex);
        if (!cancelled) {
            insertAttendance(connection, session, courseIndex, scheduled, date);
        }
    }

    private static void insertOnlineSession(Connection connection, int courseIndex, int sessionIndex,
                                             LocalDateTime scheduled)
            throws SQLException {
        execute(connection, """
                INSERT INTO app.course_session(id,course_id,sequence_number,scheduled_at,mode,status,
                    duration_minutes,online_url,venue,capacity,attendance_closes_at)
                VALUES (?,?,?,?,'ONLINE','SCHEDULED',75,?,NULL,NULL,NULL)
                """, id("session-" + courseIndex + "-" + sessionIndex), id("course-" + courseIndex), sessionIndex + 1, scheduled,
                "https://example.org/uninafoodlab/incontro-" + (courseIndex + 1));
    }

    private static void insertRecipes(Connection connection, UUID session, int courseIndex, int sessionIndex)
            throws SQLException {
        var courseDefinition = COURSES[courseIndex];
        int count = sessionIndex == 0 ? 2 : 1;
        for (int recipe = 0; recipe < count; recipe++) {
            execute(connection, """
                    INSERT INTO app.session_recipe(session_id,recipe_id,portions_per_participant)
                    SELECT ?,id,1 FROM app.recipe WHERE name=?
                    """, session, courseDefinition[2 + (sessionIndex + recipe) % (courseDefinition.length - 2)]);
        }
    }

    private static void insertAttendance(Connection connection, UUID session, int courseIndex,
                                          LocalDateTime scheduled, LocalDate date) throws SQLException {
        var latestResponse = date.minusDays(1).atTime(10, 0);
        var respondedAt = scheduled.minusDays(3).isBefore(latestResponse) ? scheduled.minusDays(3) : latestResponse;
        for (int participant = 0; participant < 12; participant++) {
            execute(connection, """
                    INSERT INTO app.practical_attendance(enrollment_id,session_id,status,updated_at)
                    VALUES (?,?,?,?)
                    """, id("enrollment-" + courseIndex + "-" + participant), session,
                    participant < 9 + (courseIndex % 3) ? "CONFIRMED" : "DECLINED",
                    respondedAt.atZone(ZoneId.of(DEMO_TIME_ZONE)).toOffsetDateTime());
        }
    }

    private static void insertNotifications(Connection connection, int courseIndex, UUID course, LocalDate start)
            throws SQLException {
        for (int index = 0; index < 2; index++) {
            var notification = id("notification-" + courseIndex + "-" + index);
            var title = index == 0 ? "Benvenuti: il programma del laboratorio" : "Cosa portare al primo incontro";
            var message = index == 0
                    ? "Il percorso prevede quattro incontri. Troverete ingredienti già pesati per una persona e lavoreremo insieme sulle tecniche di preparazione. Confermate le adesioni alle pratiche nella vostra area entro il termine indicato."
                    : "Portate un grembiule e due contenitori richiudibili per le preparazioni. Arrivate dieci minuti prima per sistemare la postazione; gli utensili sono disponibili in laboratorio.";
            execute(connection, """
                    INSERT INTO app.notification(id,chef_profile_id,course_id,title,message,reason,created_at)
                    VALUES (?,?,?,?,?,'GENERAL',?)
                    """, notification, CHEF, course, title, message,
                    start.minusDays(10 - index * 2L).atTime(12, 0).atZone(ZoneId.of(DEMO_TIME_ZONE)).toOffsetDateTime());
            execute(connection, """
                    INSERT INTO app.notification_recipient(notification_id,participant_profile_id)
                    SELECT ?,participant_profile_id FROM app.enrollment WHERE course_id=?
                    """, notification, course);
        }
    }
}
