package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.presentation.common.ResultPager;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

final class CourseRecipeCatalog {
    private final Fields fields;
    private final CourseWorkspaceActions actions;
    private final Function<RecipeSummary, Node> createRow;
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250));
    private List<RecipeSummary> recipes = List.of();
    private int page;
    private long total;
    private boolean available = true;
    private boolean searchPending;
    private IntConsumer loadPage;
    private ResultPager bottomPager;

    record Fields(VBox rows, Label results, Button retry, TextField search) {}

    CourseRecipeCatalog(Fields fields, CourseWorkspaceActions actions, Function<RecipeSummary, Node> createRow) {
        this.fields = fields;
        this.actions = actions;
        this.createRow = createRow;
        searchDelay.setOnFinished(event -> {
            searchPending = true;
            resumeSearch();
        });
    }

    void installPagination(HBox bottomHost) {
        bottomPager = new ResultPager(requested -> {
            if (loadPage != null) loadPage.accept(requested);
        });
        bottomHost.getChildren().setAll(bottomPager);
        HBox.setHgrow(bottomPager, Priority.ALWAYS);
        bottomHost.managedProperty().bind(bottomPager.managedProperty());
        bottomHost.visibleProperty().bind(bottomPager.visibleProperty());
    }

    void setPageLoader(IntConsumer loadPage) { this.loadPage = loadPage; }
    int page() { return page; }
    String search() { return fields.search().getText(); }
    List<RecipeSummary> items() { return recipes; }
    boolean isAvailable() { return available; }

    void markUnavailable() {
        available = false;
        render();
    }

    void setItems(Page<RecipeSummary> recipes, int page) {
        available = true;
        this.recipes = recipes.items();
        total = recipes.totalCount();
        this.page = page;
        render();
    }

    void searchChanged() {
        searchPending = false;
        searchDelay.playFromStart();
    }

    void resumeSearch() {
        if (!searchPending || actions.isBusy() || loadPage == null) return;
        searchPending = false;
        loadPage.accept(0);
    }

    void render() {
        if (fields.rows() == null) return;
        boolean focusWasInResults = focusInResults();
        if (bottomPager != null) {
            bottomPager.update(page, Math.toIntExact(total), "ricette");
            bottomPager.setDisable(actions.isBusy() || !available);
        }
        fields.retry().setVisible(!available);
        fields.retry().setManaged(!available);
        fields.retry().setDisable(actions.isBusy());
        fields.results().setText(resultMessage());
        fields.rows().getChildren().clear();
        for (var recipe : recipes) fields.rows().getChildren().add(createRow.apply(recipe));
        if (focusWasInResults) fields.search().requestFocus();
    }

    private String resultMessage() {
        if (actions.isBusy()) return "Aggiornamento ricettario…";
        if (!available) return "Ricettario non aggiornato. Riprova prima di aggiungere ricette.";
        if (total > 0) return total + (total == 1 ? " ricetta" : " ricette");
        return search().isBlank() ? "Nessuna ricetta nel ricettario."
                : "Nessuna ricetta trovata. Prova un altro nome o categoria.";
    }

    private boolean focusInResults() {
        var scene = fields.rows().getScene();
        if (scene == null) return false;
        for (var node = scene.getFocusOwner(); node != null; node = node.getParent()) {
            if (node == fields.rows()) return true;
        }
        return false;
    }
}
