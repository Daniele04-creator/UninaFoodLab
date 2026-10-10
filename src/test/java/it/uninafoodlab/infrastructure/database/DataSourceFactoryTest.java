package it.uninafoodlab.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import org.junit.jupiter.api.Test;

class DataSourceFactoryTest {

    @Test
    void createsABoundedTransactionalApplicationPool() {
        var config = new ApplicationConfig(
                "jdbc:postgresql://localhost:5432/uninafoodlab",
                "uninafoodlab_app",
                "application-test-value",
                "uninafoodlab_migrator",
                "migrator-test-value",
                "test");

        try (var dataSource = DataSourceFactory.create(config)) {
            assertThat(dataSource.getPoolName()).isEqualTo("UninaFoodLabPool");
            assertThat(dataSource.getJdbcUrl()).isEqualTo(config.databaseUrl());
            assertThat(dataSource.getUsername()).isEqualTo(config.databaseUser());
            assertThat(dataSource.getPassword()).isEqualTo(config.databasePassword());
            assertThat(dataSource.isAutoCommit()).isFalse();
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
            assertThat(dataSource.getMinimumIdle()).isZero();
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(10_000L);
        }
    }
}
