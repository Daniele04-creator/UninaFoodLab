package it.uninafoodlab.infrastructure.planning;

import it.uninafoodlab.application.planning.SessionLifecycleRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcSessionLifecycleRepository implements SessionLifecycleRepository {
    private final DataSource dataSource;

    public JdbcSessionLifecycleRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public State state(UUID sessionToken, UUID sessionId) {
        return query("SELECT * FROM app.session_lifecycle(?,?)",
                row -> new State(row.getInt(1), row.getString(2), row.getString(3)), sessionToken, sessionId).getFirst();
    }

    @Override
    public List<SavedRecipe> menu(UUID sessionToken, UUID sessionId) {
        return query("SELECT * FROM app.saved_session_menu(?,?)",
                row -> new SavedRecipe(row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getString(4)), sessionToken, sessionId);
    }

    @Override
    public List<ReportRow> report(UUID sessionToken, YearMonth month) {
        return query("SELECT * FROM app.chef_monthly_report_details(?,?,?)",
                row -> new ReportRow(row.getObject(1, UUID.class), row.getString(2), row.getObject(3, LocalDateTime.class),
                        row.getString(4), row.getString(5), row.getObject(6, Integer.class)),
                sessionToken, month.getYear(), month.getMonthValue());
    }

    @Override
    public void refresh(UUID sessionToken, UUID sessionId) {
        write("SELECT app.refresh_session_menu(?,?)", sessionToken, sessionId);
    }

    private void write(String sql, Object... values) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            statement.execute();
            connection.commit();
        } catch (SQLException exception) {
            throw new ChefPlanningRepositoryException("Operazione non completata. Verifica stato, scadenza e dati della sessione prima di riprovare.", exception);
        }
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... values) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            try (var rows = statement.executeQuery()) {
                var result = new ArrayList<T>();
                while (rows.next()) result.add(mapper.map(rows));
                return List.copyOf(result);
            }
        } catch (SQLException exception) {
            throw new ChefPlanningRepositoryException("Impossibile leggere lo stato della sessione. Aggiorna dopo aver verificato la connessione.", exception);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> { T map(ResultSet result) throws SQLException; }
}
