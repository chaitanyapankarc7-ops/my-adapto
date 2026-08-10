package com.tejyash.myadapto.notifications;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Requires the user to manually grant "Notification access" in system
 * settings (Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) — this can't
 * be requested as a normal runtime permission. HomeFragment prompts for
 * this once via NotificationAccessHelper.
 *
 * Whenever a notification posts or clears, recomputes that package's
 * active-notification count and saves it to NotificationBadgeStore, which
 * HomeGridAdapter/AppGridAdapter read from when rendering each icon.
 */
public class AdaptoNotificationListenerService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        // Build initial counts for whatever's already showing when access is granted.
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                recomputeAndSave(sbn.getPackageName());
            }
        } catch (Exception ignored) {
            // Some OEMs restrict getActiveNotifications() briefly right after connect.
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        recomputeAndSave(sbn.getPackageName());
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        recomputeAndSave(sbn.getPackageName());
    }

    private void recomputeAndSave(String packageName) {
        int count = 0;
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                if (sbn.getPackageName().equals(packageName)) count++;
            }
        } catch (Exception ignored) {
            return; // leave the last known count rather than zeroing on a transient failure
        }
        NotificationBadgeStore.setCount(this, packageName, count);
    }
}
