package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.application.catalog.IngredientSummary;
import java.util.List;
import java.util.UUID;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;


final class CatalogIngredientEditor {
    private final VBox ingredientEditor;
    private final Label ingredientEditorTitle;
    private final Button newIngredientButton;
    private final Button cancelIngredientButton;
    private final TextField ingredientName;
    private final Label ingredientNameReadOnly;
    private final ComboBox<MeasurementUnit> ingredientUnit;
    private final Label ingredientUnitReadOnly;
    private final Button saveIngredientButton;
    private final Label ingredientState;
    private final ComboBox<IngredientSummary> ingredientChoice;
    private boolean ingredientLocked;

    CatalogIngredientEditor(
            VBox ingredientEditor,
            Label ingredientEditorTitle,
            Button newIngredientButton,
            Button cancelIngredientButton,
            TextField ingredientName,
            Label ingredientNameReadOnly,
            ComboBox<MeasurementUnit> ingredientUnit,
            Label ingredientUnitReadOnly,
            Button saveIngredientButton,
            Label ingredientState,
            ComboBox<IngredientSummary> ingredientChoice) {
        this.ingredientEditor = ingredientEditor;
        this.ingredientEditorTitle = ingredientEditorTitle;
        this.newIngredientButton = newIngredientButton;
        this.cancelIngredientButton = cancelIngredientButton;
        this.ingredientName = ingredientName;
        this.ingredientNameReadOnly = ingredientNameReadOnly;
        this.ingredientUnit = ingredientUnit;
        this.ingredientUnitReadOnly = ingredientUnitReadOnly;
        this.saveIngredientButton = saveIngredientButton;
        this.ingredientState = ingredientState;
        this.ingredientChoice = ingredientChoice;
    }

    boolean isLocked() { return ingredientLocked; }

    void refreshChoices(List<IngredientSummary> ingredients, UUID selectedId) {
        ingredientChoice.getItems().setAll(ingredients);
        selectIngredient(selectedId);
    }

    void selectIngredient(UUID id) {
        ingredientChoice.setValue(ingredientChoice.getItems().stream()
                .filter(ingredient -> ingredient.id().equals(id)).findFirst().orElse(null));
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    void protection(boolean locked, boolean unitLocked, boolean creating) {
        ingredientLocked = locked;
        show(ingredientEditor, true);
        ingredientEditorTitle.setText(creating ? "Nuovo ingrediente" : "Dettagli ingrediente");
        show(newIngredientButton, !creating);
        show(cancelIngredientButton, true);
        cancelIngredientButton.setText(locked ? "Chiudi" : "Annulla");
        ingredientName.setEditable(!locked);
        show(ingredientName, !locked);
        show(ingredientNameReadOnly, locked);
        show(ingredientUnit, !unitLocked);
        show(ingredientUnitReadOnly, unitLocked);
        show(saveIngredientButton, !locked);
        String state;
        if (locked) {
            state = "Già utilizzato. Nome e unità sono protetti per conservare lo storico. Usa Nuovo ingrediente per crearne uno diverso.";
        } else if (unitLocked) {
            state = "Puoi correggere il nome. L’unità è protetta perché usata in ricette o sessioni; le copie già salvate conservano nome e dosi originali.";
        } else {
            state = "";
        }
        ingredientState.setText(state);
        show(ingredientState, !state.isEmpty());
    }

}
