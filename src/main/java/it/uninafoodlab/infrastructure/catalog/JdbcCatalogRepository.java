package it.uninafoodlab.infrastructure.catalog;

import it.uninafoodlab.application.catalog.CatalogRepository;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.catalog.NewCategoryInput;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.catalog.RecipeFilter;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcCatalogRepository implements CatalogRepository {
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String CATEGORY_ID = "category_id";
    private static final String DESCRIPTION = "description";

    private static final String SELECT_CATEGORIES = """
            SELECT category_id, name, description
              FROM app.list_catalog_categories(?)
            """;
    private static final String CREATE_CATEGORY = """
            SELECT category_id, name, description
              FROM app.create_category(?, ?, ?)
            """;

    private final DataSource dataSource;
    private final CatalogPageQueries pages;

    public JdbcCatalogRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.pages = new CatalogPageQueries(dataSource);
    }

    @Override
    public CategorySummary createCategory(UUID sessionToken, NewCategoryInput input) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        Objects.requireNonNull(input, "input");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(CREATE_CATEGORY)) {
            statement.setObject(1, sessionToken);
            statement.setString(2, input.name());
            statement.setString(3, input.description());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new CatalogRepositoryException("Category creation returned no row", null);
                }
                var category = new CategorySummary(
                        result.getObject(CATEGORY_ID, UUID.class),
                        result.getString("name"),
                        result.getString(DESCRIPTION));
                connection.commit();
                return category;
            }
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new CatalogRepositoryException("Category name already exists", exception);
            }
            throw new CatalogRepositoryException("Unable to create category", exception);
        }
    }

    @Override
    public List<CategorySummary> categories(UUID sessionToken) {
        Objects.requireNonNull(sessionToken, SESSION_TOKEN);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SELECT_CATEGORIES)) {
            statement.setObject(1, sessionToken);
            try (var result = statement.executeQuery()) {
                var categories = new ArrayList<CategorySummary>();
                while (result.next()) {
                    categories.add(new CategorySummary(
                            result.getObject(CATEGORY_ID, UUID.class),
                            result.getString("name"),
                            result.getString(DESCRIPTION)));
                }
                return List.copyOf(categories);
            }
        } catch (SQLException exception) {
            throw new CatalogRepositoryException("Unable to load categories", exception);
        }
    }

    @Override
    public Page<RecipeSummary> recipes(UUID sessionToken, RecipeFilter filter, PageRequest page) {
        return pages.recipes(sessionToken, filter, page);
    }

    @Override
    public Map<String, Integer> recipeCounts(UUID sessionToken, RecipeFilter filter) {
        return pages.recipeCounts(sessionToken, filter);
    }

    @Override
    public Optional<RecipeSummary> recipe(UUID sessionToken, UUID recipeId) {
        return pages.recipe(sessionToken, recipeId);
    }

    static RecipeSummary mapRecipe(ResultSet result) throws SQLException {
        return new RecipeSummary(
                result.getObject("recipe_id", UUID.class),
                result.getObject(CATEGORY_ID, UUID.class),
                result.getString("category_name"),
                result.getString("name"),
                result.getString(DESCRIPTION),
                result.getInt("base_servings"),
                result.getInt("ingredient_count"),
                result.getBoolean("locked"));
    }
}
