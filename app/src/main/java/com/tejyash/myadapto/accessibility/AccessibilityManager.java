package com.tejyash.myadapto.accessibility;

import android.content.Context;

import com.tejyash.myadapto.launcher.GridPreferences;

/**
 * Business-logic layer over AccessibilityPreferences. Activities talk to
 * this class, not to AccessibilityPreferences directly — it keeps rules
 * like "how many grid columns should this icon size get" in one place
 * instead of being duplicated wherever it's needed.
 */
public class AccessibilityManager implements AccessibilityPreferences.OnPrefsChangedListener {

    /**
     * Grid column count for each size step (0 = smallest, 3 = largest).
     * Bigger icons/text need more room per cell, so fewer columns fit on
     * screen — and vice versa for smaller ones. Whichever of icon size or
     * font size is currently larger drives the column count, since either
     * one growing means each grid cell needs more space.
     */
    private static final int[] GRID_COLUMNS_BY_STEP = { 5, 4, 4, 3 };

    private final Context context;
    private final AccessibilityPreferences prefs;
    private OnAccessibilityChangedListener listener;

    public AccessibilityManager(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.prefs = AccessibilityPreferences.get(ctx);
    }

    public interface OnAccessibilityChangedListener {
        void onAccessibilityChanged();
    }

    public void setListener(OnAccessibilityChangedListener l) {
        this.listener = l;
        prefs.setListener(this);
    }

    public void clearListener() {
        this.listener = null;
        prefs.clearListener(this);
    }

    @Override
    public void onPrefsChanged() {
        if (listener != null) listener.onAccessibilityChanged();
    }

    // ── Reads ───────────────────────────────────────────────────────
    public float getFontSizeSp() { return prefs.getFontSizeSp(); }
    public int   getIconSizeDp() { return prefs.getIconSizeDp(); }
    public int   getFontStep()   { return prefs.getFontStep(); }
    public int   getIconStep()   { return prefs.getIconStep(); }

    /** Derives the app grid's column count from the current icon/font size. */
    public int getGridColumns() {
        return gridColumnsFor(prefs.getIconStep(), prefs.getFontStep());
    }

    private int gridColumnsFor(int iconStep, int fontStep) {
        int step = Math.max(iconStep, fontStep);
        return GRID_COLUMNS_BY_STEP[step];
    }

    // ── Writes ──────────────────────────────────────────────────────
    // Grid columns are pushed to GridPreferences *before* the prefs
    // setter fires its change notification, so that by the time
    // AppsFragment/HomeFragment react to onAccessibilityChanged(),
    // GridPreferences.getColumns() already reflects the new size —
    // otherwise the grid listeners would read the stale column count.
    public void setFontStep(int step) {
        GridPreferences.saveColumns(context, gridColumnsFor(prefs.getIconStep(), step));
        prefs.setFontStep(step);
    }

    public void setIconStep(int step) {
        GridPreferences.saveColumns(context, gridColumnsFor(step, prefs.getFontStep()));
        prefs.setIconStep(step);
    }
}