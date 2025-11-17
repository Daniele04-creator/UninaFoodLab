package it.unina.foodlab.model;

import java.time.LocalDate;

public class Corso {

    private long idCorso;
    private LocalDate dataInizio;
    private LocalDate dataFine;
    private String argomento;
    private String frequenza;
    private Chef chef;
    private int numSessioni;

    public Corso() {}

    public Corso(long idCorso, LocalDate dataInizio, LocalDate dataFine,
                 String argomento, String frequenza, Chef chef, int numSessioni) {
        this.idCorso = idCorso;
        this.dataInizio = dataInizio;
        this.dataFine = dataFine;
        this.argomento = argomento;
        this.frequenza = frequenza;
        this.chef = chef;
        this.numSessioni = numSessioni;
    }

    public long getIdCorso() {
		return idCorso;
	}

	public void setIdCorso(long idCorso) {
		this.idCorso = idCorso;
	}

	public LocalDate getDataInizio() {
		return dataInizio;
	}

	public void setDataInizio(LocalDate dataInizio) {
		this.dataInizio = dataInizio;
	}

	public LocalDate getDataFine() {
		return dataFine;
	}

	public void setDataFine(LocalDate dataFine) {
		this.dataFine = dataFine;
	}

	public String getArgomento() {
		return argomento;
	}

	public void setArgomento(String argomento) {
		this.argomento = argomento;
	}

	public String getFrequenza() {
		return frequenza;
	}

	public void setFrequenza(String frequenza) {
		this.frequenza = frequenza;
	}

	public Chef getChef() {
		return chef;
	}
	
	public String getNomeChef() {
	    return chef != null ? chef.getNome() : "";
	}

	public String getCognomeChef() {
	    return chef != null ? chef.getCognome() : "";
	}

	public void setChef(Chef chef) {
		this.chef = chef;
	}

	public int getNumSessioni() {
		return numSessioni;
	}

	public void setNumSessioni(int numSessioni) {
		this.numSessioni = numSessioni;
	}

	@Override
    public String toString() {
        return "Corso{" +
                "idCorso=" + idCorso +
                ", dataInizio=" + dataInizio +
                ", dataFine=" + dataFine +
                ", argomento='" + argomento + '\'' +
                ", frequenza='" + frequenza + '\'' +
                ", numSessioni=" + numSessioni +
                '}';
    }
}