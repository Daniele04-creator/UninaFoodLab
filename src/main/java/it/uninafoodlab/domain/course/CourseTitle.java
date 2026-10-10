package it.uninafoodlab.domain.course;

import java.util.Objects;
import java.util.regex.Pattern;

public record CourseTitle(String value) {

    private static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    public CourseTitle {
        if (!isSingleLine(value)) {
            throw new IllegalArgumentException("course title must be on a single line");
        }
        value = value.strip();
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("course title must contain 3 to 30 characters");
        }
    }

    public static CourseTitle of(String value) {
        return new CourseTitle(value);
    }

    public static boolean isSingleLine(String value) {
        return !LINE_BREAK.matcher(Objects.requireNonNull(value, "value")).find();
    }
}
