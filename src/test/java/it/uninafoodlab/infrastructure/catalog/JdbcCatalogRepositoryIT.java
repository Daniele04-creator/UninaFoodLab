package it.uninafoodlab.infrastructure.catalog;

import it.uninafoodlab.infrastructure.support.DatabaseActors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.bootstrap.ApplicationRuntime;
import it.uninafoodlab.application.catalog.NewCategoryInput;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.catalog.RecipeFilter;
import it.uninafoodlab.application.catalog.RecipeSearchScope;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class JdbcCatalogRepositoryIT {

    private static final String MIGRATOR_PASSWORD = "catalog-migrator-only";
    private static final String APP_PASSWORD = "catalog-app-only";
    private static final UUID PASTRY_CATEGORY =
            UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    private static ApplicationRuntime runtime;
    private static JdbcCatalogRepository repository;
    private static UUID sessionToken;
    private static UUID participantSessionToken;

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.copyFileToContainer(
                MountableFile.forHostPath(
                        Path.of("database", "bootstrap", "001__create_roles.sql")),
                "/tmp/001__create_roles.sql");
        var result = POSTGRES.execInContainer(
                "psql",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + POSTGRES.getDatabaseName(),
                "--set=ON_ERROR_STOP=1",
                "--set=migrator_password=" + MIGRATOR_PASSWORD,
                "--set=app_password=" + APP_PASSWORD,
                "--file=/tmp/001__create_roles.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();

        runtime = it.uninafoodlab.infrastructure.support.DemoDatabaseFixture.start(new ApplicationConfig(
                POSTGRES.getJdbcUrl(),
                "uninafoodlab_app",
                APP_PASSWORD,
                "uninafoodlab_migrator",
                MIGRATOR_PASSWORD,
                "test"));
        repository = new JdbcCatalogRepository(runtime.dataSource());
        sessionToken = runtime.authenticateUser()
                .execute("marta.rossi", "MartaRossi!2026".toCharArray())
                .sessionToken();
        participantSessionToken = DatabaseActors.authenticate(runtime, "luca.bianchi", "LucaBianchi!2026".toCharArray())
                .sessionToken();
    }

    @AfterAll
    static void stopDatabase() {
        runtime.close();
    }

    @Test
    void exposesAllRecipesAsEditableToAuthenticatedCatalogReaders() {
        var linkedRecipe = UUID.fromString("a2940000-0000-0000-0000-000000000004");
        for (var token : java.util.List.of(sessionToken, participantSessionToken)) {
            assertThat(recipes(token, Optional.empty()))
                    .filteredOn(recipe -> recipe.id().equals(linkedRecipe))
                    .singleElement()
                    .satisfies(recipe -> assertThat(recipe.locked()).isFalse());
        }
        var invalidToken = UUID.randomUUID();
        Optional<UUID> allCategories = Optional.empty();
        assertThatThrownBy(() -> recipes(invalidToken, allCategories))
                .isInstanceOf(CatalogRepositoryException.class);
    }

    @Test
    void listsCategoriesAndRecipesInDisplayOrder() {
        assertThat(repository.categories(sessionToken))
                .extracting(CategorySummary::name)
                .containsExactly(
                        "Cucina internazionale",
                        "Cucina italiana",
                        "Cucina vegetale",
                        "Panificazione",
                        "Pasticceria",
                        "Street food globale");

        assertThat(recipes(sessionToken, Optional.empty()))
                .hasSize(9)
                .allSatisfy(recipe -> assertThat(recipe.ingredientCount()).isPositive())
                .extracting(RecipeSummary::name)
                .isSorted();
    }

    @Test
    void filtersRecipesByCategoryWithAParameter() {
        assertThat(recipes(sessionToken, Optional.of(PASTRY_CATEGORY)))
                .extracting(RecipeSummary::name)
                .containsExactly("Crostata al burro", "Tiramisù al bicchiere");
        assertThat(recipes(sessionToken, Optional.of(PASTRY_CATEGORY)))
                .allSatisfy(recipe -> {
                    assertThat(recipe.categoryName()).isEqualTo("Pasticceria");
                    assertThat(recipe.ingredientCount()).isPositive();
                });
    }

    @Test
    void pagesRecipesWithStableCategoryOrderAndMatchingCounts() {
        var filter = new RecipeFilter(Optional.empty(), "");
        var first = repository.recipes(sessionToken, filter, new PageRequest(0, 2));
        var second = repository.recipes(sessionToken, filter, new PageRequest(1, 2));
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2).doesNotContainAnyElementsOf(first.items());
        assertThat(first.totalCount()).isEqualTo(9);
        assertThat(second.totalCount()).isEqualTo(first.totalCount());
        assertThat(repository.recipeCounts(sessionToken, filter).values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(Math.toIntExact(first.totalCount()));
        var accented = new RecipeFilter(Optional.of(PASTRY_CATEGORY), "TIRAMISÙ");
        assertThat(repository.recipes(participantSessionToken, accented, new PageRequest(0, 2)).items())
                .extracting(RecipeSummary::name).containsExactly("Tiramisù al bicchiere");
        assertThat(repository.recipeCounts(sessionToken, accented)).containsOnlyKeys("Pasticceria");
        var recipe = first.items().getFirst();
        assertThat(repository.recipe(sessionToken, recipe.id())).contains(recipe);
        assertThat(repository.recipe(sessionToken, UUID.randomUUID())).isEmpty();
        var empty = repository.recipes(sessionToken, filter, new PageRequest(100, 2));
        assertThat(empty.items()).isEmpty();
        assertThat(empty.totalCount()).isEqualTo(first.totalCount());
        assertThatThrownBy(() -> repository.recipes(UUID.randomUUID(), filter, new PageRequest(0, 2)))
                .isInstanceOf(CatalogRepositoryException.class);
    }

    @Test
    void unknownCategoryReturnsAnEmptyList() {
        assertThat(recipes(sessionToken, Optional.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void createsACaseInsensitiveGlobalCategoryOnlyForChefs() {
        var created = repository.createCategory(
                sessionToken, new NewCategoryInput("  Cucina vegetariana  ", "  Verdure e legumi  "));

        assertThat(created.name()).isEqualTo("Cucina vegetariana");
        assertThat(created.description()).isEqualTo("Verdure e legumi");
        assertThat(repository.categories(sessionToken)).contains(created);
        var duplicate = new NewCategoryInput("cucina VEGETARIANA", "");
        assertThatThrownBy(() -> repository.createCategory(sessionToken, duplicate))
                .isInstanceOf(CatalogRepositoryException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState())
                        .isEqualTo("23505"));
        var participantCategory = new NewCategoryInput("Cucina vegana", "");
        assertThatThrownBy(() -> repository.createCategory(participantSessionToken, participantCategory))
                .isInstanceOf(CatalogRepositoryException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error.getCause()).getSQLState())
                        .isEqualTo("42501"));
    }

    @Test
    void applicationRoleUsesCatalogFunctionsInsteadOfDirectTableReads() throws SQLException {
        try (var connection = runtime.dataSource().getConnection();
                var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("select * from app.recipe"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
            connection.rollback();
            assertThatThrownBy(() -> statement.executeUpdate("insert into app.category(name, description) values ('Test diretto', '')"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                            .isEqualTo("42501"));
            connection.rollback();
            var invalidToken = UUID.randomUUID();
            assertThatThrownBy(() -> repository.categories(invalidToken))
                    .isInstanceOf(CatalogRepositoryException.class)
                    .hasRootCauseInstanceOf(SQLException.class);
        }
    }

    private static java.util.List<RecipeSummary> recipes(UUID token, Optional<UUID> categoryId) {
        return repository.recipes(token, new RecipeFilter(categoryId, "", RecipeSearchScope.MENU), new PageRequest(0, 100)).items();
    }
}
