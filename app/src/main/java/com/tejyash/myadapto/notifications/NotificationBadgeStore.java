package com.tejyash.myadapto.notifications;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Holds the current active-notification count per package. Written by
 * AdaptoNotificationListenerService whenever notifications post/clear,
 * read by HomeGridAdapter/AppGridAdapter when binding each cell.
 *
 * Kept as plain SharedPreferences rather than a live service binding —
 * simpler, and adapters already re-bind on a normal Android lifecycle
 * (grid refresh, RecyclerView scroll) so a live push isn't needed.
 */
public class NotificationBadgeStore {

    private static final String PREFS = "adapto_badges";

    public static int getCount(Context ctx, String packageName) {
        return prefs(ctx).getInt(packageName, 0);
    }

    public static void setCount(Context ctx, String packageName, int count) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (count <= 0) e.remove(packageName); else e.putInt(packageName, count);
        e.apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
