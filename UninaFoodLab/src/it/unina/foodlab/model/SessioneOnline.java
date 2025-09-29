package it.unina.foodlab.model;
import java.time.*;

public class SessioneOnline extends Sessione {
	String piattaforma;
	
	public SessioneOnline() {}
	
	public SessioneOnline(int Id, LocalDate data, LocalTime oraInizio, LocalTime oraFine, Corso corso, String piattaforma) {
		super(Id, data, oraInizio, oraFine, corso);
		this.piattaforma = piattaforma;

	}

	public String getPiattaforma() {
		return piattaforma;
	}

	public void setPiattaforma(String piattaforma) {
		this.piattaforma = piattaforma;
	}

	@Override public String getModalita() {
		return "Online";
	}

	@Override
	public String toString() {
		return "SessioneOnline [piattaforma=" + piattaforma + ", getModalita()=" + getModalita() + ", getData()="
				+ getData() + ", getOraInizio()=" + getOraInizio() + ", getOraFine()=" + getOraFine() + ", getCorso()="
				+ getCorso() + ", getId()=" + getId() + "]";
	}
}