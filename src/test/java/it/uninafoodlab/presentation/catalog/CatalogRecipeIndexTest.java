package it.uninafoodlab.presentation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.common.Page;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogRecipeIndexTest {
    @Test
    void serverPageRetainsItsResultsAndUsesTheFullResultCount() {
        var index = new CatalogRecipeIndex();
        var recipe = recipe("Dolci");
        index.updatePage(snapshot(List.of(recipe), 25, 2), "Tutte le categorie");
        assertThat(index.visible()).containsExactly(recipe);
        assertThat(index.page()).isEqualTo(3);
        assertThat(index.pageCount()).isEqualTo(3);
        assertThat(index.total()).isEqualTo(25);
        index.page(9);
        assertThat(index.page()).isEqualTo(3);
    }

    @Test
    void categoriesStaySortedAndChapterCountsDescribeTheFilteredCatalog() {
        var index = new CatalogRecipeIndex();
        var recipe = recipe("Dolci");
        index.updatePage(new CatalogRecipePages.Snapshot(new Page<>(List.of(recipe), 1),
                Map.of("Pane", 3, "Dolci", 25), Map.of("Dolci", 1), 0, Optional.empty()), "Tutte le categorie");
        assertThat(index.categoryCounts().keySet()).containsExactly("Dolci", "Pane");
        assertThat(index.chapterCount("Dolci")).isEqualTo(1);
        assertThat(index.chapterCount("Pane")).isZero();
    }

    @Test
    void selectingACategoryHidesTheChapterMarginAndAllCategoriesRestoresIt() {
        var index = new CatalogRecipeIndex();
        index.updatePage(snapshot(List.of(recipe("Dolci")), 1, 0), "Dolci");
        assertThat(index.showChapters()).isFalse();
        index.updatePage(snapshot(List.of(recipe("Dolci")), 1, 0), "Tutte le categorie");
        assertThat(index.showChapters()).isTrue();
    }

    private static CatalogRecipePages.Snapshot snapshot(List<RecipeSummary> recipes, int total, int page) {
        return new CatalogRecipePages.Snapshot(new Page<>(recipes, total), Map.of("Dolci", total),
                Map.of("Dolci", total), page, Optional.empty());
    }

    private static RecipeSummary recipe(String category) {
        return new RecipeSummary(UUID.randomUUID(), UUID.randomUUID(), category, "Biscotti", "Cuocere", 1, 2);
    }
}
