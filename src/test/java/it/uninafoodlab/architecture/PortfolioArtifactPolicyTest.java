package it.uninafoodlab.architecture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PortfolioArtifactPolicyTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final String RETIRED_PROTOTYPE_MARKER = "mock" + "up";

    @Test
    void repositoryKeepsInitialSchemaAndVersionedCorrections() throws IOException {
        try (var files = Files.list(MIGRATIONS)) {
            var names = files
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();

            assertThat(names)
                    .containsExactly("V001__create_initial_schema.sql", "V002__protect_past_session_schedules.sql")
                    .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains(RETIRED_PROTOTYPE_MARKER));
        }
    }

    @Test
    void productionSourcesDoNotContainPrototypeMarkersOrDemoSpecificBranches() throws IOException {
        assertThat(Files.exists(Path.of("docs", RETIRED_PROTOTYPE_MARKER + "s"))).isFalse();

        var courseController = Files.readString(Path.of(
                "src/main/java/it/uninafoodlab/presentation/course/CourseWorkspaceController.java"), UTF_8);
        var planningController = Files.readString(Path.of(
                "src/main/java/it/uninafoodlab/presentation/planning/PlanningWorkspaceController.java"), UTF_8);
        var notificationController = Files.readString(Path.of(
                "src/main/java/it/uninafoodlab/presentation/notification/NotificationWorkspaceController.java"), UTF_8);
        var stylesheet = Files.readString(Path.of(
                "src/main/resources/it/uninafoodlab/presentation/styles/application.css"), UTF_8);

        assertThat(courseController)
                .doesNotContain("MOCK" + "UP_PENDING_SESSION", "12 iscritti", "Fondamenti e impasti",
                        "Lievitazione controllata", "Cottura e conservazione");
        assertThat(planningController)
                .doesNotContain("approvedPeriod", "Olio extravergine", "Lievitazione controllata",
                        "user.username().value().equals(\"chef.demo\")");
        assertThat(notificationController)
                .doesNotContain("Fermentazioni contemporanee", "titleField.setText(",
                        "messageField.setText(");
        assertThat(stylesheet.toLowerCase(Locale.ROOT)).doesNotContain(RETIRED_PROTOTYPE_MARKER);
    }
}
