package com.webgdl.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.UserHandle;
import android.util.LruCache;

/** Carga y cachea los iconos de las apps como Bitmaps, en un hilo aparte. */
class IconCache {
    private final Context ctx;
    private final LauncherApps launcherApps;
    private final int iconSize;
    private final LruCache<String, Bitmap> cache;
    private final Handler bg;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Bitmap defaultIcon;

    interface IconCallback { void onIcon(Bitmap bmp); }

    IconCache(Context c, int iconSizePx) {
        this.ctx = c.getApplicationContext();
        this.launcherApps = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        this.iconSize = iconSizePx;
        int cacheKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8); // 1/8 de la memoria
        this.cache = new LruCache<String, Bitmap>(Math.max(2048, cacheKb)) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        HandlerThread t = new HandlerThread("icon-loader", Thread.MIN_PRIORITY);
        t.start();
        bg = new Handler(t.getLooper());
    }

    int iconSize() { return iconSize; }

    Bitmap defaultIcon() {
        if (defaultIcon == null) {
            Drawable d = ctx.getPackageManager().getDefaultActivityIcon();
            defaultIcon = Utilities.drawableToBitmap(d, iconSize);
        }
        return defaultIcon;
    }

    private String keyFor(ItemInfo it) {
        return it.key() + "@" + (it.user == null ? "0" : it.user.hashCode());
    }

    /** Devuelve el icono cacheado o lo carga en segundo plano y llama al callback. */
    void loadIcon(final ItemInfo it, final IconCallback cb) {
        final String key = keyFor(it);
        Bitmap cached = cache.get(key);
        if (cached != null) { cb.onIcon(cached); return; }
        cb.onIcon(defaultIcon()); // provisional
        bg.post(new Runnable() {
            @Override public void run() {
                Bitmap bmp = loadSync(it);
                if (bmp == null) bmp = defaultIcon();
                cache.put(key, bmp);
                final Bitmap f = bmp;
                ui.post(new Runnable() { @Override public void run() { cb.onIcon(f); } });
            }
        });
    }

    private Bitmap loadSync(ItemInfo it) {
        try {
            Drawable d = null;
            if (it.type == ItemInfo.TYPE_APP && it.component != null) {
                LauncherActivityInfo lai = resolveActivity(it.component, it.user);
                if (lai != null) {
                    d = lai.getBadgedIcon(0);
                }
            } else if (it.type == ItemInfo.TYPE_DEEP_SHORTCUT) {
                d = null; // el icono del deep shortcut lo resuelve quien lo cree; cae al genérico
            } else if (it.intent != null) {
                ComponentName cn = it.intent.getComponent();
                if (cn != null) {
                    LauncherActivityInfo lai = resolveActivity(cn, it.user);
                    if (lai != null) d = lai.getBadgedIcon(0);
                }
            }
            if (d == null && it.component != null) {
                d = ctx.getPackageManager().getActivityIcon(it.component);
            }
            return Utilities.drawableToBitmap(d, iconSize);
        } catch (Throwable t) {
            return null;
        }
    }

    LauncherActivityInfo resolveActivity(ComponentName cn, UserHandle user) {
        if (cn == null) return null;
        try {
            UserHandle u = user != null ? user : android.os.Process.myUserHandle();
            for (LauncherActivityInfo lai : launcherApps.getActivityList(cn.getPackageName(), u)) {
                if (lai.getComponentName().equals(cn)) return lai;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    void evict(String pkg) {
        // Vacía todo; es simple y poco frecuente (install/remove).
        cache.evictAll();
    }

    /** Detiene el hilo de carga (al destruir la actividad). */
    void shutdown() {
        try { bg.getLooper().quitSafely(); } catch (Throwable ignored) {}
    }
}
