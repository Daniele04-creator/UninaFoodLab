package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.RecipeFilter;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.ResultPager;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class CatalogRecipePages {
    private final AuthenticatedUser user;
    private final BrowseCatalog browse;

    CatalogRecipePages(AuthenticatedUser user, BrowseCatalog browse) {
        this.user = user;
        this.browse = browse;
    }

    Snapshot load(RecipeFilter filter, int requestedPage, Optional<UUID> selectedRecipe) {
        var categoryCounts = browse.recipeCounts(user, new RecipeFilter(Optional.empty(), ""));
        var chapterCounts = browse.recipeCounts(user, filter);
        int total = chapterCounts.values().stream().mapToInt(Integer::intValue).sum();
        int page = ResultPager.clamp(requestedPage, total);
        var recipes = browse.recipes(user, filter, new PageRequest(page, ResultPager.PAGE_SIZE));
        var selected = selectedRecipe.flatMap(id -> browse.recipe(user, id));
        return new Snapshot(recipes, categoryCounts, chapterCounts, page, selected);
    }

    record Snapshot(Page<RecipeSummary> recipes, Map<String, Integer> categoryCounts,
            Map<String, Integer> chapterCounts, int page, Optional<RecipeSummary> selected) {}
}
