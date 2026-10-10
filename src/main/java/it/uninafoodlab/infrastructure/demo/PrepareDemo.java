package it.uninafoodlab.infrastructure.demo;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.database.DatabaseMigrator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public final class PrepareDemo {
    private PrepareDemo() {}

    public static void main(String[] args) {
        try {
            if (args.length > 1) {
                throw new IllegalArgumentException("Uso: PrepareDemo [AAAA-MM-GG]");
            }
            var date = args.length == 0 ? LocalDate.now(ZoneId.of("Europe/Rome")) : LocalDate.parse(args[0]);
            var config = ApplicationConfig.from(System::getenv);
            requireDevelopment(config);
            DatabaseMigrator.migrate(config);
            var created = prepare(config, date);
            System.out.println(created ? "Scenario demo aggiunto per " + date
                    : "Scenario demo già presente per " + date);
            System.out.println("Corso laboratorio dal " + date.plusDays(7)
                    + "; report dimostrativo di " + date.minusMonths(1).withDayOfMonth(1));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (SQLException exception) {
            System.err.println("Scenario non preparato: verificare database e configurazione locale (SQLSTATE "
                    + exception.getSQLState() + "). Nessun dato demo parziale è stato salvato.");
            System.exit(1);
        }
    }

    public static boolean prepare(ApplicationConfig config, LocalDate date) throws SQLException {
        requireDevelopment(config);
        try (var connection = DriverManager.getConnection(config.databaseUrl(),
                config.databaseMigratorUser(), config.databaseMigratorPassword())) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(738291645)")) {
                    lock.execute();
                }
                var future = id(date, "future");
                var past = id(date, "past");
                var futureExists = PortfolioDemoCourse.exists(connection, future);
                var pastExists = PortfolioDemoCourse.exists(connection, past);
                if (futureExists || pastExists) {
                    requireUnmodifiedScenario(connection, date, futureExists, pastExists);
                    connection.commit();
                    return false;
                }
                if (DemoSql.scalar(connection, "SELECT count(*) FROM app.chef_profile WHERE id='20000000-0000-0000-0000-000000000001'") != 1
                        || DemoSql.scalar(connection, "SELECT count(*) FROM app.recipe WHERE id IN ('50000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000002')") != 2) {
                    throw new IllegalStateException("Catalogo demo assente: questo comando aggiunge corsi a una demo esistente. "
                            + "Per crearla usare scripts/prepare-demo.ps1 su un database locale vuoto.");
                }
                PortfolioDemoCourse.insert(connection, date, false);
                PortfolioDemoCourse.insert(connection, date, true);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void requireDevelopment(ApplicationConfig config) {
        if (!"development".equals(config.environmentName())) {
            throw new IllegalArgumentException("La preparazione demo richiede UNINAFOODLAB_ENV=development.");
        }
    }

    static UUID id(LocalDate date, String part) {
        return PortfolioDemoCourse.id(date, part);
    }

    private static void requireUnmodifiedScenario(
            Connection connection, LocalDate date, boolean futureExists, boolean pastExists) throws SQLException {
        if (!futureExists || !pastExists
                || !PortfolioDemoCourse.matches(connection, date, false)
                || !PortfolioDemoCourse.matches(connection, date, true)) {
            throw new IllegalStateException("Scenario demo modificato o incompleto: conservato senza "
                    + "sovrascritture. Scegliere una nuova data di riferimento.");
        }
    }
}
