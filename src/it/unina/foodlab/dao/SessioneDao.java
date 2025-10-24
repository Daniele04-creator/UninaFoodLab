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

	
	private static final String TBL_ONLINE = "sessione_online";
	private static final String TBL_PRESENZA = "sessione_presenza";
	private static final String TBL_CORSO = "corso";
	private static final String TBL_RICETTA = "ricetta";
	private static final String TBL_LINK = "sessione_presenza_ricetta";

	private static final String COL_FK_SESS = "fk_id_sess_pr"; 
	private static final String COL_FK_RIC = "fk_id_ricetta";

	private final String ownerCfChef;

	public SessioneDao(String ownerCfChef) {
		if (ownerCfChef == null || ownerCfChef.trim().isEmpty()) {
			throw new IllegalArgumentException("CF_Chef mancante per SessioneDao");
		}
		this.ownerCfChef = ownerCfChef.trim();
	}

	private static final String SQL_BASE_SELECT = ("""
	    SELECT x.id, x.fk_id_corso, x.data, x.ora_inizio, x.ora_fine, x.tipo,
	           x.piattaforma, x.via, x.num, x.cap, x.aula, x.posti_max
	      FROM (
	            SELECT so.idsessioneonline AS id, so.fk_id_corso, so.data, so.ora_inizio, so.ora_fine,
	                   'ONLINE'::varchar AS tipo,
	                   so.piattaforma,
	                   NULL::varchar AS via, NULL::varchar AS num, NULL::integer AS cap,
	                   NULL::varchar AS aula, NULL::integer AS posti_max
	              FROM %s so
	            UNION ALL
	            SELECT sp."idSessionePresenza" AS id, sp.fk_id_corso, sp.data, sp.ora_inizio, sp.ora_fine,
	                   'PRESENZA'::varchar AS tipo,
	                   NULL::varchar AS piattaforma,
	                   sp.via, sp.num, sp.cap, sp.aula, sp.posti_max
	              FROM %s sp
	      ) x
	      JOIN %s c ON c.id_corso = x.fk_id_corso
	    """).formatted(TBL_ONLINE, TBL_PRESENZA, TBL_CORSO);


	private static final String SQL_FIND_RICETTE_BY_SESSP = ("""
			SELECT r.id_ricetta, r.nome, r.descrizione, r.difficolta, r.tempo_preparazione
			  FROM %s spr
			  JOIN %s r ON r.id_ricetta = spr.%s
			  JOIN %s sp ON sp."idSessionePresenza" = spr.%s
			  JOIN %s c ON c.id_corso = sp.fk_id_corso
			 WHERE spr.%s = ? AND c.fk_cf_chef = ?
			 ORDER BY LOWER(r.nome)
			""").formatted(TBL_LINK, TBL_RICETTA, COL_FK_RIC, TBL_PRESENZA, COL_FK_SESS, TBL_CORSO, COL_FK_SESS);

	private static final String SQL_ADD_LINK = ("""
			INSERT INTO %s (%s, %s)
			VALUES (?, ?)
			ON CONFLICT (%s, %s) DO NOTHING
			""").formatted(TBL_LINK, COL_FK_SESS, COL_FK_RIC, COL_FK_SESS, COL_FK_RIC);

	private static final String SQL_DEL_LINK = ("""
			DELETE FROM %s
			 WHERE %s = ? AND %s = ?
			""").formatted(TBL_LINK, COL_FK_SESS, COL_FK_RIC);


	public Sessione findById(int id) throws Exception {
		String sql = SQL_BASE_SELECT + " WHERE x.id = ? AND c.fk_cf_chef = ?";
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, id);
			ps.setString(2, ownerCfChef);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? mapRow(rs) : null;
			}
		}
	}

	public List<Sessione> findByCorso(long corsoId) throws Exception {
		String sql = SQL_BASE_SELECT + " WHERE x.fk_id_corso = ? AND c.fk_cf_chef = ? ORDER BY x.data, x.ora_inizio";
		List<Sessione> out = new ArrayList<Sessione>();
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setLong(1, corsoId);
			ps.setString(2, ownerCfChef);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next())
					out.add(mapRow(rs));
			}
		}
		return out;
	}

	public List<Sessione> findByCorsoAndDate(long corsoId, LocalDate from, LocalDate to) throws Exception {
		String sql = SQL_BASE_SELECT
				+ " WHERE x.fk_id_corso=? AND c.fk_cf_chef=? AND x.data BETWEEN ? AND ? ORDER BY x.data, x.ora_inizio";
		List<Sessione> out = new ArrayList<Sessione>();
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setLong(1, corsoId);
			ps.setString(2, ownerCfChef);
			ps.setObject(3, from);
			ps.setObject(4, to);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next())
					out.add(mapRow(rs));
			}
		}
		return out;
	}

	public List<Sessione> findAll() throws Exception {
		String sql = SQL_BASE_SELECT + " WHERE c.fk_cf_chef=? ORDER BY x.data, x.ora_inizio";
		List<Sessione> out = new ArrayList<Sessione>();
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, ownerCfChef);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next())
					out.add(mapRow(rs));
			}
		}
		return out;
	}



	public int insert(Sessione s) throws Exception {
		ensureCourseOwned(s.getCorso().getIdCorso());
		try (Connection conn = Db.get()) {
			if (s instanceof SessioneOnline) {
				SessioneOnline so = (SessioneOnline) s;
				String sql = ("""
						    INSERT INTO %s (fk_id_corso, data, ora_inizio, ora_fine, piattaforma)
						    VALUES (?,?,?,?,?)
						    RETURNING idsessioneonline
						""").formatted(TBL_ONLINE);
				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					bindCommon(ps, s);
					ps.setString(5, nullSafe(so.getPiattaforma()));
					try (ResultSet rs = ps.executeQuery()) {
						rs.next();
						return rs.getInt(1);
					}
				}
			} else if (s instanceof SessionePresenza) {
				SessionePresenza sp = (SessionePresenza) s;
				String sql = ("""
						    INSERT INTO %s (fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max)
						    VALUES (?,?,?,?,?,?,?,?,?)
						    RETURNING "idSessionePresenza"
						""").formatted(TBL_PRESENZA);
				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					bindCommon(ps, s);
					ps.setString(5, nullSafe(sp.getVia()));
					ps.setString(6, nullSafe(sp.getNum()));
					if (sp.getCap() == 0)
						ps.setNull(7, Types.INTEGER);
					else
						ps.setInt(7, sp.getCap());
					ps.setString(8, nullSafe(sp.getAula()));
					if (sp.getPostiMax() == 0)
						ps.setNull(9, Types.INTEGER);
					else
						ps.setInt(9, sp.getPostiMax());
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
		if (s.getId() <= 0)
			throw new IllegalArgumentException("id sessione mancante");
		if (!existsForOwner(s.getId()))
			throw new SQLException("Update negato: sessione non dell'owner");

		try (Connection conn = Db.get()) {
			if (s instanceof SessioneOnline) {
				SessioneOnline so = (SessioneOnline) s;
				String sql = ("""
						    UPDATE %s
						       SET fk_id_corso=?, data=?, ora_inizio=?, ora_fine=?, piattaforma=?
						     WHERE idsessioneonline=?
						""").formatted(TBL_ONLINE);
				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					bindCommon(ps, s);
					ps.setString(5, nullSafe(so.getPiattaforma()));
					ps.setInt(6, s.getId());
					if (ps.executeUpdate() != 1)
						throw new SQLException("Update fallito o nessuna riga ONLINE (id=" + s.getId() + ")");
				}
			} else if (s instanceof SessionePresenza) {
				SessionePresenza sp = (SessionePresenza) s;
				String sql = ("""
						    UPDATE %s
						       SET fk_id_corso=?, data=?, ora_inizio=?, ora_fine=?, via=?, num=?, cap=?, aula=?, posti_max=?
						     WHERE "idSessionePresenza"=?
						""")
						.formatted(TBL_PRESENZA);
				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					bindCommon(ps, s);
					ps.setString(5, nullSafe(sp.getVia()));
					ps.setString(6, nullSafe(sp.getNum()));
					if (sp.getCap() == 0)
						ps.setNull(7, Types.INTEGER);
					else
						ps.setInt(7, sp.getCap());
					ps.setString(8, nullSafe(sp.getAula()));
					if (sp.getPostiMax() == 0)
						ps.setNull(9, Types.INTEGER);
					else
						ps.setInt(9, sp.getPostiMax());
					ps.setInt(10, s.getId());
					if (ps.executeUpdate() != 1)
						throw new SQLException("Update fallito o nessuna riga PRESENZA (id=" + s.getId() + ")");
				}
			} else {
				throw new SQLException("Tipo sessione non supportato: " + s.getClass());
			}
		}
	}

	public void delete(int id) throws Exception {
		if (!existsForOwner(id))
			throw new SQLException("Delete negato: sessione non dell'owner");

		try (Connection conn = Db.get()) {
			int tot = 0;
		
			String sqlDelOnline = ("""
					    DELETE FROM %s
					     WHERE idsessioneonline=? AND fk_id_corso IN (SELECT id_corso FROM %s WHERE fk_cf_chef=?)
					""").formatted(TBL_ONLINE, TBL_CORSO);
			try (PreparedStatement ps = conn.prepareStatement(sqlDelOnline)) {
				ps.setInt(1, id);
				ps.setString(2, ownerCfChef);
				tot += ps.executeUpdate();
			}
		
			String sqlDelLinks = ("""
					    DELETE FROM %s WHERE %s=?
					""").formatted(TBL_LINK, COL_FK_SESS);
			try (PreparedStatement ps = conn.prepareStatement(sqlDelLinks)) {
				ps.setInt(1, id);
				ps.executeUpdate();
			}
			String sqlDelPresenza = ("""
					    DELETE FROM %s
					     WHERE "idSessionePresenza"=? AND fk_id_corso IN (SELECT id_corso FROM %s WHERE fk_cf_chef=?)
					""").formatted(TBL_PRESENZA, TBL_CORSO);
			try (PreparedStatement ps = conn.prepareStatement(sqlDelPresenza)) {
				ps.setInt(1, id);
				ps.setString(2, ownerCfChef);
				tot += ps.executeUpdate();
			}
			if (tot == 0)
				throw new SQLException("Nessuna riga eliminata (id=" + id + ")");
		}
	}


	public void saveAll(List<Sessione> sessioni) throws Exception {
		if (sessioni == null || sessioni.isEmpty())
			return;

		try (Connection conn = Db.get()) {
			boolean old = conn.getAutoCommit();
			conn.setAutoCommit(false);
			try {
				for (int i = 0; i < sessioni.size(); i++) {
					Sessione s = sessioni.get(i);
					if (s.getId() == 0)
						insertOn(conn, s);
					else
						updateOn(conn, s);
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


	public List<Ricetta> findRicetteBySessionePresenza(int idSessionePresenza) throws Exception {
		List<Ricetta> out = new ArrayList<Ricetta>();
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(SQL_FIND_RICETTE_BY_SESSP)) {
			ps.setInt(1, idSessionePresenza);
			ps.setString(2, ownerCfChef);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next())
					out.add(mapRicetta(rs));
			}
		}
		return out;
	}

	public void addRicettaToSessionePresenza(int idSessionePresenza, long idRicetta) throws Exception {
		if (!existsPresenzaForOwner(idSessionePresenza))
			throw new SQLException("Operazione negata: sessione non dell'owner");
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(SQL_ADD_LINK)) {
			ps.setInt(1, idSessionePresenza);
			ps.setLong(2, idRicetta);
			ps.executeUpdate(); 
		}
	}

	public void removeRicettaFromSessionePresenza(int idSessionePresenza, long idRicetta) throws Exception {
		if (!existsPresenzaForOwner(idSessionePresenza))
			throw new SQLException("Operazione negata: sessione non dell'owner");
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(SQL_DEL_LINK)) {
			ps.setInt(1, idSessionePresenza);
			ps.setLong(2, idRicetta);
			ps.executeUpdate();
		}
	}
	
	public void replaceRicetteForSessionePresenza(int idSessionePresenza, List<Long> idRicette) throws Exception {
		if (idSessionePresenza <= 0)
			throw new IllegalArgumentException("idSessionePresenza non valido");

		String chkSql = ("""
				    SELECT 1
				      FROM %s sp
				      JOIN %s c ON c.id_corso = sp.fk_id_corso
				     WHERE sp."idSessionePresenza"=? AND c.fk_cf_chef=?
				""").formatted(TBL_PRESENZA, TBL_CORSO);
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(chkSql)) {
			ps.setInt(1, idSessionePresenza);
			ps.setString(2, ownerCfChef);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					throw new SQLException("Operazione negata: sessione non dell'owner");
			}
		}

		try (Connection conn = Db.get()) {
			boolean old = conn.getAutoCommit();
			conn.setAutoCommit(false);
			try {
				String delSql = ("""
						    DELETE FROM %s WHERE %s=?
						""").formatted(TBL_LINK, COL_FK_SESS);
				try (PreparedStatement del = conn.prepareStatement(delSql)) {
					del.setInt(1, idSessionePresenza);
					del.executeUpdate();
				}

				if (idRicette != null && !idRicette.isEmpty()) {
					String insSql = ("""
							    INSERT INTO %s (%s, %s) VALUES (?, ?)
							""").formatted(TBL_LINK, COL_FK_SESS, COL_FK_RIC);
					try (PreparedStatement ins = conn.prepareStatement(insSql)) {
						for (int i = 0; i < idRicette.size(); i++) {
							Long idr = idRicette.get(i);
							if (idr == null || idr.longValue() <= 0L)
								continue;
							ins.setInt(1, idSessionePresenza);
							ins.setLong(2, idr.longValue());
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

	
	public void replaceForCorso(long corsoId, List<Sessione> nuove) throws Exception {
		ensureCourseOwned(corsoId);

		try (Connection conn = Db.get()) {
			boolean old = conn.getAutoCommit();
			conn.setAutoCommit(false);
			try {
				
				String delLinks = ("""
						    DELETE FROM %s
						     WHERE %s IN (
						           SELECT sp."idSessionePresenza"
						             FROM %s sp
						            WHERE sp.fk_id_corso = ?
						     )
						""").formatted(TBL_LINK, COL_FK_SESS, TBL_PRESENZA);
				try (PreparedStatement ps = conn.prepareStatement(delLinks)) {
					ps.setLong(1, corsoId);
					ps.executeUpdate();
				}

				
				String delOnline = ("""
						    DELETE FROM %s WHERE fk_id_corso = ?
						""").formatted(TBL_ONLINE);
				try (PreparedStatement ps = conn.prepareStatement(delOnline)) {
					ps.setLong(1, corsoId);
					ps.executeUpdate();
				}
				String delPresenza = ("""
						    DELETE FROM %s WHERE fk_id_corso = ?
						""").formatted(TBL_PRESENZA);
				try (PreparedStatement ps = conn.prepareStatement(delPresenza)) {
					ps.setLong(1, corsoId);
					ps.executeUpdate();
				}

		
				if (nuove != null) {
					for (int i = 0; i < nuove.size(); i++) {
						Sessione s = nuove.get(i);
						if (s == null)
							continue;

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
				conn.setAutoCommit(old);
			}
		}
	}

	

	private void bindCommon(PreparedStatement ps, Sessione s) throws SQLException {
		if (s.getCorso() == null || s.getCorso().getIdCorso() <= 0)
			throw new SQLException("corso_id mancante/inesistente");
		if (s.getData() == null)
			throw new SQLException("data obbligatoria");
		if (s.getOraInizio() == null || s.getOraFine() == null)
			throw new SQLException("ora_inizio/ora_fine obbligatorie");
		if (!s.getOraFine().isAfter(s.getOraInizio()))
			throw new SQLException("ora_fine deve essere successiva a ora_inizio");

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
			if (rs.wasNull())
				cap = 0;
			String aula = rs.getString("aula");
			int posti = rs.getInt("posti_max");
			if (rs.wasNull())
				posti = 0;
			return new SessionePresenza(id, data, inizio, fine, corso, via, num, cap, aula, posti);
		}
	}

	private void ensureCourseOwned(long corsoId) throws Exception {
		String sql = "SELECT 1 FROM corso WHERE id_corso=? AND fk_cf_chef=?";
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setLong(1, corsoId);
			ps.setString(2, ownerCfChef);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					throw new SQLException("Operazione negata: corso non dell'owner");
			}
		}
	}

	private boolean existsForOwner(int sessioneId) throws Exception {
		String sql = ("""
				    SELECT 1
				      FROM %s so
				      JOIN %s c ON c.id_corso = so.fk_id_corso
				     WHERE so.idsessioneonline=? AND c.fk_cf_chef=?
				    UNION ALL
				    SELECT 1
				      FROM %s sp
				      JOIN %s c ON c.id_corso = sp.fk_id_corso
				     WHERE sp."idSessionePresenza"=? AND c.fk_cf_chef=?
				    LIMIT 1
				""").formatted(TBL_ONLINE, TBL_CORSO, TBL_PRESENZA, TBL_CORSO);
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, sessioneId);
			ps.setString(2, ownerCfChef);
			ps.setInt(3, sessioneId);
			ps.setString(4, ownerCfChef);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private boolean existsPresenzaForOwner(int idSessionePresenza) throws Exception {
		String sql = ("""
				    SELECT 1
				      FROM %s sp
				      JOIN %s c ON c.id_corso = sp.fk_id_corso
				     WHERE sp."idSessionePresenza"=? AND c.fk_cf_chef=?
				     LIMIT 1
				""").formatted(TBL_PRESENZA, TBL_CORSO);
		try (Connection conn = Db.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
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

	public String getOwnerCfChef() {
		return ownerCfChef;
	}



	private int insertOn(Connection conn, Sessione s) throws Exception {
		if (s instanceof SessioneOnline) {
			SessioneOnline so = (SessioneOnline) s;
			String sql = ("""
					    INSERT INTO %s (fk_id_corso, data, ora_inizio, ora_fine, piattaforma)
					    VALUES (?,?,?,?,?)
					    RETURNING idsessioneonline
					""").formatted(TBL_ONLINE);
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
			String sql = ("""
					    INSERT INTO %s (
					        fk_id_corso, data, ora_inizio, ora_fine, via, num, cap, aula, posti_max
					    ) VALUES (?,?,?,?,?,?,?,?,?)
					    RETURNING "idSessionePresenza"
					""").formatted(TBL_PRESENZA);
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				bindCommon(ps, s);
				ps.setString(5, nullSafe(sp.getVia()));
				ps.setString(6, nullSafe(sp.getNum()));
				if (sp.getCap() == 0)
					ps.setNull(7, Types.INTEGER);
				else
					ps.setInt(7, sp.getCap());
				ps.setString(8, nullSafe(sp.getAula()));
				if (sp.getPostiMax() == 0)
					ps.setNull(9, Types.INTEGER);
				else
					ps.setInt(9, sp.getPostiMax());
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

	private void updateOn(Connection conn, Sessione s) throws Exception {
		if (s.getId() <= 0)
			throw new IllegalArgumentException("id sessione mancante");
		if (s instanceof SessioneOnline) {
			SessioneOnline so = (SessioneOnline) s;
			String sql = ("""
					    UPDATE %s
					       SET fk_id_corso=?, data=?, ora_inizio=?, ora_fine=?, piattaforma=?
					     WHERE idsessioneonline=?
					""").formatted(TBL_ONLINE);
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				bindCommon(ps, s);
				ps.setString(5, nullSafe(so.getPiattaforma()));
				ps.setInt(6, s.getId());
				ps.executeUpdate();
			}
		} else if (s instanceof SessionePresenza) {
			SessionePresenza sp = (SessionePresenza) s;
			String sql = ("""
					    UPDATE %s
					       SET fk_id_corso=?, data=?, ora_inizio=?, ora_fine=?, via=?, num=?, cap=?, aula=?, posti_max=?
					     WHERE "idSessionePresenza"=?
					""").formatted(TBL_PRESENZA);
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				bindCommon(ps, s);
				ps.setString(5, nullSafe(sp.getVia()));
				ps.setString(6, nullSafe(sp.getNum()));
				if (sp.getCap() == 0)
					ps.setNull(7, Types.INTEGER);
				else
					ps.setInt(7, sp.getCap());
				ps.setString(8, nullSafe(sp.getAula()));
				if (sp.getPostiMax() == 0)
					ps.setNull(9, Types.INTEGER);
				else
					ps.setInt(9, sp.getPostiMax());
				ps.setInt(10, s.getId());
				ps.executeUpdate();
			}
		} else {
			throw new SQLException("Tipo sessione non supportato: " + s.getClass());
		}
	}
}
