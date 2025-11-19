package it.unina.foodlab.dao;

import it.unina.foodlab.model.Corso;
import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.model.Sessione;
import it.unina.foodlab.model.SessioneOnline;
import it.unina.foodlab.model.SessionePresenza;
import it.unina.foodlab.util.Db;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class SessioneDao {
    private final String ownerCfChef;

    public SessioneDao(String ownerCfChef) {
        if (ownerCfChef == null || ownerCfChef.trim().isEmpty()) {
            throw new IllegalArgumentException("CF_Chef mancante per SessioneDao");
        }
        this.ownerCfChef = ownerCfChef.trim();
    }

    private static final String SQL_BASE_SELECT =
            "SELECT x.id, x.fk_id_corso, x.data, x.ora_inizio, x.ora_fine, x.tipo, " +
            "       x.piattaforma, x.via, x.num, x.cap, x.aula, x.posti_max " +
            "FROM ( " +
            "    SELECT so.idsessioneonline AS id, " +
            "           so.fk_id_corso, " +
            "           so.data, " +
            "           so.ora_inizio, " +
            "           so.ora_fine, " +
            "           'ONLINE' AS tipo, " +
            "           so.piattaforma, " +
            "           NULL AS via, " +
            "           NULL AS num, " +
            "           NULL AS cap, " +
            "           NULL AS aula, " +
            "           NULL AS posti_max " +
            "    FROM sessione_online so " +
            "    UNION ALL " +
            "    SELECT sp.\"idSessionePresenza\" AS id, " +
            "           sp.fk_id_corso, " +
            "           sp.data, " +
            "           sp.ora_inizio, " +
            "           sp.ora_fine, " +
            "           'PRESENZA' AS tipo, " +
            "           NULL AS piattaforma, " +
            "           sp.via, " +
            "           sp.num, " +
            "           sp.cap, " +
            "           sp.aula, " +
            "           sp.posti_max " +
            "    FROM sessione_presenza sp " +
            ") x " +
            "JOIN corso c ON c.id_corso = x.fk_id_corso ";


    private static final String SQL_FIND_RICETTE_BY_SESSP =
            "SELECT r.id_ricetta, r.nome, r.descrizione, r.difficolta, r.tempo_preparazione " +
            "  FROM sessione_presenza_ricetta spr " +
            "  JOIN ricetta r ON r.id_ricetta = spr.fk_id_ricetta " +
            "  JOIN sessione_presenza sp ON sp.\"idSessionePresenza\" = spr.fk_id_sess_pr " +
            "  JOIN corso c ON c.id_corso = sp.fk_id_corso " +
            " WHERE spr.fk_id_sess_pr = ? AND c.fk_cf_chef = ? " +
            " ORDER BY LOWER(r.nome)";

    private static final String SQL_ADD_LINK =
            "INSERT INTO sessione_presenza_ricetta (fk_id_sess_pr, fk_id_ricetta) " +
            "VALUES (?, ?) " +
            "ON CONFLICT (fk_id_sess_pr, fk_id_ricetta) DO NOTHING";

    private static final String SQL_DEL_LINK =
            "DELETE FROM sessione_presenza_ricetta " +
            " WHERE fk_id_sess_pr = ? AND fk_id_ricetta = ?";

    public List<Sessione> findByCorso(long corsoId) throws Exception {
        String sql = SQL_BASE_SELECT +
                " WHERE x.fk_id_corso = ? AND c.fk_cf_chef = ? " +
                " ORDER BY x.data, x.ora_inizio";

        List<Sessione> out = new ArrayList<>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, corsoId);
            ps.setString(2, ownerCfChef);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        }
        return out;
    }

    public List<Ricetta> findRicetteBySessionePresenza(int idSessionePresenza) throws Exception {
        List<Ricetta> out = new ArrayList<>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_RICETTE_BY_SESSP)) {

            ps.setInt(1, idSessionePresenza);
            ps.setString(2, ownerCfChef);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRicetta(rs));
                }
            }
        }
        return out;
    }

    public void addRicettaToSessionePresenza(int idSessionePresenza, long idRicetta) throws Exception {
        if (!existsPresenzaForOwner(idSessionePresenza)) {
            throw new SQLException("Operazione negata: sessione non dell'owner");
        }
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_ADD_LINK)) {

            ps.setInt(1, idSessionePresenza);
            ps.setLong(2, idRicetta);
            ps.executeUpdate();
        }
    }

    public void removeRicettaFromSessionePresenza(int idSessionePresenza, long idRicetta) throws Exception {
        if (!existsPresenzaForOwner(idSessionePresenza)) {
            throw new SQLException("Operazione negata: sessione non dell'owner");
        }
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_DEL_LINK)) {

            ps.setInt(1, idSessionePresenza);
            ps.setLong(2, idRicetta);
            ps.executeUpdate();
        }
    }

    public void replaceForCorso(long corsoId, List<Sessione> nuove) throws Exception {
        ensureCourseOwned(corsoId);

        try (Connection conn = Db.get()) {
            conn.setAutoCommit(false);
            try {
                String delLinks =
                        "DELETE FROM sessione_presenza_ricetta " +
                        " WHERE fk_id_sess_pr IN (" +
                        "   SELECT sp.\"idSessionePresenza\" FROM sessione_presenza sp WHERE sp.fk_id_corso = ?" +
                        " )";
                try (PreparedStatement ps = conn.prepareStatement(delLinks)) {
                    ps.setLong(1, corsoId);
                    ps.executeUpdate();
                }

                String delOnline = "DELETE FROM sessione_online WHERE fk_id_corso = ?";
                try (PreparedStatement ps = conn.prepareStatement(delOnline)) {
                    ps.setLong(1, corsoId);
                    ps.executeUpdate();
                }

                String delPresenza = "DELETE FROM sessione_presenza WHERE fk_id_corso = ?";
                try (PreparedStatement ps = conn.prepareStatement(delPresenza)) {
                    ps.setLong(1, corsoId);
                    ps.executeUpdate();
                }

                if (nuove != null) {
                    for (Sessione s : nuove) {
                        if (s == null) {
                            continue;
                        }

                        if (s.getCorso() == null || s.getCorso().getIdCorso() != corsoId) {
                            Corso c = new Corso();
                            c.setIdCorso(corsoId);
                            s.setCorso(c);
                        }

                        insertOn(conn, s);
                    }
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ignore) {
                }
            }
        }
    }

    private void bindCommon(PreparedStatement ps, Sessione s) throws SQLException {
        if (s.getCorso() == null || s.getCorso().getIdCorso() <= 0) {
            throw new SQLException("corso_id mancante/inesistente");
        }
        if (s.getData() == null) {
            throw new SQLException("data obbligatoria");
        }
        if (s.getOraInizio() == null || s.getOraFine() == null) {
            throw new SQLException("ora_inizio/ora_fine obbligatorie");
        }
        if (!s.getOraFine().isAfter(s.getOraInizio())) {
            throw new SQLException("ora_fine deve essere successiva a ora_inizio");
        }

        ps.setLong(1, s.getCorso().getIdCorso());
        ps.setObject(2, s.getData());
        ps.setObject(3, s.getOraInizio());
        ps.setObject(4, s.getOraFine());
    }

    private Sessione mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        long corsoId = rs.getLong("fk_id_corso");
        LocalDate data = rs.getObject("data", LocalDate.class);
        LocalTime inizio = rs.getObject("ora_inizio", LocalTime.class);
        LocalTime fine = rs.getObject("ora_fine", LocalTime.class);
        String tipo = rs.getString("tipo");

        Corso corso = new Corso();
        corso.setIdCorso(corsoId);

        if ("ONLINE".equalsIgnoreCase(tipo)) {
            String piattaforma = rs.getString("piattaforma");
            return new SessioneOnline(id, data, inizio, fine, corso, piattaforma);
        } else {
            String via = rs.getString("via");
            String num = rs.getString("num");

            int cap = rs.getInt("cap");
            if (rs.wasNull()) cap = 0;

            String aula = rs.getString("aula");

            int posti = rs.getInt("posti_max");
            if (rs.wasNull()) posti = 0;

            return new SessionePresenza(id, data, inizio, fine, corso, via, num, cap, aula, posti);
        }
    }

    private void ensureCourseOwned(long corsoId) throws Exception {
        String sql = "SELECT 1 FROM corso WHERE id_corso=? AND fk_cf_chef=?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, corsoId);
            ps.setString(2, ownerCfChef);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Operazione negata: corso non dell'owner");
                }
            }
        }
    }

   private boolean existsPresenzaForOwner(int idSessionePresenza) throws Exception {
    String sql =
            "SELECT 1 " +
            "  FROM sessione_presenza sp " +
            "  JOIN corso c ON c.id_corso = sp.fk_id_corso " +
            " WHERE sp.\"idSessionePresenza\" = ? AND c.fk_cf_chef = ? " +
            " LIMIT 1";

    try (Connection conn = Db.get();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, idSessionePresenza);
        ps.setString(2, ownerCfChef);

        try (ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }
}


    private Ricetta mapRicetta(ResultSet rs) throws SQLException {
        Ricetta r = new Ricetta();
        r.setIdRicetta(rs.getLong("id_ricetta"));
        r.setNome(rs.getString("nome"));
        r.setDescrizione(rs.getString("descrizione"));
        r.setDifficolta(rs.getString("difficolta"));

        int tp = rs.getInt("tempo_preparazione");
        r.setTempoPreparazione(rs.wasNull() ? 0 : tp);

        return r;
    }

    private String nullSafe(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private int insertOn(Connection conn, Sessione s) throws Exception {
        if (s instanceof SessioneOnline) {
            SessioneOnline so = (SessioneOnline) s;
            String sql =
                    "INSERT INTO sessione_online " +
                    " (fk_id_corso, data, ora_inizio, ora_fine, piattaforma) " +
                    "VALUES (?,?,?,?,?) " +
                    "RETURNING idsessioneonline";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindCommon(ps, s);
                ps.setString(5, nullSafe(so.getPiattaforma()));

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int id = rs.getInt(1);
                    s.setId(id);
                    return id;
                }
            }
        } else if (s instanceof SessionePresenza) {
            SessionePresenza sp = (SessionePresenza) s;
            String sql =
                    "INSERT INTO sessione_presenza " +
                    " (fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max) " +
                    "VALUES (?,?,?,?,?,?,?,?,?) " +
                    "RETURNING \"idSessionePresenza\"";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindCommon(ps, s);
                ps.setString(5, nullSafe(sp.getVia()));
                ps.setString(6, nullSafe(sp.getNum()));

                if (sp.getCap() == 0) {
                    ps.setNull(7, Types.INTEGER);
                } else {
                    ps.setInt(7, sp.getCap());
                }

                ps.setString(8, nullSafe(sp.getAula()));

                if (sp.getPostiMax() == 0) {
                    ps.setNull(9, Types.INTEGER);
                } else {
                    ps.setInt(9, sp.getPostiMax());
                }

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int id = rs.getInt(1);
                    s.setId(id);
                    return id;
                }
            }
        } else {
            throw new SQLException("Tipo sessione non supportato: " + s.getClass());
        }
    }
}
