package com.webgdl.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Ajustes: cuadrícula, tamaño de iconos, etiquetas, insignias y restablecer. */
public class SettingsActivity extends Activity {

    private Prefs prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Utilities.dp(this, 20);
        sv.addView(root);
        setContentView(sv);
        Utilities.drawBehindSystemBars(this);
        Utilities.padForSystemBars(root, pad, pad, pad, pad);

        addTitle(root, getString(R.string.settings));

        addSeek(root, getString(R.string.grid_columns), 3, 7, prefs.columns(), prefs::setColumns);
        addSeek(root, getString(R.string.grid_rows), 4, 8, prefs.rows(), prefs::setRows);
        addSeek(root, getString(R.string.icon_size) + " (dp)", 36, 72, prefs.iconSizeDp(), prefs::setIconSizeDp);

        addSwitch(root, getString(R.string.show_labels), prefs.showLabels(), prefs::setShowLabels);

        if (hasBadgeService()) {
            addButton(root, getString(R.string.badges_enable), () -> {
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (Throwable t) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            });
            addHint(root, getString(R.string.badges_hint));
        } else {
            addHint(root, getString(R.string.badges_unavailable));
        }

        addButton(root, getString(R.string.reset_layout), () ->
                new AlertDialog.Builder(this)
                        .setMessage(R.string.reset_layout_confirm)
                        .setPositiveButton(R.string.ok, (d, w) -> {
                            // La pantalla de inicio lo aplica al volver (borra la disposición y los widgets).
                            prefs.setResetPending(true);
                            finish();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());
    }

    /** La variante «normal» no declara el servicio de insignias (ver build.sh). */
    private boolean hasBadgeService() {
        try {
            getPackageManager().getServiceInfo(
                    new android.content.ComponentName(this, BadgeNotificationListener.class), 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private interface IntSetter { void set(int v); }
    private interface BoolSetter { void set(boolean v); }
    private interface Run { void run(); }

    private void addTitle(LinearLayout root, String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(22);
        tv.setPadding(0, 0, 0, Utilities.dp(this, 16));
        root.addView(tv);
    }

    private void addSeek(LinearLayout root, final String label, final int min, int max, int value,
                         final IntSetter setter) {
        final TextView tv = new TextView(this);
        tv.setText(label + ": " + value);
        tv.setPadding(0, Utilities.dp(this, 14), 0, 0);
        root.addView(tv);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = min + progress;
                tv.setText(label + ": " + v);
                if (fromUser) setter.set(v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(sb);
    }

    private void addSwitch(LinearLayout root, String label, boolean value, final BoolSetter setter) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setChecked(value);
        sw.setPadding(0, Utilities.dp(this, 18), 0, Utilities.dp(this, 6));
        sw.setOnCheckedChangeListener((btn, checked) -> setter.set(checked));
        root.addView(sw);
    }

    private void addButton(LinearLayout root, String label, final Run action) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Utilities.dp(this, 16);
        btn.setOnClickListener(v -> action.run());
        root.addView(btn, lp);
    }

    private void addHint(LinearLayout root, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setAlpha(0.7f);
        tv.setPadding(0, Utilities.dp(this, 6), 0, 0);
        root.addView(tv);
    }
}
