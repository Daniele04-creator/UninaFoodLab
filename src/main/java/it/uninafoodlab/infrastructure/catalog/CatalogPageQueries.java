package it.uninafoodlab.infrastructure.catalog;

import it.uninafoodlab.application.catalog.RecipeFilter;
import it.uninafoodlab.application.catalog.RecipeSearchScope;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

final class CatalogPageQueries {

    private final DataSource dataSource;

    CatalogPageQueries(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    Page<RecipeSummary> recipes(UUID sessionToken, RecipeFilter filter, PageRequest page) {
        Objects.requireNonNull(page, "page");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT * FROM app.page_catalog_recipes(?, ?, ?, ?, ?, ?)")) {
            bind(statement, sessionToken, filter);
            statement.setInt(5, page.size());
            statement.setInt(6, page.offset());
            try (var result = statement.executeQuery()) {
                var recipes = new ArrayList<RecipeSummary>();
                long total = 0;
                while (result.next()) {
                    total = result.getLong("total_count");
                    if (result.getObject("recipe_id", UUID.class) != null) recipes.add(JdbcCatalogRepository.mapRecipe(result));
                }
                return new Page<>(recipes, total);
            }
        } catch (SQLException exception) {
            throw new CatalogRepositoryException("Unable to load recipe page", exception);
        }
    }

    Map<String, Integer> recipeCounts(UUID sessionToken, RecipeFilter filter) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT * FROM app.catalog_recipe_counts(?, ?, ?, ?)")) {
            bind(statement, sessionToken, filter);
            try (var result = statement.executeQuery()) {
                var counts = new LinkedHashMap<String, Integer>();
                while (result.next()) {
                    counts.put(result.getString("category_name"), Math.toIntExact(result.getLong("recipe_count")));
                }
                return Map.copyOf(counts);
            }
        } catch (SQLException exception) {
            throw new CatalogRepositoryException("Unable to load recipe category counts", exception);
        }
    }

    Optional<RecipeSummary> recipe(UUID sessionToken, UUID recipeId) {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(recipeId, "recipeId");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT * FROM app.catalog_recipe(?, ?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, recipeId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(JdbcCatalogRepository.mapRecipe(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new CatalogRepositoryException("Unable to load recipe", exception);
        }
    }

    private static void bind(PreparedStatement statement, UUID sessionToken, RecipeFilter filter) throws SQLException {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(filter, "filter");
        statement.setObject(1, sessionToken);
        statement.setObject(2, filter.categoryId().orElse(null), Types.OTHER);
        statement.setString(3, filter.search());
        statement.setBoolean(4, filter.scope() == RecipeSearchScope.MENU);
    }
}
