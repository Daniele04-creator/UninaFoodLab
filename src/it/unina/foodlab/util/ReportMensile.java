package it.unina.foodlab.util;

public record ReportMensile( int totaleCorsi,
        int totaleOnline,
        int totalePratiche,
        Integer minRicettePratiche,   
        Integer maxRicettePratiche,
        Double  mediaRicettePratiche  
) {
public ReportMensile {
if (minRicettePratiche == null) minRicettePratiche = 0;
if (maxRicettePratiche == null) maxRicettePratiche = 0;
if (mediaRicettePratiche == null) mediaRicettePratiche = 0.0;
}
}