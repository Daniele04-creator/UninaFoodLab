package it.uninafoodlab.infrastructure.catalog;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import it.uninafoodlab.infrastructure.support.DatabaseActors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Testcontainers
class JdbcRecipeCatalogManagementRepositoryIT {

    private static final String MIGRATOR_PASSWORD = "catalog-migrator-only";
    private static final String APP_PASSWORD = "catalog-app-only";
    private static final UUID PASTRY_CATEGORY =
            UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;
    private static JdbcRecipeCatalogManagementRepository repository;
    private static UUID sessionToken;
    private static UUID participantSessionToken;

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(
                MountableFile.forHostPath(
                        Path.of("database", "bootstrap", "001__create_roles.sql")),
                "/tmp/001__create_roles.sql");
        var result =
                POSTGRES.execInContainer(
                        "psql",
                        "--username=" + POSTGRES.getUsername(),
                        "--dbname=" + POSTGRES.getDatabaseName(),
                        "--set=ON_ERROR_STOP=1",
                        "--set=migrator_password=" + MIGRATOR_PASSWORD,
                        "--set=app_password=" + APP_PASSWORD,
                        "--file=/tmp/001__create_roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();

        runtime =
                it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(
                        new ApplicationConfig(
                                POSTGRES.getJdbcUrl(),
                                "uninafoodlab_app",
                                APP_PASSWORD,
                                "uninafoodlab_migrator",
                                MIGRATOR_PASSWORD,
                                "test"));
        repository = new JdbcRecipeCatalogManagementRepository(runtime.dataSource());
        sessionToken =
                runtime.authenticateUser()
                        .execute("marta.rossi", "MartaRossi!2026".toCharArray())
                        .sessionToken();
        participantSessionToken =
                DatabaseActors.authenticate(runtime, "luca.bianchi", "LucaBianchi!2026".toCharArray())
                        .sessionToken();
    }

    @AfterAll
    static void stopDatabase() {
        runtime.close();
    }

    @Test
    void protectsUnitsFromFirstRecipeUseWhileAllowingNameCorrections() {
        var ingredient = repository.saveIngredient(sessionToken, Optional.empty(),
                new it.uninafoodlab.application.catalog.IngredientInput("Unit protection flour",
                        it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM));
        var line = new it.uninafoodlab.application.catalog.RecipeIngredientInput(
                ingredient, new java.math.BigDecimal("500"));
        var recipe = repository.saveRecipe(sessionToken, Optional.empty(),
                new it.uninafoodlab.application.catalog.RecipeInput(PASTRY_CATEGORY,
                        "Unit protection recipe", "Preparazione", 1, java.util.List.of(line)));

        var selectedIngredient = Optional.of(ingredient);
        var changedUnit = new it.uninafoodlab.application.catalog.IngredientInput("Unit protection flour",
                it.uninafoodlab.domain.catalog.MeasurementUnit.MILLILITER);
        assertThatThrownBy(() -> repository.saveIngredient(sessionToken, selectedIngredient, changedUnit))
                .isInstanceOf(CatalogRepositoryException.class)
                .hasMessageContaining("unità");
        repository.saveIngredient(sessionToken, Optional.of(ingredient),
                new it.uninafoodlab.application.catalog.IngredientInput("Unit protection flour renamed",
                        it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM));
        assertThat(repository.ingredients(sessionToken)).filteredOn(i -> i.id().equals(ingredient))
                .singleElement().satisfies(i -> {
                    assertThat(i.name()).endsWith("renamed");
                    assertThat(i.unit()).isEqualTo(it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM);
                    assertThat(i.locked()).isFalse();
                    assertThat(i.unitLocked()).isTrue();
                });
        assertThat(repository.recipeIngredients(sessionToken, recipe)).singleElement()
                .satisfies(i -> assertThat(i.quantity()).isEqualByComparingTo("500"));
    }

    @Test
    void createsEditsAndReplacesRecipeIngredientsTransactionally() {
        var a =
                repository.saveIngredient(
                        sessionToken,
                        Optional.empty(),
                        new it.uninafoodlab.application.catalog.IngredientInput(
                                "Test catalog ingredient A",
                                it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM));
        var b =
                repository.saveIngredient(
                        sessionToken,
                        Optional.empty(),
                        new it.uninafoodlab.application.catalog.IngredientInput(
                                "Test catalog ingredient B",
                                it.uninafoodlab.domain.catalog.MeasurementUnit.MILLILITER));
        repository.saveIngredient(
                sessionToken,
                Optional.of(a),
                new it.uninafoodlab.application.catalog.IngredientInput(
                        "Test catalog ingredient A updated",
                        it.uninafoodlab.domain.catalog.MeasurementUnit.MILLILITER));
        var first =
                new it.uninafoodlab.application.catalog.RecipeIngredientInput(
                        a, new java.math.BigDecimal("125.001"));
        var id =
                repository.saveRecipe(
                        sessionToken,
                        Optional.empty(),
                        new it.uninafoodlab.application.catalog.RecipeInput(
                                PASTRY_CATEGORY,
                                "Test catalog recipe",
                                "Descrizione",
                                1,
                                java.util.List.of(first)));
        assertThat(repository.recipeIngredients(sessionToken, id)).containsExactly(first);
        assertThat(new JdbcCatalogRepository(runtime.dataSource()).recipe(sessionToken, id))
                .get()
                .satisfies(recipe -> assertThat(recipe.locked()).isFalse());
        var second =
                new it.uninafoodlab.application.catalog.RecipeIngredientInput(
                        b, new java.math.BigDecimal("0.5"));
        repository.saveRecipe(
                sessionToken,
                Optional.of(id),
                new it.uninafoodlab.application.catalog.RecipeInput(
                        PASTRY_CATEGORY,
                        "Test catalog updated recipe",
                        "Descrizione aggiornata",
                                1,
                        java.util.List.of(second)));
        assertThat(repository.recipeIngredients(sessionToken, id))
                .singleElement()
                .satisfies(
                        line -> {
                            assertThat(line.ingredientId()).isEqualTo(b);
                            assertThat(line.quantity())
                                    .isEqualByComparingTo("0.5");
                        });
        var invalid =
                new it.uninafoodlab.application.catalog.RecipeIngredientInput(
                        UUID.randomUUID(), java.math.BigDecimal.ONE);
        var selectedRecipe = Optional.of(id);
        var invalidUpdate = new it.uninafoodlab.application.catalog.RecipeInput(
                PASTRY_CATEGORY, "Test catalog failed update", "Descrizione", 1,
                java.util.List.of(invalid));
        assertThatThrownBy(() -> repository.saveRecipe(sessionToken, selectedRecipe, invalidUpdate))
                .isInstanceOf(CatalogRepositoryException.class);
        assertThat(repository.recipeIngredients(sessionToken, id))
                .singleElement()
                .satisfies(
                        line -> {
                            assertThat(line.ingredientId()).isEqualTo(b);
                            assertThat(line.quantity())
                                    .isEqualByComparingTo("0.5");
                        });
        assertThat(repository.ingredients(sessionToken))
                .filteredOn(i -> i.id().equals(a))
                .singleElement()
                .satisfies(
                        i -> {
                            assertThat(i.name()).endsWith("updated");
                            assertThat(i.locked()).isFalse();
                        });
    }

    @Test
    void deniesParticipantWritesAndAllowsChefNameCorrections() {
        var input =
                new it.uninafoodlab.application.catalog.IngredientInput(
                        "Unauthorized ingredient",
                        it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM);
        assertThatThrownBy(
                        () ->
                                repository.saveIngredient(
                                        participantSessionToken, Optional.empty(), input))
                .isInstanceOf(CatalogRepositoryException.class)
                .hasMessageContaining("Chef");
        assertThatThrownBy(() -> repository.ingredients(participantSessionToken))
                .isInstanceOf(CatalogRepositoryException.class);
        var used = repository.ingredients(sessionToken).stream()
                .filter(it.uninafoodlab.application.catalog.IngredientSummary::unitLocked)
                .findFirst().orElseThrow();
        repository.saveIngredient(sessionToken, Optional.of(used.id()),
                new it.uninafoodlab.application.catalog.IngredientInput("Corrected used ingredient", used.unit()));
        assertThat(repository.ingredients(sessionToken)).filteredOn(i -> i.id().equals(used.id()))
                .singleElement().satisfies(i -> {
                    assertThat(i.name()).isEqualTo("Corrected used ingredient");
                    assertThat(i.locked()).isFalse();
                    assertThat(i.unitLocked()).isTrue();
                });
    }

    @Test
    void editsLinkedRecipesInPlaceWithoutRewritingSavedPreparation() {

        var recipe = UUID.fromString("a2940000-0000-0000-0000-000000000004");
        var session = UUID.fromString("b3160000-0000-0000-0000-000000000002");
        var planning = new it.uninafoodlab.infrastructure.planning.JdbcChefPlanningRepository(
                runtime.dataSource());
        var before = planning.sessionPreparation(sessionToken, session);
        var originalLines = repository.recipeIngredients(sessionToken, recipe);
        var ingredient = repository.saveIngredient(sessionToken, Optional.empty(),
                new it.uninafoodlab.application.catalog.IngredientInput("Revised historical dose",
                        it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM));
        var input = new it.uninafoodlab.application.catalog.RecipeInput(PASTRY_CATEGORY,
                "Revised linked recipe", "Preparazione completa aggiornata", 1,
                java.util.List.of(new it.uninafoodlab.application.catalog.RecipeIngredientInput(
                        ingredient, new java.math.BigDecimal("123"))));
        var selectedRecipe = Optional.of(recipe);
        assertThatThrownBy(() -> repository.saveRecipe(participantSessionToken, selectedRecipe, input)).isInstanceOf(CatalogRepositoryException.class);
        assertThat(repository.recipeIngredients(sessionToken, recipe))
                .containsExactlyElementsOf(originalLines);
        assertThat(repository.saveRecipe(sessionToken, Optional.of(recipe), input)).isEqualTo(recipe);
        var after = planning.sessionPreparation(sessionToken, session);
        assertThat(after.confirmedParticipants()).isPositive().isEqualTo(before.confirmedParticipants());
        assertThat(after.recipes()).extracting(r -> r.recipeId())
                .containsExactlyElementsOf(before.recipes().stream().map(r -> r.recipeId()).toList());
        assertThat(after).isEqualTo(before);
        assertThat(repository.recipeIngredients(sessionToken, recipe)).singleElement().satisfies(line -> {
            assertThat(line.ingredientId()).isEqualTo(ingredient);
            assertThat(line.quantity()).isEqualByComparingTo("123");
        });
        var selectedIngredient = Optional.of(ingredient);
        var changedUnit = new it.uninafoodlab.application.catalog.IngredientInput("Changed unit",
                it.uninafoodlab.domain.catalog.MeasurementUnit.MILLILITER);
        assertThatThrownBy(() -> repository.saveIngredient(sessionToken, selectedIngredient, changedUnit))
                .isInstanceOf(CatalogRepositoryException.class);
    }

    @Test
    void databaseRejectsExcessPrecisionBeforeRoundingAndMultipleServings() throws Exception {
        var ingredient = repository.ingredients(sessionToken).getFirst().id();
        for (var invalid : java.util.List.of("1,ARRAY[1.0001]", "1,ARRAY[0]", "1,ARRAY[-1]", "1,ARRAY[1000000000000]", "1,ARRAY['NaN'::numeric]", "1,ARRAY['Infinity'::numeric]", "2,ARRAY[1]")) {
            try (var c = runtime.dataSource().getConnection();
                    var s = c.prepareStatement("SELECT app.save_catalog_recipe(?,NULL,?,"
                            + "'Invalid exact recipe','Descrizione'," + invalid.split(",", 2)[0]
                            + ",ARRAY[?]::uuid[]," + invalid.split(",", 2)[1] + "::numeric[])")) {
                s.setObject(1, sessionToken);
                s.setObject(2, PASTRY_CATEGORY);
                s.setObject(3, ingredient);
                assertThatThrownBy(s::executeQuery).isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException)e).getSQLState()).isEqualTo("23514"));
            }
        }
    }
    @Test
    void databaseRejectsDuplicateIngredientsWithoutCreatingRecipe() throws Exception {
        var ingredient = repository.ingredients(sessionToken).getFirst().id();
        try (var c = runtime.dataSource().getConnection();
                var s =
                        c.prepareStatement(
                                "SELECT app.save_catalog_recipe(?,NULL,?,'Invalid direct"
                                    + " recipe','Descrizione',1,ARRAY[?,?]::uuid[],ARRAY[1,2]::numeric[])")) {
            s.setObject(1, sessionToken);
            s.setObject(2, PASTRY_CATEGORY);
            s.setObject(3, ingredient);
            s.setObject(4, ingredient);
            assertThatThrownBy(s::executeQuery)
                    .isInstanceOf(SQLException.class)
                    .satisfies(
                            e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
        }
    }
}
