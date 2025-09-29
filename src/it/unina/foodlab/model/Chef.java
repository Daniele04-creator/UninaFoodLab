package it.unina.foodlab.model;

import java.time.LocalDate;

public class Chef {

    private String CF_Chef;   
    private String nome;
    private String cognome;
    private LocalDate nascita;
    private String username;
    private String password;

    public Chef() {}

    public Chef(String CF_Chef, String nome, String cognome, LocalDate nascita,
                String username, String password) {
        this.CF_Chef = CF_Chef;
        this.nome = nome;
        this.cognome = cognome;
        this.nascita = nascita;
        this.username = username;
        this.password = password;
    }

    /* ===== GETTER & SETTER ===== */

    public String getCF_Chef() {
        return CF_Chef;
    }

    public void setCF_Chef(String CF_Chef) {
        this.CF_Chef = CF_Chef;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCognome() {
        return cognome;
    }

    public void setCognome(String cognome) {
        this.cognome = cognome;
    }

    public LocalDate getNascita() {
        return nascita;
    }

    public void setNascita(LocalDate nascita) {
        this.nascita = nascita;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "Chef{" +
                "CF_Chef='" + CF_Chef + '\'' +
                ", nome='" + nome + '\'' +
                ", cognome='" + cognome + '\'' +
                ", nascita=" + nascita +
                ", username='" + username + '\'' +
                '}';
    }
}