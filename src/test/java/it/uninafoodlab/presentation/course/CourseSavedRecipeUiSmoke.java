package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.planning.SessionLifecycleRepository.SavedRecipe;
import it.uninafoodlab.presentation.FxAwait;
import it.uninafoodlab.presentation.FxSnapshot;
import it.uninafoodlab.presentation.common.DialogTheme;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.stage.Window;

public final class CourseSavedRecipeUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var saved = new SavedRecipe(UUID.randomUUID(), "Pane alle erbe",
                    "Pane rustico. 1. Mescola con 0.125 g di sale. 2. Lascia riposare. 3. Cuoci.",
                    "Farina della copia: 100.000 g\nSale storico: 0.125 cucchiaini");
            Platform.runLater(() -> new CourseRecipeDetails(null, null, dialog -> DialogTheme.apply(dialog, null)).showSaved(saved));
            FxAwait.until(() -> Window.getWindows().stream().anyMatch(Window::isShowing), 10);
            var check = new FutureTask<Void>(() -> {
                var window = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElseThrow();
                var pane = (DialogPane) window.getScene().lookup(".dialog-pane");
                var texts = pane.lookupAll(".label").stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).map(Label::getText).toList();
                if (!texts.containsAll(List.of(saved.name(), "Farina della copia: 100.000 g",
                        "Sale storico: 0.125 cucchiaini", "Pane rustico.\n\n1. Mescola con 0.125 g di sale.\n\n2. Lascia riposare.\n\n3. Cuoci."))) {
                    throw new AssertionError("Chef must show the actual saved title, quantities and complete separated preparation");
                }
                var ingredients = pane.lookup(".recipe-dialog-ingredients");
                var preparation = pane.lookup(".recipe-dialog-preparation");
                if (ingredients.localToScene(ingredients.getBoundsInLocal()).getMinY()
                        >= preparation.localToScene(preparation.getBoundsInLocal()).getMinY()) {
                    throw new AssertionError("Saved ingredient list must precede preparation");
                }
                FxSnapshot.save(pane, "target/ui-polish/chef-saved-recipe.png");
                ((Button) pane.lookupButton(pane.getButtonTypes().getFirst())).fire();
                if (window.isShowing()) throw new AssertionError("Saved recipe dialog must close");
                return null;
            });
            Platform.runLater(check);
            check.get(10, TimeUnit.SECONDS);
            System.out.println("PASS: Chef reads saved recipe title, doses and spaced preparation without catalog access.");
        } finally { Platform.exit(); }
    }
}
