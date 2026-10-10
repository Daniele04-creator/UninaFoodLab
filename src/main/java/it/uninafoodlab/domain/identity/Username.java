package it.uninafoodlab.domain.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Username(String value) {

    private static final Pattern VALID_VALUE = Pattern.compile("[a-z0-9._-]{3,50}");
    private static final String INVALID_MESSAGE =
            "Username must contain 3 to 50 lowercase letters, numbers, dots, underscores or hyphens";

    public Username {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }
    }

    public static Username of(String value) {
        return new Username(value);
    }
}
