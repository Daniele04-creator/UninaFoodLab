package it.unina.foodlab.dao;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.SessionePresenza;
import it.unina.foodlab.util.Db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RicettaDao {

    public RicettaDao() { }

    // ================== CRUD BASE ==================

    public List<Ricetta> findAll() throws Exception {
        String sql = """
            SELECT id_ricetta, nome, descrizione, difficolta, tempo_preparazione
              FROM ricetta
             ORDER BY LOWER(nome)
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Ricetta> out = new ArrayList<>();
            while (rs.next()) out.add(mapRow(rs));
            return out;
        }
    }

    public Ricetta findById(long id) throws Exception {
        String sql = """
            SELECT id_ricetta, nome, descrizione, difficolta, tempo_preparazione
              FROM ricetta
             WHERE id_ricetta = ?
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public void insert(Ricetta r) throws Exception {
        String sql = """
            INSERT INTO ricetta (nome, descrizione, difficolta, tempo_preparazione)
                 VALUES (?, ?, ?, ?)
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        String sql = """
            UPDATE ricetta
               SET nome = ?, descrizione = ?, difficolta = ?, tempo_preparazione = ?
             WHERE id_ricetta = ?
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getNome());
            ps.setString(2, r.getDescrizione());
            ps.setString(3, r.getDifficolta());
            ps.setInt(4, r.getTempoPreparazione());
            ps.setLong(5, r.getIdRicetta());
            ps.executeUpdate();
        }
    }

    public void delete(long id) throws Exception {
        String sql = "DELETE FROM ricetta WHERE id_ricetta = ?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ================== ASSOCIAZIONE N<->N (PRESENZA <-> RICETTA) ==================

    /** Sessioni pratiche in cui Ã¨ usata la ricetta. Nessun filtro per owner qui. */
    public List<SessionePresenza> listSessioniByRicetta(long idRicetta) throws Exception {
        String sql = """
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
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idRicetta);
            try (ResultSet rs = ps.executeQuery()) {
                List<SessionePresenza> out = new ArrayList<>();
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

                    // opzionale: set del corso (solo id)
                    Corso c = new Corso();
                    c.setIdCorso(rs.getLong("fk_id_corso"));
                    sp.setCorso(c);

                    out.add(sp);
                }
                return out;
            }
        }
    }

    /** Collega idRicetta -> idSessionePresenza (idempotente). */
    public void addSessione(long idRicetta, int idSessionePresenza) throws Exception {
        final String sql = """
            INSERT INTO sessione_presenza_ricetta (fk_id_sess_pr, fk_id_ricetta)
            VALUES (?, ?)
            ON CONFLICT (fk_id_sess_pr, fk_id_ricetta) DO NOTHING
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSessionePresenza);
            ps.setLong(2, idRicetta);
            ps.executeUpdate(); // ritorna 0 se la coppia è già presente (ok)
        }
    }

    /** Scollega idRicetta <-/-> idSessionePresenza. */
    public void removeSessione(long idRicetta, int idSessionePresenza) throws Exception {
        String sql = """
            DELETE FROM sessione_presenza_ricetta
             WHERE fk_id_sess_pr = ? AND fk_id_ricetta = ?
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSessionePresenza);
            ps.setLong(2, idRicetta);
            ps.executeUpdate();
        }
    }

    // ================== MAPPER ==================

    private Ricetta mapRow(ResultSet rs) throws SQLException {
        Ricetta r = new Ricetta();
        r.setIdRicetta(rs.getLong("id_ricetta"));
        r.setNome(rs.getString("nome"));
        r.setDescrizione(rs.getString("descrizione"));
        r.setDifficolta(rs.getString("difficolta")); // facile | medio | difficile
        r.setTempoPreparazione(rs.getInt("tempo_preparazione"));
        return r;
    }
}