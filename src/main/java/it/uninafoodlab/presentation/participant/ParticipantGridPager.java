package it.uninafoodlab.presentation.participant;

import static it.uninafoodlab.presentation.participant.ParticipantCourseCards.label;

import it.uninafoodlab.presentation.common.ResultPager;
import java.util.function.IntConsumer;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class ParticipantGridPager {
    private final VBox before = new VBox();
    private final VBox after = new VBox();
    private final Label count = label("", "participant-card-meta");
    private final ResultPager trailing;
    private int page;

    ParticipantGridPager(VBox panel, Node grid, ObservableBooleanValue busy, IntConsumer loadPage) {
        trailing = new ResultPager(requested -> {
            page = requested;
            loadPage.accept(requested);
        });
        trailing.disableProperty().bind(busy);
        before.getChildren().add(count);
        after.getChildren().add(trailing);
        int index = panel.getChildren().indexOf(grid);
        panel.getChildren().add(index, before);
        panel.getChildren().add(index + 2, after);
    }

    int page() { return page; }
    void reset() { page = 0; }

    void update(int index, int total, String name) {
        page = ResultPager.clamp(index, total);
        trailing.update(page, total, "corsi");
        trailing.setAccessibleText(name + ": paginazione");
        count.setText(total + (total == 1 ? " corso" : " corsi"));
    }

    void show(boolean visible) {
        before.setVisible(visible); before.setManaged(visible);
        after.setVisible(visible); after.setManaged(visible);
    }
}
