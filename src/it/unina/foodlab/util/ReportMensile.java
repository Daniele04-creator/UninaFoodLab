package it.unina.foodlab.util;

public record ReportMensile(
        int totaleCorsi,
        int totaleOnline,
        int totalePratiche,
        Double mediaRicettePratiche, 
        Integer maxRicettePratiche,  
        Integer minRicettePratiche   
) {}