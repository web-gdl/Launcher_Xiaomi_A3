package com.webgdl.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pantalla de inicio: escritorio con páginas, dock, cajón de apps, carpetas y widgets.
 * Solo APIs públicas. Sin acceso a red.
 */
public class HomeActivity extends Activity implements
        AllAppsContainer.Callbacks, Folder.Callbacks, DragController.DragListener {

    private static final String TAG = "Inicio";
    private static final int REQ_BIND_WIDGET = 1001;
    private static final int REQ_CONFIG_WIDGET = 1002;
    private static final int WIDGET_HOST_ID = 0x4C31;
    /** Tope de páginas para crear al arrastrar y para fiarse de un número de página guardado. */
    private static final int MAX_PAGES = 100;
    private static final String KEY_PENDING_WIDGET = "pendingWidgetId";
    private static final String KEY_PENDING_RESULT = "pendingWidgetResult";

    private Prefs prefs;
    private SharedPreferences rawPrefs;
    private LauncherModel model;
    private IconCache iconCache;
    private DragController dragController;
    private LauncherApps launcherApps;
    private LauncherApps.Callback appsCallback;

    private DragLayer dragLayer;
    private LinearLayout main;
    private TextView searchBar;          // franja superior; durante el arrastre la tapa la barra de destinos
    private PagedView workspace;
    private CellLayout hotseat;
    private PageIndicator pageIndicator;
    private AllAppsContainer allApps;
    private LinearLayout dropBar;
    private int dropBarBaseHeight;
    private Folder folderView;
    private FrameLayout folderContainer;

    private AppWidgetManager widgetManager;
    private LauncherWidgetHost widgetHost;

    private int cols, rows, hotseatCount, iconSizePx;
    private boolean showLabels;
    private boolean allAppsOpen;
    private boolean uiReady;
    private boolean pendingReload;
    private int loadGeneration;
    private long lastEdgeFlip;

    private final Set<String> knownAppKeys = new HashSet<>();
    private ItemInfo pendingWidget;     // durante el flujo de añadir widget

    private DragOrigin dragOrigin;      // de dónde salió el elemento que se arrastra
    private float lastRawX, lastRawY;   // último toque sobre un icono

    private static class DragOrigin { int container, page, cellX, cellY; }

    // =================================================================== ciclo de vida

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        rawPrefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE);
        cols = prefs.columns();
        rows = prefs.rows();
        hotseatCount = prefs.hotseatCount();
        iconSizePx = Utilities.dp(this, prefs.iconSizeDp());
        showLabels = prefs.showLabels();

        model = new LauncherModel(this);
        iconCache = new IconCache(this, iconSizePx);
        launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new LauncherWidgetHost(this, WIDGET_HOST_ID);

        int pendingId = rawPrefs.getInt(KEY_PENDING_WIDGET, -1);
        if (pendingId >= 0) {           // el sistema pudo cerrar esta actividad mientras se configuraba
            pendingWidget = new ItemInfo();
            pendingWidget.type = ItemInfo.TYPE_WIDGET;
            pendingWidget.appWidgetId = pendingId;
        }

        buildUi();
        registerBackHandler();
        registerAppCallback();
        if (prefs.resetPending()) applyReset(); else loadInitial();
    }

    @Override protected void onStart() {
        super.onStart();
        try { widgetHost.startListening(); } catch (Throwable t) { Log.w(TAG, "startListening", t); }
    }

    @Override protected void onStop() {
        super.onStop();
        try { widgetHost.stopListening(); } catch (Throwable ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        // Primero los cambios de cuadrícula/iconos/nombres: recreate() pasa por onCreate,
        // que ya aplica también un restablecimiento pendiente con los valores nuevos.
        if (uiReady && prefsChanged()) { recreate(); return; }
        if (prefs.resetPending()) applyReset();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        try { if (appsCallback != null) launcherApps.unregisterCallback(appsCallback); } catch (Throwable ignored) {}
        iconCache.shutdown();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Botón Inicio estando ya en el inicio.
        if (dragController.isDragging()) { dragController.cancel(); return; }
        if (folderContainer.getVisibility() == View.VISIBLE) { closeFolder(true); return; }
        if (allAppsOpen) { closeAllApps(); return; }
        workspace.snapToPage(0);
    }

    private boolean prefsChanged() {
        return cols != prefs.columns() || rows != prefs.rows()
                || iconSizePx != Utilities.dp(this, prefs.iconSizeDp())
                || showLabels != prefs.showLabels();
    }

    // ----- botón/gesto Atrás (Android 16 usa OnBackInvokedCallback; antes, onBackPressed)

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { handleBack(); }

    private void handleBack() {
        if (dragController.isDragging()) { dragController.cancel(); return; }
        if (folderContainer.getVisibility() == View.VISIBLE) { closeFolder(true); return; }
        if (allAppsOpen) closeAllApps();
        // En el inicio, Atrás no hace nada más.
    }

    // =================================================================== interfaz

    private void buildUi() {
        dragLayer = new DragLayer(this);
        dragController = new DragController(this, dragLayer);
        dragController.setListener(this);
        dragLayer.setDragController(dragController);
        setContentView(dragLayer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Utilities.drawBehindSystemBars(this);

        // Columna principal: búsqueda + escritorio (ocupa el resto) + puntos de página + dock
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        dragLayer.addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Franja superior reservada: muestra la búsqueda y, al arrastrar, la barra de destinos encima.
        // Así la barra nunca tapa la primera fila de iconos.
        dropBarBaseHeight = Utilities.dp(this, 56);
        searchBar = new TextView(this);
        searchBar.setText(R.string.search_hint);
        searchBar.setTextColor(getColor(R.color.search_hint));
        searchBar.setTextSize(15);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackgroundResource(R.drawable.search_bg);
        searchBar.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        searchBar.setCompoundDrawablePadding(Utilities.dp(this, 10));
        int sp = Utilities.dp(this, 14);
        searchBar.setPadding(sp, 0, sp, 0);
        searchBar.setOnClickListener(v -> openAllApps(true));
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utilities.dp(this, 44));
        int sm = Utilities.dp(this, 16);
        sbl.setMargins(sm, Utilities.dp(this, 6), sm, Utilities.dp(this, 6));   // 6 + 44 + 6 = 56 dp
        main.addView(searchBar, sbl);

        workspace = new PagedView(this);
        main.addView(workspace, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        workspace.setPageListener(page -> pageIndicator.setCurrent(page));
        workspace.setEmptyLongPressListener(this::showHomeMenu);

        // Deslizar arriba/abajo desde cualquier parte (escritorio, dock, puntos de página)
        dragLayer.setSwipeHandler(new DragLayer.SwipeHandler() {
            @Override public boolean canSwipe() {
                return uiReady && !allAppsOpen && !dragController.isDragging()
                        && folderContainer.getVisibility() != View.VISIBLE;
            }
            @Override public void onSwipeUp() { openAllApps(false); }
            @Override public void onSwipeDown() { expandNotifications(); }
        });

        pageIndicator = new PageIndicator(this);
        main.addView(pageIndicator, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utilities.dp(this, 16)));

        hotseat = new CellLayout(this, hotseatCount, 1);
        int hpad = Utilities.dp(this, 8);
        hotseat.setPadding(hpad, hpad, hpad, hpad);
        main.addView(hotseat, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                iconSizePx + Utilities.dp(this, showLabels ? 44 : 28)));

        // Barra de destinos durante el arrastre (oculta)
        dropBar = new LinearLayout(this);
        dropBar.setOrientation(LinearLayout.HORIZONTAL);
        dropBar.setGravity(Gravity.CENTER);
        dropBar.setBackgroundColor(0xCC000000);
        dropBar.setVisibility(View.GONE);
        addDropZone(R.string.remove, R.string.remove, R.drawable.ic_remove);
        addDropZone(R.string.app_info, R.string.drop_info, R.drawable.ic_app_info);   // «Info»: cabe en una línea
        addDropZone(R.string.uninstall, R.string.uninstall, R.drawable.ic_uninstall);
        dragLayer.addView(dropBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dropBarBaseHeight));

        // Cajón de apps (oculto)
        allApps = new AllAppsContainer(this, this);
        dragLayer.addView(allApps, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        allApps.setVisibility(View.INVISIBLE);

        // Carpeta abierta (oculta); tocar fuera la cierra
        folderContainer = new FrameLayout(this);
        folderContainer.setBackgroundColor(0xB0000000);
        folderContainer.setVisibility(View.GONE);
        folderContainer.setOnClickListener(v -> closeFolder(true));
        dragLayer.addView(folderContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        folderView = new Folder(this, this);
        folderView.setClickable(true); // un toque dentro no la cierra
        folderContainer.addView(folderView, new FrameLayout.LayoutParams(
                Utilities.dp(this, 320), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // Márgenes reales de la barra de estado y la de navegación (botones o gestos).
        dragLayer.setOnApplyWindowInsetsListener((v, insets) -> {
            Rect r = Utilities.systemBarInsets(insets);
            main.setPadding(r.left, r.top, r.right, r.bottom);
            FrameLayout.LayoutParams alp = (FrameLayout.LayoutParams) allApps.getLayoutParams();
            alp.topMargin = r.top + Utilities.dp(this, 8);
            alp.leftMargin = r.left;
            alp.rightMargin = r.right;
            allApps.setLayoutParams(alp);
            allApps.setBottomInset(r.bottom);
            ViewGroup.LayoutParams dlp = dropBar.getLayoutParams();
            dlp.height = dropBarBaseHeight + r.top;
            dropBar.setLayoutParams(dlp);
            dropBar.setPadding(r.left, r.top, r.right, 0);
            return insets;
        });
        dragLayer.requestApplyInsets();
    }

    /** @param action identifica la acción (se guarda como tag); @param labelRes texto corto de la zona. */
    private void addDropZone(int action, int labelRes, int iconRes) {
        TextView tv = new TextView(this);
        tv.setText(labelRes);
        tv.setTextColor(0xFFFFFFFF);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setCompoundDrawablePadding(Utilities.dp(this, 4));
        tv.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        tv.setTag(action);
        dropBar.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    // =================================================================== carga

    /** Primer arranque o tras restablecer: lee apps + disposición guardada en el hilo IO. */
    private void loadInitial() {
        final int gen = ++loadGeneration;
        uiReady = false;
        model.runIo(() -> {
            try {
                LauncherModel.AppList list;
                try { list = model.loadAllApps(); }
                catch (Throwable t) { Log.e(TAG, "loadAllApps", t); list = new LauncherModel.AppList(); }

                LauncherModel.Loaded loaded = null;
                try {
                    loaded = model.loadLayout();
                    if (loaded != null) reconcile(loaded, list);
                } catch (Throwable t) {
                    Log.e(TAG, "disposición dañada; se crea una nueva", t);
                    loaded = null;
                }
                if (loaded == null) {
                    try { loaded = model.buildDefault(list.apps, hotseatCount); }
                    catch (Throwable t) { Log.e(TAG, "buildDefault", t); loaded = new LauncherModel.Loaded(); }
                }
                final LauncherModel.AppList fList = list;
                final LauncherModel.Loaded fLoaded = loaded;
                runOnUiThread(() -> {
                    if (isDestroyed() || gen != loadGeneration) return;
                    applyLoaded(fList, fLoaded);
                });
            } catch (Throwable t) {
                Log.e(TAG, "loadInitial", t);   // nunca tumbar el proceso desde el hilo IO
            }
        });
    }

    /** Se instaló, desinstaló o actualizó una app: se reconcilia la disposición en memoria. */
    private void reloadApps() {
        if (!uiReady) { loadInitial(); return; }
        if (dragController.isDragging()) { pendingReload = true; return; }
        final int gen = ++loadGeneration;
        model.runIo(() -> {
            final LauncherModel.AppList list;
            try { list = model.loadAllApps(); }
            catch (Throwable t) { Log.e(TAG, "loadAllApps", t); return; }
            runOnUiThread(() -> {
                if (isDestroyed() || gen != loadGeneration) return;
                if (!uiReady) { loadInitial(); return; }
                if (dragController.isDragging()) { pendingReload = true; return; }
                LauncherModel.Loaded snap = snapshotLayout();
                try { reconcile(snap, list); } catch (Throwable t) { Log.e(TAG, "reconcile", t); }
                iconCache.evict(null);
                applyLoaded(list, snap);
            });
        });
    }

    private void applyLoaded(LauncherModel.AppList list, LauncherModel.Loaded loaded) {
        allApps.setApps(list.apps);
        knownAppKeys.clear();
        if (loaded.knownApps != null) knownAppKeys.addAll(loaded.knownApps);
        else if (!list.apps.isEmpty()) knownAppKeys.addAll(LauncherModel.keysOf(list.apps));
        else collectHomeKeys(loaded, knownAppKeys);   // lista vacía (fallo pasajero): lo que ya está en el inicio
        boolean changed = populate(loaded);
        uiReady = true;
        if (changed || loaded.dirty || loaded.knownApps == null) saveCurrentLayout();
        resumePendingWidget();
    }

    private static void collectHomeKeys(LauncherModel.Loaded l, Set<String> out) {
        List<ItemInfo> all = new ArrayList<>(l.desktop);
        all.addAll(l.hotseat);
        for (ItemInfo it : all) {
            if (it.type == ItemInfo.TYPE_FOLDER) { for (ItemInfo c : it.contents) out.add(c.key()); }
            else if (it.type == ItemInfo.TYPE_APP) out.add(it.key());
        }
    }

    /** Termina el alta de un widget que quedó a medias porque el sistema cerró la actividad. */
    private void resumePendingWidget() {
        if (pendingWidget == null) return;
        String r = rawPrefs.getString(KEY_PENDING_RESULT, null);
        if (r == null) return;
        rawPrefs.edit().remove(KEY_PENDING_RESULT).apply();
        if ("bound".equals(r)) {
            configureOrPlaceWidget(pendingWidget);
        } else if ("configured".equals(r)) {
            placeWidget(pendingWidget);
            clearPendingWidget(false);
        }
    }

    /**
     * Ajusta la disposición a las apps instaladas:
     * quita las desinstaladas, actualiza nombres y añade al final las instaladas
     * desde la última vez. Las que el usuario quitó del inicio NO vuelven: siguen
     * en «knownApps» y solo se ven en el cajón.
     * Los perfiles que no se pudieron consultar (en pausa, bloqueados, error) no se tocan,
     * y una lista vacía (casi seguro un fallo pasajero) no borra nada.
     */
    private static void reconcile(LauncherModel.Loaded loaded, LauncherModel.AppList list) {
        final Map<String, ItemInfo> present = new HashMap<>();
        final Map<String, ItemInfo> byPackage = new HashMap<>();   // "paquete#perfil" -> primera actividad
        for (ItemInfo a : list.apps) {
            present.put(a.key(), a);
            if (a.component != null) {
                String pk = a.component.getPackageName() + "#" + a.userId();
                if (!byPackage.containsKey(pk)) byPackage.put(pk, a);
            }
        }
        final Set<String> claimed = new HashSet<>();   // claves nuevas que heredan un icono existente
        final boolean canPrune = !list.apps.isEmpty();

        int before = countItems(loaded);
        loaded.desktop.removeIf(it -> pruneMissing(it, present, byPackage, claimed, list.okUsers, canPrune));
        loaded.hotseat.removeIf(it -> pruneMissing(it, present, byPackage, claimed, list.okUsers, canPrune));
        if (countItems(loaded) != before || !claimed.isEmpty()) loaded.dirty = true;

        if (loaded.knownApps != null && canPrune) {
            for (ItemInfo a : list.apps) {
                if (loaded.knownApps.contains(a.key()) || claimed.contains(a.key())) continue;
                ItemInfo copy = a.copyForHome();
                copy.container = ItemInfo.CONTAINER_DESKTOP;
                copy.page = -1;                 // se colocará al final
                loaded.desktop.add(copy);
                loaded.dirty = true;
            }
        }
        if (!canPrune) return;   // sin lista fiable no se actualiza lo conocido
        Set<String> nowKnown = new HashSet<>(present.keySet());
        if (loaded.knownApps != null) {
            for (String k : loaded.knownApps) {   // se conservan las de perfiles no consultados
                if (!list.okUsers.contains(ItemInfo.userIdOfKey(k))) nowKnown.add(k);
            }
        }
        if (loaded.knownApps == null || !loaded.knownApps.equals(nowKnown)) loaded.dirty = true;
        loaded.knownApps = nowKnown;
    }

    private static boolean pruneMissing(ItemInfo it, Map<String, ItemInfo> present,
                                        Map<String, ItemInfo> byPackage, Set<String> claimed,
                                        Set<Integer> okUsers, boolean canPrune) {
        if (it.type == ItemInfo.TYPE_FOLDER) {
            it.contents.removeIf(c -> pruneMissing(c, present, byPackage, claimed, okUsers, canPrune));
            return it.contents.isEmpty();
        }
        if (it.type != ItemInfo.TYPE_APP) return false;     // widgets y accesos directos se conservan
        if (!canPrune || !okUsers.contains(it.userId())) return false;   // perfil no consultado: no tocar
        ItemInfo fresh = present.get(it.key());
        if (fresh == null && it.component != null) {
            // ¿La app sigue instalada pero cambió su actividad de inicio (actualización, alias)?
            ItemInfo alt = byPackage.get(it.component.getPackageName() + "#" + it.userId());
            if (alt != null) {
                it.component = alt.component;   // el icono se queda en su sitio (o en su carpeta)
                claimed.add(alt.key());
                fresh = alt;
            }
        }
        if (fresh == null) return true;
        it.title = fresh.title;   // el nombre puede cambiar al actualizar la app o el idioma
        return false;
    }

    private static int countItems(LauncherModel.Loaded l) {
        int n = 0;
        for (ItemInfo it : l.desktop) n += 1 + it.contents.size();
        for (ItemInfo it : l.hotseat) n += 1 + it.contents.size();
        return n;
    }

    // =================================================================== poblar vistas

    /**
     * Crea las vistas. Lo que no cabe en su sitio guardado (cuadrícula más pequeña,
     * celdas solapadas, apps nuevas) se recoloca en el primer hueco libre.
     * Devuelve true si algo cambió de sitio.
     */
    private boolean populate(LauncherModel.Loaded loaded) {
        int keepPage = workspace.getCurrentPage();
        workspace.removeAllViews();
        hotseat.removeAllViews();
        boolean changed = false;

        int pageCount = 1;
        for (ItemInfo it : loaded.desktop) {
            if (it.page >= 0 && it.page < MAX_PAGES) pageCount = Math.max(pageCount, it.page + 1);
        }
        for (int p = 0; p < pageCount; p++) addPage();

        List<ItemInfo> overflow = new ArrayList<>();
        for (ItemInfo it : loaded.desktop) {
            normalizeSpan(it);
            if (it.page < 0 || it.page >= workspace.getPageCount() || !fitsGrid(it)) { overflow.add(it); continue; }
            CellLayout cl = workspace.getPageAt(it.page);
            if (cl.isOccupied(it.cellX, it.cellY, it.spanX, it.spanY, null)) { overflow.add(it); continue; }
            it.container = ItemInfo.CONTAINER_DESKTOP;
            if (!addItemToCell(cl, it)) changed = true;
        }
        for (ItemInfo it : loaded.hotseat) {
            if (it.type == ItemInfo.TYPE_WIDGET) { overflow.add(it); continue; }
            int cx = it.cellX;
            if (cx < 0 || cx >= hotseatCount || hotseat.childAtCell(cx, 0) != null) {
                int[] v = hotseat.findVacant(1, 1);
                if (v == null) { overflow.add(it); continue; }
                cx = v[0];
                changed = true;
            }
            it.container = ItemInfo.CONTAINER_HOTSEAT;
            it.page = 0; it.cellX = cx; it.cellY = 0; it.spanX = 1; it.spanY = 1;
            hotseat.addInCell(createItemView(it), cx, 0, 1, 1);
        }
        for (ItemInfo it : overflow) {
            changed = true;
            boolean isNew = it.page < 0;
            if (!placeAnywhere(it, isNew)) Log.w(TAG, "Sin espacio para " + it.title);
        }
        pageIndicator.setCount(workspace.getPageCount());
        workspace.setCurrentPageImmediate(Math.min(keepPage, workspace.getPageCount() - 1));
        if (folderContainer.getVisibility() == View.VISIBLE) folderView.refresh();
        return changed;
    }

    private CellLayout addPage() {
        CellLayout page = new CellLayout(this, cols, rows);
        int cp = Utilities.dp(this, 4);
        page.setPadding(cp, cp, cp, cp);
        workspace.addView(page);
        pageIndicator.setCount(workspace.getPageCount());
        return page;
    }

    private void normalizeSpan(ItemInfo it) {
        if (it.type == ItemInfo.TYPE_WIDGET) {
            it.spanX = Math.max(1, Math.min(cols, it.spanX));
            it.spanY = Math.max(1, Math.min(rows, it.spanY));
        } else {
            it.spanX = 1;
            it.spanY = 1;
        }
    }

    private boolean fitsGrid(ItemInfo it) {
        return it.cellX >= 0 && it.cellY >= 0 && it.cellX + it.spanX <= cols && it.cellY + it.spanY <= rows;
    }

    /** Coloca el elemento en el primer hueco (desde la primera o la última página); añade página si hace falta. */
    private boolean placeAnywhere(ItemInfo it, boolean fromLast) {
        normalizeSpan(it);
        int n = workspace.getPageCount();
        if (fromLast && n > 0) {
            CellLayout last = workspace.getPageAt(n - 1);
            int[] v = last.findVacant(it.spanX, it.spanY);
            if (v != null) return placeAt(last, n - 1, v, it);
        } else {
            for (int p = 0; p < n; p++) {
                CellLayout cl = workspace.getPageAt(p);
                int[] v = cl.findVacant(it.spanX, it.spanY);
                if (v != null) return placeAt(cl, p, v, it);
            }
        }
        // Sin tope: un elemento que ya existía nunca se descarta por falta de páginas.
        CellLayout np = addPage();
        int[] v = np.findVacant(it.spanX, it.spanY);
        return v != null && placeAt(np, workspace.getPageCount() - 1, v, it);
    }

    private boolean placeOnCurrentPageOrAnywhere(ItemInfo it) {
        normalizeSpan(it);
        int p = workspace.getCurrentPage();
        if (p >= 0 && p < workspace.getPageCount()) {
            CellLayout cl = workspace.getPageAt(p);
            int[] v = cl.findVacant(it.spanX, it.spanY);
            if (v != null) return placeAt(cl, p, v, it);
        }
        return placeAnywhere(it, true);
    }

    private boolean placeAt(CellLayout cl, int page, int[] cell, ItemInfo it) {
        it.container = ItemInfo.CONTAINER_DESKTOP;
        it.page = page;
        it.cellX = cell[0];
        it.cellY = cell[1];
        return addItemToCell(cl, it);
    }

    private boolean addItemToCell(CellLayout cl, ItemInfo it) {
        if (it.type == ItemInfo.TYPE_WIDGET) return addWidgetView(cl, it);
        cl.addInCell(createItemView(it), it.cellX, it.cellY, 1, 1);
        return true;
    }

    private View createItemView(final ItemInfo it) {
        if (it.type == ItemInfo.TYPE_FOLDER) {
            FolderIcon fi = new FolderIcon(this, iconSizePx, showLabels, iconCache);
            fi.setFolder(it);
            attachListeners(fi, it);
            return fi;
        }
        final BubbleTextView v = new BubbleTextView(this, iconSizePx, showLabels);
        v.setItem(it);
        iconCache.loadIcon(it, bmp -> v.applyIcon(it, bmp));
        attachListeners(v, it);
        return v;
    }

    private void attachListeners(final View v, final ItemInfo it) {
        v.setOnTouchListener((view, ev) -> {
            lastRawX = ev.getRawX();
            lastRawY = ev.getRawY();
            return false;
        });
        v.setOnClickListener(view -> onItemClick(it, view));
        v.setOnLongClickListener(view -> {
            beginDrag(view, it, lastRawX, lastRawY);
            return true;
        });
    }

    // =================================================================== abrir apps

    private void onItemClick(ItemInfo it, View v) {
        switch (it.type) {
            case ItemInfo.TYPE_FOLDER:
                openFolder(it);
                break;
            case ItemInfo.TYPE_APP:
                launchApp(it, v);
                break;
            case ItemInfo.TYPE_DEEP_SHORTCUT:
                try {
                    launcherApps.startShortcut(it.shortcutPackage, it.shortcutId, bounds(v), null,
                            it.user != null ? it.user : Process.myUserHandle());
                } catch (Throwable t) { toast(R.string.cannot_open); }
                break;
            default:
                if (it.intent != null) {
                    try { startActivity(it.intent); } catch (Throwable t) { toast(R.string.cannot_open); }
                }
        }
    }

    private void launchApp(ItemInfo it, View v) {
        if (it.component == null) return;
        try {
            launcherApps.startMainActivity(it.component,
                    it.user != null ? it.user : Process.myUserHandle(), bounds(v), null);
        } catch (Throwable t) {
            toast(getString(R.string.cannot_open) + (it.title == null ? "" : " " + it.title));
        }
    }

    private Rect bounds(View v) {
        if (v == null) return null;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
    }

    // ----- cajón: callbacks

    @Override public void onAppClick(ItemInfo it, View v) { launchApp(it, v); }
    @Override public void onAppLongClick(ItemInfo it, View v) { showAppOptions(it, v); }
    @Override public void onRequestCloseAllApps() { closeAllApps(); }
    @Override public IconCache iconCache() { return iconCache; }
    @Override public Prefs prefs() { return prefs; }

    // =================================================================== menú de una app (cajón)

    private void showAppOptions(final ItemInfo it, final View anchor) {
        OptionsPopup popup = new OptionsPopup(this);
        final String pkg = it.targetPackage();

        popup.addItem(R.drawable.ic_add, getString(R.string.add_to_home), () -> addAppToHome(it));
        popup.addItem(R.drawable.ic_app_info, getString(R.string.app_info), () -> openAppInfo(it));
        if (pkg != null && isUninstallable(pkg)) {
            popup.addItem(R.drawable.ic_uninstall, getString(R.string.uninstall), () -> uninstall(pkg));
        }
        for (final ShortcutInfo si : queryShortcuts(pkg, it.user)) {
            CharSequence lbl = si.getShortLabel() != null ? si.getShortLabel() : si.getLongLabel();
            if (lbl == null) continue;
            popup.addShortcut(lbl, loadShortcutIcon(si), () -> {
                try { launcherApps.startShortcut(si, bounds(anchor), null); }
                catch (Throwable t) { toast(R.string.cannot_open); }
            });
        }
        popup.showAt(anchor);
    }

    /** Atajos de la app. Android solo deja consultarlos al launcher predeterminado. */
    private List<ShortcutInfo> queryShortcuts(String pkg, UserHandle user) {
        List<ShortcutInfo> out = new ArrayList<>();
        if (pkg == null) return out;
        try {
            if (!launcherApps.hasShortcutHostPermission()) return out;
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
            q.setPackage(pkg);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            List<ShortcutInfo> list = launcherApps.getShortcuts(q, user != null ? user : Process.myUserHandle());
            if (list != null) {
                for (ShortcutInfo si : list) {
                    if (!si.isEnabled()) continue;
                    out.add(si);
                    if (out.size() >= 4) break;
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private Drawable loadShortcutIcon(ShortcutInfo si) {
        try {
            return launcherApps.getShortcutIconDrawable(si, getResources().getDisplayMetrics().densityDpi);
        } catch (Throwable t) { return null; }
    }

    private boolean isUninstallable(String pkg) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (Throwable t) { return false; }
    }

    private void openAppInfo(ItemInfo it) {
        String pkg = it.targetPackage();
        if (pkg == null) return;
        ComponentName cn = it.component != null ? it.component
                : (it.widgetProvider != null ? it.widgetProvider : new ComponentName(pkg, pkg));
        try {
            launcherApps.startAppDetailsActivity(cn, it.user != null ? it.user : Process.myUserHandle(), null, null);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
                startActivity(i);
            } catch (Throwable t2) { toast(R.string.not_available); }
        }
    }

    private void uninstall(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
        } catch (Throwable t) { toast(R.string.not_available); }
    }

    // =================================================================== arrastre

    private void beginDrag(View view, ItemInfo it, float rawX, float rawY) {
        if (dragController.isDragging() || !uiReady) return;
        Bitmap bmp = viewToBitmap(view);
        if (bmp == null) return;

        int[] vS = new int[2], dS = new int[2];
        view.getLocationOnScreen(vS);
        dragLayer.getLocationOnScreen(dS);
        int left = vS[0] - dS[0], top = vS[1] - dS[1];
        float tx = rawX - dS[0], ty = rawY - dS[1];
        if (tx < left || tx > left + view.getWidth() || ty < top || ty > top + view.getHeight()) {
            tx = left + view.getWidth() / 2f;   // toque fuera de la vista: se agarra por el centro
            ty = top + view.getHeight() / 2f;
        }

        dragOrigin = new DragOrigin();
        dragOrigin.container = it.container;
        dragOrigin.page = it.page;
        dragOrigin.cellX = it.cellX;
        dragOrigin.cellY = it.cellY;

        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);   // libera el hueco

        dropBar.setVisibility(View.VISIBLE);
        searchBar.setVisibility(View.INVISIBLE);   // la barra de destinos ocupa su franja; que no se transparente
        dragController.startDrag(bmp, left, top, tx, ty, it);
    }

    private Bitmap viewToBitmap(View v) {
        if (v.getWidth() <= 0 || v.getHeight() <= 0) return null;
        try {
            Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(b));
            return b;
        } catch (Throwable t) { return null; }
    }

    /** La barra de destinos ocupa solo la franja reservada encima del escritorio: nunca tapa iconos. */
    private boolean isOverDropBar(float y) {
        return y < absRect(workspace).top;
    }

    @Override public void onDragMove(ItemInfo item, float x, float y) {
        boolean overBar = isOverDropBar(y);
        int zone = overBar ? zoneAt(x) : -1;
        for (int i = 0; i < dropBar.getChildCount(); i++) {
            dropBar.getChildAt(i).setAlpha(i == zone ? 1f : 0.55f);
        }
        if (overBar) return;

        // Pasar de página al llevar el elemento al borde (solo sobre el escritorio).
        Rect ws = absRect(workspace);
        if (y < ws.top || y > ws.bottom) return;
        long now = System.currentTimeMillis();
        if (now - lastEdgeFlip < 650) return;
        int edge = Utilities.dp(this, 32);
        int cur = workspace.getCurrentPage();
        if (x > dragLayer.getWidth() - edge) {
            if (cur < workspace.getPageCount() - 1) {
                workspace.snapToPage(cur + 1);
                lastEdgeFlip = now;
            } else if (workspace.getPageCount() < MAX_PAGES && workspace.getPageAt(cur).getChildCount() > 0) {
                addPage();                       // página nueva al final
                workspace.snapToPage(cur + 1);
                lastEdgeFlip = now;
            }
        } else if (x < edge && cur > 0) {
            workspace.snapToPage(cur - 1);
            lastEdgeFlip = now;
        }
    }

    private int zoneAt(float x) {
        int n = Math.max(1, dropBar.getChildCount());
        int z = (int) (x * n / Math.max(1, dragLayer.getWidth()));
        return Math.max(0, Math.min(n - 1, z));
    }

    @Override public void onDrop(ItemInfo item, float x, float y) {
        workspace.finishScroll();   // si se suelta durante un cambio de página, se calcula sobre la página ya quieta
        try {
            if (isOverDropBar(y)) { handleDropBar(zoneAt(x), item); return; }
            boolean overHotseat = y >= absRect(hotseat).top;
            if (item.type == ItemInfo.TYPE_WIDGET) {
                if (overHotseat) {
                    restoreToOrigin(item);
                    toast(R.string.widgets_no_dock);
                    saveCurrentLayout();
                } else {
                    dropWidgetOnDesktop(item);
                }
                return;
            }
            if (overHotseat) dropIntoHotseat(item, x, y);
            else dropIntoDesktop(item, x, y);
        } catch (Throwable t) {
            Log.e(TAG, "onDrop", t);
            // Nunca perder el elemento, pero tampoco duplicarlo si ya se había colocado.
            if (!isItemInViews(item)) restoreToOrigin(item);
            saveCurrentLayout();
        }
    }

    /** ¿El elemento ya tiene vista en el escritorio, el dock o dentro de una carpeta? */
    private boolean isItemInViews(ItemInfo item) {
        if (isItemIn(hotseat, item)) return true;
        for (int p = 0; p < workspace.getPageCount(); p++) {
            if (isItemIn(workspace.getPageAt(p), item)) return true;
        }
        return false;
    }

    private boolean isItemIn(CellLayout cl, ItemInfo item) {
        for (int i = 0; i < cl.getChildCount(); i++) {
            ItemInfo it = itemOf(cl.getChildAt(i));
            if (it == item) return true;
            if (it != null && it.type == ItemInfo.TYPE_FOLDER && it.contents.contains(item)) return true;
        }
        return false;
    }

    private void handleDropBar(int zone, ItemInfo item) {
        Object tag = dropBar.getChildAt(zone).getTag();
        int which = tag instanceof Integer ? (Integer) tag : 0;
        if (which == R.string.remove) {
            // Se quita del inicio (sigue en el cajón). Un widget libera su id.
            if (item.type == ItemInfo.TYPE_WIDGET) deleteWidgetId(item.appWidgetId);
            saveCurrentLayout();
        } else if (which == R.string.app_info) {
            restoreToOrigin(item);
            saveCurrentLayout();
            openAppInfo(item);
        } else if (which == R.string.uninstall) {
            // Se devuelve a su sitio: si el usuario cancela, el icono no desaparece.
            // Si de verdad se desinstala, llega onPackageRemoved y se quita.
            restoreToOrigin(item);
            saveCurrentLayout();
            String pkg = item.targetPackage();
            if (pkg != null && isUninstallable(pkg)) uninstall(pkg);
            else toast(R.string.cannot_uninstall);
        }
    }

    private void dropIntoHotseat(ItemInfo item, float x, float y) {
        Rect hs = absRect(hotseat);
        int[] cell = hotseat.pointToCell(x - hs.left, y - hs.top);
        int cx = cell[0];
        View existing = hotseat.childAtCell(cx, 0);
        if (existing instanceof BubbleTextView && canFolder(item, ((BubbleTextView) existing).getItem())) {
            makeFolder(hotseat, ItemInfo.CONTAINER_HOTSEAT, 0, existing, ((BubbleTextView) existing).getItem(), item);
            return;
        }
        if (existing instanceof FolderIcon && isFolderable(item)) {
            addToFolder((FolderIcon) existing, item);
            return;
        }
        if (existing != null) {
            int[] v = hotseat.findVacant(1, 1);
            if (v == null) {
                restoreToOrigin(item);
                toast(R.string.dock_full);
                saveCurrentLayout();
                return;
            }
            cx = v[0];
        }
        item.container = ItemInfo.CONTAINER_HOTSEAT;
        item.page = 0; item.cellX = cx; item.cellY = 0; item.spanX = 1; item.spanY = 1;
        hotseat.addInCell(createItemView(item), cx, 0, 1, 1);
        saveCurrentLayout();
    }

    private void dropIntoDesktop(ItemInfo item, float x, float y) {
        int page = workspace.getCurrentPage();
        CellLayout cl = workspace.getPageAt(page);
        Rect r = absRect(cl);
        int[] cell = cl.pointToCell(x - r.left, y - r.top);
        View existing = cl.childAtCell(cell[0], cell[1]);

        if (existing instanceof BubbleTextView) {
            ItemInfo target = ((BubbleTextView) existing).getItem();
            if (canFolder(item, target)) {
                makeFolder(cl, ItemInfo.CONTAINER_DESKTOP, page, existing, target, item);
                return;
            }
        } else if (existing instanceof FolderIcon && isFolderable(item)) {
            addToFolder((FolderIcon) existing, item);
            return;
        }

        int[] dst = cell;
        if (cl.isOccupied(cell[0], cell[1], 1, 1, null)) {
            dst = cl.findVacant(1, 1);
            if (dst == null) {
                restoreToOrigin(item);
                toast(R.string.page_full);
                saveCurrentLayout();
                return;
            }
        }
        placeAt(cl, page, dst, item);
        saveCurrentLayout();
    }

    private void dropWidgetOnDesktop(ItemInfo item) {
        int page = workspace.getCurrentPage();
        CellLayout cl = workspace.getPageAt(page);
        Rect r = absRect(cl);
        normalizeSpan(item);
        int cw = Math.max(1, cl.cellWidth()), ch = Math.max(1, cl.cellHeight());
        // La esquina superior izquierda del widget flotante decide la celda.
        int[] cell = cl.pointToCell(dragController.getViewLeft() - r.left + cw / 2f,
                dragController.getViewTop() - r.top + ch / 2f);
        int cx = Math.max(0, Math.min(cell[0], cols - item.spanX));
        int cy = Math.max(0, Math.min(cell[1], rows - item.spanY));
        int[] dst = {cx, cy};
        if (cl.isOccupied(cx, cy, item.spanX, item.spanY, null)) {
            dst = cl.findVacant(item.spanX, item.spanY);
            if (dst == null) {
                restoreToOrigin(item);
                toast(R.string.no_space_widget);
                saveCurrentLayout();
                return;
            }
        }
        if (!placeAt(cl, page, dst, item)) toast(R.string.widget_failed);
        saveCurrentLayout();
    }

    private boolean isFolderable(ItemInfo it) {
        return it.type == ItemInfo.TYPE_APP || it.type == ItemInfo.TYPE_DEEP_SHORTCUT;
    }

    private boolean canFolder(ItemInfo dragged, ItemInfo target) {
        return target != null && isFolderable(dragged) && isFolderable(target);
    }

    private void makeFolder(CellLayout cl, int container, int page, View targetView, ItemInfo target, ItemInfo dragged) {
        CellLayout.LP lp = (CellLayout.LP) targetView.getLayoutParams();
        int cx = lp.cellX, cy = lp.cellY;
        cl.removeView(targetView);

        ItemInfo folder = new ItemInfo();
        folder.type = ItemInfo.TYPE_FOLDER;
        folder.folderId = model.allocFolderId();
        folder.title = getString(R.string.folder_default_name);
        folder.container = container;
        folder.page = page;
        folder.cellX = cx;
        folder.cellY = cy;
        target.container = folder.folderId;
        dragged.container = folder.folderId;
        folder.contents.add(target);
        folder.contents.add(dragged);

        cl.addInCell(createItemView(folder), cx, cy, 1, 1);
        saveCurrentLayout();
    }

    private void addToFolder(FolderIcon fi, ItemInfo item) {
        ItemInfo folder = fi.getFolder();
        item.container = folder.folderId;
        folder.contents.add(item);
        fi.setFolder(folder);
        saveCurrentLayout();
    }

    /** Devuelve el elemento a su sitio; si está ocupado, al primer hueco libre. Nunca se pierde. */
    private void restoreToOrigin(ItemInfo item) {
        DragOrigin o = dragOrigin;
        if (o != null && o.container == ItemInfo.CONTAINER_HOTSEAT && item.type != ItemInfo.TYPE_WIDGET) {
            if (o.cellX >= 0 && o.cellX < hotseatCount && hotseat.childAtCell(o.cellX, 0) == null) {
                item.container = ItemInfo.CONTAINER_HOTSEAT;
                item.page = 0; item.cellX = o.cellX; item.cellY = 0;
                hotseat.addInCell(createItemView(item), o.cellX, 0, 1, 1);
                return;
            }
        } else if (o != null && o.page >= 0 && o.page < workspace.getPageCount()) {
            normalizeSpan(item);
            CellLayout cl = workspace.getPageAt(o.page);
            item.cellX = o.cellX;
            item.cellY = o.cellY;
            if (fitsGrid(item) && !cl.isOccupied(o.cellX, o.cellY, item.spanX, item.spanY, null)) {
                if (placeAt(cl, o.page, new int[]{o.cellX, o.cellY}, item)) return;
            }
        }
        if (!placeAnywhere(item, false)) Log.w(TAG, "No se pudo devolver " + item.title);
    }

    @Override public void onDragEnd(ItemInfo item, boolean dropped) {
        dropBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.VISIBLE);
        if (!dropped && item != null) {   // cancelado (p. ej. el sistema tomó el gesto)
            restoreToOrigin(item);
            saveCurrentLayout();
        }
        dragOrigin = null;
        if (pendingReload) {
            pendingReload = false;
            reloadApps();
        }
    }

    private Rect absRect(View v) {
        int[] vLoc = new int[2], dLoc = new int[2];
        v.getLocationInWindow(vLoc);
        dragLayer.getLocationInWindow(dLoc);
        int l = vLoc[0] - dLoc[0], t = vLoc[1] - dLoc[1];
        return new Rect(l, t, l + v.getWidth(), t + v.getHeight());
    }

    // =================================================================== carpetas

    private void openFolder(ItemInfo folder) {
        folderView.bind(folder);
        folderContainer.setVisibility(View.VISIBLE);
    }

    private void closeFolder(boolean save) {
        if (folderContainer.getVisibility() != View.VISIBLE) return;
        folderContainer.setVisibility(View.GONE);
        refreshFolderIcons();
        if (save) saveCurrentLayout();
    }

    private void refreshFolderIcons() {
        refreshFolderIconsIn(hotseat);
        for (int p = 0; p < workspace.getPageCount(); p++) refreshFolderIconsIn(workspace.getPageAt(p));
    }

    private void refreshFolderIconsIn(CellLayout cl) {
        for (int i = 0; i < cl.getChildCount(); i++) {
            View c = cl.getChildAt(i);
            if (c instanceof FolderIcon) ((FolderIcon) c).setFolder(((FolderIcon) c).getFolder());
        }
    }

    @Override public void onFolderItemClick(ItemInfo folder, ItemInfo item, View v) {
        onItemClick(item, v);
    }

    /** Mantener pulsada una app dentro de la carpeta la saca al escritorio. */
    @Override public void onFolderItemLongClick(ItemInfo folder, ItemInfo item, View v) {
        folder.contents.remove(item);
        if (folder.contents.size() <= 1) dissolveFolder(folder);
        closeFolder(false);
        if (!placeOnCurrentPageOrAnywhere(item)) toast(R.string.page_full);
        saveCurrentLayout();
    }

    @Override public void onFolderRenamed(ItemInfo folder, String name) {
        folder.title = name;   // se guarda al cerrar la carpeta
    }

    /** Una carpeta con 0-1 apps vuelve a ser un icono suelto en el mismo sitio. */
    private void dissolveFolder(ItemInfo folder) {
        if (dissolveIn(hotseat, folder, ItemInfo.CONTAINER_HOTSEAT, 0)) return;
        for (int p = 0; p < workspace.getPageCount(); p++) {
            if (dissolveIn(workspace.getPageAt(p), folder, ItemInfo.CONTAINER_DESKTOP, p)) return;
        }
    }

    private boolean dissolveIn(CellLayout cl, ItemInfo folder, int container, int page) {
        for (int i = 0; i < cl.getChildCount(); i++) {
            View c = cl.getChildAt(i);
            if (c instanceof FolderIcon && ((FolderIcon) c).getFolder() == folder) {
                CellLayout.LP lp = (CellLayout.LP) c.getLayoutParams();
                int cx = lp.cellX, cy = lp.cellY;
                cl.removeView(c);
                if (!folder.contents.isEmpty()) {
                    ItemInfo last = folder.contents.remove(0);
                    last.container = container;
                    last.page = page;
                    last.cellX = cx;
                    last.cellY = cy;
                    cl.addInCell(createItemView(last), cx, cy, 1, 1);
                }
                return true;
            }
        }
        return false;
    }

    private void addAppToHome(ItemInfo app) {
        ItemInfo copy = app.copyForHome();
        if (placeOnCurrentPageOrAnywhere(copy)) {
            saveCurrentLayout();
            toast(R.string.added_to_home);
        } else {
            toast(R.string.page_full);
        }
    }

    // =================================================================== cajón

    private void openAllApps(final boolean focusSearch) {
        if (allAppsOpen || !uiReady) return;
        allAppsOpen = true;
        allApps.clearSearch();
        allApps.setVisibility(View.VISIBLE);
        allApps.setTranslationY(Math.max(1, allApps.getHeight()));
        allApps.animate().translationY(0).setDuration(220)
                .withEndAction(() -> {
                    if (!allAppsOpen) return;
                    // Con el cajón abierto no se dibuja el escritorio debajo: sin iconos fantasma
                    // a través del fondo translúcido y menos trabajo para la GPU.
                    main.setVisibility(View.INVISIBLE);
                    if (focusSearch) allApps.focusSearch();
                }).start();
    }

    private void closeAllApps() {
        if (!allAppsOpen) return;
        allAppsOpen = false;
        main.setVisibility(View.VISIBLE);
        allApps.hideKeyboard();
        allApps.animate().translationY(allApps.getHeight()).setDuration(200)
                .withEndAction(() -> { if (!allAppsOpen) allApps.setVisibility(View.INVISIBLE); }).start();
    }

    /** Baja la barra de notificaciones (función no documentada; si el sistema la bloquea no hace nada). */
    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Throwable ignored) {}
    }

    // =================================================================== menú del escritorio

    private void showHomeMenu() {
        if (!uiReady || dragController.isDragging()) return;
        CharSequence[] items = {
                getString(R.string.widgets),
                getString(R.string.wallpaper),
                getString(R.string.settings)
        };
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) showWidgetPicker();
                    else if (which == 1) pickWallpaper();
                    else startActivity(new Intent(this, SettingsActivity.class));
                })
                .show();
    }

    private void pickWallpaper() {
        try {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper)));
        } catch (Throwable t) { toast(R.string.not_available); }
    }

    // =================================================================== widgets

    private void showWidgetPicker() {
        List<AppWidgetProviderInfo> providers = null;
        try { providers = widgetManager.getInstalledProviders(); } catch (Throwable ignored) {}
        if (providers == null || providers.isEmpty()) { toast(R.string.no_widgets); return; }

        final PackageManager pm = getPackageManager();
        final List<AppWidgetProviderInfo> list = new ArrayList<>(providers);
        final Map<AppWidgetProviderInfo, String> labels = new HashMap<>();
        for (AppWidgetProviderInfo p : list) {
            String app;
            try { app = pm.getApplicationLabel(pm.getApplicationInfo(p.provider.getPackageName(), 0)).toString(); }
            catch (Throwable t) { app = p.provider.getPackageName(); }
            CharSequence w = null;
            try { w = p.loadLabel(pm); } catch (Throwable ignored) {}
            labels.put(p, w == null || app.contentEquals(w) ? app : app + " · " + w);
        }
        Collections.sort(list, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) items[i] = labels.get(list.get(i));

        new AlertDialog.Builder(this)
                .setTitle(R.string.widgets)
                .setItems(items, (d, which) -> addWidget(list.get(which)))
                .show();
    }

    private void addWidget(AppWidgetProviderInfo info) {
        final int id;
        try { id = widgetHost.allocateAppWidgetId(); }
        catch (Throwable t) { toast(R.string.widget_failed); return; }

        ItemInfo it = new ItemInfo();
        it.type = ItemInfo.TYPE_WIDGET;
        it.appWidgetId = id;
        it.widgetProvider = info.provider;
        it.user = info.getProfile();
        it.container = ItemInfo.CONTAINER_DESKTOP;
        setPendingWidget(it);

        boolean bound;
        try { bound = widgetManager.bindAppWidgetIdIfAllowed(id, info.getProfile(), info.provider, null); }
        catch (Throwable t) { bound = false; }
        if (!bound) {
            // Android pide permiso al usuario la primera vez.
            Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
            try { startActivityForResult(bind, REQ_BIND_WIDGET); }
            catch (Throwable t) { clearPendingWidget(true); toast(R.string.widget_failed); }
            return;
        }
        configureOrPlaceWidget(it);
    }

    private void configureOrPlaceWidget(ItemInfo it) {
        AppWidgetProviderInfo info = null;
        try { info = widgetManager.getAppWidgetInfo(it.appWidgetId); } catch (Throwable ignored) {}
        if (info == null) { clearPendingWidget(true); toast(R.string.widget_failed); return; }

        boolean needsConfig = info.configure != null;
        if (needsConfig && Build.VERSION.SDK_INT >= 31
                && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0) {
            needsConfig = false;   // la configuración es opcional: se coloca directamente
        }
        if (needsConfig) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, it.appWidgetId, 0, REQ_CONFIG_WIDGET, null);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "configurar widget", t);   // algunos no lo permiten: se coloca igual
            }
        }
        placeWidget(it);
        clearPendingWidget(false);
    }

    private void placeWidget(ItemInfo it) {
        AppWidgetProviderInfo info = null;
        try { info = widgetManager.getAppWidgetInfo(it.appWidgetId); } catch (Throwable ignored) {}
        if (info == null) { deleteWidgetId(it.appWidgetId); toast(R.string.widget_failed); return; }
        it.widgetProvider = info.provider;

        if (workspace.getPageCount() == 0) addPage();
        int page = Math.max(0, Math.min(workspace.getCurrentPage(), workspace.getPageCount() - 1));
        CellLayout cl = workspace.getPageAt(page);
        int spanX = 2, spanY = 2;
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            spanX = info.targetCellWidth;
            spanY = info.targetCellHeight;
        } else if (cl.cellWidth() > 0 && cl.cellHeight() > 0) {
            spanX = (int) Math.ceil(info.minWidth / (double) cl.cellWidth());
            spanY = (int) Math.ceil(info.minHeight / (double) cl.cellHeight());
        }
        it.spanX = spanX;
        it.spanY = spanY;
        normalizeSpan(it);
        int[] v = cl.findVacant(it.spanX, it.spanY);
        boolean ok = v != null ? placeAt(cl, page, v, it) : placeAnywhere(it, false);
        if (!ok) {
            deleteWidgetId(it.appWidgetId);
            toast(R.string.no_space_widget);
            return;
        }
        saveCurrentLayout();
    }

    private boolean addWidgetView(CellLayout cl, final ItemInfo it) {
        AppWidgetProviderInfo info = null;
        try { info = widgetManager.getAppWidgetInfo(it.appWidgetId); } catch (Throwable ignored) {}
        if (info == null) {
            if (it.appWidgetId < 0 || isWidgetProviderGone(it)) {   // su app ya no está: se borra
                deleteWidgetId(it.appWidgetId);
                return false;
            }
            addWidgetPlaceholder(cl, it);   // fallo pasajero: se conserva y se reintenta al recargar
            return true;
        }
        try {
            final AppWidgetHostView host = widgetHost.createView(this, it.appWidgetId, info);
            host.setTag(it);
            it.widgetProvider = info.provider;
            if (host instanceof LauncherWidgetHost.WidgetView) {
                ((LauncherWidgetHost.WidgetView) host).setLongPressCallback(
                        (v, rawX, rawY) -> beginDrag(v, it, rawX, rawY));
            }
            cl.addInCell(host, it.cellX, it.cellY, it.spanX, it.spanY);
            host.post(() -> updateWidgetSize(host, it, cl));
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "crear widget", t);
            addWidgetPlaceholder(cl, it);
            return true;
        }
    }

    /** ¿Se desinstaló (o desactivó) la app que da el widget? */
    private boolean isWidgetProviderGone(ItemInfo it) {
        if (it.widgetProvider == null) return true;
        try {
            return !launcherApps.isPackageEnabled(it.widgetProvider.getPackageName(),
                    it.user != null ? it.user : Process.myUserHandle());
        } catch (Throwable t) {
            return false;   // ante la duda, se conserva
        }
    }

    /** Hueco provisional de un widget que no se pudo cargar. Se puede arrastrar a «Quitar». */
    private void addWidgetPlaceholder(CellLayout cl, ItemInfo it) {
        TextView ph = new TextView(this);
        ph.setText(R.string.widget_unavailable);
        ph.setTextColor(getColor(R.color.search_hint));
        ph.setGravity(Gravity.CENTER);
        ph.setBackgroundResource(R.drawable.search_bg);
        int p = Utilities.dp(this, 8);
        ph.setPadding(p, p, p, p);
        ph.setTag(it);
        attachListeners(ph, it);
        cl.addInCell(ph, it.cellX, it.cellY, it.spanX, it.spanY);
    }

    /** Informa al widget de su tamaño real (muchos eligen su diseño según el tamaño). */
    @SuppressWarnings("deprecation")
    private void updateWidgetSize(AppWidgetHostView host, ItemInfo it, CellLayout cl) {
        try {
            int cw = cl.cellWidth(), ch = cl.cellHeight();
            if (cw <= 0 || ch <= 0) return;
            float d = getResources().getDisplayMetrics().density;
            int w = (int) (it.spanX * cw / d), h = (int) (it.spanY * ch / d);
            host.updateAppWidgetSize(null, w, h, w, h);
        } catch (Throwable ignored) {}
    }

    private void deleteWidgetId(int id) {
        if (id < 0) return;
        try { widgetHost.deleteAppWidgetId(id); } catch (Throwable ignored) {}
    }

    private void setPendingWidget(ItemInfo it) {
        pendingWidget = it;
        rawPrefs.edit().putInt(KEY_PENDING_WIDGET, it.appWidgetId).apply();
    }

    private void clearPendingWidget(boolean deleteId) {
        if (pendingWidget != null && deleteId) deleteWidgetId(pendingWidget.appWidgetId);
        pendingWidget = null;
        rawPrefs.edit().remove(KEY_PENDING_WIDGET).remove(KEY_PENDING_RESULT).apply();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_BIND_WIDGET && req != REQ_CONFIG_WIDGET) return;
        ItemInfo it = pendingWidget;
        if (it == null) return;
        if (res != RESULT_OK) { clearPendingWidget(true); return; }
        if (!uiReady) {
            // La actividad se recreó (poca memoria, cambio de idioma…) y el escritorio aún se está
            // cargando: se completa en applyLoaded → resumePendingWidget().
            rawPrefs.edit().putString(KEY_PENDING_RESULT, req == REQ_BIND_WIDGET ? "bound" : "configured").apply();
            return;
        }
        if (req == REQ_BIND_WIDGET) {
            configureOrPlaceWidget(it);
        } else {
            placeWidget(it);
            clearPendingWidget(false);
        }
    }

    // =================================================================== guardar

    /** Foto de la disposición actual a partir de las vistas (hilo principal). */
    private LauncherModel.Loaded snapshotLayout() {
        LauncherModel.Loaded out = new LauncherModel.Loaded();
        for (int p = 0; p < workspace.getPageCount(); p++) {
            CellLayout cl = workspace.getPageAt(p);
            for (int i = 0; i < cl.getChildCount(); i++) {
                View child = cl.getChildAt(i);
                ItemInfo it = itemOf(child);
                if (it == null) continue;
                CellLayout.LP lp = (CellLayout.LP) child.getLayoutParams();
                it.container = ItemInfo.CONTAINER_DESKTOP;
                it.page = p;
                it.cellX = lp.cellX; it.cellY = lp.cellY;
                it.spanX = lp.spanX; it.spanY = lp.spanY;
                out.desktop.add(it);
            }
        }
        for (int i = 0; i < hotseat.getChildCount(); i++) {
            View child = hotseat.getChildAt(i);
            ItemInfo it = itemOf(child);
            if (it == null) continue;
            CellLayout.LP lp = (CellLayout.LP) child.getLayoutParams();
            it.container = ItemInfo.CONTAINER_HOTSEAT;
            it.page = 0; it.cellX = lp.cellX; it.cellY = 0;
            out.hotseat.add(it);
        }
        out.knownApps = new HashSet<>(knownAppKeys);
        return out;
    }

    /** Guarda: el JSON se arma aquí (hilo principal) y solo la escritura va al hilo IO. */
    private void saveCurrentLayout() {
        if (!uiReady) return;
        cleanupTrailingEmptyPages();
        LauncherModel.Loaded snap = snapshotLayout();
        final String json = model.serialize(snap.desktop, snap.hotseat, snap.knownApps);
        if (json != null) model.runIo(() -> model.writeLayout(json));
    }

    /** Quita las páginas vacías del final (siempre queda al menos una). */
    private void cleanupTrailingEmptyPages() {
        boolean removed = false;
        while (workspace.getPageCount() > 1
                && workspace.getPageAt(workspace.getPageCount() - 1).getChildCount() == 0) {
            workspace.removeViewAt(workspace.getPageCount() - 1);
            removed = true;
        }
        if (removed) {
            pageIndicator.setCount(workspace.getPageCount());
            if (workspace.getCurrentPage() >= workspace.getPageCount()) {
                workspace.snapToPage(workspace.getPageCount() - 1);
            } else {
                pageIndicator.setCurrent(workspace.getCurrentPage());
            }
        }
    }

    private ItemInfo itemOf(View v) {
        if (v instanceof BubbleTextView) return ((BubbleTextView) v).getItem();
        if (v instanceof FolderIcon) return ((FolderIcon) v).getFolder();
        if (v.getTag() instanceof ItemInfo) return (ItemInfo) v.getTag();   // widgets
        return null;
    }

    // =================================================================== cambios de apps

    // Las actualizaciones llegan en ráfagas (varias apps seguidas): se agrupan en una sola recarga.
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable reloadRunnable = this::reloadApps;

    private void scheduleReload() {
        uiHandler.removeCallbacks(reloadRunnable);
        uiHandler.postDelayed(reloadRunnable, 400);
    }

    private void registerAppCallback() {
        appsCallback = new LauncherApps.Callback() {
            @Override public void onPackageRemoved(String packageName, UserHandle user) { scheduleReload(); }
            @Override public void onPackageAdded(String packageName, UserHandle user) { scheduleReload(); }
            @Override public void onPackageChanged(String packageName, UserHandle user) { scheduleReload(); }
            @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean replacing) { scheduleReload(); }
            @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean replacing) { scheduleReload(); }
        };
        try {
            launcherApps.registerCallback(appsCallback, new Handler(Looper.getMainLooper()));
        } catch (Throwable t) {
            Log.e(TAG, "registerCallback", t);
        }
    }

    /** Ajustes → Restablecer: borra la disposición y los widgets, y vuelve a empezar. */
    private void applyReset() {
        prefs.setResetPending(false);
        closeFolder(false);
        if (allAppsOpen) closeAllApps();
        clearPendingWidget(false);   // deleteHost() borra también ese widget
        try {
            widgetHost.deleteHost();
            widgetHost.startListening();
        } catch (Throwable t) { Log.w(TAG, "deleteHost", t); }
        uiReady = false;
        model.runIo(model::deleteLayout);   // en el hilo IO: después de cualquier guardado pendiente
        loadInitial();
    }

    // =================================================================== utilidades

    private void toast(int resId) { Toast.makeText(this, resId, Toast.LENGTH_SHORT).show(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
