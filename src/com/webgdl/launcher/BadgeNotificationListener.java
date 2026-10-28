package com.webgdl.launcher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

/**
 * Escucha las notificaciones activas y cuenta cuántas tiene cada paquete,
 * solo para mostrar las insignias. No lee el contenido ni lo guarda en disco.
 * El usuario concede el acceso manualmente en Ajustes del sistema.
 */
public class BadgeNotificationListener extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        recount();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        recount();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        recount();
    }

    private void recount() {
        StatusBarNotification[] active;
        try {
            active = getActiveNotifications();
        } catch (Throwable t) {
            return; // puede lanzar si el servicio aún no está listo
        }
        if (active == null) {
            BadgeCache.replaceAll(new HashMap<String, Integer>());
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (StatusBarNotification sbn : active) {
            if (!sbn.isClearable()) continue;        // ignora las fijas (reloj, cargador…)
            String pkg = sbn.getPackageName();
            if (pkg == null) continue;
            Integer n = counts.get(pkg);
            counts.put(pkg, (n == null ? 0 : n) + 1);
        }
        BadgeCache.replaceAll(counts);
    }
}
