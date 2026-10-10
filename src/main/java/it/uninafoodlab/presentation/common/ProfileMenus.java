package it.uninafoodlab.presentation.common;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import java.util.List;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class ProfileMenus {
    private ProfileMenus() {}

    public static void show(Button anchor, AuthenticatedUser user, Runnable password, Runnable logout) {
        var menu = new ContextMenu();
        menu.getItems().setAll(items(user, menu::hide, password, logout));
        menu.setAutoHide(true);
        menu.setHideOnEscape(true);
        menu.show(anchor, Side.BOTTOM, anchor.getWidth() - 224, 8);
        apply(menu);
        menu.requestFocus();
    }

    public static void configure(MenuButton anchor, AuthenticatedUser user, Runnable password, Runnable logout) {
        anchor.getItems().setAll(items(user, anchor::hide, password, logout));
        anchor.setOnShown(event -> Window.getWindows().stream()
                .filter(ContextMenu.class::isInstance).map(ContextMenu.class::cast)
                .filter(menu -> menu.getOwnerNode() == anchor).forEach(menu -> {
                    apply(menu);
                    var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
                    if (bounds != null) menu.setX(bounds.getMaxX() - menu.getWidth());
                }));
    }

    private static List<MenuItem> items(AuthenticatedUser user, Runnable hide, Runnable password, Runnable logout) {
        var details = new Label(user.username().value());
        details.getStyleClass().add("profile-menu-details");
        details.setWrapText(true);
        details.setMaxWidth(184);
        var identity = new VBox(details);
        identity.setPrefWidth(184);
        identity.getStyleClass().add("profile-menu-identity");
        var header = new CustomMenuItem(identity, false);
        header.getStyleClass().add("profile-menu-header");
        header.setDisable(true);
        var change = new MenuItem("Cambia password");
        var exit = new MenuItem("Esci dall’account");
        change.setOnAction(event -> { hide.run(); password.run(); });
        exit.setOnAction(event -> { hide.run(); logout.run(); });
        return List.of(header, change, exit);
    }

    private static void apply(ContextMenu menu) {
        if (!menu.getStyleClass().contains("profile-menu")) menu.getStyleClass().add("profile-menu");
        if (menu.getSkin() != null && !menu.getSkin().getNode().getStyleClass().contains("profile-menu")) {
            menu.getSkin().getNode().getStyleClass().add("profile-menu");
        }
        var stylesheet = ProfileMenus.class.getResource("/it/uninafoodlab/presentation/shell/profile.css").toExternalForm();
        if (!menu.getScene().getStylesheets().contains(stylesheet)) menu.getScene().getStylesheets().add(stylesheet);
        menu.getScene().getRoot().applyCss();
        menu.getScene().getRoot().layout();
    }
}
