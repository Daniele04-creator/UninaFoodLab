package it.uninafoodlab.infrastructure.demo;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

final class EnrichmentFixture {
    static final int[] COURSE_INDICES = {2};
    static final UUID CHEF = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID CAMPANIA = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID BAKING = UUID.fromString("c1000000-0000-4000-8000-000000000003");
    static final UUID VEGETARIAN = UUID.fromString("c1000000-0000-4000-8000-000000000004");
    static final String[] TITLES = {
        "Cucina di stagione: verdure, cereali e legumi in tre incontri",
        "Dolci da forno per la colazione: impasti, consistenze e conservazione senza sprechi",
        "Il pranzo della domenica",
        "Tecniche di base per la cucina di mare",
        "Piccoli piatti vegetariani da condividere",
        "Pasticceria di casa: creme e frolle",
        "Sapori campani tra tradizione e innovazione",
        "Laboratorio di impasti rinviato"
    };
    static final UUID[] CATEGORIES = {
        VEGETARIAN, BAKING, CAMPANIA, UUID.fromString("c1000000-0000-4000-8000-000000000005"),
        VEGETARIAN, BAKING, CAMPANIA, UUID.fromString("c1000000-0000-4000-8000-000000000002")
    };
    static final UUID[] RECIPES = {
        id("recipe-farro"), id("recipe-biscotti"),
        UUID.fromString("50000000-0000-0000-0000-000000000002"),
        UUID.fromString("50000000-0000-0000-0000-000000000001"),
        UUID.fromString("b1000000-0000-4000-8000-000000000003"),
        UUID.fromString("b1000000-0000-4000-8000-000000000006")
    };

    private EnrichmentFixture() {}

    static LocalDate start(LocalDate reference, int index) {
        return switch (index) {
            case 0, 1 -> reference.plusDays(1);
            case 2, 3 -> reference.plusDays(2);
            case 4, 5, 6 -> reference.minusDays(17 - (index - 4));
            default -> reference.plusDays(6);
        };
    }

    static int preferredHour(int index) {
        return switch (index) {
            case 0, 2, 4 -> 9;
            case 1, 3 -> 14;
            case 5 -> 12;
            case 6 -> 15;
            default -> 11;
        };
    }

    static UUID id(String part) {
        return UUID.nameUUIDFromBytes(("uninafoodlab:presentation-enrichment:v1:" + part)
                .getBytes(StandardCharsets.UTF_8));
    }
}
