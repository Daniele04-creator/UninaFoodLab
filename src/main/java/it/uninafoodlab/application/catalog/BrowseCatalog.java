package it.uninafoodlab.application.catalog;

import java.util.List;
import java.util.Map;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import it.uninafoodlab.domain.identity.AuthenticatedUser;

public final class BrowseCatalog {

    private final CatalogRepository catalogRepository;

    public BrowseCatalog(CatalogRepository catalogRepository) {
        this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
    }

    public List<CategorySummary> categories(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        return List.copyOf(catalogRepository.categories(actor.sessionToken()));
    }

    public Page<RecipeSummary> recipes(AuthenticatedUser actor, RecipeFilter filter, PageRequest page) {
        Objects.requireNonNull(actor, "actor");
        return catalogRepository.recipes(actor.sessionToken(),
                Objects.requireNonNull(filter, "filter"), Objects.requireNonNull(page, "page"));
    }

    public Map<String, Integer> recipeCounts(AuthenticatedUser actor, RecipeFilter filter) {
        Objects.requireNonNull(actor, "actor");
        return Map.copyOf(catalogRepository.recipeCounts(actor.sessionToken(), Objects.requireNonNull(filter, "filter")));
    }

    public Optional<RecipeSummary> recipe(AuthenticatedUser actor, UUID recipeId) {
        Objects.requireNonNull(actor, "actor");
        return catalogRepository.recipe(actor.sessionToken(), Objects.requireNonNull(recipeId, "recipeId"));
    }
}
