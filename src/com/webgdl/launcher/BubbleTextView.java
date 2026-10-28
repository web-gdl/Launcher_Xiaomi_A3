package com.webgdl.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * Celda visual de un icono: dibuja icono + etiqueta (opcional) + insignia de notificación.
 * Se usa en el escritorio, el dock, las carpetas y el cajón de apps.
 */
class BubbleTextView extends View implements BadgeCache.Observer {
    private ItemInfo item;
    private Bitmap icon;
    private final int iconSize;
    private final boolean showLabel;
    private final TextPaint textPaint;
    private final Paint iconPaint;
    private final Paint badgePaint;
    private final TextPaint badgeTextPaint;
    private final int labelTopMargin;
    private final int badgeSize;
    private final int pad;
    private String labelCache;
    private boolean badgesEnabled = true;

    BubbleTextView(Context c, int iconSizePx, boolean showLabel) {
        super(c);
        this.iconSize = iconSizePx;
        this.showLabel = showLabel;
        this.pad = Utilities.dp(c, 2);
        this.labelTopMargin = Utilities.dp(c, 4);
        this.badgeSize = Utilities.dp(c, 16);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(c.getColor(R.color.label_text));
        textPaint.setTextSize(Utilities.sp(c, 12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(Utilities.dp(c, 2), 0, Utilities.dp(c, 1),
                c.getColor(R.color.label_shadow));

        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(c.getColor(R.color.badge_bg));

        badgeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        badgeTextPaint.setColor(c.getColor(R.color.badge_text));
        badgeTextPaint.setTextSize(Utilities.sp(c, 10));
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        setWillNotDraw(false);
    }

    void setBadgesEnabled(boolean enabled) { badgesEnabled = enabled; }

    ItemInfo getItem() { return item; }

    void setItem(ItemInfo it) {
        this.item = it;
        this.icon = it != null ? it.icon : null;
        this.labelCache = (it != null && it.title != null) ? it.title.toString() : "";
        invalidate();
    }

    /**
     * Aplica un icono cargado en segundo plano. Solo se pinta si esta vista sigue
     * mostrando ese mismo elemento (en listas recicladas puede haber cambiado).
     */
    void applyIcon(ItemInfo forItem, Bitmap bmp) {
        if (forItem != null) forItem.icon = bmp;
        if (forItem == item) {
            this.icon = bmp;
            invalidate();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        BadgeCache.addObserver(this);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        BadgeCache.removeObserver(this);
    }

    @Override public void onBadgesChanged() { postInvalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int iconLeft = (w - iconSize) / 2;
        int iconTop = pad;

        if (icon != null) {
            Rect dst = new Rect(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
            canvas.drawBitmap(icon, null, dst, iconPaint);
        }

        if (showLabel && labelCache != null && labelCache.length() > 0) {
            int textTop = iconTop + iconSize + labelTopMargin;
            String ell = TextUtils.ellipsize(labelCache, textPaint, w - pad * 2,
                    TextUtils.TruncateAt.END).toString();
            canvas.drawText(ell, w / 2f, textTop - textPaint.ascent(), textPaint);
        }

        if (badgesEnabled && item != null) {
            String pkg = item.targetPackage();
            if (pkg != null && BadgeCache.has(pkg)) {
                int count = BadgeCache.getCount(pkg);
                float cx = iconLeft + iconSize - badgeSize / 2f + pad;
                float cy = iconTop + badgeSize / 2f - pad;
                canvas.drawCircle(cx, cy, badgeSize / 2f, badgePaint);
                if (count > 0) {
                    String txt = count > 99 ? "99+" : String.valueOf(count);
                    float ty = cy - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f;
                    canvas.drawText(txt, cx, ty, badgeTextPaint);
                }
            }
        }
    }
}
