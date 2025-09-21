package it.unina.foodlab.dao;

import it.unina.foodlab.model.Chef;
import it.unina.foodlab.model.Corso;
import it.unina.foodlab.util.Db;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CorsoDao {

    
    private final String ownerCfChef;

    public CorsoDao(String ownerCfChef) {
        if (ownerCfChef == null || ownerCfChef.isBlank()) {
            throw new IllegalArgumentException("CF_Chef mancante per CorsoDao");
        }
        this.ownerCfChef = ownerCfChef.trim();
    }

    
    public List<Corso> findAll() throws Exception {
        String sql = """
            SELECT c.id_corso, c.data_inizio, c.data_fine, c.argomento, c.frequenza, c."numSessioni" AS num_sessioni,
                   ch.CF_Chef, ch.nome, ch.cognome, ch.username, ch.password
            FROM corso c
            JOIN chef ch ON c.fk_cf_chef = ch.CF_Chef
            ORDER BY c.data_inizio DESC
        """;
        List<Corso> out = new ArrayList<>();
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapRow(rs));
        }
        return out;
    }

    public Corso findById(long id) throws Exception {
        String sql = """
            SELECT c.id_corso, c.data_inizio, c.data_fine, c.argomento, c.frequenza, c."numSessioni" AS num_sessioni,
                   ch.CF_Chef, ch.nome, ch.cognome, ch.username, ch.password
            FROM corso c
            JOIN chef ch ON c.fk_cf_chef = ch.CF_Chef
            WHERE c.id_corso = ?
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    
    public long insert(Corso corso) throws Exception {
        String sql = """
            INSERT INTO corso (data_inizio, data_fine, argomento, frequenza, "numSessioni", fk_cf_chef)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id_corso
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindWithoutOwner(ps, corso);
            ps.setString(6, ownerCfChef); 
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                throw new SQLException("Insert corso: ID non restituito");
            }
        }
    }

    
    public void update(Corso corso) throws Exception {
        if (corso.getIdCorso() <= 0) throw new IllegalArgumentException("idCorso mancante");
        String sql = """
            UPDATE corso
               SET data_inizio=?, data_fine=?, argomento=?, frequenza=?, "numSessioni"=?
             WHERE id_corso=? AND fk_cf_chef=?
        """;
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindWithoutOwner(ps, corso);
            ps.setLong(6, corso.getIdCorso());
            ps.setString(7, ownerCfChef);
            int n = ps.executeUpdate();
            if (n != 1) throw new SQLException("Update negato o nessuna riga (id=" + corso.getIdCorso() + ")");
        }
    }

    
    public void delete(long id) throws Exception {
        String sql = "DELETE FROM corso WHERE id_corso=? AND fk_cf_chef=?";
        try (Connection conn = Db.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, ownerCfChef);
            int n = ps.executeUpdate();
            if (n != 1) throw new SQLException("Delete negato o nessuna riga (id=" + id + ")");
        }
    }

    /* ====== UTILS ====== */

    private Corso mapRow(ResultSet rs) throws SQLException {
        Corso c = new Corso();
        c.setIdCorso(rs.getLong("id_corso"));
        Date di = rs.getDate("data_inizio");
        Date df = rs.getDate("data_fine");
        c.setDataInizio(di != null ? di.toLocalDate() : null);
        c.setDataFine(df != null ? df.toLocalDate() : null);
        c.setArgomento(rs.getString("argomento"));
        c.setFrequenza(rs.getString("frequenza"));
        int ns = rs.getInt("num_Sessioni");
        if (rs.wasNull()) ns = 1;
        c.setNumSessioni(ns);

        Chef chef = new Chef();
        chef.setCF_Chef(rs.getString("CF_Chef"));
        chef.setNome(rs.getString("nome"));
        chef.setCognome(rs.getString("cognome"));
        chef.setUsername(rs.getString("username"));
        chef.setPassword(rs.getString("password"));
        c.setChef(chef);
        return c;
    }

    
    private void bindWithoutOwner(PreparedStatement ps, Corso c) throws SQLException {
        LocalDate di = c.getDataInizio();
        LocalDate df = c.getDataFine();

        if (di == null || df == null) {
            throw new SQLException("data_inizio e data_fine sono obbligatorie");
        }
        if (c.getArgomento() == null || c.getArgomento().isBlank()) {
            throw new SQLException("argomento obbligatorio");
        }
        if (c.getFrequenza() == null || c.getFrequenza().isBlank()) {
            throw new SQLException("frequenza obbligatoria");
        }

        Integer ns = c.getNumSessioni(); 
        if (ns == null || ns < 1) {
            throw new SQLException("numSessioni obbligatorio e >= 1");
        }

        ps.setDate(1, Date.valueOf(di));
        ps.setDate(2, Date.valueOf(df));
        ps.setString(3, c.getArgomento());
        ps.setString(4, c.getFrequenza());
        ps.setInt(5, ns);
    }

    public String getOwnerCfChef() { return ownerCfChef; }
}