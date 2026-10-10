package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.domain.identity.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;


public final class CatalogFeedbackUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("smoke.chef"), "Chef sintetico", Role.CHEF);
            var ingredient = new IngredientSummary(UUID.randomUUID(), "Farina", MeasurementUnit.GRAM, false, false);
            var category = new CategorySummary(UUID.randomUUID(), "Pane", "Panificazione");
            var emptyCategory = new CategorySummary(UUID.randomUUID(), "Categoria senza ricette", "Categoria sintetica");
            var recipe = new RecipeSummary(UUID.randomUUID(), category.id(), category.name(), "Pane", "Impastare", 1, 1, true);
            var readFailure = new AtomicBoolean(true);
            var writeCount = new AtomicInteger();
            var createdRecipes = new AtomicInteger();
            var recipeReads = new AtomicInteger();
            var gate = new AtomicReference<CountDownLatch>();
            var initialRecipeBrowserWidth = new AtomicReference<Double>();
            var browse = new BrowseCatalog((CatalogRepository)Proxy.newProxyInstance(CatalogFeedbackUiSmoke.class.getClassLoader(),
                    new Class<?>[]{CatalogRepository.class}, (proxy, method, arguments) -> {
                        if (readFailure.get()) throw new IllegalStateException("jdbc:secret-host technical detail");
                        if (method.getName().equals("recipes")) recipeReads.incrementAndGet();
                        return method.getName().equals("categories") ? List.of(category, emptyCategory) : CatalogBrowseResponses.recipes(method, arguments, List.of(recipe));
                    }));
            var manage = new ManageRecipeCatalog((RecipeCatalogManagementRepository)Proxy.newProxyInstance(
                    CatalogFeedbackUiSmoke.class.getClassLoader(), new Class<?>[]{RecipeCatalogManagementRepository.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("ingredients")) return List.of(ingredient);
                        if (method.getName().equals("recipeIngredients")) {
                            if (gate.get() != null) gate.get().await(10, TimeUnit.SECONDS);
                            return List.of(new RecipeIngredientInput(ingredient.id(), new BigDecimal("100")));
                        }
                        if (method.getName().equals("saveRecipe")) {
                            var selected = Arrays.stream(arguments).filter(Optional.class::isInstance)
                                    .map(Optional.class::cast).findFirst().orElseThrow();
                            if (selected.isEmpty()) {
                                createdRecipes.incrementAndGet();
                                readFailure.set(true);
                                return recipe.id();
                            }
                            check(Arrays.stream(arguments).filter(Optional.class::isInstance)
                                    .anyMatch(value -> value.equals(Optional.of(recipe.id()))), "Editing preserves recipe identity instead of creating a variant");
                            check(Arrays.stream(arguments).filter(RecipeInput.class::isInstance).map(RecipeInput.class::cast)
                                    .allMatch(input -> input.baseServings() == 1), "Recipe writes are always for one person");
                            check(Arrays.stream(arguments).filter(RecipeInput.class::isInstance).map(RecipeInput.class::cast)
                                    .allMatch(input -> input.ingredients().getFirst().quantity().compareTo(new BigDecimal("0.5")) == 0),
                                    "Decimal dose reaches the save operation unchanged");
                        }
                        writeCount.incrementAndGet();
                        throw new IllegalStateException("jdbc:secret-host uncertain write");
                    }));
            var controller = new CatalogWorkspaceController(actor, browse, manage);
            fx(() -> { var loader = new FXMLLoader(CatalogFeedbackUiSmoke.class.getResource("catalog-workspace.fxml"));
                loader.setController(controller);
                var root = (VBox)loader.load();
                var scene = new javafx.scene.Scene(root, 780, 650);
                scene.getStylesheets().add(Objects.requireNonNull(CatalogFeedbackUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css")).toExternalForm());
                var stage = new javafx.stage.Stage(); stage.setScene(scene); stage.show(); return null; });
            await(() -> button(controller,"retryButton").isVisible());
            fx(() -> {
                var root = (VBox) get(controller, "workspace");
                root.applyCss();
                root.layout();
                double browserWidth = ((VBox) get(controller, "recipeBrowser")).getWidth();
                check(browserWidth >= root.getWidth() - 70,
                        "Recipe index uses the complete workspace instead of reserving a detail pane");
                initialRecipeBrowserWidth.set(browserWidth);
                check(!label(controller).contains("jdbc:"), "Technical error must not leak");
                check(!((VBox)get(controller,"workspace")).isDisabled(), "Status and navigation remain enabled");
                button(controller,"newRecipeButton").fire();
                check(!controller.hasUnsavedChanges(), "Opening an untouched recipe does not warn on exit");
                ((TextField)get(controller,"recipeName")).setText("Bozza durante errore");
                check(controller.hasUnsavedChanges(), "The incomplete recipe is protected before any save");
                button(controller,"retryButton").fire();
                check(((TextField)get(controller,"recipeName")).getText().equals("Bozza durante errore"),
                        "A stale retry cannot overwrite a newly edited draft");
                check(!button(controller,"retryButton").isVisible(), "Stale retry is invalidated");
                ((TextField)get(controller,"recipeName")).clear();
                check(!controller.hasUnsavedChanges(), "Reverting the recipe name clears exit protection");
                readFailure.set(false); controller.activate(); return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                button(controller,"newRecipeButton").fire();
                ((TextField)get(controller,"recipeName")).setText("Pane nuovo");
                ((TextArea)get(controller,"recipeDescription")).setText("Impastare e cuocere");
                ((List<RecipeIngredientInput>)get(controller,"lines")).add(
                        new RecipeIngredientInput(ingredient.id(), new BigDecimal("0.5")));
                button(controller,"saveRecipeButton").fire();
                return null;
            });
            await(() -> !controller.isLoading() && button(controller, "retryButton").isVisible());
            fx(() -> {
                check(createdRecipes.get() == 1, "Creating the recipe succeeds before the refresh fails");
                check(label(controller).contains("Ricetta salvata"),
                        "A failed refresh preserves explicit confirmation that the recipe was saved");
                check(label(controller).contains("senza ripetere il salvataggio"),
                        "Refresh failure explains that retry only updates the saved recipe");
                readFailure.set(false);
                button(controller, "retryButton").fire();
                return null;
            });
            await(() -> !controller.isLoading() && !((CatalogRecipeMode)get(controller,"recipeMode")).editing());
            fx(() -> {
                check(createdRecipes.get() == 1, "Retry after saving never creates a second recipe");
                check(!controller.hasUnsavedChanges(), "Successful save and its refresh clear recipe exit protection");
                check(!((VBox)get(controller,"recipeBrowser")).isManaged()
                        && ((VBox)get(controller,"recipeDetails")).isManaged(),
                        "Saving a recipe opens its full-width consultation sheet");
                return null;
            });
            fx(() -> {
                ((TextField)get(controller,"ingredientName")).setText("Farina integrale");
                ((ComboBox<MeasurementUnit>)get(controller,"ingredientUnit")).setValue(null);
                invoke(controller,"saveIngredient");
                check(label(controller).equals("Completa nome e unità."),
                        "Missing ingredient unit gives an actionable message");
                check(((TextField)get(controller,"ingredientName")).getText().equals("Farina integrale"),
                        "Missing ingredient unit preserves the entered name");
                check(writeCount.get() == 0, "Incomplete ingredients are not sent to the repository");
                invoke(controller, "showRecipes");
                check(controller.hasUnsavedChanges(), "A hidden ingredient draft remains protected on exit");
                invoke(controller, "showIngredients");
                check(((Label) get(controller, "catalogTitle")).isVisible()
                        && ((Label) get(controller, "catalogTitle")).getText().equals("Ingredienti"),
                        "The secondary ingredient area keeps its page title");
                var ingredientSearchField = (TextField) get(controller, "ingredientSearch");
                var ingredientResults = (ListView<IngredientSummary>) get(controller, "ingredientList");
                ingredientSearchField.setText("ingrediente inesistente");
                check(ingredientResults.getItems().isEmpty(), "An unmatched ingredient search shows an empty result list");
                check(ingredientResults.getPlaceholder() instanceof Label placeholder
                        && placeholder.getText().equals("Nessun ingrediente trovato."),
                        "An empty ingredient list explains why no rows are visible");
                ingredientSearchField.clear();
                check(((VBox) get(controller, "ingredientEditor")).isManaged(),
                        "Returning to ingredients keeps an unsaved draft editor visible");
                check(((TextField) get(controller, "ingredientName")).getText().equals("Farina integrale"),
                        "Switching tabs preserves the exact ingredient draft");
                ((TextField)get(controller,"ingredientName")).clear();
                ((ComboBox<MeasurementUnit>)get(controller,"ingredientUnit")).setValue(MeasurementUnit.GRAM);
                invoke(controller, "showRecipes");
                return null;
            });
            gate.set(new CountDownLatch(1));
            fx(() -> {
                var selection = ((ListView<RecipeSummary>)get(controller,"recipeList")).getSelectionModel();
                selection.clearSelection();
                open(controller, recipe);
                return null;
            });
            await(controller::isLoading);
            fx(() -> { check(label(controller).contains("Caricamento"), "Explicit loading state");
                check(controller.isBusy(), "The shell can prevent exit during an active catalog operation");
                check(((Label)get(controller,"feedback")).getStyleClass().contains("course-feedback-info"), "Loading uses information styling");
                check(!button(controller,"retryButton").isVisible(), "Retry hidden during pending read"); return null; });
            gate.get().countDown();
            await(() -> !controller.isLoading());
            fx(() -> {
                var details = (VBox) get(controller, "recipeDetails");
                var selection = ((ListView<RecipeSummary>) get(controller, "recipeList")).getSelectionModel();
                check(details.isVisible(), "Selecting a recipe opens its consultation details");
                check(selection.getSelectedItem().equals(recipe), "The open details correspond to the selected recipe");
                check(!((Label) get(controller, "catalogTitle")).isVisible(),
                        "The redundant recipe title is omitted because the main navigation already names the catalog");
                var search = (TextField) get(controller, "recipeSearch");
                var categoryFilter = (ComboBox<String>) get(controller, "recipeCategoryFilter");
                search.setText("pane");
                categoryFilter.setValue(recipe.categoryName());
                check(controller.isLoading() && button(controller, "backToRecipesButton").isDisabled(),
                        "Recipe filtering keeps details protected while the database read is pending");
                return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                var details = (VBox) get(controller, "recipeDetails");
                var selection = ((ListView<RecipeSummary>) get(controller, "recipeList")).getSelectionModel();
                var search = (TextField) get(controller, "recipeSearch");
                var categoryFilter = (ComboBox<String>) get(controller, "recipeCategoryFilter");
                var close = button(controller, "backToRecipesButton");
                check(close.isVisible() && close.isManaged() && close.getText().equals("Torna al ricettario"),
                        "Consultation details provide a visible return action");
                check(close.getAccessibleText().equals("Torna al ricettario") && close.isFocusTraversable(),
                        "The close action has a clear accessible name and remains reachable by keyboard");
                close.fire();
                check(!details.isVisible() && !details.isManaged(), "Closing details returns to the recipe index");
                var root = (VBox) get(controller, "workspace");
                root.applyCss();
                root.layout();
                double closedBrowserWidth = ((VBox) get(controller, "recipeBrowser")).getWidth();
                check(Math.abs(closedBrowserWidth - initialRecipeBrowserWidth.get()) < 2,
                        "Closing recipe details restores the initial recipe browser width");
                check(selection.getSelectedItem() == null, "Closing details clears the recipe selection");
                check(search.getText().equals("pane"), "Closing details retains the recipe search");
                check(categoryFilter.getValue().equals(recipe.categoryName()), "Closing details retains the category filter");
                return null;
            });
            await(() -> {
                var owner = ((VBox) get(controller, "workspace")).getScene().getFocusOwner();
                return owner instanceof Button && recipe.id().equals(owner.getUserData());
            });
            fx(() -> {
                var search = (TextField) get(controller, "recipeSearch");
                search.requestFocus();
                search.setText("ricetta senza corrispondenze");
                return null;
            });
            await(() -> !controller.isLoading() && ((ListView<?>) get(controller, "recipeList")).getItems().isEmpty());
            fx(() -> {
                var details = (VBox) get(controller, "recipeDetails");
                var selection = ((ListView<RecipeSummary>) get(controller, "recipeList")).getSelectionModel();
                check(((ListView<?>) get(controller, "recipeList")).getItems().isEmpty(),
                        "A non-matching recipe search produces no list items");
                check(!details.isVisible() && ((VBox) get(controller, "recipeBrowser")).isVisible(),
                        "Filtering out the open read-only recipe closes its details pane");
                check(selection.getSelectedItem() == null, "Filtering out the open recipe clears its selection");
                return null;
            });
            await(() -> focused(controller, "recipeSearch"));
            fx(() -> {
                ((TextField) get(controller, "recipeSearch")).setText("pane");
                return null;
            });
            await(() -> !controller.isLoading() && !((ListView<?>) get(controller, "recipeList")).getItems().isEmpty());
            fx(() -> {
                open(controller, recipe);
                return null;
            });
            await(() -> !controller.isLoading() && ((VBox) get(controller, "recipeDetails")).isVisible());
            fx(() -> {
                check(((ListView<RecipeSummary>) get(controller, "recipeList")).getSelectionModel()
                        .getSelectedItem().equals(recipe), "Reopening details restores the explicit recipe selection");
                check(!controller.hasUnsavedChanges(), "Loaded recipe data is the clean exit baseline");
                button(controller, "editRecipeButton").fire();
                var doseEditor = (TextField) ((VBox) get(controller, "ingredientLines")).lookup(".text-field");
                doseEditor.setText("125");
                check(controller.hasUnsavedChanges(), "Focused dose text is protected before focus loss or Enter");
                doseEditor.setText("100");
                check(!controller.hasUnsavedChanges(), "Reverting focused dose text restores the saved baseline");
                doseEditor.clear();
                check(controller.hasUnsavedChanges(), "Incomplete focused dose text is protected on window close");
                doseEditor.setText("100");
                invoke(controller, "cancelRecipe");
                return null;
            });
            for (int width : new int[]{1000, 1440}) {
                fx(() -> {
                    var root = (VBox) get(controller, "workspace");
                    var stage = (javafx.stage.Stage) root.getScene().getWindow();
                    stage.setWidth(width + stage.getWidth() - root.getScene().getWidth());
                    stage.setHeight(900 + stage.getHeight() - root.getScene().getHeight());
                    return null;
                });
                await(() -> Math.abs(((VBox) get(controller, "workspace")).getWidth() - width) < 2);
                fx(() -> {
                    var root = (VBox) get(controller, "workspace");
                    root.applyCss(); root.layout();
                    it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/catalog-" + width + ".png");
                    button(controller, "editRecipeButton").fire();
                    root.applyCss(); root.layout();
                    check(!((VBox) get(controller, "recipeBrowser")).isManaged(),
                            "Wide recipe editor uses the full workspace");
                    var categoryAction = button(controller, "newCategoryButton");
                    check(categoryAction.getWidth() + 1 >= categoryAction.prefWidth(-1),
                            "New category action keeps its complete label at " + width + " px");
                    it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/recipe-editor-" + width + ".png");
                    invoke(controller, "cancelRecipe");
                    invoke(controller, "showIngredients");
                    root.applyCss(); root.layout();
                    check(!((VBox) get(controller, "ingredientEditor")).isManaged(),
                            "Ingredient list opens without reserving editor space");
                    it.uninafoodlab.presentation.FxSnapshot.save(root, "target/native-match/ingredients-" + width + ".png");
                    invoke(controller, "showRecipes");
                    return null;
                });
            }
            fx(() -> {
                var root = (VBox) get(controller, "workspace");
                var stage = (javafx.stage.Stage) root.getScene().getWindow();
                stage.setWidth(780 + stage.getWidth() - root.getScene().getWidth());
                stage.setHeight(650 + stage.getHeight() - root.getScene().getHeight());
                return null;
            });
            await(() -> Math.abs(((VBox) get(controller, "workspace")).getWidth() - 780) < 2);
            fx(() -> {
                var root = (VBox)get(controller,"workspace");
                root.applyCss(); root.layout();
                var browser = (VBox)get(controller,"recipeBrowser");
                check(!browser.isManaged(), "Consultation keeps the recipe index out of the full-width sheet");
                var list = (ListView<?>)get(controller,"recipeList");
                check(list.lookupAll(".list-cell .label").stream()
                        .map(node -> ((Label)node).getText()).noneMatch(text -> text.contains("1 persona")),
                        "Recipe list omits the redundant serving count");
                check(button(controller,"editRecipeButton").isVisible(), "Any existing recipe can be edited, including one previously protected");
                check(!((CatalogRecipeMode)get(controller,"recipeMode")).editing(), "Existing recipe opens in consultation");
                snapshot(controller, "target/native-match/catalog-read.png");
                var categoryFilter = (ComboBox<String>) get(controller, "recipeCategoryFilter");
                categoryFilter.setValue(recipe.categoryName());
                check(((ListView<?>) get(controller, "recipeList")).getItems().size() == 1,
                        "Matching category preserves the visible recipe");
                button(controller, "backToRecipesButton").fire();
                categoryFilter.setValue("Categoria senza ricette");
                return null;
            });
            await(() -> !controller.isLoading() && ((ListView<?>) get(controller, "recipeList")).getItems().isEmpty());
            fx(() -> {
                var categoryFilter = (ComboBox<String>) get(controller, "recipeCategoryFilter");
                check(((ListView<?>) get(controller, "recipeList")).getItems().isEmpty(),
                        "Category filter excludes recipes from other categories");
                check(!((VBox) get(controller, "recipeDetails")).isVisible()
                        && ((VBox) get(controller, "recipeBrowser")).isVisible(),
                        "A category filter also closes details that no longer match");
                categoryFilter.setValue("Tutte le categorie");
                return null;
            });
            await(() -> !controller.isLoading() && !((ListView<?>) get(controller, "recipeList")).getItems().isEmpty());
            fx(() -> {
                check(!((ListView<?>) get(controller, "recipeList")).getItems().isEmpty(),
                        "All categories restores the recipe list");
                open(controller, recipe);
                return null;
            });
            await(() -> !controller.isLoading() && ((VBox) get(controller, "recipeDetails")).isVisible());
            int readsBeforeHiddenFilter = recipeReads.get();
            fx(() -> {
                button(controller,"editRecipeButton").fire();
                check(((CatalogRecipeMode)get(controller,"recipeMode")).editing(), "Edit opens the existing recipe draft");
                check(button(controller, "backToRecipesButton").isVisible()
                        && button(controller, "backToRecipesButton").getText().equals("Torna al ricettario"),
                        "The editor keeps its explicit return action");
                var editorRoot = (VBox)get(controller,"workspace");
                editorRoot.applyCss(); editorRoot.layout();
                check(!((VBox)get(controller,"recipeBrowser")).isManaged(),
                        "Editing uses the full workspace instead of keeping the recipe index beside the form");
                check(!((javafx.scene.layout.HBox)get(controller,"catalogToolbar")).isManaged(),
                        "Search and filters leave room for the editor while a recipe is being edited");
                check(((ScrollPane)get(controller,"recipeScroll")).getViewportBounds().getHeight() >= 260,
                        "Recipe fields have a useful viewport at the compact window size");
                var recipeSearchField = (TextField) get(controller, "recipeSearch");
                var draftName = (TextField) get(controller, "recipeName");
                draftName.setText("Pane bozza");
                recipeSearchField.setText("nessuna corrispondenza");
                return null;
            });
            await(() -> recipeReads.get() > readsBeforeHiddenFilter && !controller.isLoading()
                    && ((ListView<?>) get(controller, "recipeList")).getItems().isEmpty());
            fx(() -> {
                var recipeSearchField = (TextField) get(controller, "recipeSearch");
                var draftName = (TextField) get(controller, "recipeName");
                check(((CatalogRecipeMode) get(controller, "recipeMode")).editing()
                        && draftName.getText().equals("Pane bozza"),
                        "A hidden filter change cannot close or overwrite the active recipe draft");
                recipeSearchField.setText("pane");
                return null;
            });
            await(() -> recipeReads.get() > readsBeforeHiddenFilter + 1 && !controller.isLoading()
                    && !((ListView<?>) get(controller, "recipeList")).getItems().isEmpty());
            fx(() -> {
                var draftName = (TextField) get(controller, "recipeName");
                check(((ListView<RecipeSummary>) get(controller, "recipeList")).getSelectionModel()
                        .getSelectedItem().equals(recipe),
                        "Restoring the filter restores the recipe selection while editing");
                draftName.setText("Pane");
                invoke(controller,"showIngredients");
                check(button(controller, "backFromIngredientsButton").isManaged()
                        && button(controller, "backFromIngredientsButton").getGraphic() != null,
                        "Ingredients offer an explicit arrow return to the recipe draft");
                button(controller, "backFromIngredientsButton").fire();
                check(!((TextField)get(controller,"recipeSearch")).isManaged(),
                        "Returning from ingredients keeps the recipe draft in its full-width editor");
                invoke(controller,"cancelRecipe");
                check(!((CatalogRecipeMode)get(controller,"recipeMode")).editing(), "Cancel returns to consultation");
                check(!((VBox)get(controller,"recipeBrowser")).isManaged()
                        && !((javafx.scene.layout.HBox)get(controller,"catalogToolbar")).isManaged()
                        && ((VBox)get(controller,"recipeReadView")).isManaged(),
                        "Cancelling after an ingredients detour restores full-width consultation");
                check(recipe.id().equals(get(controller,"editingRecipe")), "Cancel preserves recipe identity");
                button(controller,"editRecipeButton").fire();
                var rows = (VBox)get(controller,"ingredientLines");
                check(rows.getChildren().size()==1, "Editor uses rows inside the main scroll");
                var controls = (javafx.scene.layout.HBox)((VBox)rows.getChildren().getFirst()).getChildren().getLast();
                var quantity = (TextField)controls.getChildren().getFirst();
                quantity.setText("0,5"); quantity.fireEvent(new javafx.event.ActionEvent());
                check(((List<RecipeIngredientInput>)get(controller,"lines")).getFirst().quantity().compareTo(new BigDecimal("0.5"))==0,
                        "Italian decimal quantity is committed to the draft");
                ((TextField)get(controller,"recipeName")).setText("Pane modificato");
                var columns = (javafx.scene.layout.GridPane)get(controller,"recipeColumns");
                columns.resize(850, 600);
                check(javafx.scene.layout.GridPane.getColumnIndex((VBox)get(controller,"recipeIngredientsPanel")) == 1,
                        "Wide recipe sheets put preparation and doses side by side");
                columns.resize(450, 600);
                check(javafx.scene.layout.GridPane.getRowIndex((VBox)get(controller,"recipeIngredientsPanel")) == 1,
                        "Narrow recipe sheets stack preparation and doses");
                check(((TextField)get(controller,"recipeName")).getText().equals("Pane modificato"),
                        "Responsive reflow preserves the recipe name draft");
                check(((List<RecipeIngredientInput>)get(controller,"lines")).getFirst().quantity().compareTo(new BigDecimal("0.5")) == 0,
                        "Responsive reflow preserves edited quantities");
                snapshot(controller, "target/studio-catalog-decimal.png");
                button(controller,"saveRecipeButton").fire(); return null;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                check(((TextField)get(controller,"recipeName")).getText().equals("Pane modificato"), "Failed write retains draft");
                check(!label(controller).contains("jdbc:"), "Write failure hides technical details");
                check(!button(controller,"retryButton").isVisible(), "Uncertain write cannot be replayed by retry");
                check(writeCount.get()==1, "No automatic write replay"); return null;
            });
            fx(() -> {
                ((TextField)get(controller,"recipeName")).clear();
                ((TextArea)get(controller,"recipeDescription")).clear();
                ((TextField)get(controller,"quantity")).setText("0");
                button(controller,"saveRecipeButton").fire(); return null;
            });
            await(() -> focused(controller,"recipeName"));
            fx(() -> {
                check(((Label)get(controller,"recipeNameError")).isVisible(), "Name error precedes description and ingredient validation");
                ((TextField)get(controller,"recipeName")).setText("Pane modificato");
                button(controller,"saveRecipeButton").fire(); return null;
            });
            await(() -> focused(controller,"recipeDescription"));
            fx(() -> {
                ((TextArea)get(controller,"recipeDescription")).setText("Impastare con cura");
                button(controller,"saveRecipeButton").fire(); return null;
            });
            await(() -> focused(controller,"lineIngredient"));
            var additional = new IngredientSummary(UUID.randomUUID(), "Acqua", MeasurementUnit.GRAM, false, false);
            fx(() -> {
                ((ComboBox<IngredientSummary>)get(controller,"lineIngredient")).setValue(additional);
                invoke(controller,"addLine"); return null;
            });
            await(() -> focused(controller,"quantity"));
            fx(() -> {
                var scroll = (ScrollPane)get(controller,"recipeScroll");
                check(scroll.getVvalue() > 0, "Narrow editor scrolls to the invalid ingredient quantity");
                var viewport = scroll.lookup(".viewport");
                var field = (TextField)get(controller,"quantity");
                var bounds = viewport.sceneToLocal(field.localToScene(field.getBoundsInLocal()));
                check(bounds.getMinY() >= -1 && bounds.getMaxY() <= viewport.getBoundsInLocal().getHeight()+1,
                        "Invalid quantity is entirely inside the viewport");
                check(((Label)get(controller,"recipeIngredientError")).isVisible(), "Ingredient error appears beside ingredient controls");
                check(field.getText().equals("0"), "Invalid typed quantity is preserved");
                check(((TextField)get(controller,"recipeName")).getText().equals("Pane modificato"), "Validation preserves name");
                check(((TextArea)get(controller,"recipeDescription")).getText().equals("Impastare con cura"), "Validation preserves description");
                check(((List<RecipeIngredientInput>)get(controller,"lines")).getFirst().quantity().compareTo(new BigDecimal("0.5")) == 0,
                        "Validation preserves existing ingredient doses");
                snapshot(controller, "target/studio-catalog-error.png");
                field.setText("25"); button(controller,"saveRecipeButton").fire(); return null;
            });
            await(() -> focused(controller,"quantity"));
            fx(() -> {
                check(writeCount.get()==1, "Pending ingredient must be added before any write");
                invoke(controller,"addLine");
                var status = (Label)get(controller,"feedback");
                check(status.getStyleClass().contains("course-feedback-success")
                        && !status.getStyleClass().contains("course-feedback-error"), "Successful addition replaces error styling");
                ((VBox)get(controller,"workspace")).applyCss(); ((VBox)get(controller,"workspace")).layout();
                snapshot(controller, "target/studio-catalog-success.png");
                check(((Label)get(controller,"recipeIngredientError")).getText().isEmpty(), "Resolved contextual error clears");
                ((List<?>)get(controller,"lines")).clear();
                button(controller,"saveRecipeButton").fire(); return null;
            });
            await(() -> focused(controller,"lineIngredient"));
            fx(() -> { check(writeCount.get()==1, "Recipe without ingredients cannot be written"); return null; });
            fx(() -> {
                invoke(controller, "showIngredients");
                var root = (VBox) get(controller, "workspace");
                root.applyCss(); root.layout();
                ((TextField) get(controller, "ingredientSearch")).requestFocus();
                ((Button) root.lookup(".catalog-ingredient-action")).fire();
                return null;
            });
            await(() -> focused(controller, "ingredientName"));
            fx(() -> {
                check(((TextField) get(controller, "ingredientName")).getText().equals("Farina"),
                        "Explicit ingredient edit focuses the loaded ingredient name");
                ((TextField) get(controller, "ingredientSearch")).requestFocus();
                invoke(controller, "loadIngredient");
                return null;
            });
            fx(() -> {
                check(focused(controller, "ingredientSearch"),
                        "Passive ingredient refresh does not steal existing focus");
                return null;
            });
            System.out.println("PASS: catalog loading, safe read retry, generic errors, responsive draft retention, write failure without replay, ordered field errors, narrow viewport and focus recovery, success styling. Screenshots use synthetic repository fixtures.");
        } finally { Platform.exit(); }
    }
    private static String label(Object controller) throws Exception {
        return ((Label) get(controller, "feedback")).getText();
    }

    private static boolean focused(Object controller, String fieldName) throws Exception {
        return ((javafx.scene.Node) get(controller, fieldName)).isFocused();
    }

    private static void invoke(Object controller, String methodName) throws Exception {
        var method = controller.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static void open(Object controller, RecipeSummary recipe) throws Exception {
        var root = (VBox) get(controller, "workspace");
        if (((VBox) get(controller, "recipeDetails")).isVisible()) button(controller, "backToRecipesButton").fire();
        root.applyCss(); root.layout();
        var action = root.lookupAll(".cookbook-open-action").stream()
                .filter(node -> recipe.id().equals(node.getUserData())).findFirst().orElseThrow();
        ((Button) action).fire();
    }

    private static void snapshot(Object controller, String path) throws Exception {
        it.uninafoodlab.presentation.FxSnapshot.save((VBox) get(controller, "workspace"), path);
    }

    private static Button button(Object controller, String fieldName) throws Exception {
        return (Button) get(controller, fieldName);
    }

    private static Object get(Object controller, String fieldName) throws Exception {
        var field = controller.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(controller);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        it.uninafoodlab.presentation.FxAwait.until(condition, 10);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
