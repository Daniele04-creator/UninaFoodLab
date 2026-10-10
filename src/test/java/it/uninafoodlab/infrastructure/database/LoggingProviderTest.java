package it.uninafoodlab.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingProviderTest {

    @Test
    void runtimeLoggingDoesNotFallBackToTheNoOpProvider() {
        assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                .doesNotContain("NOPLoggerFactory");
    }
}
