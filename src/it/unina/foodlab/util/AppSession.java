package it.unina.foodlab.util;

public final class AppSession {
    private static String cfChef;

    private AppSession() {}

    /** Restituisce il CF dello chef loggato (null se non loggato). */
    public static String getCfChef() { return cfChef; }

    /** Imposta/azzera il CF in sessione (trim applicato). */
    public static void setCfChef(String v) { cfChef = (v == null ? null : v.trim()); }

    /** true se c'è uno chef autenticato. */
    public static boolean isLogged() { return cfChef != null && !cfChef.isEmpty(); }
}
