package it.uninafoodlab.infrastructure.demo;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.database.DatabaseMigrator;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;

public final class InitializePresentationDemo {
    private InitializePresentationDemo() {}

    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("Uso: InitializePresentationDemo [AAAA-MM-GG]");
            var date = args.length == 0 ? LocalDate.now(ZoneId.of("Europe/Rome")) : LocalDate.parse(args[0]);
            var config = ApplicationConfig.from(System::getenv);
            ResetPresentationDemo.requireLocalDevelopment(config);
            DatabaseMigrator.migrate(config);
            var created = prepare(config, date);
            System.out.println(created ? "Demo creata: 3 corsi, 12 sessioni e 18 partecipanti sintetici."
                    : "Demo già inizializzata: dati e modifiche esistenti conservati.");
            if (created) {
                System.out.println("Account sintetici locali: Chef marta.rossi; Partecipante demo.partecipante.1");
                System.out.println("Password esclusivamente demo per entrambi: MartaRossi!2026");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (SQLException | IOException exception) {
            System.err.println("Demo non preparata: verificare database e configurazione locale. Nessun popolamento parziale salvato.");
            System.exit(1);
        }
    }

    public static boolean prepare(ApplicationConfig config, LocalDate date) throws SQLException, IOException {
        return ResetPresentationDemo.initialize(config, date);
    }

    static boolean requireEmptyDatabase(Connection connection) throws SQLException {
        if (DemoSql.scalar(connection, "SELECT count(*) FROM app.schema_metadata WHERE component='presentation_demo_initialized'") != 0) {
            return false;
        }
        var tables = new ArrayList<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT tablename FROM pg_catalog.pg_tables
                WHERE schemaname='app' AND tablename NOT IN ('schema_metadata','flyway_schema_history')
                ORDER BY tablename
                """)) {
            while (rows.next()) {
                var name = rows.getString(1);

                if (!name.matches("[a-z][a-z0-9_]*")) {
                    throw new IllegalStateException("Nome tabella inatteso: inizializzazione demo annullata.");
                }
                tables.add("app.\"" + name + "\"");
            }
        }

        DemoSql.execute(connection, "LOCK TABLE " + String.join(",", tables) + " IN SHARE ROW EXCLUSIVE MODE");
        for (var table : tables) {
            if (DemoSql.scalar(connection, "SELECT count(*) FROM " + table) != 0) {
                throw new IllegalStateException("Database non vuoto: dati conservati. Prepara la demo su un nuovo database locale dedicato.");
            }
        }
        return true;
    }
}
