package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;


final class CatalogOperations {
    enum FeedbackType { INFO, SUCCESS, ERROR }
    private final VBox workspace;
    private final HBox recipesPane;
    private final VBox ingredientsPane;
    private final Button newRecipeButton;
    private final Button retryButton;
    private final Label feedback;
    private final java.util.function.Supplier<CatalogDraftState.RecipeDraft> recipeDraft;
    private final java.util.function.Supplier<CatalogDraftState.IngredientDraft> ingredientDraft;
    private boolean loading;
    private Runnable retryRead;

    CatalogOperations(VBox workspace, HBox recipesPane, VBox ingredientsPane,
            Button newRecipeButton, Button retryButton, Label feedback,
            java.util.function.Supplier<CatalogDraftState.RecipeDraft> recipeDraft,
            java.util.function.Supplier<CatalogDraftState.IngredientDraft> ingredientDraft) {
        this.workspace = workspace;
        this.recipesPane = recipesPane;
        this.ingredientsPane = ingredientsPane;
        this.newRecipeButton = newRecipeButton;
        this.retryButton = retryButton;
        this.feedback = feedback;
        this.recipeDraft = recipeDraft;
        this.ingredientDraft = ingredientDraft;
    }

    boolean isLoading() { return loading; }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    void retryRead() {
        if (!loading && retryRead != null) retryRead.run();
    }

    void focus(Node target) {
        javafx.application.Platform.runLater(() -> {
            if (workspace.getScene() == null || target == null || target.isDisabled()) return;
            for (Node node = target; node != null; node = node.getParent()) if (!node.isVisible()) return;
            target.requestFocus();
        });
    }

    void setFeedback(String message, FeedbackType type) {
        feedback.getStyleClass().removeAll("course-feedback-info", "course-feedback-success", "course-feedback-error");
        feedback.getStyleClass().add("course-feedback-" + type.name().toLowerCase(Locale.ROOT));
        feedback.setText(message);
    }

    private boolean isCatalogNode(Node target) {
        for (Node node = target; node != null; node = node.getParent()) if (node == workspace) return true;
        return false;
    }

    <T> void run(String loadingMessage, String failureMessage, boolean readOnly, Callable<T> action, Consumer<T> onSuccess) {
        if (loading) return;
        var previousFocus = workspace.getScene() == null ? null : workspace.getScene().getFocusOwner();
        loading = true;
        retryRead = null;
        show(retryButton, false);

        recipesPane.setDisable(true);
        ingredientsPane.setDisable(true);
        newRecipeButton.setDisable(true);
        setFeedback(loadingMessage, FeedbackType.INFO);
        FxAsyncExecutor.run("catalog-management", action, value -> {
            loading = false;
            recipesPane.setDisable(false);
            ingredientsPane.setDisable(false);
            newRecipeButton.setDisable(false);
            onSuccess.accept(value);
            if (!loading && isCatalogNode(previousFocus)) focus(previousFocus);
        }, error -> {
            loading = false;
            recipesPane.setDisable(false);
            ingredientsPane.setDisable(false);
            newRecipeButton.setDisable(false);
            setFeedback(failureMessage, FeedbackType.ERROR);
            if (readOnly) {
                var failedRecipe = recipeDraft.get();
                var failedIngredient = ingredientDraft.get();
                retryRead = () -> {
                    if (!failedRecipe.equals(recipeDraft.get()) || !failedIngredient.equals(ingredientDraft.get())) {
                        retryRead = null;
                        show(retryButton, false);
                        setFeedback("La bozza è cambiata. Salva o annulla le modifiche prima di aggiornare il catalogo.", FeedbackType.INFO);
                        return;
                    }
                    run(loadingMessage, failureMessage, true, action, onSuccess);
                };
                show(retryButton, true);
                focus(retryButton);
            } else if (isCatalogNode(previousFocus)) focus(previousFocus);
        });
    }

}
