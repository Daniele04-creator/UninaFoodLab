package it.uninafoodlab.infrastructure.support;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.database.DatabaseMigrator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public final class DemoDatabaseFixture {
    private static final List<String> TABLES = List.of("account", "chef_profile", "participant_profile",
            "category", "ingredient", "recipe", "recipe_ingredient", "course", "course_session",
            "enrollment", "practical_attendance", "session_recipe", "session_menu_snapshot",
            "notification", "notification_recipient");

    private DemoDatabaseFixture() {}

    public static ApplicationRuntime start(ApplicationConfig config) {
        migrate(config);
        return ApplicationRuntime.start(config);
    }

    public static void migrate(ApplicationConfig config) {
        DatabaseMigrator.migrate(config);
        var sql = seedSql();
        try (var connection = DriverManager.getConnection(config.databaseUrl(), config.databaseMigratorUser(),
                config.databaseMigratorPassword())) {
            connection.setAutoCommit(false);
            try {
                if (!installed(connection)) {
                    setTriggers(connection, "DISABLE");
                    try (var statement = connection.createStatement()) {
                        statement.execute(sql);
                        statement.executeUpdate("INSERT INTO app.schema_metadata(component) VALUES ('integration_demo_fixture')");
                    }
                    setTriggers(connection, "ENABLE");
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to install the synthetic integration-test fixture", exception);
        }
    }

    private static boolean installed(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT 1 FROM app.schema_metadata WHERE component='integration_demo_fixture'")) {
            return result.next();
        }
    }

    private static void setTriggers(Connection connection, String action) throws SQLException {
        try (var statement = connection.createStatement()) {
            for (var table : TABLES) {
                statement.execute("ALTER TABLE app." + table + " " + action + " TRIGGER USER");
            }
        }
    }

    private static String seedSql() {
        try (var stream = DemoDatabaseFixture.class.getResourceAsStream("/db/demo-fixture.sql")) {
            if (stream == null) throw new IllegalStateException("Missing integration-test fixture SQL");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the integration-test fixture SQL", exception);
        }
    }
}
