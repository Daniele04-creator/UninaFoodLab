package it.uninafoodlab.application.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SessionPreparation(UUID sessionId, int confirmedParticipants,
        int pendingParticipants, int declinedParticipants,
        BigDecimal ingredientMargin, List<RecipePreparation> recipes,
        List<IngredientRequirement> totals) {
    public SessionPreparation(UUID sessionId, int confirmedParticipants,
            BigDecimal ingredientMargin, List<RecipePreparation> recipes,
            List<IngredientRequirement> totals) {
        this(sessionId, confirmedParticipants, 0, 0, ingredientMargin, recipes, totals);
    }

    public SessionPreparation {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(ingredientMargin, "ingredientMargin");
        recipes = List.copyOf(recipes);
        totals = List.copyOf(totals);
        if (confirmedParticipants < 0 || pendingParticipants < 0 || declinedParticipants < 0
                || ingredientMargin.signum() < 0) {
            throw new IllegalArgumentException("Session preparation is invalid");
        }
    }
}
