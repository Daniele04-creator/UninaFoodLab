package it.unina.foodlab.dao;

import it.unina.foodlab.util.Db;
import it.unina.foodlab.util.ReportMensile;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Report mensile per Chef (range semichiuso [primoGiorno, primoGiornoMeseSuccessivo) ).
 * Codice lineare e commentato; nessun uso di feature avanzate.
 */
public class ReportDao {

    private static final String SQL_REPORT_MENSILE = """
        WITH sess AS (
            -- Tutte le sessioni del mese, marcate col tipo
            SELECT so.idsessioneonline AS id_sessione, so.fk_id_corso, 'ONLINE'::text AS tipo
              FROM sessione_online so
             WHERE so.data >= ? AND so.data < ?

            UNION ALL
            SELECT sp."idSessionePresenza" AS id_sessione, sp.fk_id_corso, 'PRATICA'::text AS tipo
              FROM sessione_presenza sp
             WHERE sp.data >= ? AND sp.data < ?
        ),
        ric AS (
            -- Ricette per ogni sessione PRATICA
            SELECT spr.fk_id_sess_pr AS id_sessione, COUNT(*)::int AS num_ricette
              FROM sessione_presenza_ricetta spr
             GROUP BY spr.fk_id_sess_pr
        ),
        joined AS (
            SELECT s.*, c.id_corso
              FROM sess s
              JOIN corso c ON c.id_corso = s.fk_id_corso
             WHERE c.fk_cf_chef = ?
        )
        SELECT
            COUNT(DISTINCT j.id_corso)                                           AS totale_corsi,
            COUNT(*) FILTER (WHERE j.tipo = 'ONLINE')                             AS totale_online,
            COUNT(*) FILTER (WHERE j.tipo = 'PRATICA')                            AS totale_pratiche,
            AVG( COALESCE(r.num_ricette, 0) ) FILTER (WHERE j.tipo = 'PRATICA')   AS media_ricette,
            MAX( COALESCE(r.num_ricette, 0) ) FILTER (WHERE j.tipo = 'PRATICA')   AS max_ricette,
            MIN( COALESCE(r.num_ricette, 0) ) FILTER (WHERE j.tipo = 'PRATICA')   AS min_ricette
          FROM joined j
          LEFT JOIN ric r ON r.id_sessione = j.id_sessione
    """;

    /** Calcola il report per cfChef nel mese indicato. */
    public ReportMensile getReportMensile(String cfChef, YearMonth month) throws Exception {
        if (cfChef == null || cfChef.trim().isEmpty()) throw new IllegalArgumentException("cfChef mancante");
        if (month == null) throw new IllegalArgumentException("month mancante");

        LocalDate startDate = month.atDay(1);
        LocalDate startNext = month.plusMonths(1).atDay(1);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to   = startNext.atStartOfDay();

        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(SQL_REPORT_MENSILE)) {

            // 1..2 (ONLINE) 3..4 (PRESENZA) 5(owner)
            ps.setObject(1, from);
            ps.setObject(2, to);
            ps.setObject(3, from);
            ps.setObject(4, to);
            ps.setString(5, cfChef.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int totaleCorsi    = rs.getInt("totale_corsi");
                    int totaleOnline   = rs.getInt("totale_online");
                    int totalePratiche = rs.getInt("totale_pratiche");

                    BigDecimal mediaBD = rs.getBigDecimal("media_ricette");
                    Double media = (mediaBD == null ? null : mediaBD.doubleValue());

                    Number maxN = (Number) rs.getObject("max_ricette");
                    Number minN = (Number) rs.getObject("min_ricette");
                    Integer max = (maxN == null ? null : maxN.intValue());
                    Integer min = (minN == null ? null : minN.intValue());

                    return new ReportMensile(totaleCorsi, totaleOnline, totalePratiche, media, max, min);
                }
            }
        }
        // Nessuna riga => tutto zero/null
        return new ReportMensile(0, 0, 0, null, null, null);
    }
}
