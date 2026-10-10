package it.uninafoodlab.presentation.course;

import java.util.function.Consumer;
import javafx.css.PseudoClass;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

final class NumericSpinner {

    private NumericSpinner() {}

    static void configure(Spinner<Integer> spinner, String validationMessage, Consumer<String> onInvalid) {
        var previous = (SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
        var factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                previous.getMin(), previous.getMax(), previous.getValue(), previous.getAmountToStepBy()) {
            @Override public void increment(int steps) {
                if (isValid(spinner)) super.increment(steps);
            }
            @Override public void decrement(int steps) {
                if (isValid(spinner)) super.decrement(steps);
            }
        };
        factory.setWrapAround(previous.isWrapAround());
        factory.setConverter(new StringConverter<>() {
            @Override public String toString(Integer value) { return value.toString(); }
            @Override public Integer fromString(String text) {
                if (CourseInputPolicy.acceptsInteger(text, factory.getMin(), factory.getMax())) {
                    return Integer.valueOf(text);
                }
                markInvalid(spinner, validationMessage);
                onInvalid.accept(validationMessage);
                return factory.getValue();
            }
        });
        spinner.setValueFactory(factory);
        spinner.getEditor().setTextFormatter(new TextFormatter<String>(change ->
                CourseInputPolicy.acceptsIntegerEdit(change.getControlNewText()) ? change : null));
        spinner.getEditor().textProperty().addListener((observable, before, text) -> {
            if (CourseInputPolicy.acceptsInteger(text, factory.getMin(), factory.getMax())) {
                markInvalid(spinner, null);
                factory.setValue(Integer.valueOf(text));
            }
        });
        spinner.setEditable(true);
    }

    static int requireValue(Spinner<Integer> spinner, String validationMessage) {
        if (!isValid(spinner)) {
            markInvalid(spinner, validationMessage);
            throw new IllegalArgumentException(validationMessage);
        }
        int value = Integer.parseInt(spinner.getEditor().getText());
        spinner.getValueFactory().setValue(value);
        return value;
    }

    private static boolean isValid(Spinner<Integer> spinner) {
        var factory = (SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
        return CourseInputPolicy.acceptsInteger(spinner.getEditor().getText(), factory.getMin(), factory.getMax());
    }

    private static void markInvalid(Spinner<Integer> spinner, String message) {
        spinner.pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"), message != null);
        spinner.getEditor().setAccessibleHelp(message);
        spinner.setTooltip(message == null ? null : new Tooltip(message));
    }

    static void configure(Spinner<Integer> spinner) {
        var factory = (SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
        var converter = new StringConverter<Integer>() {
            @Override
            public String toString(Integer value) {
                return value.toString();
            }

            @Override
            public Integer fromString(String value) {
                return value == null || value.isBlank() ? factory.getValue() : Integer.valueOf(value);
            }
        };
        factory.setConverter(converter);
        var formatter = new javafx.scene.control.TextFormatter<>(converter, factory.getValue(), change ->
                change.getControlNewText().isEmpty()
                        || CourseInputPolicy.acceptsInteger(change.getControlNewText(), factory.getMin(), factory.getMax())
                        ? change : null);
        spinner.getEditor().setTextFormatter(formatter);
        formatter.valueProperty().bindBidirectional(factory.valueProperty());
        spinner.setEditable(true);
    }

}
