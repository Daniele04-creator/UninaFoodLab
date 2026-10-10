package it.uninafoodlab.presentation.notification;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class NotificationDetailNavigation {
    private final VBox archive;
    private final VBox detail;
    private final Label heading;
    private final Button back;
    private final Button compose;
    private final Runnable restoreFocus;

    NotificationDetailNavigation(VBox archive, VBox detail, Label heading,
            Button back, Button compose, Runnable restoreFocus) {
        this.archive = archive;
        this.detail = detail;
        this.heading = heading;
        this.back = back;
        this.compose = compose;
        this.restoreFocus = restoreFocus;
        detail.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    }

    boolean isOpen() { return detail.isVisible(); }

    void open() {
        setVisible(archive, false);
        setVisible(detail, true);
        heading.setText("Dettaglio comunicazione");
        setVisible(heading, true);
        back.setText("Torna alle comunicazioni");
        back.setAccessibleText("Torna alle comunicazioni con i filtri precedenti");
        setVisible(back, true);
        setVisible(compose, false);
        back.requestFocus();
    }

    void close() {
        setVisible(detail, false);
        setVisible(archive, true);
        setVisible(back, false);
        setVisible(compose, true);
        heading.setText("Comunicazioni");
        setVisible(heading, true);
        restoreFocus.run();
    }

    private static void setVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
