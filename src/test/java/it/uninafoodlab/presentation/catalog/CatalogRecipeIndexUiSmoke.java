package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.domain.identity.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public final class CatalogRecipeIndexUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var dolci = new CategorySummary(UUID.randomUUID(), "Pasticceria", "Dolci");
            var pane = new CategorySummary(UUID.randomUUID(), "Cucina mediterranea", "Pane");
            var ingredient = new IngredientSummary(UUID.randomUUID(), "Farina", MeasurementUnit.GRAM, false, false);
            var values = new ArrayList<RecipeSummary>();
            IntStream.range(1, 28).forEach(i -> {
                var category = i <= 25 ? dolci : pane;
                values.add(new RecipeSummary(UUID.randomUUID(), category.id(), category.name(),
                        i == 1 ? "Ricetta 01 con un titolo lungo: impasto, lievitazione e cottura del pane alle erbe"
                                : "Ricetta %02d con titolo completo".formatted(i),
                        "Preparazione della ricetta " + i + ". Tempo indicativo: 30 minuti. Dosi per una persona. "
                                + "1. Unire la farina e mescolare. 2. Cuocere e servire.", 1, 1));
            });
            var snapshot = new AtomicReference<List<RecipeSummary>>(List.copyOf(values));
            var detailReads = new AtomicInteger();
            var browse = new BrowseCatalog((CatalogRepository) Proxy.newProxyInstance(
                    CatalogRecipeIndexUiSmoke.class.getClassLoader(), new Class<?>[]{CatalogRepository.class},
                    (proxy, method, arguments) -> method.getName().equals("categories") ? List.of(dolci, pane)
                            : CatalogBrowseResponses.recipes(method, arguments, snapshot.get())));
            var manage = new ManageRecipeCatalog((RecipeCatalogManagementRepository) Proxy.newProxyInstance(
                    CatalogRecipeIndexUiSmoke.class.getClassLoader(), new Class<?>[]{RecipeCatalogManagementRepository.class},
                    (proxy, method, arguments) -> {
                        check(!Platform.isFxApplicationThread(), "Catalog I/O stays outside the native UI thread");
                        if (method.getName().equals("ingredients")) return List.of(ingredient);
                        if (method.getName().equals("recipeIngredients")) {
                            detailReads.incrementAndGet();
                            return List.of(new RecipeIngredientInput(ingredient.id(), new BigDecimal("80")));
                        }
                        throw new AssertionError("No writes expected during browsing");
                    }));
            var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("index.chef"), "Chef sintetico", Role.CHEF);
            var controller = new CatalogWorkspaceController(actor, browse, manage);
            var root = fx(() -> {
                var loader = new FXMLLoader(CatalogRecipeIndexUiSmoke.class.getResource("catalog-workspace.fxml"));
                loader.setController(controller);
                var view = (VBox) loader.load();
                var scene = new Scene(view, 1280, 820);
                scene.getStylesheets().add(Objects.requireNonNull(CatalogRecipeIndexUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css")).toExternalForm());
                var stage = new Stage(); stage.setScene(scene); stage.show();
                return view;
            });
            await(() -> !controller.isLoading());
            verifyCompactViewport(root);
            fx(() -> {
                root.applyCss(); root.layout();
                check(list(root).getItems().size() == 12, "First page contains twelve recipes");
                check(root.lookup("#recipePagerTop") == null && root.lookup("#recipePagerBottom").isManaged(),
                        "Pagination appears only after the recipe results");
                check(((VBox)root.lookup("#recipeBrowser")).getWidth() >= root.getWidth() - 70,
                        "Cookbook index occupies the full workspace");
                check(!root.lookup("#recipeCategoryFilter").isManaged(),
                        "The category sidebar is the single visible category selector");
                check(((TextField)root.lookup("#recipeSearch")).getWidth() <= 480,
                        "Desktop search remains proportional to its task");
                check(list(root).lookupAll(".cookbook-entry-description").stream().map(Label.class::cast)
                        .allMatch(label -> !label.getText().contains("Tempo indicativo")
                                && !label.getText().contains("Dosi per") && !label.getText().matches("^\\d+[.)].*")),
                        "Recipe previews show the real opening sentence without time, doses or numbered steps");
                list(root).getSelectionModel().selectFirst();
                check(!root.lookup("#recipeDetails").isManaged(), "Selection alone does not open a recipe");
                checkCategoryText(root, "Cucina mediterranea");
                next(root, "recipePagerBottom").fire();
                return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                check(list(root).getItems().getFirst().name().contains("11"), "Next page continues the sorted category chapters");
                check(detailReads.get() == 0, "Filtering and pagination never read recipe ingredients per row");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-index-page-2.png");
                return null;
            });
            UUID selected = fx(() -> list(root).getItems().getFirst().id());
            fx(() -> { opener(root, selected).fire(); return null; });
            await(() -> !controller.isLoading() && root.lookup("#recipeDetails").isManaged());
            await(() -> root.lookup("#backToRecipesButton").isFocused());
            verifyShortRecipeSheet(root);
            fx(() -> {
                check(detailReads.get() == 1, "One explicit opening causes one ingredient read");
                check(!root.lookup("#recipeBrowser").isManaged(), "Consultation replaces the whole index");
                check(((Button)root.lookup("#backToRecipesButton")).getGraphic() != null,
                        "The return action shows a left arrow");
                var preparation = (Label)root.lookup("#descriptionReadOnly");
                check(preparation.getWidth() <= 680 && preparation.getText().contains("\n\n2. Cuocere e servire."),
                        "Consultation limits line length and separates real steps without dropping text");
                check(preparation.getText().contains("Tempo indicativo: 30 minuti. Dosi per una persona."),
                        "Full preparation metadata stays available in consultation");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-index-detail.png");
                ((Button)root.lookup("#backToRecipesButton")).fire();
                check(list(root).getItems().getFirst().id().equals(selected), "Returning keeps the original page");
                return null;
            });
            await(() -> opener(root, selected).isFocused());
            fx(() -> {
                category(root, "Cucina mediterranea").fire();
                return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                check(list(root).getItems().size() == 2, "Category filter shows its two recipes");
                check(!root.lookup("#recipePagerBottom").isManaged(), "One page needs no pagination controls");
                root.applyCss(); root.layout();
                checkCategoryText(root, "Cucina mediterranea");
                check(list(root).lookupAll(".cookbook-chapter-title").isEmpty(),
                        "A selected sidebar category is not repeated in the result list");
                check(list(root).lookupAll(".cookbook-filtered-entry").size() >= 2,
                        "Both filtered recipes use the full result width");
                check(list(root).lookupAll(".cookbook-filtered-entry").stream()
                        .filter(javafx.scene.layout.GridPane.class::isInstance)
                        .map(javafx.scene.layout.GridPane.class::cast)
                        .allMatch(row -> row.getColumnConstraints().size() == 2),
                        "A filtered recipe row has only recipe and action columns, without a chapter margin");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-index-mediterranean.png");
                category(root, "Tutte le ricette").fire();
                return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                root.applyCss(); root.layout();
                check(!list(root).lookupAll(".cookbook-chapter-title").isEmpty(),
                        "All categories restores the grouped chapter headings");
                check(list(root).getItems().getFirst().name().contains("26"), "Filter changes reset to the first category chapter");
                next(root, "recipePagerBottom").fire();
                return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                next(root, "recipePagerBottom").fire();
                return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                check(list(root).getItems().size() == 3, "Last page contains the remainder");
                snapshot.set(List.copyOf(values.subList(0, 5)));
                controller.activate();
                return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                check(list(root).getItems().size() == 5, "A shorter refreshed catalog clamps the page");
                check(list(root).getItems().getFirst().name().contains("01"), "Clamped page is the first available page");
                check(detailReads.get() == 1, "Catalog refresh does not open an arbitrary recipe");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-index-refreshed.png");
                return null;
            });
            for (int width : new int[]{1000, 780}) {
                fx(() -> {
                    var stage = (Stage)root.getScene().getWindow();
                    stage.setWidth(width + stage.getWidth() - root.getScene().getWidth());
                    return null;
                });
                await(() -> Math.abs(root.getWidth() - width) < 2);
                fx(() -> {
                    root.applyCss(); root.layout();
                    check(((VBox)root.lookup("#recipeBrowser")).getWidth() >= root.getWidth() - 70,
                            "Cookbook stays full width after resizing");
                    var action = opener(root, list(root).getItems().getFirst().id());
                    check(action.getBoundsInParent().getMaxX() <= action.getParent().getLayoutBounds().getWidth() + 2,
                            "Opening action remains inside its recipe row");
                    it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-index-" + width + ".png");
                    return null;
                });
            }
            System.out.println("PASS CatalogRecipeIndexUiSmoke: 27 recipes, native pages, chapter index, explicit opening, focus and refresh clamp");
        } finally { Platform.exit(); }
    }

    @SuppressWarnings("unchecked")
    private static ListView<RecipeSummary> list(VBox root) { return (ListView<RecipeSummary>) root.lookup("#recipeList"); }
    private static void verifyCompactViewport(VBox root) throws Exception {
        fx(() -> {
            var stage = (Stage) root.getScene().getWindow();
            stage.setHeight(553 + stage.getHeight() - root.getScene().getHeight());
            return null;
        });
        await(() -> Math.abs(root.getHeight() - 553) < 2);
        fx(() -> {
            root.applyCss(); root.layout();
            check(list(root).getHeight() >= 260,
                    "A short workspace leaves at least 260 px for recipe results; actual=" + list(root).getHeight());
            var categoryScroll = (ScrollPane) root.lookup("#recipeCategoryScroll");
            check(categoryScroll != null && categoryScroll.isFitToWidth(),
                    "The category index can scroll independently when it exceeds the viewport");
            it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-index-short.png");
            var stage = (Stage) root.getScene().getWindow();
            stage.setWidth(1000 + stage.getWidth() - root.getScene().getWidth());
            stage.setHeight(494 + stage.getHeight() - root.getScene().getHeight());
            return null;
        });
        await(() -> Math.abs(root.getHeight() - 494) < 2 && Math.abs(root.getWidth() - 1000) < 2);
        fx(() -> {
            root.applyCss(); root.layout();
            check(list(root).getHeight() >= 250,
                    "A 1000x600 shell reserves at least 250 px for recipe results; actual=" + list(root).getHeight());
            var action = opener(root, list(root).getItems().getFirst().id());
            check(javafx.scene.layout.GridPane.getRowIndex(action) == 0,
                    "A compact desktop row keeps its opening action alongside the recipe");
            it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-index-shell-short.png");
            var stage = (Stage) root.getScene().getWindow();
            stage.setWidth(1280 + stage.getWidth() - root.getScene().getWidth());
            stage.setHeight(820 + stage.getHeight() - root.getScene().getHeight());
            return null;
        });
        await(() -> Math.abs(root.getHeight() - 820) < 2);
    }
    private static void verifyShortRecipeSheet(VBox root) throws Exception {
        fx(() -> {
            var stage = (Stage) root.getScene().getWindow();
            stage.setWidth(1000 + stage.getWidth() - root.getScene().getWidth());
            stage.setHeight(494 + stage.getHeight() - root.getScene().getHeight());
            return null;
        });
        await(() -> Math.abs(root.getHeight() - 494) < 2 && Math.abs(root.getWidth() - 1000) < 2);
        fx(() -> {
            root.applyCss(); root.layout();
            var scroll = (ScrollPane) root.lookup("#recipeScroll");
            check(scroll.getViewportBounds().getHeight() >= 300,
                    "A recipe sheet inside a 1000x600 shell keeps at least 300 px for reading; actual="
                            + scroll.getViewportBounds().getHeight());
            it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-sheet-short.png");
            ((Button) root.lookup("#editRecipeButton")).fire();
            root.applyCss(); root.layout();
            check(scroll.getViewportBounds().getHeight() >= 250,
                    "A short editor keeps at least 250 px for fields and doses; actual="
                            + scroll.getViewportBounds().getHeight());
            check(root.lookup("#saveRecipeButton").localToScene(root.lookup("#saveRecipeButton").getBoundsInLocal())
                            .getMaxY() <= root.getScene().getHeight(),
                    "Save remains visible outside the editor scroll");
            it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-editor-short.png");
            root.lookup("#recipeFooter").lookupAll(".button").stream().filter(Button.class::isInstance)
                    .map(Button.class::cast).filter(button -> button.getText().equals("Annulla"))
                    .findFirst().orElseThrow().fire();
            var stage = (Stage) root.getScene().getWindow();
            stage.setWidth(1280 + stage.getWidth() - root.getScene().getWidth());
            stage.setHeight(820 + stage.getHeight() - root.getScene().getHeight());
            return null;
        });
        await(() -> Math.abs(root.getHeight() - 820) < 2 && Math.abs(root.getWidth() - 1280) < 2);
    }
    private static Button next(VBox root, String pager) {
        return root.lookup("#" + pager).lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> button.getText().equals("Pagina successiva")).findFirst().orElseThrow();
    }
    private static Button opener(VBox root, UUID recipe) {
        root.applyCss(); root.layout();
        return root.lookupAll(".cookbook-open-action").stream().filter(node -> recipe.equals(node.getUserData()))
                .map(Button.class::cast).findFirst().orElseThrow();
    }
    private static ToggleButton category(VBox root, String name) {
        return root.lookup("#recipeCategories").lookupAll(".toggle-button").stream()
                .filter(ToggleButton.class::isInstance).map(ToggleButton.class::cast)
                .filter(button -> button.getAccessibleText().startsWith(name + ",")).findFirst().orElseThrow();
    }
    private static void checkCategoryText(VBox root, String name) {
        var button = category(root, name);
        button.applyCss();
        var label = button.lookupAll(".label").stream().filter(Label.class::isInstance)
                .map(Label.class::cast).filter(value -> value.getText().equals(name)).findFirst().orElseThrow();
        check(!label.isTextTruncated(), "The complete category name remains readable: " + name);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        it.uninafoodlab.presentation.FxAwait.until(condition, 10);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
