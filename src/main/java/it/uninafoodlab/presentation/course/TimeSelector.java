package it.uninafoodlab.presentation.course;

import java.time.LocalTime;
import java.util.stream.IntStream;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

public final class TimeSelector extends HBox {
    private final ComboBox<Integer> hours = choice(24, "Ore");
    private final ComboBox<Integer> minutes = choice(60, "Minuti");
    private final ObjectProperty<LocalTime> value = new SimpleObjectProperty<>(LocalTime.of(18, 30));
    private boolean synchronizing;

    public TimeSelector() {
        setSpacing(4);
        setAlignment(Pos.CENTER_LEFT);
        getChildren().setAll(hours, new Label(":"), minutes);
        hours.setValue(18);
        minutes.setValue(30);
        hours.valueProperty().addListener((observable, previous, current) -> updateTime());
        minutes.valueProperty().addListener((observable, previous, current) -> updateTime());
        value.addListener((observable, previous, current) -> {
            synchronizing = true;
            try {
                hours.setValue(current.getHour());
                minutes.setValue(current.getMinute());
            } finally {
                synchronizing = false;
            }
        });
    }

    private void updateTime() {
        if (!synchronizing && hours.getValue() != null && minutes.getValue() != null) {
            value.set(LocalTime.of(hours.getValue(), minutes.getValue()));
        }
    }

    public LocalTime getValue() { return value.get(); }
    public void setValue(LocalTime time) { value.set(java.util.Objects.requireNonNull(time)); }
    public ObjectProperty<LocalTime> valueProperty() { return value; }

    private static ComboBox<Integer> choice(int count, String label) {
        var field = new ComboBox<Integer>();
        field.getItems().setAll(IntStream.range(0, count).boxed().toList());
        field.setEditable(false);
        field.setAccessibleText(label);
        field.setMinWidth(84);
        field.setPrefWidth(84);
        field.getStyleClass().add("course-combo");
        field.setConverter(new StringConverter<>() {
            public String toString(Integer number) { return number == null ? "" : "%02d".formatted(number); }
            public Integer fromString(String text) { throw new UnsupportedOperationException("Selection only"); }
        });
        return field;
    }
}
