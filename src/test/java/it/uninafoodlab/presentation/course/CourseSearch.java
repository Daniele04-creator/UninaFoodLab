package it.uninafoodlab.presentation.course;

final class CourseSearch {
    private CourseSearch() { }

    static boolean matches(String query, String... labels) {
        var haystack = String.join(" ", labels).toLowerCase(java.util.Locale.ROOT);
        return java.util.Arrays.stream(query.strip().toLowerCase(java.util.Locale.ROOT).split("\\s+"))
                .allMatch(haystack::contains);
    }
}
