package com.webgdl.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Puntitos de página bajo el escritorio. */
class PageIndicator extends View {
    private int count = 1;
    private int current = 0;
    private final Paint dot;
    private final Paint active;
    private final int dotR;
    private final int gap;

    PageIndicator(Context c) {
        super(c);
        dotR = Utilities.dp(c, 3);
        gap = Utilities.dp(c, 6);
        dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(c.getColor(R.color.page_dot));
        active = new Paint(Paint.ANTI_ALIAS_FLAG);
        active.setColor(c.getColor(R.color.page_dot_active));
    }

    void setCount(int n) { count = Math.max(1, n); invalidate(); }
    void setCurrent(int c) { current = c; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        if (count <= 1) return;
        float total = count * (dotR * 2) + (count - 1) * gap;
        float x = (getWidth() - total) / 2f + dotR;
        float y = getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            canvas.drawCircle(x, y, i == current ? dotR + 1 : dotR, i == current ? active : dot);
            x += dotR * 2 + gap;
        }
    }
}
