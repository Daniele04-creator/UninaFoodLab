package it.uninafoodlab.infrastructure.account;

import it.uninafoodlab.infrastructure.config.ApplicationConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class AccountProvisioning {

    private AccountProvisioning() {}

    public static void suspendAccount(ApplicationConfig config, String username) throws SQLException {
        provisionAccountMutation(config, username, "select app.suspend_account(?)");
    }

    public static void disableAccount(ApplicationConfig config, String username) throws SQLException {
        provisionAccountMutation(config, username, "select app.disable_account(?)");
    }

    private static void provisionAccountMutation(ApplicationConfig config, String username, String sql)
            throws SQLException {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username obbligatorio.");
        validateLocalDevelopment(config);
        try (var connection = ownerConnection(config)) {
            connection.setAutoCommit(false);
            try {
                execute(connection, sql, username);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static Connection ownerConnection(ApplicationConfig config) throws SQLException {
        return DriverManager.getConnection(config.databaseUrl(),
                config.databaseMigratorUser(), config.databaseMigratorPassword());
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            statement.execute();
        }
    }

    static void validateLocalDevelopment(ApplicationConfig config) {
        if (!"development".equals(config.environmentName())
                || !config.databaseUrl().matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1|\\[::1\\])(:\\d+)?/[a-zA-Z0-9_-]+(?:\\?.*)?")) {
            throw new IllegalArgumentException(
                    "Il provisioning account è consentito solo sul database locale di sviluppo.");
        }
    }

}
