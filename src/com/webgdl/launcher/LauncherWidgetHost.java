package com.webgdl.launcher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** AppWidgetHost que crea vistas con detección propia de pulsación larga. */
class LauncherWidgetHost extends AppWidgetHost {

    LauncherWidgetHost(Context c, int hostId) { super(c, hostId); }

    @Override protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                                                       AppWidgetProviderInfo appWidget) {
        return new WidgetView(context);
    }

    /**
     * Vista de widget. Los widgets suelen tener botones y listas que se quedan con el toque,
     * así que la pulsación larga se detecta aquí, viendo todos los eventos del gesto
     * (dispatchTouchEvent) antes de que lleguen a las vistas internas.
     */
    static class WidgetView extends AppWidgetHostView {
        interface LongPressCallback { void onWidgetLongPress(WidgetView v, float rawX, float rawY); }

        private LongPressCallback callback;
        private final int slop;
        private float downX, downY, downRawX, downRawY;
        private boolean fired;
        private final Runnable longPress = new Runnable() {
            @Override public void run() {
                fired = true;
                if (callback != null) callback.onWidgetLongPress(WidgetView.this, downRawX, downRawY);
            }
        };

        WidgetView(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        void setLongPressCallback(LongPressCallback cb) { this.callback = cb; }

        @Override public boolean dispatchTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX(); downY = ev.getY();
                    downRawX = ev.getRawX(); downRawY = ev.getRawY();
                    fired = false;
                    removeCallbacks(longPress);
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop)
                        removeCallbacks(longPress);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPress);
                    break;
            }
            if (fired) {
                // Tras la pulsación larga, el resto del gesto es del arrastre: no se reparte a los hijos.
                if (ev.getActionMasked() == MotionEvent.ACTION_UP
                        || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) fired = false;
                return true;
            }
            return super.dispatchTouchEvent(ev);
        }

        /** Se queda con el gesto aunque ningún hijo lo tome, para poder ver el ACTION_UP. */
        @Override public boolean onTouchEvent(MotionEvent ev) { return true; }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(longPress);
        }
    }
}
