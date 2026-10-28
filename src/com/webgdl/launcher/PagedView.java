package com.webgdl.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/**
 * Escritorio con páginas horizontales de {@link CellLayout}.
 *
 * Gestos (empiecen sobre un icono o sobre una zona vacía):
 *  - horizontal  → cambiar de página
 *  - hacia arriba → abrir el cajón de apps
 *  - hacia abajo  → bajar la barra de notificaciones
 *  - pulsación larga en zona vacía → menú del escritorio
 */
class PagedView extends ViewGroup {
    interface PageListener { void onPageChanged(int page); }
    interface SwipeListener { void onSwipeUp(); void onSwipeDown(); }
    interface EmptyLongPressListener { void onEmptyLongPress(); }

    private static final int STATE_NONE = 0;     // aún no se sabe la dirección
    private static final int STATE_PAGING = 1;   // arrastre horizontal
    private static final int STATE_SWIPED = 2;   // gesto vertical ya consumido

    private final Scroller scroller;
    private VelocityTracker velocityTracker;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;

    private float lastX, downX, downY;
    private int state = STATE_NONE;
    private boolean caughtInMotion;
    private int currentPage;

    private PageListener pageListener;
    private SwipeListener swipeListener;
    private EmptyLongPressListener longPressListener;
    private Runnable longPressRunnable;

    PagedView(Context c) {
        super(c);
        scroller = new Scroller(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        touchSlop = vc.getScaledPagingTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        setClipChildren(false);
    }

    void setPageListener(PageListener l) { pageListener = l; }
    void setSwipeListener(SwipeListener l) { swipeListener = l; }
    void setEmptyLongPressListener(EmptyLongPressListener l) { longPressListener = l; }
    int getCurrentPage() { return currentPage; }
    int getPageCount() { return getChildCount(); }
    CellLayout getPageAt(int i) { return (CellLayout) getChildAt(i); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = getWidth();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(i * w, 0, i * w + w, getHeight());
        }
        if (scroller.isFinished() && state != STATE_PAGING) scrollTo(currentPage * w, 0);
    }

    // ------------------------------------------------------------------ gestos

    /** Decide la dirección del gesto. Devuelve true si a partir de aquí lo gestiona esta vista. */
    private boolean decide(MotionEvent ev) {
        float dx = Math.abs(ev.getX() - downX);
        float dy = ev.getY() - downY;
        float ady = Math.abs(dy);
        if (dx > touchSlop && dx > ady) {
            state = STATE_PAGING;
            lastX = ev.getX();
            cancelEmptyLongPress();
            return true;
        }
        if (ady > touchSlop * 2 && ady > dx * 1.5f) {
            state = STATE_SWIPED;
            cancelEmptyLongPress();
            if (swipeListener != null) {
                if (dy < 0) swipeListener.onSwipeUp(); else swipeListener.onSwipeDown();
            }
            return true;
        }
        return false;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                    state = STATE_PAGING;      // se atrapa la página en movimiento
                    caughtInMotion = true;
                    return true;
                }
                state = STATE_NONE;
                caughtInMotion = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (state == STATE_NONE) return decide(ev);
                return state != STATE_NONE;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return false;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(ev);
        int w = Math.max(1, getWidth());
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (caughtInMotion) {          // viene de onInterceptTouchEvent: sigue en PAGING
                    caughtInMotion = false;
                    return true;
                }
                // Llega aquí si el toque empezó en una zona vacía (ningún icono lo tomó).
                downX = lastX = ev.getX();
                downY = ev.getY();
                if (!scroller.isFinished()) { scroller.abortAnimation(); state = STATE_PAGING; }
                else { state = STATE_NONE; scheduleEmptyLongPress(); }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (state == STATE_NONE) {
                    if (Math.abs(ev.getX() - downX) > touchSlop || Math.abs(ev.getY() - downY) > touchSlop)
                        cancelEmptyLongPress();
                    decide(ev);
                }
                if (state == STATE_PAGING) {
                    float delta = lastX - ev.getX();
                    lastX = ev.getX();
                    int maxScroll = Math.max(0, (getChildCount() - 1) * w);
                    float target = getScrollX() + delta;
                    if (target < 0 || target > maxScroll) target = getScrollX() + delta * 0.3f; // resistencia
                    target = Math.max(-w * 0.2f, Math.min(maxScroll + w * 0.2f, target));
                    scrollTo((int) target, 0);
                }
                return true;
            case MotionEvent.ACTION_UP:
                cancelEmptyLongPress();
                if (state == STATE_PAGING) {
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    int vx = (int) velocityTracker.getXVelocity();
                    int target;
                    if (Math.abs(vx) > minFlingVelocity) target = vx < 0 ? currentPage + 1 : currentPage - 1;
                    else target = (getScrollX() + w / 2) / w;
                    snapToPage(target);
                }
                state = STATE_NONE;
                recycleVelocity();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelEmptyLongPress();
                if (state == STATE_PAGING) snapToPage(currentPage);
                state = STATE_NONE;
                recycleVelocity();
                return true;
        }
        return true;
    }

    private void scheduleEmptyLongPress() {
        cancelEmptyLongPress();
        if (longPressListener == null) return;
        longPressRunnable = () -> {
            longPressRunnable = null;
            if (state == STATE_NONE) {
                state = STATE_SWIPED; // ignora el resto del gesto
                longPressListener.onEmptyLongPress();
            }
        };
        postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelEmptyLongPress() {
        if (longPressRunnable != null) { removeCallbacks(longPressRunnable); longPressRunnable = null; }
    }

    private void recycleVelocity() {
        if (velocityTracker != null) { velocityTracker.recycle(); velocityTracker = null; }
    }

    // ------------------------------------------------------------------ páginas

    void snapToPage(int page) {
        if (getChildCount() == 0) return;
        page = Math.max(0, Math.min(getChildCount() - 1, page));
        int dx = page * getWidth() - getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, 320);
        if (page != currentPage) {
            currentPage = page;
            if (pageListener != null) pageListener.onPageChanged(page);
        }
        invalidate();
    }

    /** Termina en seco cualquier animación de cambio de página (antes de calcular dónde se suelta algo). */
    void finishScroll() {
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
            scrollTo(currentPage * getWidth(), 0);
        }
    }

    void setCurrentPageImmediate(int page) {
        currentPage = Math.max(0, Math.min(Math.max(0, getChildCount() - 1), page));
        if (!scroller.isFinished()) scroller.abortAnimation();
        scrollTo(currentPage * getWidth(), 0);
        if (pageListener != null) pageListener.onPageChanged(currentPage);
    }

    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }
}
