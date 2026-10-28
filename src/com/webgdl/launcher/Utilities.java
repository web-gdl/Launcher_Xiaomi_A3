package com.webgdl.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;

/** Utilidades generales: medidas, iconos y márgenes de las barras del sistema. */
final class Utilities {
    private Utilities() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics()));
    }

    /** Convierte un Drawable (incluido adaptativo) en un Bitmap cuadrado del tamaño pedido. */
    static Bitmap drawableToBitmap(Drawable d, int size) {
        if (d == null) return null;
        if (d instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable) d).getBitmap();
            if (bm != null && bm.getWidth() == size && bm.getHeight() == size) return bm;
        }
        if (size <= 0) size = 1;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        return bitmap;
    }

    /** Hace que la ventana se dibuje detrás de las barras del sistema (los márgenes se aplican aparte). */
    @SuppressWarnings("deprecation")
    static void drawBehindSystemBars(Activity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            a.getWindow().setDecorFitsSystemWindows(false);
        } else {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    /** Márgenes ocupados por la barra de estado, la de navegación y el recorte de cámara. */
    @SuppressWarnings("deprecation")
    static Rect systemBarInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new Rect(i.left, i.top, i.right, i.bottom);
        }
        return new Rect(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
    }

    /** Aplica los márgenes del sistema como padding de la vista (además del padding base dado). */
    static void padForSystemBars(final View v, final int baseL, final int baseT, final int baseR, final int baseB) {
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            Rect r = systemBarInsets(insets);
            view.setPadding(baseL + r.left, baseT + r.top, baseR + r.right, baseB + r.bottom);
            return insets;
        });
        v.requestApplyInsets();
    }
}
