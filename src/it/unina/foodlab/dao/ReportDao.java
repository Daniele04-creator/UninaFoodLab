package it.unina.foodlab.dao;

import it.unina.foodlab.util.Db;
import it.unina.foodlab.util.ReportMensile;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;

public class ReportDao {

    /**
     * Restituisce il report mensile per uno chef in un certo mese.
     * @param cfChef Codice fiscale/ID dello chef (come usato in corso.fk_cf_chef)
     * @param month  Mese (YearMonth) da analizzare
     */
	public ReportMensile getReportMensile(String cfChef, YearMonth month) throws Exception {
	    if (cfChef == null || cfChef.isBlank()) throw new IllegalArgumentException("cfChef mancante");
	    if (month == null) throw new IllegalArgumentException("month mancante");

	    LocalDate from = month.atDay(1);
	    LocalDate to   = month.atEndOfMonth();

	    // NOTE: adatta i nomi delle colonne/tabelle se nel tuo schema sono diversi.
	    String sql = """
	        WITH sess AS (
	            /* tutte le sessioni del mese, marcate col tipo */
	            SELECT so.idsessioneonline AS id_sessione, so.fk_id_corso, 'ONLINE'::text AS tipo
	            FROM sessione_online so
	            WHERE so.data BETWEEN ? AND ?   -- 1..2

	            UNION ALL

	            SELECT sp."idSessionePresenza" AS id_sessione, sp.fk_id_corso, 'PRATICA'::text AS tipo
	            FROM sessione_presenza sp
	            WHERE sp.data BETWEEN ? AND ?   -- 3..4
	        ),
	        ric AS (
	            /* quante ricette per ogni sessione PRATICA */
	            SELECT spr.fk_id_sess_pr AS id_sessione, COUNT(*) AS num_ricette
	            FROM sessione_presenza_ricetta spr
	            GROUP BY spr.fk_id_sess_pr
	        )
	        SELECT
	            COUNT(DISTINCT c.id_corso) AS totale_corsi,
	            SUM(CASE WHEN s.tipo = 'ONLINE'  THEN 1 ELSE 0 END) AS totale_online,
	            SUM(CASE WHEN s.tipo = 'PRATICA' THEN 1 ELSE 0 END) AS totale_pratiche,
	            AVG(CASE WHEN s.tipo = 'PRATICA' THEN COALESCE(r.num_ricette, 0) END) AS media_ricette,
	            MAX(CASE WHEN s.tipo = 'PRATICA' THEN COALESCE(r.num_ricette, 0) END) AS max_ricette,
	            MIN(CASE WHEN s.tipo = 'PRATICA' THEN COALESCE(r.num_ricette, 0) END) AS min_ricette
	        FROM corso c
	        JOIN sess s ON s.fk_id_corso = c.id_corso
	        LEFT JOIN ric r ON r.id_sessione = s.id_sessione
	        WHERE c.fk_cf_chef = ?            -- 5
	        """;

	    try (Connection conn = Db.get();
	         PreparedStatement ps = conn.prepareStatement(sql)) {
	        ps.setObject(1, from);
	        ps.setObject(2, to);
	        ps.setObject(3, from);
	        ps.setObject(4, to);
	        ps.setString(5, cfChef.trim());

	        try (ResultSet rs = ps.executeQuery()) {
	            if (rs.next()) {
	                int totaleCorsi     = rs.getInt("totale_corsi");
	                int totaleOnline    = rs.getInt("totale_online");
	                int totalePratiche  = rs.getInt("totale_pratiche");
	                BigDecimal mediaBD = rs.getBigDecimal("media_ricette");
	                Double media = (mediaBD == null ? null : mediaBD.doubleValue());
	                Number maxN = (Number) rs.getObject("max_ricette");
	                Number minN = (Number) rs.getObject("min_ricette");
	                Integer max = (maxN == null ? null : maxN.intValue());
	                Integer min = (minN == null ? null : minN.intValue());
	                return new ReportMensile(totaleCorsi, totaleOnline, totalePratiche, media, max, min);
	            }
	            return new ReportMensile(0, 0, 0, null, null, null);
	        }
	    }
	}
}