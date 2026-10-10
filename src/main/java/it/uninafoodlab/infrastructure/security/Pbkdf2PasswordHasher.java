package it.uninafoodlab.infrastructure.security;

import it.uninafoodlab.application.security.PasswordHasher;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT_ALGORITHM = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;

    private final SecureRandom secureRandom;

    public Pbkdf2PasswordHasher() {
        this(new SecureRandom());
    }

    Pbkdf2PasswordHasher(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public String hash(char[] password) {
        requirePassword(password);
        var salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        var derivedKey = deriveKey(password, salt, ITERATIONS);
        var encoder = Base64.getEncoder().withoutPadding();
        return "$%s$%d$%s$%s".formatted(
                FORMAT_ALGORITHM,
                ITERATIONS,
                encoder.encodeToString(salt),
                encoder.encodeToString(derivedKey));
    }

    @Override
    public boolean matches(char[] password, String encodedHash) {
        requirePassword(password);
        if (encodedHash == null) {
            return false;
        }

        try {
            var parts = encodedHash.split("\\$", -1);
            if (parts.length != 5 || !parts[0].isEmpty() || !FORMAT_ALGORITHM.equals(parts[1])) {
                return false;
            }
            var iterations = Integer.parseInt(parts[2]);
            var decoder = Base64.getDecoder();
            var salt = decoder.decode(parts[3]);
            var expected = decoder.decode(parts[4]);
            if (iterations != ITERATIONS || salt.length != SALT_BYTES || expected.length != KEY_BYTES) {
                return false;
            }
            return MessageDigest.isEqual(expected, deriveKey(password, salt, iterations));
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    public byte[] authenticationProof(char[] password, byte[] salt, int iterations) {
        requirePassword(password);
        Objects.requireNonNull(salt, "salt");
        if (salt.length != SALT_BYTES || iterations != ITERATIONS) {
            throw new IllegalArgumentException("Unsupported password parameters");
        }
        return deriveKey(password, salt, iterations);
    }

    private static byte[] deriveKey(char[] password, byte[] salt, int iterations) {
        var keySpec = new PBEKeySpec(password, salt, iterations, KEY_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new PasswordHashingException("Password hashing is unavailable", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    private static void requirePassword(char[] password) {
        Objects.requireNonNull(password, "password");
        if (password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
    }
}
