package com.webgdl.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Arrastre de iconos y widgets. Dibuja una copia flotante que sigue al dedo
 * dentro del DragLayer y avisa al listener de los movimientos y del soltado.
 * Si el arrastre se cancela (p. ej. el sistema roba el gesto), el listener
 * recibe el elemento para devolverlo a su sitio: nunca se pierde.
 */
class DragController {
    interface DragListener {
        void onDragMove(ItemInfo item, float x, float y);
        void onDrop(ItemInfo item, float x, float y);
        void onDragEnd(ItemInfo item, boolean dropped);
    }

    private final Context ctx;
    private final FrameLayout dragLayer;
    private DragListener listener;

    private ImageView floatingView;
    private ItemInfo dragItem;
    private float touchOffsetX, touchOffsetY;
    private float viewLeft, viewTop;
    private boolean dragging;

    DragController(Context c, FrameLayout dragLayer) {
        this.ctx = c;
        this.dragLayer = dragLayer;
    }

    void setListener(DragListener l) { this.listener = l; }
    boolean isDragging() { return dragging; }
    float getViewLeft() { return viewLeft; }
    float getViewTop() { return viewTop; }

    void startDrag(Bitmap bmp, int left, int top, float touchX, float touchY, ItemInfo item) {
        if (dragging) return;
        dragging = true;
        dragItem = item;
        touchOffsetX = touchX - left;
        touchOffsetY = touchY - top;
        viewLeft = left;
        viewTop = top;

        floatingView = new ImageView(ctx);
        floatingView.setImageBitmap(bmp);
        floatingView.setAlpha(0.9f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bmp.getWidth(), bmp.getHeight());
        lp.gravity = Gravity.START | Gravity.TOP;
        dragLayer.addView(floatingView, lp);
        floatingView.setTranslationX(left);
        floatingView.setTranslationY(top);
        floatingView.setScaleX(1.08f);
        floatingView.setScaleY(1.08f);
        try {
            dragLayer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable ignored) {}
    }

    void move(float touchX, float touchY) {
        if (!dragging || floatingView == null) return;
        viewLeft = touchX - touchOffsetX;
        viewTop = touchY - touchOffsetY;
        // translation en vez de márgenes: no fuerza un nuevo layout en cada movimiento
        floatingView.setTranslationX(viewLeft);
        floatingView.setTranslationY(viewTop);
        if (listener != null) listener.onDragMove(dragItem, touchX, touchY);
    }

    void drop(float touchX, float touchY) {
        if (!dragging) return;
        ItemInfo it = dragItem;
        boolean ok = true;
        try {
            if (listener != null) listener.onDrop(it, touchX, touchY);
        } catch (Throwable t) {
            ok = false;
        }
        finish(ok);
    }

    void cancel() {
        if (!dragging) return;
        finish(false);
    }

    private void finish(boolean dropped) {
        if (floatingView != null) {
            dragLayer.removeView(floatingView);
            floatingView = null;
        }
        dragging = false;
        ItemInfo it = dragItem;
        dragItem = null;
        if (listener != null) listener.onDragEnd(it, dropped);
    }
}
