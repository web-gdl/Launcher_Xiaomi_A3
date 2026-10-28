package com.webgdl.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Un elemento colocado en el launcher: app, acceso directo, carpeta o widget.
 * Se serializa a JSON para persistir la disposición.
 */
class ItemInfo {
    static final int TYPE_APP = 0;
    static final int TYPE_SHORTCUT = 1;   // acceso directo con Intent propio
    static final int TYPE_FOLDER = 2;
    static final int TYPE_WIDGET = 3;
    static final int TYPE_DEEP_SHORTCUT = 4; // ShortcutInfo de otra app

    static final int CONTAINER_DESKTOP = -100;
    static final int CONTAINER_HOTSEAT = -101;

    int type;
    int container = CONTAINER_DESKTOP;
    int page;        // página del escritorio; -1 = sin colocar todavía
    int cellX, cellY;
    int spanX = 1, spanY = 1;

    CharSequence title;
    ComponentName component;   // apps
    UserHandle user;           // perfil (personal / trabajo)
    long userSerial = -1;      // número de serie estable del perfil, para persistir
    Intent intent;             // accesos directos
    Uri iconUri;

    // Carpeta
    int folderId = -1;
    final List<ItemInfo> contents = new ArrayList<>();

    // Widget
    int appWidgetId = -1;
    ComponentName widgetProvider;

    // Acceso directo profundo
    String shortcutId;
    String shortcutPackage;

    // En memoria (no se serializa)
    Bitmap icon;

    ItemInfo() {}

    static ItemInfo fromApp(LauncherActivityInfo lai) {
        ItemInfo it = new ItemInfo();
        it.type = TYPE_APP;
        it.component = lai.getComponentName();
        it.user = lai.getUser();
        it.title = lai.getLabel();
        return it;
    }

    /** Copia para colocar en el inicio: no comparte posición ni contenedor con la del cajón. */
    ItemInfo copyForHome() {
        ItemInfo c = new ItemInfo();
        c.type = type;
        c.component = component;
        c.user = user;
        c.title = title;
        c.intent = intent;
        c.shortcutId = shortcutId;
        c.shortcutPackage = shortcutPackage;
        c.icon = icon;
        return c;
    }

    /** Id del perfil (0 = personal). Estable mientras exista el perfil. */
    int userId() {
        return (user != null ? user : Process.myUserHandle()).hashCode();
    }

    /** Id de perfil contenido en una clave ("app:pkg/.Cls#10"), o -1 si no tiene. */
    static int userIdOfKey(String key) {
        int i = key == null ? -1 : key.lastIndexOf('#');
        if (i < 0) return -1;
        try { return Integer.parseInt(key.substring(i + 1)); } catch (NumberFormatException e) { return -1; }
    }

    /** Identidad estable del elemento (incluye el perfil para no mezclar personal/trabajo). */
    String key() {
        switch (type) {
            case TYPE_APP:
                return "app:" + (component == null ? "?" : component.flattenToShortString()) + "#" + userId();
            case TYPE_DEEP_SHORTCUT:
                return "deep:" + shortcutPackage + "/" + shortcutId + "#" + userId();
            case TYPE_FOLDER:
                return "folder:" + folderId;
            case TYPE_WIDGET:
                return "widget:" + appWidgetId;
            default:
                return "shortcut:" + (intent == null ? String.valueOf(title) : intent.toUri(0));
        }
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", type);
        o.put("container", container);
        o.put("page", page);
        o.put("cellX", cellX);
        o.put("cellY", cellY);
        o.put("spanX", spanX);
        o.put("spanY", spanY);
        if (title != null) o.put("title", title.toString());
        if (component != null) o.put("component", component.flattenToShortString());
        if (userSerial >= 0) o.put("user", userSerial);
        if (intent != null) o.put("intent", intent.toUri(0));
        if (iconUri != null) o.put("iconUri", iconUri.toString());
        if (type == TYPE_FOLDER) {
            o.put("folderId", folderId);
            JSONArray arr = new JSONArray();
            for (ItemInfo c : contents) arr.put(c.toJson());
            o.put("contents", arr);
        }
        if (type == TYPE_WIDGET) {
            o.put("appWidgetId", appWidgetId);
            if (widgetProvider != null) o.put("widgetProvider", widgetProvider.flattenToShortString());
        }
        if (type == TYPE_DEEP_SHORTCUT) {
            o.put("shortcutId", shortcutId);
            o.put("shortcutPackage", shortcutPackage);
        }
        return o;
    }

    static ItemInfo fromJson(JSONObject o) throws JSONException {
        ItemInfo it = new ItemInfo();
        it.type = o.optInt("type", TYPE_APP);
        it.container = o.optInt("container", CONTAINER_DESKTOP);
        it.page = o.optInt("page", 0);
        it.cellX = o.optInt("cellX", 0);
        it.cellY = o.optInt("cellY", 0);
        it.spanX = Math.max(1, o.optInt("spanX", 1));
        it.spanY = Math.max(1, o.optInt("spanY", 1));
        it.title = o.has("title") ? o.getString("title") : null;
        if (o.has("component")) it.component = ComponentName.unflattenFromString(o.getString("component"));
        it.userSerial = o.optLong("user", -1);
        if (o.has("intent")) {
            try { it.intent = Intent.parseUri(o.getString("intent"), 0); } catch (Exception ignored) {}
        }
        if (o.has("iconUri")) it.iconUri = Uri.parse(o.getString("iconUri"));
        if (it.type == TYPE_FOLDER) {
            it.folderId = o.optInt("folderId", -1);
            JSONArray arr = o.optJSONArray("contents");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    ItemInfo c = fromJson(arr.getJSONObject(i));
                    c.container = it.folderId;
                    it.contents.add(c);
                }
            }
        }
        if (it.type == TYPE_WIDGET) {
            it.appWidgetId = o.optInt("appWidgetId", -1);
            if (o.has("widgetProvider"))
                it.widgetProvider = ComponentName.unflattenFromString(o.getString("widgetProvider"));
        }
        if (it.type == TYPE_DEEP_SHORTCUT) {
            it.shortcutId = o.has("shortcutId") ? o.getString("shortcutId") : null;
            it.shortcutPackage = o.has("shortcutPackage") ? o.getString("shortcutPackage") : null;
        }
        return it;
    }

    String targetPackage() {
        if (component != null) return component.getPackageName();
        if (type == TYPE_DEEP_SHORTCUT) return shortcutPackage;
        if (type == TYPE_WIDGET && widgetProvider != null) return widgetProvider.getPackageName();
        if (intent != null && intent.getComponent() != null) return intent.getComponent().getPackageName();
        if (intent != null && intent.getPackage() != null) return intent.getPackage();
        return null;
    }
}
