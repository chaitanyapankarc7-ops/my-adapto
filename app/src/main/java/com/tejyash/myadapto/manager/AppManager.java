package com.tejyash.myadapto.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import com.tejyash.myadapto.model.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns all app-loading, filtering, launching, and default-app-resolution
 * logic that used to live directly inside HomeActivity. Pulling it out
 * means HomeActivity's job goes back to just wiring up views, matching the
 * roadmap rule: "Activities only manage UI, business logic belongs in
 * Manager classes."
 */
public class AppManager {

    private final Context appContext;

    public AppManager(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    /** Every launchable app on the device, alphabetical, excluding Adapto itself. */
    public List<AppInfo> loadInstalledApps() {
        PackageManager pm = appContext.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        Collections.sort(resolved, new ResolveInfo.DisplayNameComparator(pm));

        List<AppInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : resolved) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(appContext.getPackageName())) continue; // skip Adapto itself
            apps.add(new AppInfo(
                    ri.loadLabel(pm).toString(),
                    pkg,
                    ri.activityInfo.name,
                    ri.loadIcon(pm)
            ));
        }
        return apps;
    }

    /** Case-insensitive label filter. Returns the full list if the query is blank. */
    public List<AppInfo> filterApps(List<AppInfo> allApps, String query) {
        if (query == null || query.trim().isEmpty()) return allApps;
        String lower = query.toLowerCase().trim();
        List<AppInfo> filtered = new ArrayList<>();
        for (AppInfo a : allApps) {
            if (a.label.toLowerCase().contains(lower)) filtered.add(a);
        }
        return filtered;
    }

    /**
     * Launches an app. Returns false (instead of throwing) if the app was
     * uninstalled since the grid was last loaded, so the caller can refresh.
     */
    public boolean launchApp(Context ctx, AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.packageName, app.activityName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves whichever app the device would actually launch for a given
     * system action (e.g. ACTION_DIAL) and returns its real icon. Used by
     * the launcher dock so "Phone"/"Camera"/"Gallery"/"Contacts" always show
     * the user's own installed app instead of a fixed placeholder image.
     * Returns null if no app on the device handles this action.
     */
    public Drawable resolveIconFor(Intent intent) {
        PackageManager pm = appContext.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null ? ri.loadIcon(pm) : null;
    }

    /** Same resolution as resolveIconFor(), but returns the app's display name. */
    public String resolveLabelFor(Intent intent) {
        PackageManager pm = appContext.getPackageManager();
        ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null ? ri.loadLabel(pm).toString() : null;
    }
}