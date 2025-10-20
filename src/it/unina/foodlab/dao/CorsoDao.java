package it.unina.foodlab.dao;

import it.unina.foodlab.model.Chef;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import it.unina.foodlab.util.Db;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO dei corsi.
 * - Mantiene l'owner (CF_Chef) per operazioni protette (findById, update, delete, insert).
 * - Niente Stream/Lambda: codice lineare e chiaro.
 * - Transazioni esplicite per creare corso + sessioni in modo atomico.
 */
public class CorsoDao {

    private final String ownerCfChef;

    public CorsoDao(String ownerCfChef) {
        if (ownerCfChef == null || ownerCfChef.trim().isEmpty()) {
            throw new IllegalArgumentException("CF_Chef mancante per CorsoDao");
        }
        this.ownerCfChef = ownerCfChef.trim();
    }

    /* =========================
       SQL (costanti ordinate)
       ========================= */

    private static final String SQL_FIND_ALL = """
        SELECT  c.id_corso,
                c.data_inizio,
                c.data_fine,
                c.argomento,
                c.frequenza,
                c."numSessioni" AS num_sessioni,
                ch.CF_Chef,
                ch.nome,
                ch.cognome,
                ch.username,
                ch.password
        FROM corso c
        LEFT JOIN chef ch ON ch.CF_Chef = c.fk_cf_chef
        ORDER BY c.data_inizio DESC
    """;

    private static final String SQL_FIND_BY_ID_OWNER = """
        SELECT  c.id_corso,
                c.data_inizio,
                c.data_fine,
                c.argomento,
                c.frequenza,
                c."numSessioni" AS num_sessioni,
                ch.CF_Chef,
                ch.nome,
                ch.cognome,
                ch.username,
                ch.password
        FROM corso c
        JOIN chef ch ON c.fk_cf_chef = ch.CF_Chef
        WHERE c.id_corso = ? AND c.fk_cf_chef = ?
    """;

    private static final String SQL_INSERT_CORSO = """
        INSERT INTO corso (data_inizio, data_fine, argomento, frequenza, "numSessioni", fk_cf_chef)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING id_corso
    """;

    private static final String SQL_UPDATE_CORSO = """
        UPDATE corso
           SET data_inizio   = ?,
               data_fine     = ?,
               argomento     = ?,
               frequenza     = ?,
               "numSessioni" = ?
         WHERE id_corso = ? AND fk_cf_chef = ?
    """;

    private static final String SQL_DELETE_CORSO = """
        DELETE FROM corso
        WHERE id_corso = ? AND fk_cf_chef = ?
    """;

    private static final String SQL_INSERT_SES_ONLINE = """
        INSERT INTO sessione_online
            (fk_id_corso, data, ora_inizio, ora_fine, piattaforma)
        VALUES (?, ?, ?, ?, ?)
    """;

    private static final String SQL_INSERT_SES_PRESENZA = """
        INSERT INTO sessione_presenza
            (fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private static final String SQL_DISTINCT_ARG = """
        SELECT a
        FROM (
            SELECT DISTINCT TRIM(argomento) AS a
            FROM corso
            WHERE argomento IS NOT NULL AND TRIM(argomento) <> ''
        ) t
        ORDER BY LOWER(a), a
    """;

    private static final String SQL_DISTINCT_FREQ = """
        SELECT f
        FROM (
            SELECT DISTINCT TRIM(frequenza) AS f
            FROM corso
            WHERE frequenza IS NOT NULL AND TRIM(frequenza) <> ''
        ) t
        ORDER BY LOWER(f), f
    """;

    /* =========================
       QUERY CORSI
       ========================= */

    /** Restituisce TUTTI i corsi (nessun filtro per chef). */
    public List<Corso> findAll() throws Exception {
        List<Corso> out = new ArrayList<Corso>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapRow(rs));
        }
        return out;
    }

    /** Restituisce il corso SOLO se appartiene all'owner (utile per edit/controllo). */
    public Corso findById(long id) throws Exception {
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID_OWNER)) {
            ps.setLong(1, id);
            ps.setString(2, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Inserisce SOLO il corso (non le sessioni). */
    public long insert(Corso corso) throws Exception {
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_CORSO)) {
            bindWithoutOwner(ps, corso);
            ps.setString(6, ownerCfChef); // ownership forzata
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                throw new SQLException("Insert corso: ID non restituito");
            }
        }
    }

    /** Aggiorna il corso dell'owner. */
    public void update(Corso corso) throws Exception {
        if (corso.getIdCorso() <= 0) throw new IllegalArgumentException("idCorso mancante");
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_CORSO)) {
            bindWithoutOwner(ps, corso);
            ps.setLong(6, corso.getIdCorso());
            ps.setString(7, ownerCfChef);
            int n = ps.executeUpdate();
            if (n != 1) throw new SQLException("Update negato o nessuna riga (id=" + corso.getIdCorso() + ")");
        }
    }

    /** Elimina il corso dell'owner. */
    public void delete(long id) throws Exception {
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_CORSO)) {
            ps.setLong(1, id);
            ps.setString(2, ownerCfChef);
            int n = ps.executeUpdate();
            if (n != 1) throw new SQLException("Delete negato o nessuna riga (id=" + id + ")");
        }
    }

    /* =========================
       CREAZIONE ATOMICA: CORSO + SESSIONI
       ========================= */

    public long insertWithSessions(Corso c, List<Sessione> sessions) throws Exception {
        if (c == null) throw new IllegalArgumentException("Corso nullo");
        if (sessions == null || sessions.isEmpty()) throw new IllegalArgumentException("Nessuna sessione");

        try (Connection conn = Db.get()) {
            boolean oldAuto = conn.getAutoCommit();
            int oldIso = Connection.TRANSACTION_READ_COMMITTED;
            try {
                // isolamento ragionevole per scritture semplici
                oldIso = conn.getTransactionIsolation();
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.setAutoCommit(false);

                long idCorso = insertCorso(conn, c); // imposta anche c.idCorso
                for (int i = 0; i < sessions.size(); i++) {
                    insertSession(conn, idCorso, sessions.get(i));
                }
                conn.commit();
                return idCorso;

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                try { conn.setTransactionIsolation(oldIso); } catch (Exception ignore) {}
                conn.setAutoCommit(oldAuto);
            }
        }
    }

    private long insertCorso(Connection conn, Corso c) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_CORSO)) {
            bindWithoutOwner(ps, c);
            ps.setString(6, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                c.setIdCorso(id);
                // Garantisco che il modello corso abbia l'owner impostato
                if (c.getChef() == null) {
                    Chef s = new Chef();
                    s.setCF_Chef(ownerCfChef);
                    c.setChef(s);
                } else if (c.getChef().getCF_Chef() == null || c.getChef().getCF_Chef().trim().isEmpty()) {
                    c.getChef().setCF_Chef(ownerCfChef);
                }
                return id;
            }
        }
    }

    private void insertSession(Connection conn, long corsoId, Sessione s) throws SQLException {
        if (s instanceof SessioneOnline) {
            SessioneOnline so = (SessioneOnline) s;
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_SES_ONLINE)) {
                ps.setLong(1, corsoId);
                ps.setObject(2, so.getData());
                ps.setObject(3, so.getOraInizio());
                ps.setObject(4, so.getOraFine());
                ps.setString(5, so.getPiattaforma());
                ps.executeUpdate();
            }
        } else if (s instanceof SessionePresenza) {
            SessionePresenza sp = (SessionePresenza) s;
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_SES_PRESENZA)) {
                ps.setLong(1, corsoId);
                ps.setObject(2, sp.getData());
                ps.setObject(3, sp.getOraInizio());
                ps.setObject(4, sp.getOraFine());
                ps.setString(5, sp.getVia());
                ps.setString(6, sp.getNum());
                ps.setInt(7, sp.getCap());
                ps.setString(8, sp.getAula());
                ps.setInt(9, sp.getPostiMax());
                ps.executeUpdate();
            }
        } else {
            throw new SQLException("Tipo sessione non supportato: " + s.getClass());
        }
    }

    /* =========================
       OPZIONI DISTINCT (per editor)
       ========================= */

    /** Distinct su TUTTI i corsi. */
    public List<String> findDistinctArgomenti() throws Exception {
        List<String> out = new ArrayList<String>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_DISTINCT_ARG);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    /** Distinct su TUTTI i corsi. */
    public List<String> findDistinctFrequenze() throws Exception {
        List<String> out = new ArrayList<String>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_DISTINCT_FREQ);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    /* =========================
       UTILS
       ========================= */

    private Corso mapRow(ResultSet rs) throws SQLException {
        Corso c = new Corso();
        c.setIdCorso(rs.getLong("id_corso"));

        Date di = rs.getDate("data_inizio");
        Date df = rs.getDate("data_fine");
        c.setDataInizio(di != null ? di.toLocalDate() : null);
        c.setDataFine(df != null ? df.toLocalDate() : null);

        c.setArgomento(rs.getString("argomento"));
        c.setFrequenza(rs.getString("frequenza"));

        int ns = rs.getInt("num_sessioni");
        if (rs.wasNull()) ns = 1;
        c.setNumSessioni(ns);

        Chef chef = new Chef();
        chef.setCF_Chef(rs.getString("CF_Chef"));
        chef.setNome(rs.getString("nome"));
        chef.setCognome(rs.getString("cognome"));
        chef.setUsername(rs.getString("username"));
        chef.setPassword(rs.getString("password")); // mantengo per compatibilità (non lo esponi in UI)
        c.setChef(chef);

        return c;
    }

    /** Bind dei campi (senza owner) per insert/update + validazioni minime. */
    private void bindWithoutOwner(PreparedStatement ps, Corso c) throws SQLException {
        LocalDate di = c.getDataInizio();
        LocalDate df = c.getDataFine();

        if (di == null || df == null)
            throw new SQLException("data_inizio e data_fine sono obbligatorie");
        if (di.isAfter(df))
            throw new SQLException("data_inizio non può essere successiva a data_fine");
        if (c.getArgomento() == null || c.getArgomento().trim().isEmpty())
            throw new SQLException("argomento obbligatorio");
        if (c.getFrequenza() == null || c.getFrequenza().trim().isEmpty())
            throw new SQLException("frequenza obbligatoria");

        int ns = c.getNumSessioni();
        if (ns < 1) throw new SQLException("\"numSessioni\" deve essere >= 1");

        ps.setDate(1, Date.valueOf(di));
        ps.setDate(2, Date.valueOf(df));
        ps.setString(3, c.getArgomento());
        ps.setString(4, c.getFrequenza());
        ps.setInt(5, ns);
    }

    public String getOwnerCfChef() {
        return ownerCfChef;
    }
}
