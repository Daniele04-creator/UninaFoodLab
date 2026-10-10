package it.uninafoodlab.presentation.common;

import java.util.regex.Pattern;

public final class RecipePreparationText {
    private static final Pattern STEP = Pattern.compile("(?<!\\S)(\\d{1,2})[.)]\\s+");
    private RecipePreparationText() {}

    public static String readingText(String description) {
        var steps = STEP.matcher(description).results()
                .dropWhile(step -> !step.group(1).equals("1")).toList();
        if (steps.size() < 2) return description;
        for (int i = 0; i < steps.size(); i++) {
            if (Integer.parseInt(steps.get(i).group(1)) != i + 1) return description;
        }
        var result = new StringBuilder();
        int cursor = 0;
        for (var step : steps) {
            int whitespace = step.start();
            while (whitespace > cursor && Character.isWhitespace(description.charAt(whitespace - 1))) whitespace--;
            result.append(description, cursor, whitespace);
            if (!result.isEmpty()) result.append("\n\n");
            cursor = step.start();
        }
        return result.append(description, cursor, description.length()).toString();
    }
}
