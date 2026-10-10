package it.uninafoodlab.infrastructure.auth;

import it.uninafoodlab.application.auth.AccountRepository;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcAccountRepository implements AccountRepository {

    private static final String PASSWORD_PARAMETERS =
            "select iterations, salt from app.get_password_parameters(?)";
    private static final String AUTHENTICATE = """
            select session_token, account_id, profile_id, username, display_name, role
              from app.authenticate_user(?, ?)
            """;

    private final DataSource dataSource;
    private final Pbkdf2PasswordHasher passwordHasher;

    public JdbcAccountRepository(
            DataSource dataSource, Pbkdf2PasswordHasher passwordHasher) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(Username username, char[] password) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        byte[] proof = null;
        try (var connection = dataSource.getConnection()) {
            var parameters = passwordParameters(connection, username);
            proof = passwordHasher.authenticationProof(
                    password, parameters.salt(), parameters.iterations());
            try (var statement = connection.prepareStatement(AUTHENTICATE)) {
                statement.setString(1, username.value());
                statement.setBytes(2, proof);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return Optional.empty();
                    }
                    var user = new AuthenticatedUser(
                            result.getObject("account_id", UUID.class),
                            result.getObject("profile_id", UUID.class),
                            result.getObject("session_token", UUID.class),
                            Username.of(result.getString("username")),
                            result.getString("display_name"),
                            Role.valueOf(result.getString("role")));
                    connection.commit();
                    return Optional.of(user);
                }
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new AccountRepositoryException("Unable to authenticate account", exception);
        } finally {
            if (proof != null) {
                Arrays.fill(proof, (byte) 0);
            }
        }
    }

    private static PasswordParameters passwordParameters(
            java.sql.Connection connection, Username username) throws SQLException {
        try (var statement = connection.prepareStatement(PASSWORD_PARAMETERS)) {
            statement.setString(1, username.value());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Password parameters returned no row");
                }
                return new PasswordParameters(
                        result.getInt("iterations"), result.getBytes("salt"));
            }
        }
    }

    private static void rollback(java.sql.Connection connection, SQLException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private static final class PasswordParameters {
        private final int iterations;
        private final byte[] salt;

        private PasswordParameters(int iterations, byte[] salt) {
            this.iterations = iterations;
            this.salt = salt.clone();
        }

        private int iterations() {
            return iterations;
        }

        private byte[] salt() {
            return salt.clone();
        }
    }
}
