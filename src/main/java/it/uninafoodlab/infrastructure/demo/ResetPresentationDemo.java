package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;
import static it.uninafoodlab.infrastructure.demo.DemoSql.scalar;
import static it.uninafoodlab.infrastructure.demo.PresentationDemoCourses.id;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.database.DatabaseMigrator;
import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public final class ResetPresentationDemo {
    private static final UUID CHEF = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String[] NAMES = {"Luca Bianchi", "Giulia Romano", "Marco De Luca", "Elena Ferri", "Alessandro Greco", "Beatrice Rinaldi", "Chiara Conti", "Davide Moretti", "Federica Gallo", "Lorenzo Marino", "Matteo Ricci", "Sara Esposito", "Simone Caruso", "Valentina Serra", "Antonio Costa", "Francesca Leone", "Gabriele Vitale", "Martina Russo"};
    private ResetPresentationDemo() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || !"--replace-local-data".equals(args[0]))
            throw new IllegalArgumentException("Uso: ResetPresentationDemo --replace-local-data [AAAA-MM-GG]. Richiede backup preventivo.");
        var config = ApplicationConfig.from(System::getenv);
        requireLocalDevelopment(config);
        DatabaseMigrator.migrate(config);
        var date = args.length == 2 ? LocalDate.parse(args[1]) : LocalDate.now(ZoneId.of("Europe/Rome"));
        reset(config, date);
        System.out.println("Demo locale ricreata: 3 corsi, 12 sessioni, 18 partecipanti sintetici. Data: " + date);
    }

    public static void reset(ApplicationConfig config, LocalDate date) throws SQLException, IOException {
        populate(config, date, true);
    }

    static boolean initialize(ApplicationConfig config, LocalDate date) throws SQLException, IOException {
        return populate(config, date, false);
    }

    private static boolean populate(ApplicationConfig config, LocalDate date, boolean replace) throws SQLException, IOException {
        requireLocalDevelopment(config);
        java.util.Objects.requireNonNull(date, "date");
        var catalog = loadCatalog();
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            connection.setAutoCommit(false);
            try {
                execute(connection, "SELECT pg_advisory_xact_lock(738291645)");
                if (!replace && !InitializePresentationDemo.requireEmptyDatabase(connection)) {
                    connection.commit();
                    return false;
                }
                if (replace) clearApplicationData(connection);
                insertCatalogAndParticipants(connection, catalog);

                execute(connection, "ALTER TABLE app.practical_attendance DISABLE TRIGGER practical_attendance_guard");
                PresentationDemoCourses.insert(connection, date);
                execute(connection, "ALTER TABLE app.practical_attendance ENABLE TRIGGER practical_attendance_guard");
                requireCoherentAttendance(connection);
                if (!replace) execute(connection, "INSERT INTO app.schema_metadata(component) VALUES ('presentation_demo_initialized')");
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    static void requireLocalDevelopment(ApplicationConfig config) {
        if (!"development".equals(config.environmentName())) throw new IllegalArgumentException("Il reset richiede UNINAFOODLAB_ENV=development.");
        if (!config.databaseUrl().matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d+)?/[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("Il reset è consentito solo su un database PostgreSQL locale con host esplicito.");
    }

    private static String loadCatalog() throws IOException {
        try (var stream = ResetPresentationDemo.class.getResourceAsStream("/demo/presentation-catalog.sql")) {
            if (stream == null) throw new IllegalStateException("Catalogo demo mancante: nessun dato modificato.");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void clearApplicationData(Connection connection) throws SQLException {
        execute(connection, "DELETE FROM app.schema_metadata WHERE component IN "
                + "('presentation_demo_enriched:v1', 'presentation_demo_explorer:v1')");
        for (var table : new String[]{"auth_session", "account_password_reset", "chef_invitation",
                "session_change_history", "session_outcome", "session_preparation_revision", "session_menu_snapshot",
                "practical_attendance", "notification_recipient",
                "notification", "enrollment", "session_recipe", "course_session", "course", "recipe_ingredient",
                "recipe", "ingredient", "category", "participant_profile"}) {
            execute(connection, "DELETE FROM app." + table);
        }
        execute(connection, "DELETE FROM app.chef_profile");
        execute(connection, "DELETE FROM app.account");
    }

    private static void insertCatalogAndParticipants(Connection connection, String catalog) throws SQLException {
        var password = "MartaRossi!2026".toCharArray();
        String hash;
        try {
            hash = new Pbkdf2PasswordHasher().hash(password);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        var account = UUID.fromString("10000000-0000-0000-0000-000000000001");
        execute(connection, "INSERT INTO app.account(id,username,password_hash,role,active) VALUES (?,?,?,'CHEF',true)",
                account, "marta.rossi", hash);
        execute(connection, "INSERT INTO app.chef_profile(id,account_id,display_name) VALUES (?,?,?)",
                CHEF, account, "Marta Rossi");
        execute(connection, "DELETE FROM app.schema_metadata WHERE component='first_chef_setup_pending'");
        execute(connection, catalog);
        for (int participant = 0; participant < NAMES.length; participant++) {

            execute(connection, """
                    INSERT INTO app.account(id,username,password_hash,role,active)
                    SELECT ?,?,a.password_hash,'PARTICIPANT',true
                    FROM app.account a JOIN app.chef_profile c ON c.account_id=a.id WHERE c.id=?
                    """, id("account-" + participant), "demo.partecipante." + (participant + 1), CHEF);
            execute(connection, "INSERT INTO app.participant_profile(id,account_id,display_name) VALUES (?,?,?)",
                    id("participant-" + participant), id("account-" + participant), NAMES[participant]);
        }
    }

    private static void requireCoherentAttendance(Connection connection) throws SQLException {
        var inconsistent = scalar(connection, """
                SELECT count(*) FROM app.practical_attendance a
                JOIN app.enrollment e ON e.id=a.enrollment_id JOIN app.course_session s ON s.id=a.session_id
                WHERE e.course_id<>s.course_id OR s.mode<>'PRACTICAL'
                    OR (a.status='CONFIRMED' AND e.status<>'ACTIVE')
                """);
        var overCapacity = scalar(connection, """
                SELECT count(*) FROM (
                    SELECT s.id FROM app.course_session s
                    JOIN app.practical_attendance a ON a.session_id=s.id AND a.status='CONFIRMED'
                    GROUP BY s.id HAVING count(*)>s.capacity
                ) overflow
                """);
        if (inconsistent != 0 || overCapacity != 0) {
            throw new IllegalStateException("Scenario incoerente: reset annullato.");
        }
    }
}
