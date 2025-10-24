package it.unina.foodlab.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Ricetta {
    private long idRicetta;
    private String nome;
    private String descrizione;
    private String difficolta; 
    private int tempoPreparazione; 

    
    private final List<SessionePresenza> sessioni = new ArrayList<>();

    public Ricetta() {}

    public Ricetta(long id, String nome, String descrizione, String difficolta, int tempoPreparazione) {
        this.idRicetta = id;
        this.nome = nome;
        this.descrizione = descrizione;
        this.difficolta = difficolta;
        this.tempoPreparazione = tempoPreparazione;
    }

    

    public long getIdRicetta() {
        return idRicetta;
    }

    public void setIdRicetta(long idRicetta) {
        this.idRicetta = idRicetta;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public String getDifficolta() {
        return difficolta;
    }

    public void setDifficolta(String difficolta) {
        this.difficolta = difficolta;
    }

    public int getTempoPreparazione() {
        return tempoPreparazione;
    }

    public void setTempoPreparazione(int tempoPreparazione) {
        this.tempoPreparazione = tempoPreparazione;
    }

    

    public List<SessionePresenza> getSessioni() {
        return Collections.unmodifiableList(sessioni);
    }

    public boolean addSessione(SessionePresenza s) {
        if (s != null && !sessioni.contains(s)) {
            sessioni.add(s);
            s._linkRicetta(this); 
            return true;
        }
        return false;
    }

    public boolean removeSessione(SessionePresenza s) {
        if (s != null && sessioni.remove(s)) {
            s._unlinkRicetta(this); 
            return true;
        }
        return false;
    }

    
    void _linkSessione(SessionePresenza s) {
        if (s != null && !sessioni.contains(s)) {
            sessioni.add(s);
        }
    }

    
    void _unlinkSessione(SessionePresenza s) {
        sessioni.remove(s);
    }

    @Override
    public String toString() {
        return "Ricetta [idRicetta=" + idRicetta 
            + ", nome=" + nome 
            + ", descrizione=" + descrizione 
            + ", difficolta=" + difficolta 
            + ", tempoPreparazione=" + tempoPreparazione 
            + ", sessioni=" + sessioni.size() + "]";
    }
}
