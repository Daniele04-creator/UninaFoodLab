package it.uninafoodlab.presentation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Parent;


public final class FxSnapshot {
    private FxSnapshot() {}

    public static void save(Parent root, String path) throws IOException {
        root.applyCss();
        root.layout();
        var image = root.snapshot(null, null);
        var png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var reader = image.getPixelReader();
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, reader.getArgb(x, y));
        }
        var destination = Path.of(path);
        Files.createDirectories(destination.toAbsolutePath().getParent());
        if (!javax.imageio.ImageIO.write(png, "png", destination.toFile())) {
            throw new IOException("Writer PNG non disponibile.");
        }
    }
}
