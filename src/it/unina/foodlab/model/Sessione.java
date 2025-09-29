package it.unina.foodlab.model;

import java.time.*;

public abstract class Sessione {         
    private LocalDate data;
    private LocalTime oraInizio;
    private LocalTime oraFine;
    private Corso corso;
    private int Id;

    
    public Sessione() {}
    public Sessione(int Id, LocalDate data, LocalTime oraInizio, LocalTime oraFine, Corso corso) {
		this.data = data;
		this.oraInizio = oraInizio;
		this.oraFine = oraFine;
		this.corso = corso;
		this.Id = Id;
	}


	public LocalDate getData() {
		return data;
	}


	public void setData(LocalDate data) {
		this.data = data;
	}


	public LocalTime getOraInizio() {
		return oraInizio;
	}


	public void setOraInizio(LocalTime oraInizio) {
		this.oraInizio = oraInizio;
	}


	public LocalTime getOraFine() {
		return oraFine;
	}


	public void setOraFine(LocalTime oraFine) {
		this.oraFine = oraFine;
	}


	public Corso getCorso() {
		return corso;
	}


	public void setCorso(Corso corso) {
		this.corso = corso;
	}

    
    public abstract String getModalita();


	public int getId() {
		return Id;
	}


	public void setId(int id) {
		this.Id = id;
	}


	@Override
	public String toString() {
		return "Sessione [data=" + data + ", oraInizio=" + oraInizio + ", oraFine=" + oraFine + ", corso=" + corso
				+ ", Id=" + Id + "]";
	}




}