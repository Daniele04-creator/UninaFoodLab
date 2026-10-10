package it.uninafoodlab.infrastructure.config;

import java.util.Objects;

public record ApplicationConfig(
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        String databaseMigratorUser,
        String databaseMigratorPassword,
        String environmentName) {

    private static final String DATABASE_URL = "UNINAFOODLAB_DB_URL";
    private static final String DATABASE_USER = "UNINAFOODLAB_DB_USER";
    private static final String DATABASE_PASSWORD = "UNINAFOODLAB_DB_PASSWORD";
    private static final String DATABASE_MIGRATOR_USER = "UNINAFOODLAB_DB_MIGRATOR_USER";
    private static final String DATABASE_MIGRATOR_PASSWORD = "UNINAFOODLAB_DB_MIGRATOR_PASSWORD";
    private static final String ENVIRONMENT_NAME = "UNINAFOODLAB_ENV";

    public ApplicationConfig {
        Objects.requireNonNull(databaseUrl, "databaseUrl");
        Objects.requireNonNull(databaseUser, "databaseUser");
        Objects.requireNonNull(databasePassword, "databasePassword");
        Objects.requireNonNull(databaseMigratorUser, "databaseMigratorUser");
        Objects.requireNonNull(databaseMigratorPassword, "databaseMigratorPassword");
        Objects.requireNonNull(environmentName, "environmentName");
    }

    public static ApplicationConfig from(Environment environment) {
        Objects.requireNonNull(environment, "environment");
        return new ApplicationConfig(
                required(environment, DATABASE_URL),
                required(environment, DATABASE_USER),
                required(environment, DATABASE_PASSWORD),
                required(environment, DATABASE_MIGRATOR_USER),
                required(environment, DATABASE_MIGRATOR_PASSWORD),
                required(environment, ENVIRONMENT_NAME));
    }

    @Override
    public String toString() {
        return "ApplicationConfig[connection details redacted]";
    }

    private static String required(Environment environment, String name) {
        var value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing required environment variable: " + name);
        }
        return value;
    }
}
