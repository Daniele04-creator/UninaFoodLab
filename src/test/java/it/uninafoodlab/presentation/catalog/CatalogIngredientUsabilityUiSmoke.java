package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.domain.identity.*;
import it.uninafoodlab.presentation.FxAwait;
import it.uninafoodlab.presentation.FxSnapshot;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;


public final class CatalogIngredientUsabilityUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var category = new CategorySummary(UUID.randomUUID(), "Pane", "Pane sintetico");
            var ingredients = new ArrayList<IngredientSummary>();
            var flour = new IngredientSummary(UUID.randomUUID(), "Farina", MeasurementUnit.GRAM, false, false);
            var water = new IngredientSummary(UUID.randomUUID(), "Acqua", MeasurementUnit.MILLILITER, false, false);
            ingredients.add(flour); ingredients.add(water);
            for (int index = 0; index < 24; index++) ingredients.add(new IngredientSummary(UUID.randomUUID(),
                    "Ingrediente sintetico " + index, MeasurementUnit.PIECE, false, false));
            var repository = (CatalogRepository) Proxy.newProxyInstance(CatalogIngredientUsabilityUiSmoke.class.getClassLoader(),
                    new Class<?>[]{CatalogRepository.class}, (proxy, method, arguments) ->
                            method.getName().equals("categories") ? List.of(category)
                                    : CatalogBrowseResponses.recipes(method, arguments, List.of()));
            var management = (RecipeCatalogManagementRepository) Proxy.newProxyInstance(
                    CatalogIngredientUsabilityUiSmoke.class.getClassLoader(), new Class<?>[]{RecipeCatalogManagementRepository.class},
                    (proxy, method, arguments) -> method.getName().equals("ingredients") ? ingredients : List.of());
            var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("catalog.chef"), "Chef sintetico", Role.CHEF);
            var controller = new CatalogWorkspaceController(actor, new BrowseCatalog(repository), new ManageRecipeCatalog(management));
            var root = fx(() -> {
                var loader = new FXMLLoader(CatalogIngredientUsabilityUiSmoke.class.getResource("catalog-workspace.fxml"));
                loader.setController(controller);
                var view = (VBox) loader.load();

                var navigation = new StackPane(new Label("Barra Chef su due righe — spazio riservato per il test"));
                navigation.setMinHeight(164); navigation.setPrefHeight(164); navigation.setMaxHeight(164);
                var shell = new BorderPane(view, navigation, null, null, null);
                var scene = new Scene(shell, 1000, 600);
                scene.getStylesheets().add(Objects.requireNonNull(CatalogIngredientUsabilityUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css")).toExternalForm());
                var stage = new Stage(); stage.setScene(scene); stage.show();
                return view;
            });
            FxAwait.until(() -> !controller.isLoading(), 10);
            boolean onlySearch = args.length > 0 && args[0].equals("search");
            if (!onlySearch) {
                fx(() -> {
                    ((ToggleButton) root.lookup("#ingredientsTab")).fire();
                    ((Button) root.lookup("#newIngredientButton")).fire();
                    root.applyCss(); root.layout();
                    FxSnapshot.save((BorderPane) root.getParent(), "tmp/catalog-usability-tdd/ingredient-editor-compact.png");
                    checkInsideScene(root.lookup("#saveIngredientButton"), "Salva ingrediente remains reachable at 1000 × 600 with the two-row Chef bar");
                    checkInsideScene(root.lookup("#cancelIngredientButton"), "Annulla remains reachable at 1000 × 600");
                    var scroll = (ScrollPane) root.lookup("#ingredientEditorScroll");
                    check(scroll != null && scroll.getViewportBounds().getHeight() > 60, "Ingredient fields have a scrollable viewport");
                    scroll.setVvalue(1);
                    root.applyCss(); root.layout();
                    checkInsideViewport(scroll, root.lookup("#ingredientUnit"), "Unit selection can be reached by scrolling");
                    check(((ListView<?>) root.lookup("#ingredientList")).getHeight() >= 130, "Compact screen reserves useful ingredient result space");
                    ((Button) root.lookup("#cancelIngredientButton")).fire();
                    ((Button) root.lookup("#backFromIngredientsButton")).fire();
                    return null;
                });
            }
            fx(() -> {
                ((Button) root.lookup("#newRecipeButton")).fire();
                ((TextField) root.lookup("#recipeName")).setText("Bozza pane");
                ((TextArea) root.lookup("#recipeDescription")).setText("Mescolare gli ingredienti.");
                var search = (TextField) root.lookup("#lineIngredientSearch");
                check(search != null, "Recipe dose selector offers text search");
                search.setText("  fArInA ");
                var choice = ingredientChoice(root);
                check(choice.getItems().equals(List.of(flour)), "Ingredient search ignores case and outer spaces");
                check(!choice.isEditable(), "Free text cannot become an ingredient");
                search.fireEvent(new javafx.event.ActionEvent());
                check(choice.getValue().equals(flour), "Enter chooses the sole matching catalog ingredient");
                check(choice.getConverter().toString(choice.getValue()).contains("g"), "Selected ingredient retains visible measurement unit");
                ((TextField) root.lookup("#quantity")).setText("0,5");
                search.setText("inesistente");
                check(choice.getItems().isEmpty(), "Unknown text offers no synthetic ingredient");
                check(choice.getValue().equals(flour), "Filtering does not erase the already selected ingredient or pending dose");
                key(search, KeyCode.ESCAPE);
                check(search.getText().isEmpty() && choice.getItems().size() == ingredients.size(), "Escape in search clears the filter");
                check(choice.getValue().equals(flour), "Escape preserves the pending selected dose");
                search.setText("ml");
                check(choice.getItems().equals(List.of(water)), "Search also finds ingredients by measurement unit");
                search.fireEvent(new javafx.event.ActionEvent());
                check(choice.getValue().equals(water), "Choosing another search result uses its real catalog identity");
                search.setText("farina");
                key(search, KeyCode.DOWN);
                check(choice.isShowing(), "Down from search opens the native results selector");
                choice.hide();
                search.fireEvent(new javafx.event.ActionEvent());
                invoke(controller, "showIngredients");
                ((Button) root.lookup("#backFromIngredientsButton")).fire();
                check(((TextField) root.lookup("#recipeName")).getText().equals("Bozza pane"), "Ingredient management detour retains recipe name");
                check(((TextField) root.lookup("#quantity")).getText().equals("0,5") && choice.getValue().equals(flour), "Ingredient management detour retains pending selected ingredient and decimal dose");
                var recipeScroll = (ScrollPane) root.lookup("#recipeScroll");
                recipeScroll.setVvalue(0);
                root.applyCss(); root.layout();
                FxSnapshot.save((BorderPane) root.getParent(), "tmp/catalog-usability-tdd/recipe-ingredient-search-compact.png");
                return null;
            });

            fx(() -> {
                invoke(controller, "addLine");
                check(((TextField) root.lookup("#lineIngredientSearch")).getText().isEmpty()
                        && ingredientChoice(root).getValue() == null, "Adding a dose resets only the ingredient search and pending choice");
                check(((VBox) root.lookup("#ingredientLines")).getChildren().size() == 1,
                        "A valid searched ingredient becomes a real editable dose row");
                ((TextField) root.lookup("#lineIngredientSearch")).setText("acqua");
                invoke(controller, "resetRecipe");
                check(((TextField) root.lookup("#lineIngredientSearch")).getText().isEmpty()
                        && ingredientChoice(root).getValue() == null
                        && ((TextField) root.lookup("#quantity")).getText().isEmpty(),
                        "A fresh recipe resets the prior search and pending ingredient dose");
                return null;
            });
            System.out.println("PASS CatalogIngredientUsabilityUiSmoke: fixed footer, scrollable fields, compact results, real ingredient search, unit, Enter/Down/Escape and preserved recipe draft");
        } finally { Platform.exit(); }
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<IngredientSummary> ingredientChoice(VBox root) { return (ComboBox<IngredientSummary>) root.lookup("#lineIngredient"); }
    private static void key(Node node, KeyCode code) {
        node.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }
    private static void checkInsideScene(Node node, String message) {
        var bounds = node.localToScene(node.getBoundsInLocal());
        check(node.isVisible() && bounds.getMinY() >= 0 && bounds.getMaxY() <= node.getScene().getHeight() + 1, message + " (bottom=" + bounds.getMaxY() + ")");
    }
    private static void checkInsideViewport(ScrollPane scroll, Node node, String message) {
        var viewport = scroll.lookup(".viewport");
        var bounds = viewport.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        check(bounds.getMinY() >= -1 && bounds.getMaxY() <= viewport.getBoundsInLocal().getHeight() + 1, message);
    }
    private static void invoke(Object controller, String name) throws Exception {
        var method = controller.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(controller);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
