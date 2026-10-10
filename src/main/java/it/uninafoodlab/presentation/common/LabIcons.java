package it.uninafoodlab.presentation.common;

import java.util.Objects;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public final class LabIcons {
    private LabIcons() {}

    public static ImageView back(double size) {
        var view = icon("chevron-right", "dark", size);
        view.setRotate(180);
        return view;
    }

    public static ImageView icon(String name, String tone, double size) {
        var resource = Objects.requireNonNull(LabIcons.class.getResource(
                "/it/uninafoodlab/presentation/assets/feather/" + name + "-" + tone + ".png"));
        var view = new ImageView(new Image(resource.toExternalForm()));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setMouseTransparent(true);
        return view;
    }
}
