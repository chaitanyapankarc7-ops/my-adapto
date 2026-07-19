package com.tejyash.myadapto.accessibility;

import android.content.Context;

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

    private final AccessibilityPreferences prefs;
    private OnAccessibilityChangedListener listener;

    public AccessibilityManager(Context ctx) {
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
        prefs.clearListener();
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
        int step = Math.max(prefs.getIconStep(), prefs.getFontStep());
        return GRID_COLUMNS_BY_STEP[step];
    }

    // ── Writes ──────────────────────────────────────────────────────
    public void setFontStep(int step) { prefs.setFontStep(step); }
    public void setIconStep(int step) { prefs.setIconStep(step); }
}