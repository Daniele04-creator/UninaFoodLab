package it.uninafoodlab.infrastructure.demo;

import static it.uninafoodlab.infrastructure.demo.DemoSql.execute;
import static it.uninafoodlab.infrastructure.demo.DemoSql.scalar;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.CAMPANIA;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.CHEF;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.RECIPES;
import static it.uninafoodlab.infrastructure.demo.EnrichmentFixture.id;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.database.DatabaseMigrator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;

public final class EnrichPresentationDemo {
    private static final String MARKER = "presentation_demo_enriched:v1";

    private EnrichPresentationDemo() {}

    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("Uso: EnrichPresentationDemo [AAAA-MM-GG]");
            var date = args.length == 0 ? LocalDate.now(ZoneId.of("Europe/Rome")) : LocalDate.parse(args[0]);
            var config = ApplicationConfig.from(System::getenv);
            ResetPresentationDemo.requireLocalDevelopment(config);
            DatabaseMigrator.migrate(config);
            System.out.println(enrich(config, date)
                    ? "Demo aggiornata con dati sintetici per Chef e Partecipante."
                    : "Demo già arricchita: dati e modifiche esistenti conservati.");
            System.out.println("Account Partecipante sintetico locale: demo.esploratore / MartaRossi!2026");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (SQLException exception) {
            System.err.println("Demo non arricchita (SQLSTATE " + exception.getSQLState()
                    + "): verificare dati e calendario. Nessun inserimento parziale salvato.");
            System.exit(1);
        }
    }

    public static boolean enrich(ApplicationConfig config, LocalDate date) throws SQLException {
        ResetPresentationDemo.requireLocalDevelopment(config);
        java.util.Objects.requireNonNull(date, "date");
        try (var connection = DriverManager.getConnection(config.databaseUrl(),
                config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            connection.setAutoCommit(false);
            try {
                execute(connection, "SELECT pg_advisory_xact_lock(738291645)");
                boolean coursesCreated = scalar(connection,
                        "SELECT count(*) FROM app.schema_metadata WHERE component=?", MARKER) == 0;
                if (coursesCreated) {
                    requirePresentationDemo(connection);
                    EnrichmentCatalog.insert(connection);
                    EnrichmentCourses.insertAll(connection, date);
                    execute(connection, "INSERT INTO app.schema_metadata(component) VALUES (?)", MARKER);
                }
                boolean participantCreated = EnrichmentParticipant.ensure(connection);
                connection.commit();
                return coursesCreated || participantCreated;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void requirePresentationDemo(Connection connection) throws SQLException {
        if (!hasInitializationEvidence(connection) || !hasRequiredDemoRecords(connection)) {
            throw new IllegalStateException("Demo di presentazione assente o incompleta: nessun dato modificato.");
        }
        if (scalar(connection, "SELECT count(*) FROM app.course WHERE id=?", id("course-2")) != 0) {
            throw new IllegalStateException("Scenario aggiuntivo già presente senza marcatura: nessun dato sovrascritto.");
        }
    }

    private static boolean hasInitializationEvidence(Connection connection) throws SQLException {
        return hasInitializationMarker(connection) || hasCompleteResetFixture(connection);
    }

    private static boolean hasInitializationMarker(Connection connection) throws SQLException {
        return scalar(connection,
                "SELECT count(*) FROM app.schema_metadata WHERE component='presentation_demo_initialized'") == 1;
    }

    private static boolean hasCompleteResetFixture(Connection connection) throws SQLException {
        return scalar(connection, "SELECT count(*) FROM app.course WHERE id IN (?,?,?)",
                PresentationDemoCourses.id("course-2"),
                PresentationDemoCourses.id("course-4"), PresentationDemoCourses.id("course-5")) == 3
                && scalar(connection, "SELECT count(*) FROM app.participant_profile WHERE id=?",
                        PresentationDemoCourses.id("participant-17")) == 1;
    }

    private static boolean hasRequiredDemoRecords(Connection connection) throws SQLException {
        return scalar(connection, "SELECT count(*) FROM app.chef_profile WHERE id=?", CHEF) == 1
                && scalar(connection, "SELECT count(*) FROM app.participant_profile WHERE id=?",
                        PresentationDemoCourses.id("participant-0")) == 1
                && scalar(connection, "SELECT count(*) FROM app.recipe WHERE id=?", RECIPES[2]) == 1
                && scalar(connection, "SELECT count(*) FROM app.category WHERE id=?", CAMPANIA) == 1
                && scalar(connection, "SELECT count(*) FROM app.course WHERE id=?",
                        PresentationDemoCourses.id("course-2")) == 1;
    }
}
