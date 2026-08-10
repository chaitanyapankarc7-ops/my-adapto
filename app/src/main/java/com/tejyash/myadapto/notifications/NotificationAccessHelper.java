package com.tejyash.myadapto.notifications;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

/**
 * Notification access can't be requested as a normal runtime permission —
 * it requires the user to flip it on manually in system settings. This
 * checks whether it's already granted, and tracks whether HomeFragment has
 * already asked once (so we prompt at most one time, not on every launch).
 */
public class NotificationAccessHelper {

    private static final String PREFS = "adapto_badges";
    private static final String KEY_PROMPTED = "notif_access_prompted";

    public static boolean isEnabled(Context ctx) {
        return NotificationManagerCompat.getEnabledListenerPackages(ctx)
                .contains(ctx.getPackageName());
    }

    public static boolean hasPromptedBefore(Context ctx) {
        return prefs(ctx).getBoolean(KEY_PROMPTED, false);
    }

    public static void markPrompted(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_PROMPTED, true).apply();
    }

    /** Takes the user to the system screen where they toggle access on. */
    public static Intent settingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
