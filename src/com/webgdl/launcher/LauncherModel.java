package com.webgdl.launcher;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carga la lista de apps instaladas (solo APIs públicas: LauncherApps) y
 * persiste la disposición del escritorio/dock/carpetas en un JSON local.
 *
 * Toda la E/S de disco pasa por un único hilo (IO), así cargas y guardados
 * nunca se pisan. El JSON se construye en el hilo principal (rápido) para no
 * leer objetos que el hilo principal pueda estar modificando.
 */
class LauncherModel {
    private static final String TAG = "LauncherModel";
    private static final String LAYOUT_FILE = "layout.json";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "launcher-io");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private final Context ctx;
    private final LauncherApps launcherApps;
    private final UserManager userManager;
    private volatile int nextFolderId = 1;

    LauncherModel(Context c) {
        ctx = c.getApplicationContext();
        launcherApps = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        userManager = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
    }

    void runIo(Runnable r) { IO.execute(r); }

    List<UserHandle> profiles() {
        try {
            List<UserHandle> p = launcherApps.getProfiles();
            if (p != null && !p.isEmpty()) return p;
        } catch (Throwable ignored) {}
        List<UserHandle> only = new ArrayList<>();
        only.add(Process.myUserHandle());
        return only;
    }

    /** Lista de apps y los perfiles que se pudieron consultar de verdad. */
    static class AppList {
        final List<ItemInfo> apps = new ArrayList<>();
        /** Ids de perfil consultados con éxito. Los demás (en pausa, espacio privado bloqueado, error) no se tocan. */
        final Set<Integer> okUsers = new HashSet<>();
    }

    /** Todas las actividades lanzables de todos los perfiles, ordenadas por nombre. */
    AppList loadAllApps() {
        AppList out = new AppList();
        String self = ctx.getPackageName();
        for (UserHandle u : profiles()) {
            try {
                if (userManager.isQuietModeEnabled(u)) continue;   // perfil en pausa: sus apps no se listan
            } catch (Throwable ignored) {}
            List<LauncherActivityInfo> list;
            try {
                list = launcherApps.getActivityList(null, u);
            } catch (Throwable t) {
                Log.w(TAG, "getActivityList " + u, t);
                continue;
            }
            if (list == null) continue;
            out.okUsers.add(u.hashCode());
            for (LauncherActivityInfo lai : list) {
                if (self.equals(lai.getComponentName().getPackageName())) continue; // oculta el propio launcher
                ItemInfo it = ItemInfo.fromApp(lai);
                it.user = u;
                out.apps.add(it);
            }
        }
        sortByTitle(out.apps);
        return out;
    }

    static void sortByTitle(List<ItemInfo> list) {
        Collections.sort(list, new Comparator<ItemInfo>() {
            @Override public int compare(ItemInfo a, ItemInfo b) {
                String sa = a.title == null ? "" : a.title.toString();
                String sb = b.title == null ? "" : b.title.toString();
                return sa.compareToIgnoreCase(sb);
            }
        });
    }

    static Set<String> keysOf(List<ItemInfo> apps) {
        Set<String> s = new HashSet<>();
        for (ItemInfo a : apps) s.add(a.key());
        return s;
    }

    boolean layoutExists() {
        return new File(ctx.getFilesDir(), LAYOUT_FILE).exists();
    }

    int allocFolderId() { return nextFolderId++; }

    // ------------------------------------------------------------------ guardar

    /** Construye el JSON de la disposición. Llamar en el hilo principal. Devuelve null si falla. */
    String serialize(List<ItemInfo> desktop, List<ItemInfo> hotseat, Set<String> knownApps) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("nextFolderId", nextFolderId);
            JSONArray d = new JSONArray();
            for (ItemInfo it : desktop) { assignSerial(it); d.put(it.toJson()); }
            root.put("desktop", d);
            JSONArray h = new JSONArray();
            for (ItemInfo it : hotseat) { assignSerial(it); h.put(it.toJson()); }
            root.put("hotseat", h);
            JSONArray k = new JSONArray();
            if (knownApps != null) for (String s : knownApps) k.put(s);
            root.put("knownApps", k);
            return root.toString();
        } catch (Throwable t) {
            Log.e(TAG, "serialize", t);
            return null;
        }
    }

    /** Escribe el JSON de forma atómica (archivo temporal + renombrar). Llamar en el hilo IO. */
    void writeLayout(String json) {
        if (json == null) return;
        try {
            File tmp = new File(ctx.getFilesDir(), LAYOUT_FILE + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.getFD().sync();
            }
            File dst = new File(ctx.getFilesDir(), LAYOUT_FILE);
            if (!tmp.renameTo(dst)) {
                try (FileOutputStream fos = new FileOutputStream(dst)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
                tmp.delete();
            }
        } catch (Throwable t) {
            Log.e(TAG, "writeLayout", t);
        }
    }

    /** Serializa y escribe en el hilo actual (solo para objetos que nadie más toca). */
    void saveLayoutNow(List<ItemInfo> desktop, List<ItemInfo> hotseat, Set<String> knownApps) {
        writeLayout(serialize(desktop, hotseat, knownApps));
    }

    private void assignSerial(ItemInfo it) {
        try {
            UserHandle u = it.user != null ? it.user : Process.myUserHandle();
            it.userSerial = userManager.getSerialNumberForUser(u);
        } catch (Throwable ignored) {}
        for (ItemInfo c : it.contents) assignSerial(c);
    }

    private void resolveUser(ItemInfo it) {
        if (it.userSerial >= 0) {
            try { it.user = userManager.getUserForSerialNumber(it.userSerial); } catch (Throwable ignored) {}
        }
        for (ItemInfo c : it.contents) resolveUser(c);
    }

    // ------------------------------------------------------------------ cargar

    static class Loaded {
        final List<ItemInfo> desktop = new ArrayList<>();
        final List<ItemInfo> hotseat = new ArrayList<>();
        Set<String> knownApps;          // null = archivo antiguo sin esta información
        boolean dirty;                  // hay cambios que conviene guardar
    }

    /** Lee la disposición guardada. Devuelve null si no existe o está dañada. Hilo IO. */
    Loaded loadLayout() {
        File f = new File(ctx.getFilesDir(), LAYOUT_FILE);
        if (!f.exists()) return null;
        try {
            byte[] bytes = new byte[(int) f.length()];
            try (FileInputStream fis = new FileInputStream(f)) {
                int off = 0, r;
                while (off < bytes.length && (r = fis.read(bytes, off, bytes.length - off)) > 0) off += r;
            }
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            nextFolderId = Math.max(1, root.optInt("nextFolderId", 1));
            Loaded out = new Loaded();
            JSONArray d = root.optJSONArray("desktop");
            if (d != null) for (int i = 0; i < d.length(); i++) {
                ItemInfo it = ItemInfo.fromJson(d.getJSONObject(i));
                resolveUser(it);
                out.desktop.add(it);
            }
            JSONArray h = root.optJSONArray("hotseat");
            if (h != null) for (int i = 0; i < h.length(); i++) {
                ItemInfo it = ItemInfo.fromJson(h.getJSONObject(i));
                resolveUser(it);
                out.hotseat.add(it);
            }
            JSONArray k = root.optJSONArray("knownApps");
            if (k != null) {
                out.knownApps = new HashSet<>();
                for (int i = 0; i < k.length(); i++) out.knownApps.add(k.getString(i));
            }
            // evita colisiones de id de carpeta con datos existentes
            for (ItemInfo it : out.desktop) bumpFolderId(it);
            for (ItemInfo it : out.hotseat) bumpFolderId(it);
            return out;
        } catch (Throwable t) {
            Log.e(TAG, "loadLayout: archivo dañado, se aparta como layout.json.bad", t);
            File bad = new File(ctx.getFilesDir(), LAYOUT_FILE + ".bad");
            bad.delete();
            f.renameTo(bad);   // se conserva por si hace falta recuperarlo
            return null;
        }
    }

    private void bumpFolderId(ItemInfo it) {
        if (it.type == ItemInfo.TYPE_FOLDER && it.folderId >= nextFolderId) nextFolderId = it.folderId + 1;
    }

    void deleteLayout() {
        new File(ctx.getFilesDir(), LAYOUT_FILE).delete();
        nextFolderId = 1;
    }

    /**
     * Disposición por defecto en el primer arranque: el dock con teléfono, mensajes,
     * navegador y cámara (si existen) y el resto de apps en el escritorio, en orden.
     * Las del escritorio quedan "sin colocar" (page = -1) y las coloca HomeActivity.
     */
    Loaded buildDefault(List<ItemInfo> allApps, int hotseatCount) {
        Loaded out = new Loaded();
        List<ItemInfo> apps = new ArrayList<>(allApps);
        List<ItemInfo> hot = pickHotseat(apps, hotseatCount);
        for (int i = 0; i < hot.size(); i++) {
            apps.remove(hot.get(i));
            ItemInfo it = hot.get(i).copyForHome();   // copias: el cajón conserva las suyas
            it.container = ItemInfo.CONTAINER_HOTSEAT;
            it.page = 0;
            it.cellX = i;
            it.cellY = 0;
            out.hotseat.add(it);
        }
        for (ItemInfo a : apps) {
            ItemInfo it = a.copyForHome();
            it.container = ItemInfo.CONTAINER_DESKTOP;
            it.page = -1;
            out.desktop.add(it);
        }
        out.knownApps = keysOf(allApps);
        out.dirty = true;
        return out;
    }

    private List<ItemInfo> pickHotseat(List<ItemInfo> apps, int count) {
        List<ItemInfo> hot = new ArrayList<>();
        String[][] hintsByRole = {
                {"dialer", "phone", "contacts"},
                {"messag", "sms", "mms"},
                {"chrome", "browser", "internet"},
                {"camera"}
        };
        for (String[] hints : hintsByRole) {
            if (hot.size() >= count) break;
            ItemInfo found = null;
            for (ItemInfo it : apps) {
                if (hot.contains(it)) continue;
                String pkg = it.targetPackage();
                if (pkg == null) continue;
                String low = pkg.toLowerCase();
                for (String h : hints) {
                    if (low.contains(h)) { found = it; break; }
                }
                if (found != null) break;
            }
            if (found != null) hot.add(found);
        }
        for (ItemInfo it : apps) {
            if (hot.size() >= count) break;
            if (!hot.contains(it)) hot.add(it);
        }
        return hot;
    }

    LauncherApps launcherApps() { return launcherApps; }
}
