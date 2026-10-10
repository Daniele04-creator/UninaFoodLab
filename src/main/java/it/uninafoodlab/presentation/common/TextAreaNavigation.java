package it.uninafoodlab.presentation.common;

import javafx.scene.TraversalDirection;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class TextAreaNavigation {
    private TextAreaNavigation() {}

    public static void install(TextArea field) {
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.TAB || event.isControlDown()
                    || event.isAltDown() || event.isMetaDown()) return;
            field.requestFocusTraversal(event.isShiftDown() ? TraversalDirection.PREVIOUS : TraversalDirection.NEXT);
            event.consume();
        });
    }
}
