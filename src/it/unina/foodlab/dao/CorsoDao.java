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
 * - Supporta schema qualificato (es. "public") per evitare ambiguità di tabella.
 * - Owner opzionale: puoi limitare findAll ai corsi dell'owner per coerenza UI.
 * - Transazioni esplicite per creare corso + sessioni in modo atomico.
 * - Niente Stream/Lambda.
 */
public class CorsoDao {

    /** Schema del database (es. "public"). Mai null/vuoto. */
    private final String schema;
    /** CF del proprietario (owner) per operazioni protette. Mai null/vuoto. */
    private final String ownerCfChef;
    /** Se true, findAll() restituisce solo corsi dell'owner; se false, restituisce tutti. */
    private final boolean restrictFindAllToOwner;

    public CorsoDao(String ownerCfChef) {
        this(ownerCfChef, "public", false);
    }

    public CorsoDao(String ownerCfChef, String schema, boolean restrictFindAllToOwner) {
        if (ownerCfChef == null || ownerCfChef.trim().isEmpty())
            throw new IllegalArgumentException("CF_Chef mancante per CorsoDao");
        if (schema == null || schema.trim().isEmpty())
            schema = "public";
        this.ownerCfChef = ownerCfChef.trim();
        this.schema = schema.trim();
        this.restrictFindAllToOwner = restrictFindAllToOwner;
    }

    /* =========================
       SQL (generate con schema)
       ========================= */

    private String tbl(String name) { return schema + "." + name; }

    private String sqlFindAll() {
        return ""
            + "SELECT  c.id_corso, c.data_inizio, c.data_fine, c.argomento, c.frequenza, c.\"numSessioni\" AS num_sessioni, "
            + "        ch.CF_Chef, ch.nome, ch.cognome, ch.username, ch.password "
            + "FROM " + tbl("corso") + " c "
            + "LEFT JOIN " + tbl("chef") + " ch ON ch.CF_Chef = c.fk_cf_chef "
            + (restrictFindAllToOwner ? "WHERE c.fk_cf_chef = ? " : "")
            + "ORDER BY c.data_inizio DESC";
    }

    private String sqlFindByIdOwner() {
        return ""
            + "SELECT  c.id_corso, c.data_inizio, c.data_fine, c.argomento, c.frequenza, c.\"numSessioni\" AS num_sessioni, "
            + "        ch.CF_Chef, ch.nome, ch.cognome, ch.username, ch.password "
            + "FROM " + tbl("corso") + " c "
            + "JOIN " + tbl("chef") + " ch ON c.fk_cf_chef = ch.CF_Chef "
            + "WHERE c.id_corso = ? AND c.fk_cf_chef = ?";
    }

    private String sqlInsertCorso() {
        return ""
            + "INSERT INTO " + tbl("corso")
            + " (data_inizio, data_fine, argomento, frequenza, \"numSessioni\", fk_cf_chef) "
            + "VALUES (?, ?, ?, ?, ?, ?) "
            + "RETURNING id_corso";
    }

    private String sqlUpdateCorso() {
        return ""
            + "UPDATE " + tbl("corso") + " "
            + "   SET data_inizio = ?, data_fine = ?, argomento = ?, frequenza = ?, \"numSessioni\" = ? "
            + " WHERE id_corso = ? AND fk_cf_chef = ?";
    }

    private String sqlDeleteCorso() {
        return "DELETE FROM " + tbl("corso") + " WHERE id_corso = ? AND fk_cf_chef = ?";
    }

    private String sqlInsertSesOnline() {
        return ""
            + "INSERT INTO " + tbl("sessione_online")
            + " (fk_id_corso, data, ora_inizio, ora_fine, piattaforma) "
            + "VALUES (?, ?, ?, ?, ?)";
    }

    private String sqlInsertSesPresenza() {
        return ""
            + "INSERT INTO " + tbl("sessione_presenza")
            + " (fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private String sqlDistinctArg() {
        return ""
            + "SELECT a FROM ( "
            + "  SELECT DISTINCT TRIM(argomento) AS a "
            + "  FROM " + tbl("corso") + " "
            + "  WHERE argomento IS NOT NULL AND TRIM(argomento) <> '' "
            + ") t "
            + "ORDER BY LOWER(a), a";
    }

    private String sqlDistinctFreq() {
        return ""
            + "SELECT f FROM ( "
            + "  SELECT DISTINCT TRIM(frequenza) AS f "
            + "  FROM " + tbl("corso") + " "
            + "  WHERE frequenza IS NOT NULL AND TRIM(frequenza) <> '' "
            + ") t "
            + "ORDER BY LOWER(f), f";
    }

    /* =========================
       QUERY CORSI
       ========================= */

    /** Restituisce TUTTI i corsi o solo quelli dell'owner (in base a restrictFindAllToOwner). */
    public List<Corso> findAll() throws Exception {
        List<Corso> out = new ArrayList<Corso>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sqlFindAll())) {

            if (restrictFindAllToOwner) {
                ps.setString(1, ownerCfChef);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    /** Restituisce il corso SOLO se appartiene all'owner (utile per edit/controllo). */
    public Corso findById(long id) throws Exception {
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sqlFindByIdOwner())) {
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
             PreparedStatement ps = conn.prepareStatement(sqlInsertCorso())) {
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
             PreparedStatement ps = conn.prepareStatement(sqlUpdateCorso())) {
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
             PreparedStatement ps = conn.prepareStatement(sqlDeleteCorso())) {
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
            int oldIso = conn.getTransactionIsolation();
            try {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.setAutoCommit(false);

                long idCorso = insertCorso(conn, c); // imposta anche c.idCorso
                for (int i = 0; i < sessions.size(); i++) {
                    insertSession(conn, idCorso, sessions.get(i));
                }
                conn.commit();
                return idCorso;

            } catch (Exception ex) {
                try { conn.rollback(); } catch (Exception ignore) {}
                throw ex;
            } finally {
                try { conn.setTransactionIsolation(oldIso); } catch (Exception ignore) {}
                conn.setAutoCommit(oldAuto);
            }
        }
    }

    private long insertCorso(Connection conn, Corso c) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sqlInsertCorso())) {
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
            try (PreparedStatement ps = conn.prepareStatement(sqlInsertSesOnline())) {
                ps.setLong(1, corsoId);
                ps.setObject(2, so.getData());
                ps.setObject(3, so.getOraInizio());
                ps.setObject(4, so.getOraFine());
                ps.setString(5, so.getPiattaforma());
                ps.executeUpdate();
            }
        } else if (s instanceof SessionePresenza) {
            SessionePresenza sp = (SessionePresenza) s;
            try (PreparedStatement ps = conn.prepareStatement(sqlInsertSesPresenza())) {
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

    public List<String> findDistinctArgomenti() throws Exception {
        List<String> out = new ArrayList<String>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sqlDistinctArg());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    public List<String> findDistinctFrequenze() throws Exception {
        List<String> out = new ArrayList<String>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sqlDistinctFreq());
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
        // password presente nel result-set ma NON usarla in UI
        chef.setPassword(rs.getString("password"));
        c.setChef(chef);

        return c;
    }

    /** Bind campi (senza owner) per insert/update + validazioni minime. */
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

    public String getOwnerCfChef() { return ownerCfChef; }
    public String getSchema() { return schema; }
    public boolean isRestrictFindAllToOwner() { return restrictFindAllToOwner; }

    /**
     * Piccolo check per capire SUBITO da dove stai leggendo.
     * Esempio d'uso in debug: System.out.println(corsoDao.sanityCheck());
     */
    public String sanityCheck() {
        Connection conn = null;
        try {
            conn = Db.get();
            DatabaseMetaData md = conn.getMetaData();
            return "[CorsoDao] DB URL=" + md.getURL() + " | User=" + md.getUserName() + " | Schema=" + schema
                    + " | restrictFindAllToOwner=" + restrictFindAllToOwner;
        } catch (Exception ex) {
            return "[CorsoDao] sanityCheck ERROR: " + ex.getMessage();
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignore) {}
        }
    }
}
