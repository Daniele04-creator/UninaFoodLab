package it.uninafoodlab.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationConfigTest {

    @Test
    void diagnosticTextDoesNotExposeConnectionSecrets() {
        var config = new ApplicationConfig(
                "jdbc:postgresql://private-host/db?password=url-secret",
                "private-app-user", "app-secret", "private-migrator-user",
                "migrator-secret", "development");

        assertThat(String.valueOf(config)).contains("ApplicationConfig")
                .doesNotContain("private-host", "url-secret", "app-secret", "migrator-secret",
                        "private-app-user", "private-migrator-user");
        assertThat(config.databasePassword()).isEqualTo("app-secret");
        assertThat(config.databaseMigratorPassword()).isEqualTo("migrator-secret");
    }

    @Test
    void loadsAllRequiredValues() {
        var values = Map.of(
                "UNINAFOODLAB_DB_URL", "jdbc:postgresql://localhost:5432/uninafoodlab",
                "UNINAFOODLAB_DB_USER", "uninafoodlab_app",
                "UNINAFOODLAB_DB_PASSWORD", "local-demo-value",
                "UNINAFOODLAB_DB_MIGRATOR_USER", "uninafoodlab_migrator",
                "UNINAFOODLAB_DB_MIGRATOR_PASSWORD", "local-migrator-value",
                "UNINAFOODLAB_ENV", "development");

        var config = ApplicationConfig.from(values::get);

        assertThat(config.databaseUrl()).isEqualTo(values.get("UNINAFOODLAB_DB_URL"));
        assertThat(config.databaseUser()).isEqualTo("uninafoodlab_app");
        assertThat(config.databasePassword()).isEqualTo("local-demo-value");
        assertThat(config.databaseMigratorUser()).isEqualTo("uninafoodlab_migrator");
        assertThat(config.databaseMigratorPassword()).isEqualTo("local-migrator-value");
        assertThat(config.environmentName()).isEqualTo("development");
    }

    @Test
    void rejectsAMissingPasswordWithoutExposingOtherValues() {
        var sentinelUrl = "jdbc:postgresql://sensitive-host/uninafoodlab";
        var values = Map.of(
                "UNINAFOODLAB_DB_URL", sentinelUrl,
                "UNINAFOODLAB_DB_USER", "uninafoodlab_app",
                "UNINAFOODLAB_ENV", "development");

        assertThatThrownBy(() -> ApplicationConfig.from(values::get))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage(
                        "Missing required environment variable: UNINAFOODLAB_DB_PASSWORD")
                .hasMessageNotContaining(sentinelUrl);
    }

    @Test
    void treatsWhitespaceOnlyValuesAsMissing() {
        Environment environment = name -> "UNINAFOODLAB_DB_URL".equals(name) ? "   " : "present";

        assertThatThrownBy(() -> ApplicationConfig.from(environment))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("Missing required environment variable: UNINAFOODLAB_DB_URL");
    }
}
