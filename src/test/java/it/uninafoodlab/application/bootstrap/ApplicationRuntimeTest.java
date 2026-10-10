package it.uninafoodlab.application.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ApplicationRuntimeTest {

    @Test
    void closesThePoolWhenConnectionValidationFails() {
        var dataSource = new TrackingDataSource(false, false);

        var config = testConfig();
        assertThatThrownBy(() -> ApplicationRuntime.start(config, () -> {}, () -> dataSource))
                .isInstanceOf(ApplicationStartupException.class)
                .hasMessage("Database connection validation failed");

        assertThat(dataSource.closed).isTrue();
    }

    @Test
    void closesThePoolWhenTheValidationConnectionCannotClose() {
        var dataSource = new TrackingDataSource(true, true);

        var config = testConfig();
        assertThatThrownBy(() -> ApplicationRuntime.start(config, () -> {}, () -> dataSource))
                .isInstanceOf(ApplicationStartupException.class)
                .hasMessage("Unable to initialize the application database")
                .hasCauseInstanceOf(SQLException.class);

        assertThat(dataSource.closed).isTrue();
    }

    private static ApplicationConfig testConfig() {
        return new ApplicationConfig(
                "jdbc:postgresql://localhost:55432/uninafoodlab",
                "uninafoodlab_app",
                "application-test-value",
                "uninafoodlab_migrator",
                "migrator-test-value",
                "test");
    }

    private static final class TrackingDataSource extends HikariDataSource {

        private final boolean valid;
        private final boolean connectionCloseFails;
        private boolean closed;

        private TrackingDataSource(boolean valid, boolean connectionCloseFails) {
            this.valid = valid;
            this.connectionCloseFails = connectionCloseFails;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "isValid" -> valid;
                        case "close" -> {
                            if (connectionCloseFails) {
                                throw new SQLException("Synthetic close failure");
                            }
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
