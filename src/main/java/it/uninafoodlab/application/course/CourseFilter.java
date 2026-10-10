package it.uninafoodlab.application.course;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CourseFilter(Optional<UUID> categoryId, String search, CoursePeriod period, LocalDateTime now) {

    public CourseFilter {
        Objects.requireNonNull(categoryId, "categoryId");
        search = Objects.requireNonNull(search, "search").strip();
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(now, "now");
    }
}
