package it.uninafoodlab.presentation.course;

final class CourseInputPolicy {
    private CourseInputPolicy() {}

    static boolean acceptsIntegerEdit(String text) {
        return text != null && text.matches("\\d*");
    }

    static boolean acceptsInteger(String text, int min, int max) {
        if (text == null || text.isEmpty()) return false;
        if (!text.matches("\\d+")) return false;
        try {
            int value = Integer.parseInt(text);
            return value >= min && value <= max;
        } catch (NumberFormatException _) {
            return false;
        }
    }
}
