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
import java.util.Comparator;
import java.util.List;

public class SessioneDao {

    private final String ownerCfChef;

    public SessioneDao(String ownerCfChef) {
        if (ownerCfChef == null || ownerCfChef.isBlank())
            throw new IllegalArgumentException("CF_Chef mancante per SessioneDao");
        this.ownerCfChef = ownerCfChef.trim();
    }

    // ========================= READ =========================

    public Sessione findById(int id) throws Exception {
        String sql = baseSelect() + " WHERE x.id = ? AND c.fk_cf_chef = ?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Sessione> findByCorso(long corsoId) throws Exception {
        String sql = baseSelect() + " WHERE x.fk_id_corso = ? AND c.fk_cf_chef = ? ORDER BY x.data, x.ora_inizio";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, corsoId);
            ps.setString(2, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                List<Sessione> out = new ArrayList<>();
                while (rs.next()) out.add(mapRow(rs));
                return out;
            }
        }
    }

    public List<Sessione> findByCorsoAndDate(long corsoId, LocalDate from, LocalDate to) throws Exception {
        String sql = baseSelect() + " WHERE x.fk_id_corso=? AND c.fk_cf_chef=? AND x.data BETWEEN ? AND ? ORDER BY x.data, x.ora_inizio";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, corsoId);
            ps.setString(2, ownerCfChef);
            ps.setObject(3, from);
            ps.setObject(4, to);
            try (ResultSet rs = ps.executeQuery()) {
                List<Sessione> out = new ArrayList<>();
                while (rs.next()) out.add(mapRow(rs));
                return out;
            }
        }
    }

    public List<Sessione> findAll() throws Exception {
        String sql = baseSelect() + " WHERE c.fk_cf_chef=? ORDER BY x.data, x.ora_inizio";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                List<Sessione> out = new ArrayList<>();
                while (rs.next()) out.add(mapRow(rs));
                return out;
            }
        }
    }

    // ========================= WRITE =========================

    public int insert(Sessione s) throws Exception {
        ensureCourseOwned(s.getCorso().getIdCorso());
        try (Connection conn = Db.get()) {
            if (s instanceof SessioneOnline so) {
                String sql = """
                    INSERT INTO sessione_online (fk_id_corso, data, ora_inizio, ora_fine, piattaforma)
                    VALUES (?,?,?,?,?)
                    RETURNING idsessioneonline
                """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindCommon(ps, s); // 1..4
                    ps.setString(5, nullSafe(so.getPiattaforma()));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getInt(1);
                    }
                }
            } else if (s instanceof SessionePresenza sp) {
                String sql = """
                    INSERT INTO sessione_presenza (fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    RETURNING "idSessionePresenza"
                """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindCommon(ps, s); // 1..4
                    ps.setString(5, nullSafe(sp.getVia()));
                    ps.setString(6, nullSafe(sp.getNum()));
                    if (sp.getCap() == 0) ps.setNull(7, Types.INTEGER); else ps.setInt(7, sp.getCap());
                    ps.setString(8, nullSafe(sp.getAula()));
                    if (sp.getPostiMax() == 0) ps.setNull(9, Types.INTEGER); else ps.setInt(9, sp.getPostiMax());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getInt(1);
                    }
                }
            } else {
                throw new SQLException("Tipo sessione non supportato: " + s.getClass());
            }
        }
    }

    public void update(Sessione s) throws Exception {
        if (s.getId() <= 0) throw new IllegalArgumentException("id sessione mancante");
        if (!existsForOwner(s.getId())) throw new SQLException("Update negato: sessione non dell'owner");

        try (Connection conn = Db.get()) {
            if (s instanceof SessioneOnline so) {
                String sql = """
                    UPDATE sessione_online
                       SET fk_id_corso=?, data=?, ora_inizio=?, ora_fine=?, piattaforma=?
                     WHERE idsessioneonline=?
                """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindCommon(ps, s); // 1..4
                    ps.setString(5, nullSafe(so.getPiattaforma()));
                    ps.setInt(6, s.getId());
                    int n = ps.executeUpdate();
                    if (n != 1) throw new SQLException("Update fallito o nessuna riga ONLINE (id=" + s.getId() + ")");
                }
            } else if (s instanceof SessionePresenza sp) {
                String sql = """
                    UPDATE sessione_presenza
                       SET fk_id_corso=?, data=?, ora_inizio=?, ora_fine=?, via=?, num=?, cap=?, aula=?, posti_max=?
                     WHERE "idSessionePresenza"=?
                """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindCommon(ps, s); // 1..4
                    ps.setString(5, nullSafe(sp.getVia()));
                    ps.setString(6, nullSafe(sp.getNum()));
                    if (sp.getCap() == 0) ps.setNull(7, Types.INTEGER); else ps.setInt(7, sp.getCap());
                    ps.setString(8, nullSafe(sp.getAula()));
                    if (sp.getPostiMax() == 0) ps.setNull(9, Types.INTEGER); else ps.setInt(9, sp.getPostiMax());
                    ps.setInt(10, s.getId());
                    int n = ps.executeUpdate();
                    if (n != 1) throw new SQLException("Update fallito o nessuna riga PRESENZA (id=" + s.getId() + ")");
                }
            } else {
                throw new SQLException("Tipo sessione non supportato: " + s.getClass());
            }
        }
    }

    public void delete(int id) throws Exception {
        if (!existsForOwner(id)) throw new SQLException("Delete negato: sessione non dell'owner");

        try (Connection conn = Db.get()) {
            int tot = 0;
            // ONLINE
            try (PreparedStatement ps = conn.prepareStatement("""
                DELETE FROM sessione_online
                 WHERE idsessioneonline=? AND fk_id_corso IN (SELECT id_corso FROM corso WHERE fk_cf_chef=?)
            """)) {
                ps.setInt(1, id);
                ps.setString(2, ownerCfChef);
                tot += ps.executeUpdate();
            }
            // PRESENZA (prima pulisco la tabella ponte)
            try (PreparedStatement ps = conn.prepareStatement("""
                DELETE FROM sessione_presenza_ricetta WHERE fk_id_sess_pr=?
            """)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                DELETE FROM sessione_presenza
                 WHERE "idSessionePresenza"=? AND fk_id_corso IN (SELECT id_corso FROM corso WHERE fk_cf_chef=?)
            """)) {
                ps.setInt(1, id);
                ps.setString(2, ownerCfChef);
                tot += ps.executeUpdate();
            }
            if (tot == 0) throw new SQLException("Nessuna riga eliminata (id=" + id + ")");
        }
    }

    /** Salvataggio batch in transazione: insert se id==0, altrimenti update. */
    public void saveAll(List<Sessione> sessioni) throws Exception {
        if (sessioni == null || sessioni.isEmpty()) return;
        try (Connection conn = Db.get()) {
            boolean old = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (Sessione s : sessioni) {
                    if (s.getId() == 0) insert(s);
                    else update(s);
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(old);
            }
        }
    }

    // ========================= ASSOCIAZIONE RICETTE =========================

    /** Ricette collegate a una sessione pratica dell'owner. */
    public List<Ricetta> findRicetteBySessionePresenza(int idSessionePresenza) throws Exception {
        String sql = """
            SELECT r.id_ricetta, r.nome, r.descrizione, r.difficolta, r.tempo_preparazione
              FROM sessione_presenza_ricetta spr
              JOIN ricetta r ON r.id_ricetta = spr.fk_id_ricetta
              JOIN sessione_presenza sp ON sp."idSessionePresenza" = spr.fk_id_sess_pr
              JOIN corso c ON c.id_corso = sp.fk_id_corso
             WHERE spr.fk_id_sess_pr = ? AND c.fk_cf_chef = ?
             ORDER BY LOWER(r.nome)
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSessionePresenza);
            ps.setString(2, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                List<Ricetta> out = new ArrayList<>();
                while (rs.next()) out.add(mapRicetta(rs));
                return out;
            }
        }
    }

    /** Aggiunge un’associazione (idempotente) tra sessione presenza e ricetta. */
    public void addRicettaToSessionePresenza(int idSessionePresenza, long idRicetta) throws Exception {
        if (!existsPresenzaForOwner(idSessionePresenza))
            throw new SQLException("Operazione negata: sessione non dell'owner");

        final String sql = """
            INSERT INTO sessione_presenza_ricetta (fk_id_sess_pr, fk_id_ricetta)
            VALUES (?, ?)
            ON CONFLICT (fk_id_sess_pr, fk_id_ricetta) DO NOTHING
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSessionePresenza);
            ps.setLong(2, idRicetta);
            ps.executeUpdate();
        }
    }

    /** Rimuove una singola ricetta associata alla sessione presenza. */
    public void removeRicettaFromSessionePresenza(int idSessionePresenza, long idRicetta) throws Exception {
        if (!existsPresenzaForOwner(idSessionePresenza))
            throw new SQLException("Operazione negata: sessione non dell'owner");

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

    /** Sostituisce tutte le ricette della sessione pratica (DELETE + INSERT batch). */
    public void replaceRicetteForSessione(Sessione s, List<Ricetta> ricette) throws Exception {
        if (s == null || s.getId() <= 0) throw new IllegalArgumentException("Sessione non valida");
        if (!existsForOwner(s.getId())) throw new SQLException("Operazione negata: sessione non dell'owner");
        if (s instanceof SessioneOnline) return;
        if (!(s instanceof SessionePresenza)) throw new SQLException("Tipo sessione non supportato");

        try (Connection conn = Db.get()) {
            boolean old = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM sessione_presenza_ricetta WHERE fk_id_sess_pr = ?")) {
                    del.setInt(1, s.getId());
                    del.executeUpdate();
                }
                if (ricette != null && !ricette.isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO sessione_presenza_ricetta (fk_id_sess_pr, fk_id_ricetta) VALUES (?, ?)")) {
                        for (Ricetta r : ricette) {
                            if (r == null || r.getIdRicetta() <= 0) continue;
                            ins.setInt(1, s.getId());
                            ins.setLong(2, r.getIdRicetta());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(old);
            }
        }
    }

    // ========================= HELPERS =========================

    private void bindCommon(PreparedStatement ps, Sessione s) throws SQLException {
        if (s.getCorso() == null || s.getCorso().getIdCorso() <= 0)
            throw new SQLException("corso_id mancante/inesistente");
        if (s.getData() == null) throw new SQLException("data obbligatoria");
        if (s.getOraInizio() == null || s.getOraFine() == null)
            throw new SQLException("ora_inizio/ora_fine obbligatorie");
        if (!s.getOraFine().isAfter(s.getOraInizio()))
            throw new SQLException("ora_fine deve essere successiva a ora_inizio");

        ps.setLong(1, s.getCorso().getIdCorso());
        ps.setObject(2, s.getData());
        ps.setObject(3, s.getOraInizio());
        ps.setObject(4, s.getOraFine());
    }

    private String baseSelect() {
        // x = unione delle due tabelle con colonna 'id' normalizzata
        return """
            SELECT x.id, x.fk_id_corso, x.data, x.ora_inizio, x.ora_fine, x.tipo,
                   x.piattaforma, x.via, x.num, x.cap, x.aula, x.posti_max
              FROM (
                    SELECT so.idsessioneonline AS id, so.fk_id_corso, so.data, so.ora_inizio, so.ora_fine,
                           'ONLINE'::varchar AS tipo,
                           so.piattaforma,
                           NULL::varchar AS via, NULL::varchar AS num, NULL::integer AS cap,
                           NULL::varchar AS aula, NULL::integer AS posti_max
                      FROM sessione_online so
                    UNION ALL
                    SELECT sp."idSessionePresenza" AS id, sp.fk_id_corso, sp.data, sp.ora_inizio, sp.ora_fine,
                           'PRESENZA'::varchar AS tipo,
                           NULL::varchar AS piattaforma,
                           sp.via, sp.num, sp.cap, sp.aula, sp.posti_max
                      FROM sessione_presenza sp
              ) x
              JOIN corso c ON c.id_corso = x.fk_id_corso
        """;
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
            int cap = rs.getInt("cap"); if (rs.wasNull()) cap = 0;
            String aula = rs.getString("aula");
            int posti = rs.getInt("posti_max"); if (rs.wasNull()) posti = 0;
            return new SessionePresenza(id, data, inizio, fine, corso, via, num, cap, aula, posti);
        }
    }

    private boolean existsForOwner(int sessioneId) throws Exception {
        String sql = """
            SELECT 1
              FROM sessione_online so
              JOIN corso c ON c.id_corso = so.fk_id_corso
             WHERE so.idsessioneonline=? AND c.fk_cf_chef=?
            UNION ALL
            SELECT 1
              FROM sessione_presenza sp
              JOIN corso c ON c.id_corso = sp.fk_id_corso
             WHERE sp."idSessionePresenza"=? AND c.fk_cf_chef=?
            LIMIT 1
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessioneId);
            ps.setString(2, ownerCfChef);
            ps.setInt(3, sessioneId);
            ps.setString(4, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void ensureCourseOwned(long corsoId) throws Exception {
        String sql = "SELECT 1 FROM corso WHERE id_corso=? AND fk_cf_chef=?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, corsoId);
            ps.setString(2, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new SQLException("Operazione negata: corso non dell'owner");
            }
        }
    }

    private String nullSafe(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public String getOwnerCfChef() { return ownerCfChef; }

    /** Verifica che la sessione PRESENZA (id) appartenga a un corso dell'owner. */
    private boolean existsPresenzaForOwner(int idSessionePresenza) throws Exception {
        String sql = """
            SELECT 1
              FROM sessione_presenza sp
              JOIN corso c ON c.id_corso = sp.fk_id_corso
             WHERE sp."idSessionePresenza" = ? AND c.fk_cf_chef = ?
             LIMIT 1
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSessionePresenza);
            ps.setString(2, ownerCfChef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Mapper leggero per Ricetta. */
    private Ricetta mapRicetta(ResultSet rs) throws SQLException {
        Ricetta r = new Ricetta();
        r.setIdRicetta(rs.getLong("id_ricetta"));
        r.setNome(rs.getString("nome"));
        r.setDescrizione(rs.getString("descrizione"));
        r.setDifficolta(rs.getString("difficolta")); // facile|medio|difficile
        int tp = rs.getInt("tempo_preparazione");
        r.setTempoPreparazione(rs.wasNull() ? 0 : tp);
        return r;
    }

    // ========================= RESCHEDULING (facoltativo) =========================

    public void rescheduleByCourse(Corso corso) throws Exception {
        if (corso == null || corso.getIdCorso() <= 0) {
            throw new IllegalArgumentException("Corso mancante");
        }
        if (corso.getDataInizio() == null) {
            throw new IllegalArgumentException("Data inizio mancante");
        }
        if (corso.getNumSessioni() <= 0) {
            throw new IllegalArgumentException("Numero sessioni non valido");
        }

        ensureCourseOwned(corso.getIdCorso());

        // Genera le nuove date senza dipendere da UI o vecchia Scheduling
        List<LocalDate> dates = generateDates(
                corso.getDataInizio(),
                corso.getFrequenza(),
                corso.getNumSessioni()
        );
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("Frequenza non valida: " + corso.getFrequenza());
        }

        List<Sessione> cur = findByCorso(corso.getIdCorso());
        cur.sort(Comparator.comparing(Sessione::getData));

        try (Connection conn = Db.get()) {
            boolean old = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // 1) aggiorna le sessioni comuni
                int common = Math.min(cur.size(), dates.size());
                for (int i = 0; i < common; i++) {
                    Sessione s = cur.get(i);
                    LocalDate nuovaData = dates.get(i);
                    if (s instanceof SessioneOnline) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE sessione_online SET data=? WHERE idsessioneonline=?")) {
                            ps.setObject(1, nuovaData);
                            ps.setInt(2, s.getId());
                            ps.executeUpdate();
                        }
                    } else if (s instanceof SessionePresenza) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE sessione_presenza SET data=? WHERE \"idSessionePresenza\"=?")) {
                            ps.setObject(1, nuovaData);
                            ps.setInt(2, s.getId());
                            ps.executeUpdate();
                        }
                    }
                }

                // 2) aggiunge eventuali nuove sessioni (qui default PRESENZA)
                for (int i = common; i < dates.size(); i++) {
                    LocalDate d = dates.get(i);
                    try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO sessione_presenza
                            (fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max)
                        VALUES (?, ?, '10:00', '12:00', NULL, NULL, NULL, NULL, NULL)
                    """)) {
                        ps.setLong(1, corso.getIdCorso());
                        ps.setObject(2, d);
                        ps.executeUpdate();
                    }
                }

                // 3) rimuove l’eccedenza se il nuovo piano ha meno date
                for (int i = cur.size() - 1; i >= dates.size(); i--) {
                    Sessione s = cur.get(i);
                    if (s instanceof SessionePresenza) {
                        try (PreparedStatement ps1 = conn.prepareStatement(
                                "DELETE FROM sessione_presenza_ricetta WHERE fk_id_sess_pr=?")) {
                            ps1.setInt(1, s.getId());
                            ps1.executeUpdate();
                        }
                        try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM sessione_presenza WHERE \"idSessionePresenza\"=?")) {
                            ps.setInt(1, s.getId());
                            ps.executeUpdate();
                        }
                    } else { // online
                        try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM sessione_online WHERE idsessioneonline=?")) {
                            ps.setInt(1, s.getId());
                            ps.executeUpdate();
                        }
                    }
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(old);
            }
        }
    }

    /* =========================
       Helper locali (no UI deps)
       ========================= */

    private static String canonicalizeFreq(String s) {
        if (s == null) return "";
        String x = s.trim().toLowerCase(java.util.Locale.ITALIAN);
        // ammesse: giornaliero, ogni 2 giorni, settimanale, mensile
        if (x.equals("ogni 2 giorni") || x.equals("ogni due giorni")) return "ogni 2 giorni";
        if (x.equals("settimanale")) return "settimanale";
        if (x.equals("mensile")) return "mensile";
        return "";
    }

    private static List<LocalDate> generateDates(LocalDate inizio, String freq, int num) {
        ArrayList<LocalDate> out = new ArrayList<>();
        if (inizio == null || num <= 0) return out;

        String f = canonicalizeFreq(freq);
        if (f.isEmpty()) return out;

        LocalDate d = inizio;
        for (int i = 0; i < num; i++) {
            out.add(d);
            switch (f) {
                case "ogni 2 giorni" -> d = d.plusDays(2);
                case "settimanale"   -> d = d.plusDays(7);
                case "mensile"       -> d = d.plusMonths(1);
            }
        }
        return out;
    }

    
    public void replaceRicetteForSessionePresenza(int idSessionePresenza, java.util.List<Long> idRicette) throws Exception {
        if (idSessionePresenza <= 0) throw new IllegalArgumentException("idSessionePresenza non valido");

        // Verifica che la sessione appartenga a un corso dell'owner
        String chkSql = """
            SELECT 1
              FROM sessione_presenza sp
              JOIN corso c ON c.id_corso = sp.fk_id_corso
             WHERE sp."idSessionePresenza"=? AND c.fk_cf_chef=?
        """;
        try (java.sql.Connection conn = it.unina.foodlab.util.Db.get();
             java.sql.PreparedStatement ps = conn.prepareStatement(chkSql)) {
            ps.setInt(1, idSessionePresenza);
            ps.setString(2, ownerCfChef);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new java.sql.SQLException("Operazione negata: sessione non dell'owner");
            }
        }

        // Transazione: DELETE tutte le associazioni + INSERT delle nuove (se presenti)
        try (java.sql.Connection conn = it.unina.foodlab.util.Db.get()) {
            boolean old = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (java.sql.PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM sessione_presenza_ricetta WHERE fk_id_sess_pr=?")) {
                    del.setInt(1, idSessionePresenza);
                    del.executeUpdate();
                }

                if (idRicette != null && !idRicette.isEmpty()) {
                    try (java.sql.PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO sessione_presenza_ricetta (fk_id_sess_pr, fk_id_ricetta) VALUES (?, ?)")) {
                        for (Long idr : idRicette) {
                            if (idr == null || idr <= 0) continue;
                            ins.setInt(1, idSessionePresenza);
                            ins.setLong(2, idr);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(old);
            }
        }
    }
    public void updateOnline(it.unina.foodlab.model.SessioneOnline s) throws Exception {
        try (Connection conn = it.unina.foodlab.util.Db.get();
             PreparedStatement ps = conn.prepareStatement("""
                UPDATE sessione_online
                   SET data = ?, ora_inizio = ?, ora_fine = ?, piattaforma = ?
                 WHERE idsessioneonline = ?
             """)) {
            ps.setObject(1, s.getData());
            ps.setObject(2, s.getOraInizio());
            ps.setObject(3, s.getOraFine());
            ps.setString(4, s.getPiattaforma());
            ps.setInt(5, s.getId());
            int n = ps.executeUpdate();
            if (n != 1) throw new SQLException("Sessione online non aggiornata (id=" + s.getId() + ")");
        }
    }

    public void updatePresenza(it.unina.foodlab.model.SessionePresenza s) throws Exception {
        try (Connection conn = it.unina.foodlab.util.Db.get();
             PreparedStatement ps = conn.prepareStatement("""
                UPDATE sessione_presenza
                   SET data = ?, ora_inizio = ?, ora_fine = ?, via = ?, num = ?, cap = ?, aula = ?, posti_max = ?
                 WHERE "idSessionePresenza" = ?
             """)) {
            ps.setObject(1, s.getData());
            ps.setObject(2, s.getOraInizio());
            ps.setObject(3, s.getOraFine());
            ps.setString(4, s.getVia());
            ps.setString(5, s.getNum());
            ps.setInt(6, s.getCap());          // INT NOT NULL
            ps.setString(7, s.getAula());
            ps.setInt(8, s.getPostiMax());     // INT NOT NULL
            ps.setInt(9, s.getId());
            int n = ps.executeUpdate();
            if (n != 1) throw new SQLException("Sessione presenza non aggiornata (id=" + s.getId() + ")");
        }
    }
    
    /** Sostituisce TUTTE le sessioni di un corso con la lista data (DELETE + INSERT in transazione). */
    public void replaceForCorso(long corsoId, List<Sessione> nuove) throws Exception {
        ensureCourseOwned(corsoId);

        try (Connection conn = Db.get()) {
            boolean old = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // 1) Elimina ricette collegate alle sessioni PRESENZA del corso
                try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE FROM sessione_presenza_ricetta
                     WHERE fk_id_sess_pr IN (
                           SELECT sp."idSessionePresenza"
                             FROM sessione_presenza sp
                            WHERE sp.fk_id_corso = ?
                     )
                """)) {
                    ps.setLong(1, corsoId);
                    ps.executeUpdate();
                }

                // 2) Elimina sessioni del corso (ONLINE e PRESENZA)
                try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE FROM sessione_online WHERE fk_id_corso = ?
                """)) {
                    ps.setLong(1, corsoId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE FROM sessione_presenza WHERE fk_id_corso = ?
                """)) {
                    ps.setLong(1, corsoId);
                    ps.executeUpdate();
                }

                // 3) Inserisce le nuove (se presenti)
                if (nuove != null) {
                    for (Sessione s : nuove) {
                        // normalizza corso/id
                        if (s.getCorso() == null || s.getCorso().getIdCorso() != corsoId) {
                            Corso c = new Corso();
                            c.setIdCorso(corsoId);
                            s.setCorso(c); // assicurati che esista setCorso(...) nel modello
                        }
                        insertOn(conn, s); // usa la stessa connessione/tx
                    }
                }

                conn.commit();
                conn.setAutoCommit(old);
            } catch (Exception ex) {
                conn.rollback();
                conn.setAutoCommit(old);
                throw ex;
            }
        }
    }

    /** INSERT su connessione esistente (evita di spezzare la transazione). */
    private int insertOn(Connection conn, Sessione s) throws Exception {
        // riusa le stesse validazioni di bindCommon
        if (s instanceof SessioneOnline so) {
            String sql = """
                INSERT INTO sessione_online (fk_id_corso, data, ora_inizio, ora_fine, piattaforma)
                VALUES (?,?,?,?,?)
                RETURNING idsessioneonline
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindCommon(ps, s); // 1..4
                ps.setString(5, nullSafe(so.getPiattaforma()));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int id = rs.getInt(1);
                    s.setId(id); // se hai il setter
                    return id;
                }
            }
        } else if (s instanceof SessionePresenza sp) {
            String sql = """
                INSERT INTO sessione_presenza (
                    fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max
                ) VALUES (?,?,?,?,?,?,?,?,?)
                RETURNING "idSessionePresenza"
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindCommon(ps, s); // 1..4
                ps.setString(5, nullSafe(sp.getVia()));
                ps.setString(6, nullSafe(sp.getNum()));
                if (sp.getCap() == 0) ps.setNull(7, Types.INTEGER); else ps.setInt(7, sp.getCap());
                ps.setString(8, nullSafe(sp.getAula()));
                if (sp.getPostiMax() == 0) ps.setNull(9, Types.INTEGER); else ps.setInt(9, sp.getPostiMax());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int id = rs.getInt(1);
                    s.setId(id); // se hai il setter
                    return id;
                }
            }
        } else {
            throw new SQLException("Tipo sessione non supportato: " + s.getClass());
        }
    }


}