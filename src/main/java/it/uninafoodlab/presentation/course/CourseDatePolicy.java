package it.uninafoodlab.presentation.course;

import java.time.LocalDate;
import java.util.Objects;

final class CourseDatePolicy {

    private CourseDatePolicy() {}

    static LocalDate requireNotPast(LocalDate candidate, LocalDate today) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(today, "today");
        if (candidate.isBefore(today)) {
            throw new IllegalArgumentException("La data non può precedere oggi.");
        }
        return candidate;
    }
}
