package it.unina.foodlab.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Scheduling {
    private Scheduling() {}

    /** Genera N date a partire da 'start' secondo la 'frequenza'. */
    public static List<LocalDate> generate(LocalDate start, String frequenza, int num) {
        var out = new ArrayList<LocalDate>();
        if (start == null || num <= 0) return out;
        String f = (frequenza == null ? "" : frequenza.trim().toLowerCase(Locale.ROOT));
        for (int i = 0; i < num; i++) {
            LocalDate d = switch (f) {
                case "ogni 2 giorni" -> start.plusDays(2L * i);
                case "bisettimanale" -> start.plusWeeks(2L * i);
                case "mensile"       -> start.plusMonths(i);
                case "settimanale"   -> start.plusWeeks(i);
                default -> start.plusWeeks(i);
            };
            out.add(d);
        }
        return out;
    }
}