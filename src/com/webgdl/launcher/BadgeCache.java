package com.webgdl.launcher;

import android.os.UserHandle;
import android.os.Process;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Conteo de notificaciones por paquete, para pintar las insignias.
 * Lo alimenta {@link BadgeNotificationListener} (si el usuario concede el acceso).
 * Es estático y en memoria: no se persiste ni sale del dispositivo.
 */
final class BadgeCache {
    interface Observer { void onBadgesChanged(); }

    private static final Map<String, Integer> COUNTS = new HashMap<>();
    private static final Set<Observer> OBSERVERS = new HashSet<>();

    private BadgeCache() {}

    static synchronized void setCount(String pkg, int count) {
        if (pkg == null) return;
        Integer old = COUNTS.get(pkg);
        if (count <= 0) {
            if (old == null) return;
            COUNTS.remove(pkg);
        } else {
            if (old != null && old == count) return;
            COUNTS.put(pkg, count);
        }
        notifyObservers();
    }

    static synchronized int getCount(String pkg) {
        Integer v = pkg == null ? null : COUNTS.get(pkg);
        return v == null ? 0 : v;
    }

    static synchronized boolean has(String pkg) {
        return pkg != null && COUNTS.containsKey(pkg);
    }

    static synchronized void replaceAll(Map<String, Integer> fresh) {
        COUNTS.clear();
        COUNTS.putAll(fresh);
        notifyObservers();
    }

    static synchronized void addObserver(Observer o) { OBSERVERS.add(o); }
    static synchronized void removeObserver(Observer o) { OBSERVERS.remove(o); }

    private static void notifyObservers() {
        for (Observer o : new HashSet<>(OBSERVERS)) {
            o.onBadgesChanged();
        }
    }
}
