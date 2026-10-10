package it.uninafoodlab.presentation.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

class ContentPaneTest {
    @Test
    void fillsTheViewportWithoutApplyingAnAdditionalDisplayScale() {
        var content = new Pane();
        var host = new ContentPane(content);

        host.resize(1488, 900);
        host.layout();

        assertThat(content.getWidth()).isEqualTo(1488);
        assertThat(content.getHeight()).isEqualTo(900);
        assertThat(content.getLayoutX()).isZero();
        assertThat(content.getLayoutY()).isZero();
        assertThat(content.getScaleX()).isEqualTo(1);
        assertThat(content.getScaleY()).isEqualTo(1);
    }

    @Test
    void resizesTheContentWhenTheViewportChanges() {
        var content = new Pane();
        var host = new ContentPane(content);
        host.resize(1488, 900);
        host.layout();

        host.resize(1000, 760);
        host.layout();

        assertThat(content.getWidth()).isEqualTo(1000);
        assertThat(content.getHeight()).isEqualTo(760);
        assertThat(content.isManaged()).isFalse();
    }
}
