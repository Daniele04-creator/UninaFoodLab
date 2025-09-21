package it.unina.foodlab.dao;
import it.unina.foodlab.model.Chef;
import it.unina.foodlab.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ChefDao {

  // Metodo che controlla se username e password corrispondono ad un record nel DB.
  // Restituisce true se le credenziali sono corrette, false altrimenti.
  public boolean authenticate(String username, String password) throws Exception {

    // Query parametrica: controlla se esiste almeno un record con username e password uguali a quelli passati.
    // "select 1" significa che non ci interessa scaricare i dati completi dello chef,
    // ci basta sapere se almeno un record corrisponde (ottimizzazione).
    String sql = "select 1 from chef where username=? and password=?";

    // Apertura connessione e preparazione query
    // try-with-resources chiuderà automaticamente la Connection e il PreparedStatement.
    try (Connection c = Db.get();
         PreparedStatement ps = c.prepareStatement(sql)) {

      // Sostituiamo i "?" con i valori passati come parametri al metodo.
      ps.setString(1, username); // Primo parametro "?" → username
      ps.setString(2, password); // Secondo parametro "?" → password

      // Eseguiamo la query e otteniamo il ResultSet
      try (ResultSet rs = ps.executeQuery()) {
        // rs.next() ritorna true se esiste almeno una riga → credenziali valide
        return rs.next();
      }
    }
  }
  public Chef findByUsername(String username) throws Exception {
      String sql = "SELECT CF_Chef, nome, cognome, data_nascita, username, password FROM chef WHERE username=?";
      try (Connection c = Db.get();
           PreparedStatement ps = c.prepareStatement(sql)) {
          ps.setString(1, username);
          try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                  Chef chef = new Chef();
                  chef.setCF_Chef(rs.getString("CF_Chef"));
                  chef.setNome(rs.getString("nome"));
                  chef.setCognome(rs.getString("cognome"));
                  chef.setNascita(rs.getDate("data_nascita").toLocalDate());
                  chef.setUsername(rs.getString("username"));
                  chef.setPassword(rs.getString("password"));
                  return chef;
              }
              return null;
          }
      }
  }
}