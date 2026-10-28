package com.webgdl.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Cuadrícula de celdas; coloca vistas en (cellX,cellY) con tamaño (spanX,spanY). */
class CellLayout extends ViewGroup {
    private int columns;
    private int rowsCount;
    private int cellWidth, cellHeight;

    CellLayout(Context c, int columns, int rows) {
        super(c);
        this.columns = columns;
        this.rowsCount = rows;
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setGrid(int cols, int rows) { this.columns = cols; this.rowsCount = rows; requestLayout(); }
    int columns() { return columns; }
    int rowsCount() { return rowsCount; }
    int cellWidth() { return cellWidth; }
    int cellHeight() { return cellHeight; }

    static class LP extends ViewGroup.LayoutParams {
        int cellX, cellY, spanX = 1, spanY = 1;
        LP(int cellX, int cellY, int spanX, int spanY) {
            super(WRAP_CONTENT, WRAP_CONTENT);
            this.cellX = cellX; this.cellY = cellY; this.spanX = spanX; this.spanY = spanY;
        }
    }

    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LP; }
    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() { return new LP(0, 0, 1, 1); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int availW = w - getPaddingLeft() - getPaddingRight();
        int availH = h - getPaddingTop() - getPaddingBottom();
        cellWidth = columns > 0 ? availW / columns : availW;
        cellHeight = rowsCount > 0 ? availH / rowsCount : availH;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LP lp = (LP) child.getLayoutParams();
            int cw = cellWidth * lp.spanX;
            int ch = cellHeight * lp.spanY;
            child.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LP lp = (LP) child.getLayoutParams();
            int x = getPaddingLeft() + lp.cellX * cellWidth;
            int y = getPaddingTop() + lp.cellY * cellHeight;
            child.layout(x, y, x + cellWidth * lp.spanX, y + cellHeight * lp.spanY);
        }
    }

    boolean isOccupied(int cellX, int cellY, int spanX, int spanY, View ignore) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == ignore) continue;
            LP lp = (LP) child.getLayoutParams();
            if (rectsOverlap(cellX, cellY, spanX, spanY, lp.cellX, lp.cellY, lp.spanX, lp.spanY)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rectsOverlap(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    /** Busca la primera celda libre (recorriendo por filas). Devuelve {x,y} o null. */
    int[] findVacant(int spanX, int spanY) {
        for (int y = 0; y <= rowsCount - spanY; y++) {
            for (int x = 0; x <= columns - spanX; x++) {
                if (!isOccupied(x, y, spanX, spanY, null)) return new int[]{x, y};
            }
        }
        return null;
    }

    int[] pointToCell(float px, float py) {
        int x = (int) ((px - getPaddingLeft()) / Math.max(1, cellWidth));
        int y = (int) ((py - getPaddingTop()) / Math.max(1, cellHeight));
        x = Math.max(0, Math.min(columns - 1, x));
        y = Math.max(0, Math.min(rowsCount - 1, y));
        return new int[]{x, y};
    }

    View childAtCell(int cellX, int cellY) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LP lp = (LP) child.getLayoutParams();
            if (cellX >= lp.cellX && cellX < lp.cellX + lp.spanX
                    && cellY >= lp.cellY && cellY < lp.cellY + lp.spanY) {
                return child;
            }
        }
        return null;
    }

    void addInCell(View v, int cellX, int cellY, int spanX, int spanY) {
        addView(v, new LP(cellX, cellY, spanX, spanY));
    }
}
