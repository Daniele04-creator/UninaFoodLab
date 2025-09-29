package it.unina.foodlab.util;
public final class AppSession {
    private static String cfChef;
    private AppSession() {}
    public static String getCfChef() { return cfChef; }
    public static void setCfChef(String v) { cfChef = (v == null ? null : v.trim()); }
    public static boolean isLogged() { return cfChef != null && !cfChef.isEmpty(); }
}