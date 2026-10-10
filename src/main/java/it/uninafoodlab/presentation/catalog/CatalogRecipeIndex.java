package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.presentation.common.ResultPager;
import java.util.TreeMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;


final class CatalogRecipeIndex {
    private List<RecipeSummary> visibleRecipes = List.of();
    private Map<String, Integer> categoryCounts = Map.of();
    private Map<String, Integer> chapterCounts = Map.of();
    private int page = 1;
    private int total;
    private boolean showChapters = true;

    void updatePage(CatalogRecipePages.Snapshot snapshot, String category) {
        visibleRecipes = snapshot.recipes().items();
        total = Math.toIntExact(snapshot.recipes().totalCount());
        categoryCounts = Collections.unmodifiableMap(new TreeMap<>(snapshot.categoryCounts()));
        chapterCounts = snapshot.chapterCounts();
        page = snapshot.page() + 1;
        showChapters = category == null || category.equals("Tutte le categorie");
    }

    void page(int requested) { page = ResultPager.clamp(requested - 1, total()) + 1; }
    int page() { return page; }
    int total() { return total; }
    int pageCount() { return ResultPager.count(total()); }
    boolean showChapters() { return showChapters; }
    List<RecipeSummary> visible() {
        return visibleRecipes;
    }
    Map<String, Integer> categoryCounts() { return categoryCounts; }
    int chapterCount(String category) {
        return chapterCounts.getOrDefault(category, 0);
    }

}
