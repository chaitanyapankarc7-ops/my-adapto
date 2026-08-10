package com.tejyash.myadapto.accessibility;

import android.content.Context;
import android.content.SharedPreferences;

import com.tejyash.myadapto.utils.Constants;

/**
 * Raw storage layer for accessibility sizing preferences — just get/set,
 * no business rules. AccessibilityManager sits on top of this and owns
 * any derived logic (e.g. how many grid columns a given icon size implies),
 * so this class stays a simple, dumb source of truth for saved values.
 */
public class AccessibilityPreferences {

    // Text size steps (sp) — 4 steps matching seekBar2's max=3
    public static final float[] TEXT_SIZES    = { 14f, 18f, 22f, 28f };
    // Icon size steps (dp) — 4 steps matching seekBar3's max=3
    public static final int[]   ICON_SIZES_DP = { 40, 52, 64, 80 };

    public static final int DEFAULT_FONT_STEP = 1;   // 18sp
    public static final int DEFAULT_ICON_STEP = 1;   // 52dp

    // ── Singleton ───────────────────────────────────────────────────
    private static AccessibilityPreferences instance;

    public static AccessibilityPreferences get(Context ctx) {
        if (instance == null) {
            instance = new AccessibilityPreferences(ctx.getApplicationContext());
        }
        return instance;
    }

    private final SharedPreferences prefs;

    private AccessibilityPreferences(Context ctx) {
        prefs = ctx.getSharedPreferences(Constants.PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
    }

    // ── Getters ─────────────────────────────────────────────────────
    public int   getFontStep()   { return prefs.getInt(Constants.KEY_FONT_STEP, DEFAULT_FONT_STEP); }
    public int   getIconStep()   { return prefs.getInt(Constants.KEY_ICON_STEP, DEFAULT_ICON_STEP); }
    public float getFontSizeSp() { return TEXT_SIZES[getFontStep()]; }
    public int   getIconSizeDp() { return ICON_SIZES_DP[getIconStep()]; }

    // ── Setters — persist to disk then notify the home screen ───────
    public void setFontStep(int step) {
        prefs.edit().putInt(Constants.KEY_FONT_STEP, clamp(step, 0, TEXT_SIZES.length - 1)).apply();
        notifyListener();
    }

    public void setIconStep(int step) {
        prefs.edit().putInt(Constants.KEY_ICON_STEP, clamp(step, 0, ICON_SIZES_DP.length - 1)).apply();
        notifyListener();
    }

    // ── Listener so HomeActivity can react immediately ───────────────
    public interface OnPrefsChangedListener {
        void onPrefsChanged();
    }

    private OnPrefsChangedListener listener;

    public void setListener(OnPrefsChangedListener l) { this.listener = l; }
    public void clearListener()                        { this.listener = null; }

    private void notifyListener() {
        if (listener != null) listener.onPrefsChanged();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}