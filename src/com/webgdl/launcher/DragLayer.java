package com.webgdl.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Contenedor raíz.
 * - Durante un arrastre, se queda con todos los toques y se los pasa al DragController.
 * - Si no, detecta el deslizamiento vertical en cualquier parte (escritorio, dock, puntos de página):
 *   hacia arriba abre el cajón, hacia abajo baja las notificaciones. El horizontal lo deja al escritorio.
 */
class DragLayer extends FrameLayout {
    interface SwipeHandler {
        boolean canSwipe();
        void onSwipeUp();
        void onSwipeDown();
    }

    private DragController dragController;
    private SwipeHandler swipeHandler;
    private final int slop;
    private float downX, downY;
    private boolean swipeDecided;   // ya se decidió la dirección de este gesto
    private boolean swipeConsumed;  // el gesto fue un deslizamiento vertical: se ignora el resto

    DragLayer(Context c) {
        super(c);
        setClipChildren(false);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    void setDragController(DragController dc) { this.dragController = dc; }
    void setSwipeHandler(SwipeHandler h) { this.swipeHandler = h; }

    private boolean dragging() { return dragController != null && dragController.isDragging(); }

    /** Devuelve true si este evento completa un deslizamiento vertical (y lo dispara). */
    private boolean detectSwipe(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                swipeDecided = false;
                swipeConsumed = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (swipeDecided || swipeHandler == null || !swipeHandler.canSwipe()) return false;
                float dx = Math.abs(ev.getX() - downX);
                float dy = ev.getY() - downY;
                float ady = Math.abs(dy);
                if (dx > slop && dx > ady) {
                    swipeDecided = true;            // horizontal: lo gestiona el escritorio
                    return false;
                }
                if (ady > slop * 2 && ady > dx * 1.5f) {
                    swipeDecided = true;
                    swipeConsumed = true;
                    if (dy < 0) swipeHandler.onSwipeUp(); else swipeHandler.onSwipeDown();
                    return true;
                }
                return false;
        }
        return false;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (dragging()) return true;
        return detectSwipe(ev);
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (dragging()) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    dragController.move(ev.getX(), ev.getY());
                    break;
                case MotionEvent.ACTION_UP:
                    dragController.drop(ev.getX(), ev.getY());
                    break;
                case MotionEvent.ACTION_CANCEL:
                    dragController.cancel();
                    break;
            }
            return true;
        }
        int a = ev.getActionMasked();
        if (swipeConsumed) {
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) swipeConsumed = false;
            return true;
        }
        // Toque que no tomó ningún hijo (puntos de página, hueco del dock): se sigue el gesto
        // aquí para poder detectar el deslizamiento.
        if (a == MotionEvent.ACTION_DOWN) {
            detectSwipe(ev);
            return swipeHandler != null && swipeHandler.canSwipe();
        }
        detectSwipe(ev);
        return true;
    }
}
