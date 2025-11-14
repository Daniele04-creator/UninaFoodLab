package it.unina.foodlab.dao;

import it.unina.foodlab.model.Ricetta;
import it.unina.foodlab.util.Db;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RicettaDao {

    private static final String SQL_FIND_ALL =
            "SELECT id_ricetta, nome, descrizione, difficolta, tempo_preparazione " +
            "FROM ricetta ORDER BY LOWER(nome)";

    public RicettaDao() {}

    public List<Ricetta> findAll() throws Exception {
        List<Ricetta> out = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = Db.get();
            ps = conn.prepareStatement(SQL_FIND_ALL);
            rs = ps.executeQuery();
            while (rs.next()) out.add(mapRow(rs));
            return out;
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
            closeQuiet(conn);
        }
    }

    private Ricetta mapRow(ResultSet rs) throws SQLException {
        Ricetta r = new Ricetta();
        r.setIdRicetta(rs.getLong("id_ricetta"));
        r.setNome(rs.getString("nome"));
        r.setDescrizione(rs.getString("descrizione"));
        r.setDifficolta(rs.getString("difficolta"));
        r.setTempoPreparazione(rs.getInt("tempo_preparazione"));
        return r;
    }

    private static void closeQuiet(AutoCloseable c) {
        if (c != null) try { c.close(); } catch (Exception ignore) {}
    }
}
