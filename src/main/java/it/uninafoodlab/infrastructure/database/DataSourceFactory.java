package it.uninafoodlab.infrastructure.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.util.Objects;

public final class DataSourceFactory {

    private static final int MAXIMUM_POOL_SIZE = 10;
    private static final long CONNECTION_TIMEOUT_MILLISECONDS = 10_000L;

    private DataSourceFactory() {}

    public static HikariDataSource create(ApplicationConfig applicationConfig) {
        Objects.requireNonNull(applicationConfig, "applicationConfig");

        var hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("UninaFoodLabPool");
        hikariConfig.setJdbcUrl(applicationConfig.databaseUrl());
        hikariConfig.setUsername(applicationConfig.databaseUser());
        hikariConfig.setPassword(applicationConfig.databasePassword());
        hikariConfig.setAutoCommit(false);
        hikariConfig.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        hikariConfig.setMinimumIdle(0);
        hikariConfig.setConnectionTimeout(CONNECTION_TIMEOUT_MILLISECONDS);
        hikariConfig.setInitializationFailTimeout(-1L);

        return new HikariDataSource(hikariConfig);
    }
}
