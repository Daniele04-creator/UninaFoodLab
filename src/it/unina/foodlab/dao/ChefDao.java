package it.unina.foodlab.dao;

import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.Db;

import java.sql.*;

public class ChefDao {

    /** Verifica se le credenziali corrispondono a un record nel DB */
    public boolean authenticate(String username, String password) throws SQLException {
        final String sql = "SELECT 1 FROM chef WHERE username=? AND password=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Recupera uno chef tramite username */
    public Chef findByUsername(String username) throws SQLException {
        final String sql = "SELECT CF_Chef, nome, cognome, data_nascita, username, password " +
                           "FROM chef WHERE username=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Chef chef = new Chef();
                chef.setCF_Chef(rs.getString("CF_Chef"));
                chef.setNome(rs.getString("nome"));
                chef.setCognome(rs.getString("cognome"));
                Date date = rs.getDate("data_nascita");
                if (date != null) chef.setNascita(date.toLocalDate());
                chef.setUsername(rs.getString("username"));
                chef.setPassword(rs.getString("password"));
                return chef;
            }
        }
    }

    /** Inserisce un nuovo chef */
    public void register(Chef chef) throws SQLException {
        final String sql = "INSERT INTO chef (nome, cognome, CF_Chef, username, password, data_nascita) " +
                           "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chef.getNome());
            ps.setString(2, chef.getCognome());
            ps.setString(3, chef.getCF_Chef());
            ps.setString(4, chef.getUsername());
            ps.setString(5, chef.getPassword());
            ps.setDate(6, java.sql.Date.valueOf(chef.getNascita()));
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Registrazione fallita: nessuna riga inserita.");
        }
    }
}
