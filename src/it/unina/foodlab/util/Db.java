package it.unina.foodlab.util;  
// Indica che questa classe si trova nel package "util" del progetto UninaFoodLab

import java.sql.*;         // Importa tutte le classi JDBC (Connection, DriverManager, Statement, SQLException, ecc.)
import java.io.InputStream; // Serve per leggere file come flussi di byte (es. db.properties)
import java.util.Properties; // Classe utile per gestire file di configurazione con coppie chiave=valore

public class Db {  
  // Classe di utilità per gestire la connessione al database

  // Variabili statiche che conterranno i parametri di connessione al DB
  private static String url, user, pwd, schema;

  // Blocco statico: viene eseguito automaticamente UNA SOLA VOLTA
  // quando la classe Db viene caricata in memoria
  static {
    try {
      // Carichiamo il driver JDBC di PostgreSQL
      // Questo passo dice a Java quale driver usare per "parlare" col DB
      Class.forName("org.postgresql.Driver");

      // Creiamo un oggetto Properties per leggere il file di configurazione
      Properties p = new Properties();

      // Apriamo il file "db.properties" che deve trovarsi nel classpath (es. cartella resources)
      try (InputStream in = Db.class.getClassLoader().getResourceAsStream("db.properties")) {
        // Se il file non viene trovato, lanciamo un errore
        if (in == null) throw new RuntimeException("db.properties non trovato nel classpath");

        // Carichiamo dentro "p" tutte le coppie chiave=valore dal file db.properties
        p.load(in);
      }

      // Recuperiamo i valori dal file di configurazione
      url = p.getProperty("url");              // es: jdbc:postgresql://localhost:5432/uninafoodlab
      user = p.getProperty("user");            // es: postgres
      pwd = p.getProperty("password");         // es: 1234
      schema = p.getProperty("schema", "public"); // se manca "schema" nel file, usa "public" come default

    } catch (Exception e) { 
      // Se qualcosa va storto (driver mancante, file non trovato, errore di lettura)
      // lanciamo una RuntimeException che blocca l'applicazione
      throw new RuntimeException(e); 
    }
  }

  // Metodo statico che restituisce una connessione attiva al database
  public static Connection get() throws SQLException {
    // Apriamo la connessione al DB con i parametri caricati
    Connection c = DriverManager.getConnection(url, user, pwd);

    // Subito dopo settiamo lo schema di default (così non dobbiamo scrivere schema.tabella ogni volta)
    try (Statement st = c.createStatement()) { 
      st.execute("set search_path to " + schema); 
    }

    // Restituiamo la connessione pronta per essere usata nei DAO
    return c;
  }
}