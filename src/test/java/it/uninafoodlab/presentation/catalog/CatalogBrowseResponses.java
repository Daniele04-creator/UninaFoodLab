package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.RecipeFilter;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

final class CatalogBrowseResponses {
    private CatalogBrowseResponses() {}

    static Object recipes(Method method, Object[] arguments, List<RecipeSummary> recipes) {
        if (method.getName().equals("recipe")) {
            return recipes.stream().filter(recipe -> recipe.id().equals(arguments[1])).findFirst();
        }
        var filter = (RecipeFilter) arguments[1];
        var filtered = recipes.stream()
                .filter(recipe -> filter.categoryId().isEmpty() || filter.categoryId().get().equals(recipe.categoryId()))
                .filter(recipe -> (recipe.name() + " " + recipe.categoryName() + " " + recipe.description())
                        .toLowerCase(java.util.Locale.ITALIAN).contains(filter.search().toLowerCase(java.util.Locale.ITALIAN)))
                .sorted(Comparator.comparing(RecipeSummary::categoryName).thenComparing(RecipeSummary::name))
                .toList();
        if (method.getName().equals("recipeCounts")) {
            var counts = new TreeMap<String, Integer>();
            for (var recipe : filtered) counts.merge(recipe.categoryName(), 1, Integer::sum);
            return counts;
        }
        var page = (PageRequest) arguments[2];
        int from = Math.min(page.offset(), filtered.size());
        return new Page<>(filtered.subList(from, Math.min(from + page.size(), filtered.size())), filtered.size());
    }
}
