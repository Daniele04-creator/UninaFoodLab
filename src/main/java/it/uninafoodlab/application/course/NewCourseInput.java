package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.SessionDetails;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record NewCourseInput(
        UUID categoryId,
        String title,
        String description,
        LocalDate startDate,
        LocalTime startTime,
        int recurrenceDays,
        BigDecimal ingredientMarginPercent,
        List<SessionDetails> sessionDetails) {

    public NewCourseInput {
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(ingredientMarginPercent, "ingredientMarginPercent");
        sessionDetails = List.copyOf(Objects.requireNonNull(sessionDetails, "sessionDetails"));
    }
}
