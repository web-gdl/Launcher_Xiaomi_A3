package com.webgdl.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** Icono de carpeta: miniaturas de su contenido en 2×2 + etiqueta. */
class FolderIcon extends View {
    private ItemInfo folder;
    private final int iconSize;
    private final boolean showLabel;
    private final Paint bgPaint;
    private final TextPaint textPaint;
    private final int pad;
    private final int labelTopMargin;
    private final IconCache iconCache;

    FolderIcon(Context c, int iconSizePx, boolean showLabel, IconCache ic) {
        super(c);
        this.iconSize = iconSizePx;
        this.showLabel = showLabel;
        this.iconCache = ic;
        this.pad = Utilities.dp(c, 2);
        this.labelTopMargin = Utilities.dp(c, 4);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(c.getColor(R.color.folder_ring));

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(c.getColor(R.color.label_text));
        textPaint.setTextSize(Utilities.sp(c, 12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(Utilities.dp(c, 2), 0, Utilities.dp(c, 1),
                c.getColor(R.color.label_shadow));
        setWillNotDraw(false);
    }

    void setFolder(ItemInfo f) {
        this.folder = f;
        // precarga las primeras 4 miniaturas
        if (f != null) {
            int n = Math.min(4, f.contents.size());
            for (int i = 0; i < n; i++) {
                final ItemInfo it = f.contents.get(i);
                if (it.icon == null) {
                    iconCache.loadIcon(it, bmp -> { it.icon = bmp; postInvalidate(); });
                }
            }
        }
        invalidate();
    }

    ItemInfo getFolder() { return folder; }

    @Override protected void onDraw(Canvas canvas) {
        if (folder == null) return;
        int w = getWidth();
        int left = (w - iconSize) / 2;
        int top = pad;
        float radius = Utilities.dp(getContext(), 12);
        RectF bg = new RectF(left, top, left + iconSize, top + iconSize);
        canvas.drawRoundRect(bg, radius, radius, bgPaint);

        int inset = Utilities.dp(getContext(), 5);
        int gridLeft = left + inset, gridTop = top + inset;
        int mini = (iconSize - inset * 2 - Utilities.dp(getContext(), 3)) / 2;
        int gap = Utilities.dp(getContext(), 3);
        int n = Math.min(4, folder.contents.size());
        for (int i = 0; i < n; i++) {
            ItemInfo it = folder.contents.get(i);
            if (it.icon == null) continue;
            int col = i % 2, row = i / 2;
            int x = gridLeft + col * (mini + gap);
            int y = gridTop + row * (mini + gap);
            canvas.drawBitmap(it.icon, null, new Rect(x, y, x + mini, y + mini), null);
        }

        if (showLabel && folder.title != null && folder.title.length() > 0) {
            int textTop = top + iconSize + labelTopMargin;
            String ell = TextUtils.ellipsize(folder.title, textPaint, w - pad * 2,
                    TextUtils.TruncateAt.END).toString();
            canvas.drawText(ell, w / 2f, textTop - textPaint.ascent(), textPaint);
        }
    }
}
