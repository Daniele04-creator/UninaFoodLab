package it.unina.foodlab.dao;

import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.Db;

import java.sql.*;


public class ChefDao {

	private static final String TBL_CHEF       = "chef"; 
	private static final String COL_CF         = "cf_chef";
	private static final String COL_USERNAME   = "username";
	private static final String COL_NOME       = "nome";
	private static final String COL_COGNOME    = "cognome";
	private static final String COL_PASSWORD   = "password";
	private static final String COL_NASCITA    = "data_nascita";

	public enum RegisterOutcome { OK, DUPLICATE_CF, DUPLICATE_USERNAME, ERROR }

	public boolean authenticate(String username, String rawPassword) throws SQLException {
		if (isBlank(username) || isBlank(rawPassword)) return false;
		final String sql = "SELECT 1 FROM " + TBL_CHEF +
				" WHERE " + COL_USERNAME + " = ? AND " + COL_PASSWORD + " = ? LIMIT 1";
		try (Connection cn = Db.get();
				PreparedStatement ps = cn.prepareStatement(sql)) {
			ps.setString(1, username.trim());
			ps.setString(2, rawPassword);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	public Chef findByUsername(String username) throws SQLException {
		if (isBlank(username)) return null;
		final String sql = "SELECT " + COL_CF + "," + COL_USERNAME + "," + COL_NOME + "," + COL_COGNOME + "," + COL_PASSWORD + "," + COL_NASCITA +
				" FROM " + TBL_CHEF + " WHERE " + COL_USERNAME + " = ? LIMIT 1";
		try (Connection cn = Db.get();
				PreparedStatement ps = cn.prepareStatement(sql)) {
			ps.setString(1, username.trim());
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) return null;
				Chef c = new Chef();
				c.setCF_Chef(rs.getString(1));
				c.setUsername(rs.getString(2));
				c.setNome(rs.getString(3));
				c.setCognome(rs.getString(4));
				c.setPassword(rs.getString(5));
				Date d = rs.getDate(6);
				if (d != null) c.setNascita(d.toLocalDate());
				return c;
			}
		}
	}

	public boolean existsByCf(String cf) throws SQLException {
		if (isBlank(cf)) return false;
		final String sql = "SELECT 1 FROM " + TBL_CHEF + " WHERE " + COL_CF + " = ? LIMIT 1";
		try (Connection cn = Db.get();
				PreparedStatement ps = cn.prepareStatement(sql)) {
			ps.setString(1, cf.trim());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	public boolean existsByUsername(String username) throws SQLException {
		if (isBlank(username)) return false;
		final String sql = "SELECT 1 FROM " + TBL_CHEF + " WHERE " + COL_USERNAME + " = ? LIMIT 1";
		try (Connection cn = Db.get();
				PreparedStatement ps = cn.prepareStatement(sql)) {
			ps.setString(1, username.trim());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	public RegisterOutcome register(Chef c) throws SQLException {
		if (c == null) return RegisterOutcome.ERROR;
		final String cf       = safeTrim(c.getCF_Chef());
		final String username = safeTrim(c.getUsername());
		final String nome     = safeTrim(c.getNome());
		final String cognome  = safeTrim(c.getCognome());
		final String password = safeTrim(c.getPassword());
		final java.time.LocalDate nascita = c.getNascita();
		if (isBlank(cf) || isBlank(username) || isBlank(password)) return RegisterOutcome.ERROR;
		if (existsByCf(cf))             return RegisterOutcome.DUPLICATE_CF;
		if (existsByUsername(username)) return RegisterOutcome.DUPLICATE_USERNAME;

		final String sql = "INSERT INTO " + TBL_CHEF +
				" (" + COL_CF + "," + COL_USERNAME + "," + COL_NOME + "," + COL_COGNOME + "," + COL_PASSWORD + "," + COL_NASCITA + ") " +
				"VALUES (?, ?, ?, ?, ?, ?)";

		try (Connection cn = Db.get();
				PreparedStatement ps = cn.prepareStatement(sql)) {
			ps.setString(1, cf);
			ps.setString(2, username);
			ps.setString(3, nome);
			ps.setString(4, cognome);
			ps.setString(5, password);
			if (nascita != null) {
				ps.setDate(6, Date.valueOf(nascita));
			} else {
				ps.setNull(6, Types.DATE);
			}
			int rows = ps.executeUpdate();
			return (rows == 1) ? RegisterOutcome.OK : RegisterOutcome.ERROR;
		} catch (SQLException ex) {
			if ("23505".equals(ex.getSQLState())) {
				String lc = getConstraintName(ex);
				if (lc != null) {
					lc = lc.toLowerCase();
					if (lc.contains("pkey") || lc.contains(COL_CF.toLowerCase())) {
						return RegisterOutcome.DUPLICATE_CF;
					}
					if (lc.contains(COL_USERNAME.toLowerCase())) {
						return RegisterOutcome.DUPLICATE_USERNAME;
					}
				}
			}
			throw ex;
		}
	}

	private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
	private static String safeTrim(String s) { return (s == null) ? "" : s.trim(); }

	private static String getConstraintName(SQLException ex) {
		try {
			Class<?> psqlEx = Class.forName("org.postgresql.util.PSQLException");
			if (psqlEx.isInstance(ex)) {
				Object serverMsg = ex.getClass().getMethod("getServerErrorMessage").invoke(ex);
				if (serverMsg != null) {
					Object c = serverMsg.getClass().getMethod("getConstraint").invoke(serverMsg);
					return (c == null) ? null : c.toString();
				}
			}
		} catch (Exception ignore) { }
		return null;
	}
}
