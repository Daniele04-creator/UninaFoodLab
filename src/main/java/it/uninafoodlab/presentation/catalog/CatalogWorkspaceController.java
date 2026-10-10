package it.uninafoodlab.presentation.catalog;

import static it.uninafoodlab.presentation.catalog.CatalogRecipeLayout.show;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.presentation.catalog.CatalogOperations.FeedbackType;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.catalog.IngredientInput;
import it.uninafoodlab.application.catalog.IngredientSummary;
import it.uninafoodlab.application.catalog.ManageRecipeCatalog;
import it.uninafoodlab.application.catalog.RecipeIngredientInput;
import it.uninafoodlab.application.catalog.RecipeInput;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.UserErrorMessages;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


public final class CatalogWorkspaceController implements it.uninafoodlab.presentation.common.WorkspaceState {
    private static final String RECIPE_SUBJECT = "ricetta";
    private static final String INGREDIENT_SUBJECT = "ingrediente";

    private final AuthenticatedUser user;
    private final BrowseCatalog browse;
    private final ManageRecipeCatalog manage;
    private it.uninafoodlab.application.catalog.CreateCategory createCategory;
    @FXML private Button newCategoryButton;
    @FXML private VBox workspace;
    @FXML private HBox recipesPane;
    @FXML private VBox ingredientsPane;
    @FXML private VBox recipeDetails;
    @FXML private ToggleButton recipesTab;
    @FXML private ToggleButton ingredientsTab;
    @FXML private Button newRecipeButton;
    @FXML private ListView<RecipeSummary> recipeList;
    @FXML private TextField recipeSearch;
    @FXML private ComboBox<String> recipeCategoryFilter;
    @FXML private Label recipeCount;
    @FXML private VBox recipeCategories;
    @FXML private HBox recipePagerBottom;
    @FXML private ComboBox<CategorySummary> categoryChoice;
    @FXML private Label categoryReadOnly;
    @FXML private VBox recipeReadView;
    @FXML private VBox recipeEditView;
    @FXML private Label servingsReadOnly;
    @FXML private Label descriptionReadOnly;
    @FXML private Label recipeQuantitiesHint;
    @FXML private TextField recipeName;
    @FXML private TextArea recipeDescription;
    @FXML private VBox recipeBrowser;
    @FXML private HBox catalogHeader;
    @FXML private HBox catalogToolbar;
    @FXML private GridPane recipeColumns;
    @FXML private HBox recipeFooter;
    @FXML private VBox recipeIngredientsPanel;
    @FXML private Button backToRecipesButton;
    @FXML private Button backFromIngredientsButton;
    @FXML private Label inlineQuantityHint;
    @FXML private Label catalogTitle;
    @FXML private TextField ingredientSearch;
    @FXML private ListView<IngredientSummary> ingredientList;
    @FXML private VBox ingredientEditor;
    @FXML private javafx.scene.control.ScrollPane ingredientEditorScroll;
    @FXML private Label ingredientEditorTitle;
    @FXML private Button newIngredientButton;
    @FXML private Button cancelIngredientButton;
    @FXML private ComboBox<IngredientSummary> lineIngredient;
    @FXML private TextField lineIngredientSearch;
    @FXML private TextField quantity;
    @FXML private VBox addIngredientRow;
    @FXML private VBox ingredientLines;
    private final javafx.collections.ObservableList<RecipeIngredientInput> lines = javafx.collections.FXCollections.observableArrayList();
    @FXML private Button retryButton;
    @FXML private ComboBox<IngredientSummary> ingredientChoice;
    @FXML private TextField ingredientName;
    @FXML private Label ingredientNameReadOnly;
    @FXML private ComboBox<MeasurementUnit> ingredientUnit;
    @FXML private Label ingredientUnitReadOnly;
    @FXML private Label ingredientState;
    @FXML private Label feedback;
    @FXML private javafx.scene.control.ScrollPane recipeScroll;
    @FXML private Label recipeNameError;
    @FXML private Label recipeDescriptionError;
    @FXML private Label recipeCategoryError;
    @FXML private Label recipeIngredientError;
    @FXML private Label recipeEditorTitle;
    @FXML private Label recipeState;
    @FXML private Button editRecipeButton;
    @FXML private VBox ingredientReadLines;
    @FXML private Button saveRecipeButton;
    @FXML private Button saveIngredientButton;
    private List<IngredientSummary> ingredients = List.of();
    private List<RecipeSummary> recipes = List.of();
    private final CatalogRecipePages recipePages;
    private final javafx.animation.PauseTransition searchDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
    private boolean updatingChoices;
    private final CatalogRecipeMode recipeMode = new CatalogRecipeMode();
    private CatalogIngredientEditor ingredientView;
    private CatalogOperations operations;
    private CatalogIngredientRows ingredientRows;
    private CatalogIngredientSearch ingredientSearchView;
    private CatalogRecipeForm recipeForm;
    private final CatalogDraftState drafts = new CatalogDraftState();
    private java.util.function.BooleanSupplier unsavedChanges =
            it.uninafoodlab.presentation.common.WorkspaceState.CLEAN;
    private UUID editingRecipe;
    private UUID editingIngredient;
    private UUID returnRecipe;
    private final CatalogRecipeIndex recipeIndex = new CatalogRecipeIndex();
    private CatalogRecipeBrowser browserView;
    private final it.uninafoodlab.presentation.common.ResultPager pagerBottom =
            new it.uninafoodlab.presentation.common.ResultPager(page -> {
                recipeIndex.page(page + 1);
                filterRecipes(false);
            });

    public CatalogWorkspaceController(AuthenticatedUser user, BrowseCatalog browse, ManageRecipeCatalog manage) {
        this.user = Objects.requireNonNull(user);
        this.browse = Objects.requireNonNull(browse);
        this.manage = Objects.requireNonNull(manage);
        recipePages = new CatalogRecipePages(user, browse);
    }

    public void setCreateCategory(it.uninafoodlab.application.catalog.CreateCategory createCategory) {
        this.createCategory = Objects.requireNonNull(createCategory);
        CatalogCategoryDialog.enable(newCategoryButton);
    }

    @FXML private void newCategory() {
        CatalogCategoryDialog.open(newCategoryButton, () -> createCategory, user,
                categoryChoice, recipeCategoryError, operations);
    }


    public void activate() {
        if (workspace == null || isLoading()) return;
        if (drafts.canRefresh(recipeDraft(), ingredientDraft())) {
            refresh("", Optional.ofNullable(editingRecipe));
        } else {
            operations.setFeedback("Le modifiche non salvate sono conservate. Completa la preparazione o salva prima di aggiornare il catalogo.", FeedbackType.INFO);
        }
    }


    @Override
    public boolean hasUnsavedChanges() { return unsavedChanges.getAsBoolean(); }

    @Override
    public boolean isBusy() {
        return operations != null && operations.isLoading();
    }

    @FXML
    private void initialize() {
        var compact = javafx.css.PseudoClass.getPseudoClass("compact");
        workspace.heightProperty().addListener((observable, before, height) ->
                workspace.pseudoClassStateChanged(compact, height.doubleValue() < 580));
        workspace.pseudoClassStateChanged(compact, workspace.getHeight() < 580);
        configureEditors();
        recipeColumns.widthProperty().addListener((observable, before, after) ->
                CatalogRecipeLayout.layoutRecipeColumns(recipeColumns, recipeReadView, recipeEditView, recipeIngredientsPanel));
        CatalogRecipeLayout.layoutRecipeColumns(recipeColumns, recipeReadView, recipeEditView, recipeIngredientsPanel);
        CatalogRecipeLayout.bindBrowserWidth(recipeBrowser);
        recipeSearch.setMaxWidth(480);
        ingredientSearch.setMaxWidth(480);
        quantity.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change ->
                CatalogQuantityPolicy.acceptsEdit(change.getControlNewText()) ? change : null));
        ingredientSearch.textProperty().addListener((observable, before, after) -> filterIngredients());
        CatalogCells.ingredients(ingredientList);
        ingredientList.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
            if (!updatingChoices && after != null) ingredientChoice.setValue(after);
        });
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        for (var error : recipeForm.recipeErrors()) {
            error.visibleProperty().bind(error.textProperty().isNotEmpty());
            error.managedProperty().bind(error.visibleProperty());
        }
        ingredientNameReadOnly.textProperty().bind(ingredientName.textProperty());
        servingsReadOnly.setText("1 persona");
        descriptionReadOnly.setMaxWidth(680);
        descriptionReadOnly.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> recipeDescription.getText().isBlank() ? "Preparazione non ancora descritta."
                        : CatalogRecipeText.readingText(recipeDescription.getText()), recipeDescription.textProperty()));
        recipeQuantitiesHint.setText("Dosi per 1 persona. Quantità nell’unità indicata, fino a 3 decimali.");
        saveRecipeButton.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon("save", "light", 18));
        saveIngredientButton.setGraphic(it.uninafoodlab.presentation.common.LabIcons.icon("save", "light", 18));
        CatalogRecipeLayout.bindReadTitle(recipeName, recipeEditorTitle, recipeMode::editing);
        newCategoryButton.setDisable(createCategory == null);
        CatalogCells.display(categoryChoice, CategorySummary::name);
        CatalogCells.display(ingredientChoice, IngredientSummary::toString);
        CatalogCells.display(lineIngredient, IngredientSummary::toString);
        ingredientSearchView = new CatalogIngredientSearch(lineIngredientSearch, lineIngredient, quantity);
        CatalogCells.display(ingredientUnit, MeasurementUnit::symbol);
        ingredientUnit.getItems().setAll(MeasurementUnit.GRAM, MeasurementUnit.MILLILITER,
                MeasurementUnit.PIECE, MeasurementUnit.TEASPOON);
        ingredientUnit.setValue(MeasurementUnit.GRAM);
        configureBrowser();
        refresh("", Optional.empty());
    }

    private void configureEditors() {
        ingredientView = new CatalogIngredientEditor(
                ingredientEditor,
                ingredientEditorTitle,
                newIngredientButton,
                cancelIngredientButton,
                ingredientName,
                ingredientNameReadOnly,
                ingredientUnit,
                ingredientUnitReadOnly,
                saveIngredientButton,
                ingredientState, ingredientChoice);
        operations = new CatalogOperations(workspace, recipesPane, ingredientsPane, newRecipeButton,
                retryButton, feedback, this::recipeDraft, this::ingredientDraft);
        it.uninafoodlab.presentation.common.TextAreaNavigation.install(recipeDescription);
        recipeForm = new CatalogRecipeForm(workspace, recipeScroll, recipeName, recipeDescription,
                categoryChoice, lineIngredient, quantity, recipeNameError, recipeDescriptionError,
                recipeCategoryError, recipeIngredientError,
                message -> operations.setFeedback(message, FeedbackType.ERROR));
        ingredientRows = new CatalogIngredientRows(ingredientLines, ingredientReadLines, lines,
                recipeMode::editing, message -> operations.setFeedback(message, FeedbackType.ERROR), () -> operations.focus(lineIngredient));
        unsavedChanges = () -> drafts.hasUnsavedChanges(this::recipeDraft, this::ingredientDraft,
                ingredientRows::currentDraftLines);
    }

    private void configureBrowser() {
        recipeList.setPlaceholder(new Label("Nessuna ricetta trovata."));
        recipePagerBottom.getChildren().add(pagerBottom);
        backToRecipesButton.setGraphic(it.uninafoodlab.presentation.common.LabIcons.back(16));
        backFromIngredientsButton.setGraphic(it.uninafoodlab.presentation.common.LabIcons.back(16));
        HBox.setHgrow(pagerBottom, javafx.scene.layout.Priority.ALWAYS);
        ingredientList.setPlaceholder(new Label("Nessun ingrediente trovato."));
        browserView = new CatalogRecipeBrowser(recipeList, recipeCategories, recipeCategoryFilter,
                recipeSearch, recipeIndex, operations::focus, this::openRecipe);
        recipeCategoryFilter.getItems().add("Tutte le categorie");
        recipeCategoryFilter.setValue("Tutte le categorie");
        recipeCategoryFilter.valueProperty().addListener((observable, before, after) -> {
            if (!updatingChoices) filterRecipes(true);
        });
        searchDelay.setOnFinished(event -> filterRecipes(true));
        recipeSearch.textProperty().addListener((observable, before, after) -> {
            if (!updatingChoices) searchDelay.playFromStart();
        });
        ingredientChoice.setOnAction(event -> selectIngredient());
    }

    private void openRecipe(RecipeSummary recipe) {
        if (isLoading() || updatingChoices || recipe == null) return;
        if (allowDiscard(drafts.recipeChanged(recipeDraft()), RECIPE_SUBJECT)) {
            returnRecipe = recipe.id();
            loadRecipe(recipe, "");
        }
    }

    private void selectIngredient() {
        if (updatingChoices || ingredientChoice.getValue() == null) return;
        if (allowDiscard(drafts.ingredientChanged(ingredientDraft()), INGREDIENT_SUBJECT)) {
            loadIngredient();
            operations.focus(ingredientName);
        } else restoreIngredientSelection();
    }

    private void filterIngredients() {
        var query = ingredientSearch.getText().strip().toLowerCase(Locale.ITALIAN);
        boolean previous = updatingChoices;
        updatingChoices = true;
        try {
            ingredientList.getItems().setAll(ingredients.stream()
                    .filter(value -> value.name().toLowerCase(Locale.ITALIAN).contains(query)).toList());
        } finally { updatingChoices = previous; }
    }

    @FXML private void cancelIngredient() {
        if (!allowDiscard(drafts.ingredientChanged(ingredientDraft()), INGREDIENT_SUBJECT)) return;
        if (!ingredients.isEmpty()) {
            boolean previous = updatingChoices;
            updatingChoices = true;
            try { ingredientChoice.setValue(ingredients.getFirst()); }
            finally { updatingChoices = previous; }
            loadIngredient();
        } else {
            resetIngredient();
            show(ingredientEditor, false);
            show(newIngredientButton, true);
        }
        operations.setFeedback("", FeedbackType.INFO);
        show(ingredientEditor, false);
        show(newIngredientButton, true);
        ingredientList.getSelectionModel().clearSelection();
        ingredientChoice.setValue(null);
        operations.focus(ingredientList);
    }

    @FXML private void backToRecipes() {
        if (!allowDiscard(drafts.recipeChanged(recipeDraft()), RECIPE_SUBJECT)) return;
        restoreSavedRecipe();
        editingRecipe = null;
        recipeMode.open();
        drafts.recipeLoaded(recipeDraft());
        updatingChoices = true;
        try { recipeList.getSelectionModel().clearSelection(); }
        finally { updatingChoices = false; }
        show(recipeDetails, false);
        CatalogRecipeLayout.showEditingStyle(workspace, false);
        showRecipes();
        operations.setFeedback("", FeedbackType.INFO);
        browserView.focusReturn(returnRecipe);
    }

    private CatalogDraftState.RecipeDraft recipeDraft() {
        return recipeForm.draft(editingRecipe, lines);
    }

    private CatalogDraftState.IngredientDraft ingredientDraft() {
        return new CatalogDraftState.IngredientDraft(editingIngredient, ingredientName.getText(), ingredientUnit.getValue());
    }

    private boolean allowDiscard(boolean changed, String subject) {
        return CatalogDiscardDialog.allowDiscard(workspace, changed, subject,
                subject.equals(RECIPE_SUBJECT) ? recipeName.getText() : ingredientName.getText());
    }

    private void restoreRecipeSelection() {
        updatingChoices = true;
        try {
            recipeList.getSelectionModel().select(recipeList.getItems().stream()
                    .filter(value -> value.id().equals(editingRecipe)).findFirst().orElse(null));
        } finally {
            updatingChoices = false;
        }
    }

    private void restoreIngredientSelection() {
        updatingChoices = true;
        try {
            ingredientView.selectIngredient(editingIngredient);
            ingredientList.getSelectionModel().select(ingredientChoice.getValue());
        } finally {
            updatingChoices = false;
        }
    }

    private void restoreSavedRecipe() {
        var saved = drafts.savedRecipe();
        if (saved == null) return;
        recipeName.setText(saved.name());
        recipeDescription.setText(saved.description());
        categoryChoice.setValue(categoryChoice.getItems().stream().filter(value -> value.id().equals(saved.categoryId()))
                .findFirst().orElse(null));
        lines.setAll(saved.lines());
        ingredientRows.renderEditIngredients(ingredients);
        ingredientSearchView.clear();
        quantity.clear();
    }

    @FXML
    private void showRecipes() {
        show(backFromIngredientsButton, false);
        show(recipesPane, true);
        boolean details = recipeDetails.isVisible();
        boolean editing = details && recipeMode.editing();
        CatalogRecipeLayout.showEditingStyle(workspace, editing);
        show(catalogToolbar, !details);
        show(recipeSearch, !details);
        show(ingredientsPane, false);
        show(recipeBrowser, !details);
        show(newRecipeButton, !details);
        show(catalogHeader, !details);
        catalogTitle.setText("Ricettario");
        show(catalogTitle, !details);
        recipesTab.setSelected(true);
        ingredientsTab.setSelected(false);
    }

    @FXML
    private void showIngredients() {
        show(backFromIngredientsButton, true);
        backFromIngredientsButton.setText(recipeDetails.isVisible() ? "Torna alla ricetta" : "Torna al ricettario");
        backFromIngredientsButton.setAccessibleText(backFromIngredientsButton.getText());
        show(recipesPane, false);
        CatalogRecipeLayout.showEditingStyle(workspace, false);
        show(catalogToolbar, false);
        show(recipeSearch, false);
        show(recipeCategoryFilter, false);
        show(ingredientsPane, true);
        show(newRecipeButton, false);
        show(catalogHeader, true);
        show(catalogTitle, true);
        show(ingredientEditor, drafts.ingredientChanged(ingredientDraft()));
        show(newIngredientButton, true);
        ingredientList.getSelectionModel().clearSelection();
        ingredientChoice.setValue(null);
        catalogTitle.setText("Ingredienti");
        recipesTab.setSelected(false);
        ingredientsTab.setSelected(true);
    }

    private void filterRecipes(boolean resetPage) {
        searchDelay.stop();
        if (isLoading()) return;
        int requestedPage = resetPage ? 0 : recipeIndex.page() - 1;
        var filter = browserView.filter(categoryChoice.getItems());
        operations.run("Caricamento delle ricette…", "Non è stato possibile aggiornare le ricette. Riprova.",
                true, () -> recipePages.load(filter, requestedPage, Optional.empty()), snapshot -> {
                    renderRecipePage(snapshot);
                    recipeList.scrollTo(0);
                    operations.setFeedback("", FeedbackType.INFO);
                });
    }

    private void renderRecipePage(CatalogRecipePages.Snapshot snapshot) {
        recipeIndex.updatePage(snapshot, recipeCategoryFilter.getValue());
        recipes = snapshot.recipes().items();
        var selected = recipeList.getSelectionModel().getSelectedItem();
        updatingChoices = true;
        try {
            CatalogRecipeLayout.showFilteredRecipes(recipeList, recipes, selected, editingRecipe);
        } finally {
            updatingChoices = false;
        }
        int total = recipeIndex.total();
        recipeCount.setText(total + (total == 1 ? " ricetta" : " ricette"));
        show(recipeCount, true);
        browserView.renderCategories(snapshot.categoryCounts().values().stream().mapToInt(Integer::intValue).sum());
        show(recipePagerBottom, recipeIndex.pageCount() > 1);
        pagerBottom.update(recipeIndex.page() - 1, total, "ricette");
        recipeList.refresh();
    }

    @FXML private void retryRead() { operations.retryRead(); }

    boolean isLoading() { return operations.isLoading(); }

    private record Snapshot(List<CategorySummary> categories, CatalogRecipePages.Snapshot page,
            List<IngredientSummary> ingredients) {}

    private void refresh(String message, Optional<UUID> selectedRecipe) {
        var previousIngredient = ingredientDraft();
        var preserveIngredient = drafts.ingredientChanged(previousIngredient);
        var filter = browserView.filter(categoryChoice.getItems());
        int requestedPage = recipeIndex.page() - 1;
        var failureMessage = message.isEmpty()
                ? "Non è stato possibile aggiornare il ricettario. Le modifiche in corso sono conservate. Riprova."
                : message + " Il ricettario non è aggiornato. Riprova ad aggiornare senza ripetere il salvataggio.";
        operations.run("Caricamento del ricettario…", failureMessage, true, () -> new Snapshot(browse.categories(user), recipePages.load(filter, requestedPage, selectedRecipe), manage.ingredients(user)), snapshot -> {
            updatingChoices = true;
            try {
                ingredients = snapshot.ingredients();
                filterIngredients();
                browserView.updateCategories(snapshot.page().categoryCounts().keySet());
                categoryChoice.getItems().setAll(snapshot.categories());
                ingredientView.refreshChoices(ingredients, previousIngredient.id());
                ingredientSearchView.ingredients(ingredients);
                if (!preserveIngredient) {
                    if (ingredientChoice.getValue() == null) resetIngredient();
                    else loadIngredient();
                }
            } finally {
                updatingChoices = false;
            }
            renderRecipePage(snapshot.page());
            var restore = snapshot.page().selected();
            if (restore.isPresent()) {
                var recipe = restore.get();
                updatingChoices = true;
                try {
                    recipeList.getSelectionModel().select(recipeList.getItems().contains(recipe) ? recipe : null);
                } finally {
                    updatingChoices = false;
                }
                loadRecipe(recipe, message);
            } else {
                backToRecipes();
                operations.setFeedback(message, FeedbackType.SUCCESS);
            }
        });
    }

    private void renderRecipeMode() {
        recipeForm.clearRecipeErrors();
        boolean editing = recipeMode.editing();
        show(recipeDetails, true);
        recipeName.setEditable(editing);
        recipeDescription.setEditable(editing);
        show(recipeReadView, !editing);
        show(recipeEditView, editing);
        show(addIngredientRow, editing);
        show(inlineQuantityHint, editing);
        show(recipeQuantitiesHint, editing);
        show(recipeBrowser, false);
        show(catalogHeader, false);
        show(catalogTitle, false);
        show(catalogToolbar, false);
        show(recipeSearch, false);
        show(recipeCategoryFilter, false);
        String actionLabel = "Torna al ricettario";
        backToRecipesButton.setText(actionLabel);
        backToRecipesButton.setAccessibleText(actionLabel);
        show(backToRecipesButton, true);
        show(categoryReadOnly, !editing);
        show(recipeFooter, editing);
        show(newRecipeButton, false);
        show(ingredientLines, editing);
        show(ingredientReadLines, !editing);
        ingredientRows.renderEditIngredients(ingredients);
        ingredientRows.renderReadIngredients(ingredients);
        show(saveRecipeButton, editing);
        show(editRecipeButton, !editing && editingRecipe != null);
        CatalogRecipeLayout.showHeading(workspace, recipeEditorTitle, recipeState,
                recipeName, editingRecipe, editing);
        CatalogRecipeLayout.layoutRecipeColumns(recipeColumns, recipeReadView, recipeEditView, recipeIngredientsPanel);
    }

    @FXML
    private void editRecipe() {
        if (editingRecipe == null) return;
        recipeMode.edit();
        renderRecipeMode();
        recipeScroll.setVvalue(0);
        operations.focus(recipeName);
    }

    @FXML
    private void cancelRecipe() {
        if (!allowDiscard(drafts.recipeChanged(recipeDraft()), RECIPE_SUBJECT)) return;
        if (editingRecipe == null) {

            restoreSavedRecipe();
            drafts.recipeLoaded(recipeDraft());
            backToRecipes();
            return;
        }
        restoreSavedRecipe();
        recipeMode.open();
        renderRecipeMode();
        operations.setFeedback("", FeedbackType.INFO);
        operations.focus(editRecipeButton);
    }

    @FXML
    private void newRecipe() {
        if (!allowDiscard(drafts.recipeChanged(recipeDraft()), RECIPE_SUBJECT)) return;
        resetRecipe();
    }

    private void resetRecipe() {
        editingRecipe = null;
        recipeList.getSelectionModel().clearSelection();
        recipeName.clear();
        recipeDescription.clear();
        lines.clear();
        quantity.clear();
        ingredientSearchView.clear();
        categoryChoice.getSelectionModel().selectFirst();
        recipeMode.create();
        renderRecipeMode();
        drafts.recipeLoaded(recipeDraft());
        showRecipes();
        recipeScroll.setVvalue(0);
        operations.setFeedback("", FeedbackType.INFO);
        operations.focus(recipeName);
    }

    private void loadRecipe(RecipeSummary recipe, String completionMessage) {
        restoreRecipeSelection();
        var failureMessage = completionMessage.isEmpty()
                ? "Non è stato possibile aprire la ricetta. La selezione precedente è conservata. Riprova."
                : completionMessage + " Non è stato possibile caricarne gli ingredienti. Riprova ad aggiornare senza ripetere il salvataggio.";
        operations.run("Caricamento degli ingredienti…", failureMessage, true, () -> manage.recipeIngredients(user, recipe.id()), loadedLines -> {
            editingRecipe = recipe.id();
            restoreRecipeSelection();
            recipeName.setText(recipe.name());
            recipeDescription.setText(recipe.description());
            categoryChoice.getItems().stream().filter(category -> category.id().equals(recipe.categoryId()))
                    .findFirst().ifPresent(categoryChoice::setValue);
            categoryReadOnly.setText(recipe.categoryName());
            lines.setAll(loadedLines);
            quantity.clear();
            ingredientSearchView.clear();
            recipeMode.open();
            renderRecipeMode();
            drafts.recipeLoaded(recipeDraft());
            operations.setFeedback(completionMessage, completionMessage.isEmpty() ? FeedbackType.INFO : FeedbackType.SUCCESS);
            recipeScroll.setVvalue(0);
            operations.focus(backToRecipesButton);
        });
    }

    @FXML
    private void addLine() {
        if (!recipeMode.editing()) return;
        recipeForm.clearRecipeErrors();
        var ingredient = lineIngredient.getValue();
        if (ingredient == null) {
            recipeForm.recipeError(recipeIngredientError, lineIngredient, "Scegli un ingrediente.");
            return;
        }
        if (lines.stream().anyMatch(value -> value.ingredientId().equals(ingredient.id()))) {
            recipeForm.recipeError(recipeIngredientError, lineIngredient, "Ingrediente già presente. Modifica la quantità direttamente nella riga.");
            return;
        }
        try {
            var line = new RecipeIngredientInput(ingredient.id(), CatalogQuantityPolicy.parse(quantity.getText()));
            lines.add(line);
            ingredientRows.renderEditIngredients(ingredients);
            quantity.clear();
            ingredientSearchView.clear();
            operations.setFeedback("Ingrediente aggiunto. Salva la ricetta per confermare le modifiche.", FeedbackType.SUCCESS);
        } catch (IllegalArgumentException error) {
            recipeForm.recipeError(recipeIngredientError, quantity,
                    UserErrorMessages.validation(error, "Inserisci una quantità positiva, fino a 3 decimali."));
        }
    }

    @FXML
    private void saveRecipe() {
        if (!recipeMode.editing()) return;
        recipeForm.clearRecipeErrors();
        if (!recipeForm.validate(lines)) return;
        try {
            var input = new RecipeInput(categoryChoice.getValue().id(), recipeName.getText(), recipeDescription.getText(),
                    1, lines);
            operations.run("Salvataggio della ricetta…", "Non è stato possibile confermare il salvataggio. La bozza è conservata: verifica il catalogo prima di salvare nuovamente.", false, () -> manage.saveRecipe(user, Optional.ofNullable(editingRecipe), input),
                    saved -> {
                        editingRecipe = saved;
                        drafts.recipeLoaded(recipeDraft());
                        refresh("Ricetta salvata nel catalogo condiviso.", Optional.of(saved));
                    });
        } catch (IllegalArgumentException error) {
            operations.setFeedback(UserErrorMessages.validation(error,
                    "Controlla nome, categoria, preparazione e ingredienti della ricetta."), FeedbackType.ERROR);
        }
    }

    @FXML
    private void newIngredient() {
        operations.setFeedback("", FeedbackType.INFO);
        if (!allowDiscard(drafts.ingredientChanged(ingredientDraft()), INGREDIENT_SUBJECT)) return;
        resetIngredient();
        operations.focus(ingredientName);
    }

    private void resetIngredient() {
        editingIngredient = null;
        ingredientChoice.setValue(null);
        ingredientName.clear();
        ingredientUnit.setValue(MeasurementUnit.GRAM);
        ingredientView.protection(false, false, true);
        ingredientEditorScroll.setVvalue(0);
        drafts.ingredientLoaded(ingredientDraft());
    }

    private void loadIngredient() {
        operations.setFeedback("", FeedbackType.INFO);
        var ingredient = ingredientChoice.getValue();
        if (ingredient == null) return;
        editingIngredient = ingredient.id();
        boolean previous = updatingChoices;
        updatingChoices = true;
        try { ingredientList.getSelectionModel().select(ingredient); }
        finally { updatingChoices = previous; }
        ingredientName.setText(ingredient.name());
        ingredientUnit.setValue(ingredient.unit());
        ingredientUnitReadOnly.setText(ingredient.unit().symbol());
        ingredientView.protection(ingredient.locked(), ingredient.unitLocked(), false);
        ingredientEditorScroll.setVvalue(0);
        drafts.ingredientLoaded(ingredientDraft());
    }

    @FXML
    private void saveIngredient() {
        if (ingredientView.isLocked()) return;
        if (ingredientName.getText() == null || ingredientUnit.getValue() == null) {
            operations.setFeedback("Completa nome e unità.", FeedbackType.ERROR);
            return;
        }
        try {
            var input = new IngredientInput(ingredientName.getText(), ingredientUnit.getValue());
            operations.run("Salvataggio dell’ingrediente…", "Non è stato possibile confermare il salvataggio. I dati sono conservati: verifica il catalogo prima di salvare nuovamente.", false, () -> manage.saveIngredient(user, Optional.ofNullable(editingIngredient), input), saved -> {
                    editingIngredient = saved;
                    drafts.ingredientLoaded(ingredientDraft());
                    operations.run("Aggiornamento degli ingredienti…", "Ingrediente salvato, ma l’elenco non è aggiornato. Riprova ad aggiornare senza ripetere il salvataggio.", true, () -> manage.ingredients(user), updated -> {
                        updatingChoices = true;
                        try {
                            var selectedLine = lineIngredient.getValue();
                            ingredients = updated;
                            filterIngredients();
                            ingredientChoice.getItems().setAll(updated);
                            ingredientSearchView.ingredients(updated);
                            if (selectedLine != null) updated.stream().filter(value -> value.id().equals(selectedLine.id()))
                                    .findFirst().ifPresent(lineIngredient::setValue);
                            ingredientRows.renderEditIngredients(ingredients);
                            ingredientRows.renderReadIngredients(ingredients);
                            ingredientChoice.setValue(updated.stream().filter(value -> value.id().equals(saved)).findFirst().orElse(null));
                            loadIngredient();
                            operations.setFeedback("Ingrediente salvato. È disponibile nella scheda Ricette; la preparazione in corso è conservata.", FeedbackType.SUCCESS);
                        } finally {
                            updatingChoices = false;
                        }
                    });
                });
        } catch (IllegalArgumentException error) {
            operations.setFeedback(UserErrorMessages.validation(error,
                    "Controlla nome e unità di misura dell’ingrediente."), FeedbackType.ERROR);
        }
    }
}
