package it.uninafoodlab.infrastructure.database;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public final class DatabaseMigrator {

    private DatabaseMigrator() {}

    public static MigrateResult migrate(ApplicationConfig applicationConfig) {
        Objects.requireNonNull(applicationConfig, "applicationConfig");

        return Flyway.configure()
                .dataSource(
                        applicationConfig.databaseUrl(),
                        applicationConfig.databaseMigratorUser(),
                        applicationConfig.databaseMigratorPassword())
                .defaultSchema("app")
                .schemas("app")
                .createSchemas(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }
}
