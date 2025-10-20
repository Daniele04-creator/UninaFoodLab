package it.unina.foodlab.dao;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.SessionePresenza;
import it.unina.foodlab.util.Db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO Ricette: CRUD semplice + associazioni con sessioni in presenza.
 * Nessun uso di stream/lambda. SQL in costanti per leggibilità.
 */
public class RicettaDao {

    private static final String SQL_FIND_ALL = """
        SELECT id_ricetta, nome, descrizione, difficolta, tempo_preparazione
          FROM ricetta
         ORDER BY LOWER(nome)
    """;

    private static final String SQL_FIND_BY_ID = """
        SELECT id_ricetta, nome, descrizione, difficolta, tempo_preparazione
          FROM ricetta
         WHERE id_ricetta = ?
    """;

    private static final String SQL_INSERT = """
        INSERT INTO ricetta (nome, descrizione, difficolta, tempo_preparazione)
        VALUES (?, ?, ?, ?)
    """;

    private static final String SQL_UPDATE = """
        UPDATE ricetta
           SET nome=?, descrizione=?, difficolta=?, tempo_preparazione=?
         WHERE id_ricetta=?
    """;

    private static final String SQL_DELETE = "DELETE FROM ricetta WHERE id_ricetta = ?";

    private static final String SQL_LIST_SESS_BY_RICETTA = """
        SELECT sp."idSessionePresenza" AS id,
               sp.fk_id_corso,
               sp.data, sp.ora_inizio, sp.ora_fine,
               sp.via, sp.num, sp.cap, sp.aula, sp.posti_max
          FROM sessione_presenza sp
          JOIN sessione_presenza_ricetta spr
            ON spr.fk_id_sess_pr = sp."idSessionePresenza"
         WHERE spr.fk_id_ricetta = ?
         ORDER BY sp.data, sp.ora_inizio
    """;

    private static final String SQL_ADD_LINK = """
        INSERT INTO sessione_presenza_ricetta (fk_id_sess_pr, fk_id_ricetta)
        VALUES (?, ?)
        ON CONFLICT (fk_id_sess_pr, fk_id_ricetta) DO NOTHING
    """;

    private static final String SQL_REMOVE_LINK = """
        DELETE FROM sessione_presenza_ricetta
         WHERE fk_id_sess_pr = ? AND fk_id_ricetta = ?
    """;

    public RicettaDao() { }

    // ================== CRUD BASE ==================

    public List<Ricetta> findAll() throws Exception {
        List<Ricetta> out = new ArrayList<Ricetta>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapRow(rs));
        }
        return out;
    }

    public Ricetta findById(long id) throws Exception {
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public void insert(Ricetta r) throws Exception {
        validateRicetta(r, false);
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getNome());
            ps.setString(2, r.getDescrizione());
            ps.setString(3, r.getDifficolta());
            ps.setInt(4, r.getTempoPreparazione());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) r.setIdRicetta(keys.getLong(1));
            }
        }
    }

    public void update(Ricetta r) throws Exception {
        if (r == null || r.getIdRicetta() <= 0) throw new IllegalArgumentException("Ricetta non valida");
        validateRicetta(r, true);
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, r.getNome());
            ps.setString(2, r.getDescrizione());
            ps.setString(3, r.getDifficolta());
            ps.setInt(4, r.getTempoPreparazione());
            ps.setLong(5, r.getIdRicetta());
            ps.executeUpdate();
        }
    }

    public void delete(long id) throws Exception {
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ================== ASSOCIAZIONE N<->N (PRESENZA <-> RICETTA) ==================

    /** Sessioni pratiche in cui è usata la ricetta. Nessun filtro per owner qui. */
    public List<SessionePresenza> listSessioniByRicetta(long idRicetta) throws Exception {
        List<SessionePresenza> out = new ArrayList<SessionePresenza>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_LIST_SESS_BY_RICETTA)) {
            ps.setLong(1, idRicetta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SessionePresenza sp = new SessionePresenza();
                    sp.setId(rs.getInt("id"));
                    sp.setData(rs.getDate("data").toLocalDate());
                    sp.setOraInizio(rs.getTime("ora_inizio").toLocalTime());
                    sp.setOraFine(rs.getTime("ora_fine").toLocalTime());
                    sp.setVia(rs.getString("via"));
                    sp.setNum(rs.getString("num"));
                    int cap = rs.getInt("cap"); if (rs.wasNull()) cap = 0;
                    sp.setCap(cap);
                    sp.setAula(rs.getString("aula"));
                    int pm = rs.getInt("posti_max"); if (rs.wasNull()) pm = 0;
                    sp.setPostiMax(pm);

                    Corso c = new Corso();
                    c.setIdCorso(rs.getLong("fk_id_corso"));
                    sp.setCorso(c);

                    out.add(sp);
                }
            }
        }
        return out;
    }

    /** Collega idRicetta -> idSessionePresenza (idempotente). */
    public void addSessione(long idRicetta, int idSessionePresenza) throws Exception {
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_ADD_LINK)) {
            ps.setInt(1, idSessionePresenza);
            ps.setLong(2, idRicetta);
            ps.executeUpdate(); // 0 se già presente (OK)
        }
    }

    /** Scollega idRicetta <-/-> idSessionePresenza. */
    public void removeSessione(long idRicetta, int idSessionePresenza) throws Exception {
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_REMOVE_LINK)) {
            ps.setInt(1, idSessionePresenza);
            ps.setLong(2, idRicetta);
            ps.executeUpdate();
        }
    }

    // ================== MAPPER & VALIDAZIONE ==================

    private Ricetta mapRow(ResultSet rs) throws SQLException {
        Ricetta r = new Ricetta();
        r.setIdRicetta(rs.getLong("id_ricetta"));
        r.setNome(rs.getString("nome"));
        r.setDescrizione(rs.getString("descrizione"));
        r.setDifficolta(rs.getString("difficolta")); // facile | medio | difficile
        r.setTempoPreparazione(rs.getInt("tempo_preparazione"));
        return r;
    }

    private void validateRicetta(Ricetta r, boolean updating) throws SQLException {
        if (r == null) throw new SQLException("Ricetta mancante");
        if (r.getNome() == null || r.getNome().trim().isEmpty()) throw new SQLException("Nome ricetta obbligatorio");
        if (r.getDifficolta() == null || r.getDifficolta().trim().isEmpty())
            throw new SQLException("Difficoltà obbligatoria (facile/medio/difficile)");
        if (r.getTempoPreparazione() < 0) throw new SQLException("Tempo di preparazione non può essere negativo");
    }
}
