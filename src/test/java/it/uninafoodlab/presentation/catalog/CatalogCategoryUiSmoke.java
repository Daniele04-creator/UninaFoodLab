package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.*;
import it.uninafoodlab.domain.identity.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;


public final class CatalogCategoryUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var writes = new AtomicInteger();
            var category = new CategorySummary(UUID.randomUUID(), "Pasticceria", "Dolci");
            var repository = (CatalogRepository) Proxy.newProxyInstance(CatalogCategoryUiSmoke.class.getClassLoader(),
                    new Class<?>[]{CatalogRepository.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("createCategory")) {
                            check(!Platform.isFxApplicationThread(), "Category persistence must run outside the FX thread");
                            if (writes.incrementAndGet() == 1) throw new IllegalStateException("jdbc:private-example");
                            return category;
                        }
                        return method.getName().equals("categories") ? List.of()
                                : CatalogBrowseResponses.recipes(method, arguments, List.of());
                    });
            var manage = new ManageRecipeCatalog((RecipeCatalogManagementRepository) Proxy.newProxyInstance(
                    CatalogCategoryUiSmoke.class.getClassLoader(), new Class<?>[]{RecipeCatalogManagementRepository.class},
                    (proxy, method, arguments) -> List.of()));
            var actor = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Username.of("setup.chef"), "Chef sintetico", Role.CHEF);
            var controller = new CatalogWorkspaceController(actor, new BrowseCatalog(repository), manage);
            var root = fx(() -> {
                var loader = new FXMLLoader(CatalogCategoryUiSmoke.class.getResource("catalog-workspace.fxml"));
                loader.setController(controller);
                var view = (VBox) loader.load();
                var scene = new Scene(view, 1120, 760);
                scene.getStylesheets().add(Objects.requireNonNull(CatalogCategoryUiSmoke.class.getResource(
                        "/it/uninafoodlab/presentation/styles/application.css")).toExternalForm());
                var stage = new Stage(); stage.setScene(scene); stage.show();
                return view;
            });
            await(() -> !controller.isLoading());
            fx(() -> {
                check(root.lookup("#newCategoryButton") instanceof Button, "Recipe editor must offer category creation on an empty catalog");
                controller.getClass().getMethod("setCreateCategory", CreateCategory.class).invoke(controller, new CreateCategory(repository));
                ((Button) root.lookup("#newRecipeButton")).fire();
                ((TextField) root.lookup("#recipeName")).setText("Bozza da conservare");
                ((TextArea) root.lookup("#recipeDescription")).setText("Preparazione ancora in corso");
                return null;
            });
            Platform.runLater(() -> ((Button) root.lookup("#newCategoryButton")).fire());
            await(() -> dialog() != null);
            fx(() -> {
                ((TextField) dialog().lookup("#newCategoryName")).setText("Pasticceria");
                ((TextArea) dialog().lookup("#newCategoryDescription")).setText("Dolci");
                ((Button) dialog().lookup("#createCategorySubmit")).fire();
                return null;
            });
            await(() -> dialog() != null && ((Label) dialog().lookup("#categoryCreateError")).isVisible());
            fx(() -> {
                var error = (Label) dialog().lookup("#categoryCreateError");
                check(!error.getText().contains("jdbc:"), "Failure must not expose technical details");
                it.uninafoodlab.presentation.FxSnapshot.save(dialog(), "target/first-run/category-dialog.png");
                check(((TextField) dialog().lookup("#newCategoryName")).getText().equals("Pasticceria"), "Retry keeps category input");
                ((Button) dialog().lookup("#createCategorySubmit")).fire();
                return null;
            });
            await(() -> dialog() == null);
            fx(() -> {
                check(((TextField) root.lookup("#recipeName")).getText().equals("Bozza da conservare"), "Category creation must preserve recipe name");
                check(((TextArea) root.lookup("#recipeDescription")).getText().equals("Preparazione ancora in corso"), "Category creation must preserve preparation");
                check(((ComboBox<?>) root.lookup("#categoryChoice")).getValue().equals(category), "Created category must be selected");
                it.uninafoodlab.presentation.FxSnapshot.save(root, "target/first-run/recipe-category.png");
                return null;
            });
            System.out.println("PASS CatalogCategoryUiSmoke: empty catalog, async retry, category selection and draft preservation");
        } finally { Platform.exit(); }
    }

    private static DialogPane dialog() {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(Objects::nonNull).map(Scene::getRoot).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).filter(pane -> pane.lookup("#newCategoryName") != null).findFirst().orElse(null);
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(condition)) { if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for JavaFX state"); Thread.sleep(30); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
