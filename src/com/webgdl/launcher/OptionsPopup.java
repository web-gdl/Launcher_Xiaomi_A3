package com.webgdl.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Menú emergente al mantener pulsado un icono: info, desinstalar, quitar, atajos. */
class OptionsPopup {

    interface Action { void run(); }

    private final Context ctx;
    private final LinearLayout root;
    private final PopupWindow window;

    OptionsPopup(Context c) {
        this.ctx = c;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.popup_bg);
        int pad = Utilities.dp(c, 6);
        root.setPadding(pad, pad, pad, pad);
        root.setElevation(Utilities.dp(c, 8));

        window = new PopupWindow(root,
                Utilities.dp(c, 220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        window.setOutsideTouchable(true);
        window.setFocusable(true);
    }

    OptionsPopup addItem(int iconRes, CharSequence label, final Action action) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.ripple_bg);
        int h = Utilities.dp(ctx, 48);
        int p = Utilities.dp(ctx, 10);
        row.setPadding(p, 0, p, 0);
        row.setMinimumHeight(h);
        row.setClickable(true);

        ImageView icon = new ImageView(ctx);
        if (iconRes != 0) {
            icon.setImageResource(iconRes);
            icon.setColorFilter(0xFFFFFFFF);
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                Utilities.dp(ctx, 22), Utilities.dp(ctx, 22));
        ilp.rightMargin = Utilities.dp(ctx, 14);
        row.addView(icon, ilp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(ctx.getColor(R.color.popup_text));
        tv.setTextSize(15);
        tv.setSingleLine(true);
        row.addView(tv, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> { window.dismiss(); action.run(); });
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));
        return this;
    }

    OptionsPopup addShortcut(CharSequence label, Drawable icon, final Action action) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.ripple_bg);
        int h = Utilities.dp(ctx, 48);
        int p = Utilities.dp(ctx, 10);
        row.setPadding(p, 0, p, 0);
        row.setClickable(true);

        ImageView iv = new ImageView(ctx);
        if (icon != null) iv.setImageDrawable(icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                Utilities.dp(ctx, 26), Utilities.dp(ctx, 26));
        ilp.rightMargin = Utilities.dp(ctx, 14);
        row.addView(iv, ilp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(ctx.getColor(R.color.popup_text));
        tv.setTextSize(14);
        tv.setSingleLine(true);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> { window.dismiss(); action.run(); });
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        return this;
    }

    void showAt(View anchor) {
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int y = loc[1] - Utilities.dp(ctx, 8);
        try {
            window.showAtLocation(anchor, Gravity.NO_GRAVITY,
                    loc[0], Math.max(Utilities.dp(ctx, 24), y - Utilities.dp(ctx, 100)));
        } catch (Throwable ignored) {}
    }

    boolean isEmpty() { return root.getChildCount() == 0; }
}
