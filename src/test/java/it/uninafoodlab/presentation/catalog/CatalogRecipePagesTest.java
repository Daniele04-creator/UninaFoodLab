package it.uninafoodlab.presentation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.CatalogRepository;
import it.uninafoodlab.application.catalog.RecipeFilter;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CatalogRecipePagesTest {
    @Test
    void pageReadIsBoundedAndPreservesSelectionOutsideTheVisiblePage() {
        var recipes = recipes(27);
        var requests = new ArrayList<PageRequest>();
        var loader = loader(recipes, requests);
        var snapshot = loader.load(new RecipeFilter(Optional.empty(), ""), 1, Optional.of(recipes.getFirst().id()));
        assertThat(snapshot.recipes().items()).hasSize(12);
        assertThat(snapshot.recipes().totalCount()).isEqualTo(27);
        assertThat(snapshot.selected()).contains(recipes.getFirst());
        assertThat(requests).containsExactly(new PageRequest(1, 12));
    }

    @Test
    void shorterCatalogClampsTheRequestBeforeReadingAndSearchRetainsFullCategoryCounts() {
        var recipes = recipes(5);
        var requests = new ArrayList<PageRequest>();
        var loader = loader(recipes, requests);
        var snapshot = loader.load(new RecipeFilter(Optional.empty(), "03"), 8, Optional.empty());
        assertThat(snapshot.page()).isZero();
        assertThat(snapshot.recipes().items()).containsExactly(recipes.get(3));
        assertThat(snapshot.categoryCounts()).containsEntry("Dolci", 5);
        assertThat(snapshot.chapterCounts()).containsEntry("Dolci", 1);
        assertThat(snapshot.selected()).isEmpty();
        assertThat(requests).containsExactly(new PageRequest(0, 12));
    }

    private static CatalogRecipePages loader(List<RecipeSummary> recipes, List<PageRequest> requests) {
        var repository = (CatalogRepository) Proxy.newProxyInstance(CatalogRecipePagesTest.class.getClassLoader(),
                new Class<?>[]{CatalogRepository.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("recipes")) {
                        assertThat(arguments).hasSize(3);
                        requests.add((PageRequest) arguments[2]);
                    }
                    return CatalogBrowseResponses.recipes(method, arguments, recipes);
                });
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("pages.chef"), "Chef", Role.CHEF);
        return new CatalogRecipePages(user, new BrowseCatalog(repository));
    }

    private static List<RecipeSummary> recipes(int count) {
        var categoryId = UUID.randomUUID();
        return IntStream.range(0, count).mapToObj(index -> new RecipeSummary(UUID.randomUUID(), categoryId,
                "Dolci", "Ricetta %02d".formatted(index), "Preparazione", 1, 2)).toList();
    }
}
