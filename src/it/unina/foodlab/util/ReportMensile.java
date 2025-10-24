package it.unina.foodlab.util;

public record ReportMensile( int totaleCorsi,
        int totaleOnline,
        int totalePratiche,
        Integer minRicettePratiche,   // può essere null se non ci sono sessioni
        Integer maxRicettePratiche,
        Double  mediaRicettePratiche  // può essere null
) {
// Costruttore compatto che normalizza i null
public ReportMensile {
if (minRicettePratiche == null) minRicettePratiche = 0;
if (maxRicettePratiche == null) maxRicettePratiche = 0;
if (mediaRicettePratiche == null) mediaRicettePratiche = 0.0;
}
}