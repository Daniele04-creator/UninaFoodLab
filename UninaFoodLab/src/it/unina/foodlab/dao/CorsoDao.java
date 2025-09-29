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

public class CorsoDao {

    private final String ownerCfChef;

    public CorsoDao(String ownerCfChef) {
        if (ownerCfChef == null || ownerCfChef.isBlank()) {
            throw new IllegalArgumentException("CF_Chef mancante per CorsoDao");
        }
        this.ownerCfChef = ownerCfChef.trim();
    }

    /* =========================
       QUERY CORSI
       ========================= */

    /** Restituisce TUTTI i corsi (nessun filtro per chef). */
    public List<Corso> findAll() throws Exception {
        String sql = """
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
        List<Corso> out = new ArrayList<>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapRow(rs));
        }
        return out;
    }

    /** Restituisce il corso SOLO se appartiene all'owner (utile per edit). */
    public Corso findById(long id) throws Exception {
        String sql = """
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
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** Inserisce SOLO il corso (non le sessioni). */
    public long insert(Corso corso) throws Exception {
        String sql = """
            INSERT INTO corso (data_inizio, data_fine, argomento, frequenza, "numSessioni", fk_cf_chef)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id_corso
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindWithoutOwner(ps, corso);
            ps.setString(6, ownerCfChef); // ownership forzata
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                throw new SQLException("Insert corso: ID non restituito");
            }
        }
    }

    public void update(Corso corso) throws Exception {
        if (corso.getIdCorso() <= 0) throw new IllegalArgumentException("idCorso mancante");
        String sql = """
            UPDATE corso
               SET data_inizio   = ?,
                   data_fine     = ?,
                   argomento     = ?,
                   frequenza     = ?,
                   "numSessioni" = ?
             WHERE id_corso = ? AND fk_cf_chef = ?
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindWithoutOwner(ps, corso);
            ps.setLong(6, corso.getIdCorso());
            ps.setString(7, ownerCfChef);
            int n = ps.executeUpdate();
            if (n != 1) throw new SQLException("Update negato o nessuna riga (id=" + corso.getIdCorso() + ")");
        }
    }

    public void delete(long id) throws Exception {
        String sql = "DELETE FROM corso WHERE id_corso = ? AND fk_cf_chef = ?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
            boolean old = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                long idCorso = insertCorso(conn, c); // imposta anche c.idCorso
                for (Sessione s : sessions) {
                    insertSession(conn, idCorso, s);
                }
                conn.commit();
                return idCorso;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(old);
            }
        }
    }

    private long insertCorso(Connection conn, Corso c) throws SQLException {
        String sql = """
            INSERT INTO corso
                (data_inizio, data_fine, argomento, frequenza, "numSessioni", fk_cf_chef)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id_corso
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindWithoutOwner(ps, c);
            ps.setString(6, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                c.setIdCorso(id);
                if (c.getChef() == null) {
                    Chef s = new Chef();
                    s.setCF_Chef(ownerCfChef);
                    c.setChef(s);
                } else if (c.getChef().getCF_Chef() == null || c.getChef().getCF_Chef().isBlank()) {
                    c.getChef().setCF_Chef(ownerCfChef);
                }
                return id;
            }
        }
    }

    private void insertSession(Connection conn, long corsoId, Sessione s) throws SQLException {
        if (s instanceof SessioneOnline so) {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sessione_online
                    (fk_id_corso, data, ora_inizio, ora_fine, piattaforma)
                VALUES (?, ?, ?, ?, ?)
            """)) {
                ps.setLong(1, corsoId);
                ps.setObject(2, so.getData());
                ps.setObject(3, so.getOraInizio());
                ps.setObject(4, so.getOraFine());
                ps.setString(5, so.getPiattaforma());
                ps.executeUpdate();
            }
        } else if (s instanceof SessionePresenza sp) {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sessione_presenza
                    (fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
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
        String sql = """
            SELECT a
            FROM (
                SELECT DISTINCT TRIM(argomento) AS a
                FROM corso
                WHERE argomento IS NOT NULL AND TRIM(argomento) <> ''
            ) t
            ORDER BY LOWER(a), a
        """;
        List<String> out = new ArrayList<>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    /** Distinct su TUTTI i corsi. */
    public List<String> findDistinctFrequenze() throws Exception {
        String sql = """
            SELECT f
            FROM (
                SELECT DISTINCT TRIM(frequenza) AS f
                FROM corso
                WHERE frequenza IS NOT NULL AND TRIM(frequenza) <> ''
            ) t
            ORDER BY LOWER(f), f
        """;
        List<String> out = new ArrayList<>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql);
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
        chef.setPassword(rs.getString("password"));
        c.setChef(chef);

        return c;
    }

    /** Bind dei campi (senza owner) per insert/update. */
    private void bindWithoutOwner(PreparedStatement ps, Corso c) throws SQLException {
        LocalDate di = c.getDataInizio();
        LocalDate df = c.getDataFine();

        if (di == null || df == null) throw new SQLException("data_inizio e data_fine sono obbligatorie");
        if (c.getArgomento() == null || c.getArgomento().isBlank()) throw new SQLException("argomento obbligatorio");
        if (c.getFrequenza() == null || c.getFrequenza().isBlank()) throw new SQLException("frequenza obbligatoria");

        Integer ns = c.getNumSessioni();
        if (ns == null || ns < 1) throw new SQLException("\"numSessioni\" obbligatorio e >= 1");

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