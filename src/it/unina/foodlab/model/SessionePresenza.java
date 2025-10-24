package it.unina.foodlab.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionePresenza extends Sessione {

    private String via;
    private String num;
    private int cap;
    private String aula;
    private int postiMax;

    
    private final List<Ricetta> ricette = new ArrayList<>();

    public SessionePresenza() { }

    public SessionePresenza(
            int id,
            LocalDate data,
            LocalTime oraInizio,
            LocalTime oraFine,
            Corso corso,
            String via,
            String num,
            int cap,
            String aula,
            int postiMax
    ) {
        super(id, data, oraInizio, oraFine, corso);
        this.via = via;
        this.num = num;
        this.cap = cap;
        this.aula = aula;
        this.postiMax = postiMax;
    }

    // ================== Getters/Setters ==================

    public String getVia() { return via; }
    public void setVia(String via) { this.via = via; }

    public String getNum() { return num; }
    public void setNum(String num) { this.num = num; }

    public int getCap() { return cap; }
    public void setCap(int cap) { this.cap = cap; }

    public String getAula() { return aula; }
    public void setAula(String aula) { this.aula = aula; }

    public int getPostiMax() { return postiMax; }
    public void setPostiMax(int postiMax) { this.postiMax = postiMax; }

  
     
    public List<Ricetta> getRicette() {
        return Collections.unmodifiableList(ricette);
    }

    

    public boolean addRicetta(Ricetta r) {
        if (r == null) return false;
        if (!ricette.contains(r)) {
            boolean added = ricette.add(r);
            if (added) {
                r._linkSessione(this); 
            }
            return added;
        }
        return false;
    }

    public boolean removeRicetta(Ricetta r) {
        if (r == null) return false;
        if (ricette.remove(r)) {
            r._unlinkSessione(this); 
            return true;
        }
        return false;
    }

    
    void _linkRicetta(Ricetta r) {
        if (r != null && !ricette.contains(r)) {
            ricette.add(r);
        }
    }

    
    void _unlinkRicetta(Ricetta r) {
        ricette.remove(r);
    }

    

    @Override
    public String getModalita() {
        return "Presenza";
    }

    @Override
    public String toString() {
        return "SessionePresenza {id=" + getId()
                + ", data=" + getData()
                + ", oraInizio=" + getOraInizio()
                + ", oraFine=" + getOraFine()
                + ", aula='" + aula + '\''
                + ", ricette=" + ricette.size()
                + '}';
    }
}