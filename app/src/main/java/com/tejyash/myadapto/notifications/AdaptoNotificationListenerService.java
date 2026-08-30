package com.tejyash.myadapto.notifications;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Requires the user to manually grant "Notification access" in system
 * settings (Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).
 *
 * 1. Computes active-notification badge counts for app icons.
 * 2. Retains a privacy-conscious, in-memory list of recent notifications
 *    for the launcher pull-down panel and Voice Assistant context.
 */
public class AdaptoNotificationListenerService extends NotificationListenerService {

    public static class NotificationItem {
        public final String packageName;
        public final String appName;
        public final String title;
        public final String text;
        public final long postTime;

        public NotificationItem(String packageName, String appName, String title, String text, long postTime) {
            this.packageName = packageName != null ? packageName : "";
            this.appName = appName != null ? appName : "";
            this.title = title != null ? title : "";
            this.text = text != null ? text : "";
            this.postTime = postTime;
        }

        public String getSummary() {
            String app = !appName.isEmpty() ? appName : packageName;
            if (!title.isEmpty() && !text.isEmpty()) {
                return title + ": " + text + " (" + app + ")";
            } else if (!title.isEmpty()) {
                return title + " (" + app + ")";
            } else if (!text.isEmpty()) {
                return text + " (" + app + ")";
            }
            return "Notification from " + app;
        }
    }

    public interface OnNotificationsChangedListener {
        void onNotificationsChanged();
    }

    private static final LinkedList<NotificationItem> recentNotifications = new LinkedList<>();
    private static final int MAX_STORED = 10;
    private static final List<OnNotificationsChangedListener> listeners = Collections.synchronizedList(new ArrayList<>());

    public static void registerListener(OnNotificationsChangedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void unregisterListener(OnNotificationsChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private static void notifyListeners() {
        synchronized (listeners) {
            for (OnNotificationsChangedListener l : listeners) {
                try {
                    l.onNotificationsChanged();
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                recomputeAndSave(sbn.getPackageName());
                recordNotification(sbn, false);
            }
            notifyListeners();
        } catch (Exception ignored) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        recomputeAndSave(sbn.getPackageName());
        recordNotification(sbn, true);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        recomputeAndSave(sbn.getPackageName());
    }

    private void recordNotification(StatusBarNotification sbn, boolean notify) {
        if (sbn == null || sbn.getPackageName() == null) return;
        if (sbn.getPackageName().equals(getPackageName())) return; // Ignore own notifications

        Notification notification = sbn.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (textCs == null) {
            textCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        }

        String title = titleCs != null ? titleCs.toString().trim() : "";
        String text = textCs != null ? textCs.toString().trim() : "";

        if (title.isEmpty() && text.isEmpty()) return;

        String appName = sbn.getPackageName();
        try {
            PackageManager pm = getPackageManager();
            appName = pm.getApplicationLabel(pm.getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (Exception ignored) {}

        NotificationItem item = new NotificationItem(sbn.getPackageName(), appName, title, text, sbn.getPostTime());

        synchronized (recentNotifications) {
            // Remove existing duplicate with same package + title + text if already present
            recentNotifications.removeIf(existing ->
                    existing.packageName.equals(item.packageName) &&
                    existing.title.equals(item.title) &&
                    existing.text.equals(item.text));

            recentNotifications.addFirst(item);
            while (recentNotifications.size() > MAX_STORED) {
                recentNotifications.removeLast();
            }
        }

        if (notify) {
            notifyListeners();
        }
    }

    private void recomputeAndSave(String packageName) {
        if (packageName == null) return;
        int count = 0;
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                if (sbn.getPackageName().equals(packageName)) count++;
            }
        } catch (Exception ignored) {
            return;
        }
        NotificationBadgeStore.setCount(this, packageName, count);
    }

    public static List<NotificationItem> getRecentNotifications() {
        synchronized (recentNotifications) {
            return new ArrayList<>(recentNotifications);
        }
    }

    public static String getLatestNotificationSummary() {
        synchronized (recentNotifications) {
            if (recentNotifications.isEmpty()) return null;
            return recentNotifications.getFirst().getSummary();
        }
    }

    public static String getAllRecentNotificationsSummary() {
        synchronized (recentNotifications) {
            if (recentNotifications.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < recentNotifications.size(); i++) {
                if (i > 0) sb.append(". Next: ");
                sb.append(recentNotifications.get(i).getSummary());
            }
            return sb.toString();
        }
    }

    public static boolean isNotificationAccessGranted(Context context) {
        return NotificationAccessHelper.isEnabled(context);
    }
}
