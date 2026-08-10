package com.tejyash.myadapto.utils;

/**
 * Central home for SharedPreferences names and keys used across the app.
 *
 * Before this class existed, the same literal strings ("AdaptoPrefs",
 * "setupComplete") were retyped by hand in GetStartedPage and
 * SizeEditingPage. That's an easy way to introduce a silent bug — if one
 * file ever misspells the key, it silently reads/writes a different,
 * disconnected preference and nothing crashes to tell you why.
 */
public final class Constants {

    private Constants() { } // static-only, never instantiated

    // ── Onboarding flow (GetStartedPage reads, SizeEditingPage writes) ──
    public static final String PREFS_ONBOARDING   = "AdaptoPrefs";
    public static final String KEY_SETUP_COMPLETE = "setupComplete";

    // ── Accessibility sizing preferences (AccessibilityPreferences) ─────
    public static final String PREFS_ACCESSIBILITY = "adapto_accessibility";
    public static final String KEY_FONT_STEP        = "font_step";
    public static final String KEY_ICON_STEP        = "icon_step";
}