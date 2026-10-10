package it.uninafoodlab.application.planning;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;

public record MonthlyReport(
        YearMonth month,
        long totalCourses,
        long onlineSessions,
        long practicalSessions,
        Optional<BigDecimal> averageRecipes,
        Optional<Integer> minimumRecipes,
        Optional<Integer> maximumRecipes) {

    public MonthlyReport {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(averageRecipes, "averageRecipes");
        Objects.requireNonNull(minimumRecipes, "minimumRecipes");
        Objects.requireNonNull(maximumRecipes, "maximumRecipes");
        if (totalCourses < 0 || onlineSessions < 0 || practicalSessions < 0) {
            throw new IllegalArgumentException("Monthly report counts must not be negative");
        }
        var completeRecipeStatistics = averageRecipes.isPresent()
                && minimumRecipes.isPresent() && maximumRecipes.isPresent();
        var absentRecipeStatistics = averageRecipes.isEmpty()
                && minimumRecipes.isEmpty() && maximumRecipes.isEmpty();
        if (!completeRecipeStatistics && !absentRecipeStatistics) {
            throw new IllegalArgumentException("Recipe statistics must be all present or all absent");
        }
    }
}
