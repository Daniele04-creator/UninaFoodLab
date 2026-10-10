package it.uninafoodlab.infrastructure.database;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalDatabaseExposureTest {

    @Test
    void composePublishesPostgreSqlOnlyOnLoopback() throws IOException {
        var compose = Files.readString(Path.of("compose.yaml"), UTF_8);

        assertThat(compose)
                .contains("127.0.0.1:${UNINAFOODLAB_DB_PORT:-55432}:5432")
                .doesNotContain("- \"${UNINAFOODLAB_DB_PORT:-55432}:5432\"");
    }
}
