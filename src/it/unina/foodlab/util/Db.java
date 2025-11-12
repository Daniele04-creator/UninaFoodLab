package it.unina.foodlab.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class Db {

    private static String url;
    private static String user;
    private static String pwd;
    private static String schema;

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
            if (schema != null) {
                schema = schema.trim();
                if (schema.isEmpty()) schema = "public";
            } else {
                schema = "public";
            }
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
