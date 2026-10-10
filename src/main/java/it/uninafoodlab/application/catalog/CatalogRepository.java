package it.uninafoodlab.application.catalog;

import java.util.List;
import java.util.Map;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import java.util.Optional;
import java.util.UUID;

public interface CatalogRepository {

    CategorySummary createCategory(UUID sessionToken, NewCategoryInput input);

    List<CategorySummary> categories(UUID sessionToken);

    default Page<RecipeSummary> recipes(UUID sessionToken, RecipeFilter filter, PageRequest page) {
        throw new UnsupportedOperationException();
    }

    default Map<String, Integer> recipeCounts(UUID sessionToken, RecipeFilter filter) {
        throw new UnsupportedOperationException();
    }

    default Optional<RecipeSummary> recipe(UUID sessionToken, UUID recipeId) {
        throw new UnsupportedOperationException();
    }
}
