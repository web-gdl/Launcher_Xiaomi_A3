package com.webgdl.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes del launcher (cuadrícula, iconos, etiquetas). */
class Prefs {
    private static final String FILE = "launcher_prefs";
    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    int columns() { return sp.getInt("columns", 4); }
    int rows() { return sp.getInt("rows", 5); }
    int hotseatCount() { return sp.getInt("hotseat", 4); }
    int iconSizeDp() { return sp.getInt("iconSizeDp", 48); }
    boolean showLabels() { return sp.getBoolean("showLabels", true); }
    boolean resetPending() { return sp.getBoolean("resetPending", false); }

    void setColumns(int v) { sp.edit().putInt("columns", clamp(v, 3, 7)).apply(); }
    void setRows(int v) { sp.edit().putInt("rows", clamp(v, 4, 8)).apply(); }
    void setIconSizeDp(int v) { sp.edit().putInt("iconSizeDp", clamp(v, 36, 72)).apply(); }
    void setShowLabels(boolean v) { sp.edit().putBoolean("showLabels", v).apply(); }
    /** Síncrono (commit) para que la pantalla de inicio lo vea al volver. */
    void setResetPending(boolean v) { sp.edit().putBoolean("resetPending", v).commit(); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
