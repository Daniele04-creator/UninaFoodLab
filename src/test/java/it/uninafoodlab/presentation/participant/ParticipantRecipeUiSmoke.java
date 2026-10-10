package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.presentation.FxAwait;
import it.uninafoodlab.presentation.FxSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.stage.Window;


public final class ParticipantRecipeUiSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            var recipe = new PublishedSessionRecipeSummary(UUID.randomUUID(), UUID.randomUUID(),
                    "Pasta fresca", "1. Lavora l'impasto. 2. Lascia riposare.", 1, BigDecimal.ONE,
                    List.of(new PublishedSessionRecipeSummary.IngredientDose("Farina", new BigDecimal("100"), MeasurementUnit.GRAM),
                            new PublishedSessionRecipeSummary.IngredientDose("Sale", new BigDecimal("0.125"), MeasurementUnit.TEASPOON)));
            Platform.runLater(() -> ParticipantRecipeDialog.show(null, recipe));
            FxAwait.until(() -> Window.getWindows().stream().anyMatch(Window::isShowing), 10);
            var result = new java.util.concurrent.FutureTask<Void>(() -> {
                var window = Window.getWindows().stream().filter(Window::isShowing).findFirst().orElseThrow();
                var pane = (DialogPane)window.getScene().lookup(".dialog-pane");
                var texts = pane.lookupAll(".label").stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).map(Label::getText).toList();
                if (!texts.containsAll(List.of("Farina", "100 g", "Sale", "0,125 cucchiaini",
                        "1. Lavora l'impasto.\n\n2. Lascia riposare.")))
                    throw new AssertionError("Snapshot names, precision, units and description must be visible");
                var ingredients = pane.lookup(".recipe-dialog-ingredients");
                var preparation = pane.lookup(".recipe-dialog-preparation");
                if (ingredients == null || preparation == null
                        || ingredients.localToScene(ingredients.getBoundsInLocal()).getMinY()
                        >= preparation.localToScene(preparation.getBoundsInLocal()).getMinY())
                    throw new AssertionError("Snapshot ingredients must appear before the spaced preparation");
                FxSnapshot.save(pane, "target/ui-polish/participant-recipe.png");
                ((Button)pane.lookupButton(ButtonType.CLOSE)).fire();
                if (window.isShowing()) throw new AssertionError("Recipe dialog must close");
                return null;
            });
            Platform.runLater(result);result.get(10, java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("PASS: Participant recipe dialog preserves snapshot ingredient doses and closes.");
        } finally { Platform.exit(); }
    }
}
