package it.uninafoodlab.presentation.planning;

import java.util.Objects;
import java.util.Optional;

record PlanningFilterChoice<T>(Optional<T> value, String label) {

    PlanningFilterChoice {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }

    static <T> PlanningFilterChoice<T> none() {
        return new PlanningFilterChoice<>(Optional.empty(), "Scegli un corso");
    }

    static <T> PlanningFilterChoice<T> all(String label) {
        return new PlanningFilterChoice<>(Optional.empty(), label);
    }

    static <T> PlanningFilterChoice<T> of(T value, String label) {
        return new PlanningFilterChoice<>(Optional.of(Objects.requireNonNull(value, "value")), label);
    }
}
