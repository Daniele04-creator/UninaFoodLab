package it.uninafoodlab.presentation.common;

import java.util.List;
import java.util.Objects;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.geometry.Rectangle2D;

public final class LabBrand {
    private static final String RESOURCE = "/it/uninafoodlab/presentation/assets/brand/uninafoodlab-logo-orange.png";
    private static final Image LOGO = new Image(Objects.requireNonNull(
            LabBrand.class.getResource(RESOURCE), "Missing app logo").toExternalForm());
    private static final Rectangle2D WORDMARK = new Rectangle2D(120, 200, 1900, 290);
    private static final Rectangle2D SYMBOL = new Rectangle2D(120, 200, 370, 290);

    private LabBrand() {}

    public static ImageView mark(double size) {
        var view = new ImageView(LOGO);
        view.setViewport(SYMBOL);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setMouseTransparent(true);
        return view;
    }

    public static List<Image> windowIcons() {
        var icon = new WritableImage(370, 370);
        icon.getPixelWriter().setPixels(0, 40, 370, 290, LOGO.getPixelReader(), 120, 200);
        return List.of(icon);
    }

    public static ImageView logo(double width) {
        var view = new ImageView(LOGO);
        view.setViewport(WORDMARK);
        view.setFitWidth(width);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setMouseTransparent(true);
        return view;
    }
}
