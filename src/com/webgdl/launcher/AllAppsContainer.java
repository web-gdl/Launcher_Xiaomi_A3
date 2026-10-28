package com.webgdl.launcher;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cajón de todas las apps: barra de búsqueda + cuadrícula desplazable. */
class AllAppsContainer extends LinearLayout {

    interface Callbacks {
        void onAppClick(ItemInfo it, View v);
        void onAppLongClick(ItemInfo it, View v);
        void onRequestCloseAllApps();
        IconCache iconCache();
        Prefs prefs();
    }

    // Deslizar hacia abajo con la lista arriba del todo cierra el cajón.
    // Se detecta en dispatchTouchEvent porque la GridView pide "no interceptar" en cuanto se mueve el dedo.
    private float downX, downY;
    private boolean atTopOnDown;
    private boolean closeFired;
    private final int touchSlop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                closeFired = false;
                atTopOnDown = isListAtTop();
                break;
            case android.view.MotionEvent.ACTION_MOVE:
                if (!closeFired && atTopOnDown && isListAtTop()) {
                    float dy = ev.getY() - downY, dx = Math.abs(ev.getX() - downX);
                    if (dy > touchSlop * 2 && dy > dx * 1.5f) {
                        closeFired = true;
                        // Cancela lo que estuviera haciendo la lista (pulsación, desplazamiento)
                        android.view.MotionEvent cancel = android.view.MotionEvent.obtain(ev);
                        cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        cb.onRequestCloseAllApps();
                        return true;
                    }
                }
                break;
        }
        if (closeFired) {
            int a = ev.getActionMasked();
            if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) closeFired = false;
            return true;   // el resto del gesto ya no es para la lista
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isListAtTop() {
        return grid.getVisibility() != VISIBLE || !grid.canScrollVertically(-1);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent ev) {
        return true; // no dejar pasar toques al escritorio de debajo
    }

    /** Pone el foco en la búsqueda y abre el teclado. */
    void focusSearch() {
        search.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
    }

    private final Callbacks cb;
    private final EditText search;
    private final GridView grid;
    private final TextView empty;
    private final Adapter adapter;
    private final List<ItemInfo> all = new ArrayList<>();
    private final List<ItemInfo> filtered = new ArrayList<>();

    AllAppsContainer(Context c, Callbacks cb) {
        super(c);
        this.cb = cb;
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.drawer_bg);
        int pad = Utilities.dp(c, 12);
        setPadding(pad, pad, pad, 0);

        search = new EditText(c);
        search.setHint(R.string.search_hint);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundResource(R.drawable.search_bg);
        search.setTextColor(c.getColor(R.color.search_text));
        search.setHintTextColor(c.getColor(R.color.search_hint));
        int sp = Utilities.dp(c, 16);
        search.setPadding(sp, 0, sp, 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Utilities.dp(c, 44));
        slp.bottomMargin = Utilities.dp(c, 10);
        addView(search, slp);

        grid = new GridView(c);
        grid.setNumColumns(cb.prefs().columns());
        grid.setVerticalSpacing(Utilities.dp(c, 8));
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        empty = new TextView(c);
        empty.setText(R.string.no_results);
        empty.setTextColor(c.getColor(R.color.drawer_text));
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (position < filtered.size()) cb.onAppClick(filtered.get(position), view);
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < filtered.size()) { cb.onAppLongClick(filtered.get(position), view); return true; }
            return false;
        });

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
    }

    void setApps(List<ItemInfo> apps) {
        all.clear();
        all.addAll(apps);
        applyFilter(search.getText().toString());
    }

    void refreshColumns() { grid.setNumColumns(cb.prefs().columns()); adapter.notifyDataSetChanged(); }

    /** Deja sitio a la barra de navegación bajo la última fila (la lista se dibuja por debajo). */
    void setBottomInset(int px) {
        grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(),
                px + Utilities.dp(getContext(), 8));
        empty.setPadding(0, 0, 0, px);
    }

    private void applyFilter(String q) {
        filtered.clear();
        if (q == null || q.trim().isEmpty()) {
            filtered.addAll(all);
        } else {
            String low = q.toLowerCase(Locale.getDefault());
            for (ItemInfo it : all) {
                if (it.title != null && it.title.toString().toLowerCase(Locale.getDefault()).contains(low)) {
                    filtered.add(it);
                }
            }
        }
        adapter.notifyDataSetChanged();
        boolean none = filtered.isEmpty();
        empty.setVisibility(none ? VISIBLE : GONE);
        grid.setVisibility(none ? GONE : VISIBLE);
    }

    void clearSearch() {
        search.setText("");
        grid.setSelection(0);
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        search.clearFocus();
    }

    private class Adapter extends BaseAdapter {
        public int getCount() { return filtered.size(); }
        public Object getItem(int position) { return filtered.get(position); }
        public long getItemId(int position) { return position; }

        public View getView(int position, View convertView, ViewGroup parent) {
            IconCache ic = cb.iconCache();
            int cell = ic.iconSize() + Utilities.dp(getContext(), 28);
            BubbleTextView v;
            if (convertView instanceof BubbleTextView) {
                v = (BubbleTextView) convertView;
            } else {
                v = new BubbleTextView(getContext(), ic.iconSize(), cb.prefs().showLabels());
                v.setLayoutParams(new GridView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, cell + Utilities.dp(getContext(), 14)));
            }
            final ItemInfo it = filtered.get(position);
            final BubbleTextView view = v;
            view.setItem(it);
            ic.loadIcon(it, bmp -> view.applyIcon(it, bmp));
            return view;
        }
    }
}
