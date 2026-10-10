package it.uninafoodlab.presentation.catalog;

import java.text.BreakIterator;
import java.util.Locale;


final class CatalogRecipeText {
    private static final int PREVIEW_LIMIT = 240;
    private CatalogRecipeText() {}

    static String preview(String description) {
        String firstParagraph = readingText(description).strip().split("\\R", 2)[0]
                .replaceFirst("^\\d{1,2}[.)]\\s+", "").replaceAll("\\s+", " ");
        if (firstParagraph.isEmpty()) return "";
        var sentences = BreakIterator.getSentenceInstance(Locale.ITALIAN);
        sentences.setText(firstParagraph);
        int end = sentences.next();
        String sentence = firstParagraph.substring(0, end == BreakIterator.DONE ? firstParagraph.length() : end).strip();
        if (sentence.length() <= PREVIEW_LIMIT) return sentence;
        int boundary = sentence.lastIndexOf(' ', PREVIEW_LIMIT - 1);
        return sentence.substring(0, boundary > 0 ? boundary : PREVIEW_LIMIT - 1).stripTrailing() + "…";
    }

    static String readingText(String description) {
        return it.uninafoodlab.presentation.common.RecipePreparationText.readingText(description);
    }
}
