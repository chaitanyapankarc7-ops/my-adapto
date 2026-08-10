package com.tejyash.myadapto.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores user-chosen overrides for dock slots (e.g. "put WhatsApp in the
 * Phone slot instead of the system dialer"). Separate from GridPreferences
 * since dock slots are keyed by name (dock_gallery, dock_phone, ...), not
 * by row/col — there's always exactly 4 of them, not a variable grid.
 *
 * If a slot has no override saved, HomeFragment falls back to its original
 * behavior: resolving whichever app the device would launch for a system
 * action (dial/camera/gallery/contacts).
 */
public class DockPreferences {

    private static final String PREFS_NAME = "adapto_dock";

    /** Returns the overridden package for this dock slot, or null if unset. */
    public static String getSlot(Context ctx, String dockKey) {
        return prefs(ctx).getString(dockKey, null);
    }

    public static void setSlot(Context ctx, String dockKey, String packageName) {
        prefs(ctx).edit().putString(dockKey, packageName).apply();
    }

    public static void clearSlot(Context ctx, String dockKey) {
        prefs(ctx).edit().remove(dockKey).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
