package it.unina.foodlab.dao;

import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.Db;

import java.sql.*;
import java.time.LocalDate;

public class ChefDao {

    private static final String TBL_CHEF    = "chef";
    private static final String COL_CF      = "cf_chef";
    private static final String COL_USERNAME= "username";
    private static final String COL_NOME    = "nome";
    private static final String COL_COGNOME = "cognome";
    private static final String COL_PASSWORD= "password";
    private static final String COL_NASCITA = "data_nascita";

    public enum RegisterOutcome {
        OK,
        DUPLICATE_CF,
        DUPLICATE_USERNAME,
        ERROR
    }

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

        final String sql = "SELECT " + COL_CF + "," + COL_USERNAME + "," + COL_NOME + "," +
                COL_COGNOME + "," + COL_PASSWORD + "," + COL_NASCITA +
                " FROM " + TBL_CHEF + " WHERE " + COL_USERNAME + " = ? LIMIT 1";

        try (Connection cn = Db.get();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Chef chef = new Chef();
                chef.setCF_Chef(rs.getString(1));
                chef.setUsername(rs.getString(2));
                chef.setNome(rs.getString(3));
                chef.setCognome(rs.getString(4));
                chef.setPassword(rs.getString(5));

                Date d = rs.getDate(6);
                if (d != null) chef.setNascita(d.toLocalDate());

                return chef;
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
        final LocalDate nascita = c.getNascita();

        if (isBlank(cf) || isBlank(username) || isBlank(password)) {
            return RegisterOutcome.ERROR;
        }

        if (existsByCf(cf)) return RegisterOutcome.DUPLICATE_CF;
        if (existsByUsername(username)) return RegisterOutcome.DUPLICATE_USERNAME;

        final String sql = "INSERT INTO " + TBL_CHEF +
                " (" + COL_CF + "," + COL_USERNAME + "," + COL_NOME + "," +
                COL_COGNOME + "," + COL_PASSWORD + "," + COL_NASCITA + ") VALUES (?, ?, ?, ?, ?, ?)";

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
            return rows == 1 ? RegisterOutcome.OK : RegisterOutcome.ERROR;

        } catch (SQLException ex) {
            ex.printStackTrace();
            return RegisterOutcome.ERROR;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
