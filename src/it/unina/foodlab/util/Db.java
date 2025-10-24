package it.unina.foodlab.util;  


import java.sql.*;         
import java.io.InputStream; 
import java.util.Properties; 

public class Db {  
  

  
  private static String url, user, pwd, schema;

  static {
    try {

      Class.forName("org.postgresql.Driver");

      
      Properties p = new Properties();

      
      try (InputStream in = Db.class.getClassLoader().getResourceAsStream("db.properties")) {
        if (in == null) throw new RuntimeException("db.properties non trovato nel classpath");

        p.load(in);
      }

      
      url = p.getProperty("url");              
      user = p.getProperty("user");            
      pwd = p.getProperty("password");         
      schema = p.getProperty("schema", "public"); 

    } catch (Exception e) { 
      throw new RuntimeException(e); 
    }
  }

  
  public static Connection get() throws SQLException {
    
    Connection c = DriverManager.getConnection(url, user, pwd);

    try (Statement st = c.createStatement()) { 
      st.execute("set search_path to " + schema); 
    }

    
    return c;
  }
}