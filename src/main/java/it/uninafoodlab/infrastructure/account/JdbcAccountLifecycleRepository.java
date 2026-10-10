package it.uninafoodlab.infrastructure.account;

import it.uninafoodlab.application.account.AccountLifecycleException;
import it.uninafoodlab.application.account.AccountLifecycleRepository;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import it.uninafoodlab.infrastructure.security.Pbkdf2PasswordHasher;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcAccountLifecycleRepository implements AccountLifecycleRepository {

    private static final String PASSWORD_PARAMETERS =
            "select iterations, salt from app.get_password_parameters(?)";
    private static final String REGISTER = """
            select session_token, account_id, profile_id, username, display_name, role
              from app.register_participant(?, ?, ?)
            """;
    private static final String REGISTER_CHEF = """
            select session_token, account_id, profile_id, username, display_name, role
              from app.register_chef(?, ?, ?)
            """;
    private static final String CHANGE_PASSWORD =
            "select app.change_account_password(?, ?, ?)";
    private static final String LOGOUT = "select app.revoke_auth_session(?)";

    private final DataSource dataSource;
    private final Pbkdf2PasswordHasher passwordHasher;

    public JdbcAccountLifecycleRepository(DataSource dataSource, Pbkdf2PasswordHasher passwordHasher) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    @Override
    public AuthenticatedUser registerParticipant(Username username, String displayName, String passwordHash) {
        return create(REGISTER, username, displayName, passwordHash,
                "Impossibile completare la registrazione.");
    }

    @Override
    public AuthenticatedUser registerChef(Username username, String displayName, String passwordHash) {
        return create(REGISTER_CHEF, username, displayName, passwordHash,
                "Impossibile completare la registrazione.");
    }

    @Override
    public void changePassword(
            UUID sessionToken, Username username, char[] currentPassword, String newPasswordHash) {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(currentPassword, "currentPassword");
        byte[] proof = null;
        try (var connection = dataSource.getConnection()) {
            try {
                var parameters = passwordParameters(connection, username);
                proof = passwordHasher.authenticationProof(
                        currentPassword, parameters.salt(), parameters.iterations());
                try (var statement = connection.prepareStatement(CHANGE_PASSWORD)) {
                    statement.setObject(1, sessionToken);
                    statement.setBytes(2, proof);
                    statement.setString(3, newPasswordHash);
                    statement.execute();
                    connection.commit();
                }
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw map("La password attuale non è corretta o la sessione è scaduta.", exception);
            }
        } catch (SQLException exception) {
            throw new AccountLifecycleRepositoryException("Unable to change account password", exception);
        } finally {
            if (proof != null) Arrays.fill(proof, (byte) 0);
        }
    }

    @Override
    public void logout(UUID sessionToken) {
        Objects.requireNonNull(sessionToken, "sessionToken");
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(LOGOUT)) {
            try {
                statement.setObject(1, sessionToken);
                statement.execute();
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw map("Impossibile chiudere la sessione.", exception);
            }
        } catch (SQLException exception) {
            throw new AccountLifecycleRepositoryException("Unable to revoke account session", exception);
        }
    }

    private AuthenticatedUser create(
            String sql, Username username, String displayName, String passwordHash, String message) {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, username.value());
                statement.setString(2, displayName);
                statement.setString(3, passwordHash);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("Account operation returned no user");
                    var user = user(result);
                    connection.commit();
                    return user;
                }
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw map(message, exception);
            }
        } catch (SQLException exception) {
            throw new AccountLifecycleRepositoryException("Unable to create account", exception);
        }
    }

    private static AuthenticatedUser user(ResultSet result) throws SQLException {
        return new AuthenticatedUser(
                result.getObject("account_id", UUID.class), result.getObject("profile_id", UUID.class),
                result.getObject("session_token", UUID.class), Username.of(result.getString("username")),
                result.getString("display_name"), Role.valueOf(result.getString("role")));
    }

    private static PasswordParameters passwordParameters(Connection connection, Username username)
            throws SQLException {
        try (var statement = connection.prepareStatement(PASSWORD_PARAMETERS)) {
            statement.setString(1, username.value());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Password parameters returned no row");
                return new PasswordParameters(result.getInt("iterations"), result.getBytes("salt"));
            }
        }
    }

    private static RuntimeException map(String message, SQLException exception) {
        if ("23505".equals(exception.getSQLState()) || "23514".equals(exception.getSQLState())
                || "42501".equals(exception.getSQLState()) || "P0001".equals(exception.getSQLState())) {
            return new AccountLifecycleException(message, exception);
        }
        return new AccountLifecycleRepositoryException("Database account operation failed", exception);
    }

    private static void rollback(Connection connection, SQLException cause) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { cause.addSuppressed(rollbackFailure); }
    }

    private record PasswordParameters(int iterations, byte[] salt) {
        private PasswordParameters { salt = salt.clone(); }
        @Override public byte[] salt() { return salt.clone(); }
    }
}
