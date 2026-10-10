package it.uninafoodlab.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import it.uninafoodlab.infrastructure.support.DemoDatabaseFixture;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class SinglePortionConstraintsIT {
    @Container private final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");
    private static final String MIGRATOR_PASSWORD = "single-portion-migrator-only";

    @BeforeEach
    void roles() throws Exception {
        postgres.copyFileToContainer(MountableFile.forHostPath(Path.of("database/bootstrap/001__create_roles.sql")), "/tmp/roles.sql");
        var result = postgres.execInContainer("psql", "--username=" + postgres.getUsername(),
                "--dbname=" + postgres.getDatabaseName(), "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD,
                "--set=app_password=single-portion-app-only", "--file=/tmp/roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    @Test
    void preservesExactBoundaryDosesAndProtectsUsedIngredientUnits() throws Exception {
        DemoDatabaseFixture.migrate(config());
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO app.recipe(id,category_id,name,description,base_servings) VALUES ('b0490000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','Boundary doses','Synthetic precision boundary',1)");
            statement.execute("INSERT INTO app.ingredient(id,name,unit) VALUES ('b0490000-0000-0000-0000-000000000002','Large mass','GRAM'),('b0490000-0000-0000-0000-000000000003','Fractional piece','PIECE')");
            statement.execute("INSERT INTO app.recipe_ingredient(recipe_id,ingredient_id,quantity) VALUES ('b0490000-0000-0000-0000-000000000001','b0490000-0000-0000-0000-000000000002',999999999999.999),('b0490000-0000-0000-0000-000000000001','b0490000-0000-0000-0000-000000000003',0.1)");
        }
        DatabaseMigrator.migrate(config());
        try (var connection = connection(); var statement = connection.createStatement()) {
            assertInvariants(connection);
            assertThatThrownBy(() -> statement.execute("UPDATE app.ingredient SET unit=CASE WHEN unit='GRAM' THEN 'MILLILITER' ELSE 'GRAM' END WHERE id=(SELECT ingredient_id FROM app.recipe_ingredient LIMIT 1)"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("55006"));
        }
        try (var runtime = it.uninafoodlab.application.bootstrap.ApplicationRuntime.start(config())) {
            var token = runtime.authenticateUser().execute("marta.rossi", "MartaRossi!2026".toCharArray()).sessionToken();
            var repository = new it.uninafoodlab.infrastructure.catalog.JdbcRecipeCatalogManagementRepository(runtime.dataSource());
            var lines = repository.recipeIngredients(token, java.util.UUID.fromString("b0490000-0000-0000-0000-000000000001"));
            assertThat(lines).filteredOn(line -> line.ingredientId().toString().endsWith("000000000002"))
                    .singleElement().satisfies(line -> assertThat(line.quantity()).isEqualByComparingTo("999999999999.999"));
            assertThat(lines).filteredOn(line -> line.ingredientId().toString().endsWith("000000000003"))
                    .singleElement().satisfies(line -> assertThat(line.quantity()).isEqualByComparingTo("0.1"));
            var copy = repository.saveRecipe(token, java.util.Optional.empty(),
                    new it.uninafoodlab.application.catalog.RecipeInput(
                            java.util.UUID.fromString("30000000-0000-0000-0000-000000000001"),
                            "Boundary dose copy", "Preserved precise doses", 1, lines));
            assertThat(repository.recipeIngredients(token, copy)).containsExactlyElementsOf(lines);
        }
    }

    @Test
    void schemaEnforcesSinglePortionsAndRejectsImplicitDecimalCoercion() throws Exception {
        DemoDatabaseFixture.migrate(config());
        try (var connection = connection()) {
            assertInvariants(connection);
            assertThat(number(connection, "SELECT count(*) FROM app.recipe_ingredient")).isPositive();
            rejects(connection, "UPDATE app.recipe SET base_servings=2");
            rejects(connection, "UPDATE app.recipe_ingredient SET quantity=1.0001");
            rejects(connection, "UPDATE app.recipe_ingredient SET quantity='Infinity'::numeric");
            rejects(connection, "UPDATE app.course SET ingredient_margin=0.001");
            rejects(connection, "UPDATE app.session_recipe SET portions_per_participant=1.001");
            rejects(connection, "INSERT INTO app.ingredient(name,unit) VALUES('Forbidden litres','LITER')");
        }
    }

    @Test
    void preciseDecimalDosesSurviveRestartAndRejectInvalidNumericValues() throws Exception {
        DemoDatabaseFixture.migrate(config());
        try (var connection = connection(); var statement = connection.createStatement()) {
            assertThat(statement.executeUpdate("UPDATE app.recipe_ingredient SET quantity=0.125")).isPositive();
        }
        DatabaseMigrator.migrate(config());
        try (var connection = connection()) {
            assertThat(number(connection, "SELECT count(*) FROM app.recipe_ingredient WHERE quantity<>0.125")).isZero();
            assertThat(number(connection, "SELECT count(*) FROM app.recipe_ingredient")).isPositive();
            rejects(connection, "UPDATE app.recipe_ingredient SET quantity=0.0001");
            rejects(connection, "UPDATE app.recipe_ingredient SET quantity=0");
            rejects(connection, "UPDATE app.recipe_ingredient SET quantity='NaN'::numeric");
            rejects(connection, "UPDATE app.recipe_ingredient SET quantity='Infinity'::numeric");
            rejects(connection, "UPDATE app.recipe_ingredient SET quantity=1000000000000");
        }
    }

    private static void assertInvariants(Connection connection) throws Exception {
        assertThat(number(connection, "SELECT count(*) FROM app.recipe WHERE base_servings<>1")).isZero();
        assertThat(number(connection, "SELECT count(*) FROM app.recipe_ingredient WHERE quantity<>round(quantity,3) OR quantity<=0 OR quantity>=1000000000000")).isZero();
        assertThat(number(connection, "SELECT count(*) FROM app.ingredient WHERE unit IN ('KILOGRAM','LITER')")).isZero();
        assertThat(number(connection, "SELECT count(*) FROM app.course WHERE ingredient_margin<>0")).isZero();
        assertThat(number(connection, "SELECT count(*) FROM app.session_recipe WHERE portions_per_participant<>1")).isZero();
    }

    private static void rejects(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
        }
    }

    private ApplicationConfig config() {
        return new ApplicationConfig(postgres.getJdbcUrl(), "uninafoodlab_app", "single-portion-app-only",
                "uninafoodlab_migrator", MIGRATOR_PASSWORD, "test");
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), "uninafoodlab_migrator", MIGRATOR_PASSWORD);
    }

    private static int number(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
