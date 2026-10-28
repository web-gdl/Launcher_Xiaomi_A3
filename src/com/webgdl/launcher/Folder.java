package com.webgdl.launcher;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;

/** Vista de una carpeta abierta: título editable + cuadrícula de su contenido. */
class Folder extends LinearLayout {

    interface Callbacks {
        void onFolderItemClick(ItemInfo folder, ItemInfo item, View v);
        void onFolderItemLongClick(ItemInfo folder, ItemInfo item, View v);
        void onFolderRenamed(ItemInfo folder, String name);
        IconCache iconCache();
        Prefs prefs();
    }

    private final Callbacks cb;
    private ItemInfo folder;
    private final EditText title;
    private final GridView grid;
    private final Adapter adapter;

    Folder(Context c, Callbacks cb) {
        super(c);
        this.cb = cb;
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.folder_bg);
        int pad = Utilities.dp(c, 16);
        setPadding(pad, pad, pad, pad);

        title = new EditText(c);
        title.setSingleLine(true);
        title.setHint(R.string.folder_name_hint);
        title.setTextColor(0xFFFFFFFF);
        title.setHintTextColor(0x99FFFFFF);
        title.setBackgroundColor(0x00000000);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = Utilities.dp(c, 8);
        addView(title, tlp);

        grid = new GridView(c);
        grid.setNumColumns(Math.min(4, cb.prefs().columns()));
        grid.setVerticalSpacing(Utilities.dp(c, 8));
        grid.setSelector(android.R.color.transparent);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (folder != null && position < folder.contents.size())
                cb.onFolderItemClick(folder, folder.contents.get(position), view);
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (folder != null && position < folder.contents.size()) {
                cb.onFolderItemLongClick(folder, folder.contents.get(position), view);
                return true;
            }
            return false;
        });

        title.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (folder != null) cb.onFolderRenamed(folder, s.toString());
            }
        });
    }

    void bind(ItemInfo f) {
        this.folder = f;
        title.setText(f.title == null ? "" : f.title.toString());
        adapter.notifyDataSetChanged();
    }

    ItemInfo getFolder() { return folder; }
    void refresh() { adapter.notifyDataSetChanged(); }

    private class Adapter extends BaseAdapter {
        public int getCount() { return folder == null ? 0 : folder.contents.size(); }
        public Object getItem(int position) { return folder.contents.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View convertView, ViewGroup parent) {
            IconCache ic = cb.iconCache();
            BubbleTextView v;
            if (convertView instanceof BubbleTextView) v = (BubbleTextView) convertView;
            else {
                v = new BubbleTextView(getContext(), ic.iconSize(), cb.prefs().showLabels());
                v.setLayoutParams(new GridView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ic.iconSize() + Utilities.dp(getContext(), 42)));
            }
            final ItemInfo it = folder.contents.get(position);
            final BubbleTextView view = v;
            view.setItem(it);
            ic.loadIcon(it, bmp -> view.applyIcon(it, bmp));
            return view;
        }
    }
}
