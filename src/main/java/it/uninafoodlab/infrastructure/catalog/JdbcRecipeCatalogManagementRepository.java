package it.uninafoodlab.infrastructure.catalog;

import it.uninafoodlab.application.catalog.IngredientInput;
import it.uninafoodlab.application.catalog.IngredientSummary;
import it.uninafoodlab.application.catalog.RecipeCatalogManagementRepository;
import it.uninafoodlab.application.catalog.RecipeIngredientInput;
import it.uninafoodlab.application.catalog.RecipeInput;
import it.uninafoodlab.domain.catalog.MeasurementUnit;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

public final class JdbcRecipeCatalogManagementRepository
        implements RecipeCatalogManagementRepository {
    private final DataSource dataSource;

    public JdbcRecipeCatalogManagementRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public List<IngredientSummary> ingredients(UUID sessionToken) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        SELECT ingredient_id, name, unit, locked, unit_locked
                          FROM app.list_managed_ingredients_with_usage(?)
                        """)) {
            statement.setObject(1, sessionToken);
            try (var result = statement.executeQuery()) {
                var ingredients = new ArrayList<IngredientSummary>();
                while (result.next())
                    ingredients.add(
                            new IngredientSummary(
                                    result.getObject("ingredient_id", UUID.class),
                                    result.getString("name"),
                                    MeasurementUnit.valueOf(result.getString("unit")),
                                    result.getBoolean("locked"),
                                    result.getBoolean("unit_locked")));
                return List.copyOf(ingredients);
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    @Override
    public List<RecipeIngredientInput> recipeIngredients(UUID sessionToken, UUID recipeId) {
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT ingredient_id, quantity FROM app.list_managed_recipe_ingredients(?,?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, recipeId);
            try (var result = statement.executeQuery()) {
                var ingredients = new ArrayList<RecipeIngredientInput>();
                while (result.next())
                    ingredients.add(
                            new RecipeIngredientInput(
                                    result.getObject("ingredient_id", UUID.class),
                                    result.getBigDecimal("quantity")));
                return List.copyOf(ingredients);
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    @Override
    public UUID saveIngredient(UUID sessionToken, Optional<UUID> id, IngredientInput input) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT app.save_catalog_ingredient(?,?,?,?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, id.orElse(null));
            statement.setString(3, input.name());
            statement.setString(4, input.unit().name());
            try (var result = statement.executeQuery()) {
                result.next();
                var saved = result.getObject(1, UUID.class);
                connection.commit();
                return saved;
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    @Override
    public UUID saveRecipe(UUID sessionToken, Optional<UUID> id, RecipeInput input) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT app.save_catalog_recipe(?,?,?,?,?,?,?,?)")) {
            statement.setObject(1, sessionToken);
            statement.setObject(2, id.orElse(null));
            statement.setObject(3, input.categoryId());
            statement.setString(4, input.name());
            statement.setString(5, input.description());
            statement.setInt(6, input.baseServings());
            var ids =
                    connection.createArrayOf(
                            "uuid",
                            input.ingredients().stream()
                                    .map(RecipeIngredientInput::ingredientId)
                                    .toArray(UUID[]::new));
            var quantities =
                    connection.createArrayOf(
                            "numeric",
                            input.ingredients().stream()
                                    .map(RecipeIngredientInput::quantity)
                                    .toArray(BigDecimal[]::new));
            try {
                statement.setArray(7, ids);
                statement.setArray(8, quantities);
                try (var result = statement.executeQuery()) {
                    result.next();
                    var saved = result.getObject(1, UUID.class);
                    connection.commit();
                    return saved;
                }
            } finally {
                ids.free();
                quantities.free();
            }
        } catch (SQLException exception) {
            throw failure(exception);
        }
    }

    private static CatalogRepositoryException failure(SQLException exception) {
        String message =
                switch (Objects.toString(exception.getSQLState(), "")) {
                    case "55006" -> "L’unità di misura è già usata in ricette o sessioni e non può cambiare."
                            + " Crea un nuovo ingrediente per usare un’altra unità.";
                    case "55000" ->
                            "Questo ingrediente è già usato in una sessione: crea un nuovo"
                                + " ingrediente per preservare lo storico.";
                    case "23505" -> "Il nome esiste già nel catalogo. Scegli un nome diverso.";
                    case "23503" ->
                            "Una categoria o un ingrediente non è più disponibile. Aggiorna il"
                                + " catalogo.";
                    case "42501" -> "La sessione Chef non è valida. Accedi nuovamente.";
                    default ->
                            "Impossibile salvare o leggere il catalogo. Verifica i dati e riprova.";
                };
        return new CatalogRepositoryException(message, exception);
    }
}
