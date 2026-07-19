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
     * system action (tries each probe intent in order, first match wins).
     * Used by the launcher dock so "Phone"/"Camera"/"Gallery"/"Contacts"
     * always point at the user's own default app. Returns null if nothing
     * on the device handles any of the probes.
     */
    private ResolveInfo resolveInfo(Intent... probes) {
        PackageManager pm = appContext.getPackageManager();
        for (Intent probe : probes) {
            ResolveInfo ri = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) return ri;
        }
        return null;
    }

    public Drawable resolveIconFor(Intent... probes) {
        ResolveInfo ri = resolveInfo(probes);
        return ri != null ? ri.loadIcon(appContext.getPackageManager()) : null;
    }

    public String resolveLabelFor(Intent... probes) {
        ResolveInfo ri = resolveInfo(probes);
        return ri != null ? ri.loadLabel(appContext.getPackageManager()).toString() : null;
    }

    /**
     * Builds an EXPLICIT intent (exact package + activity) pointing at
     * whichever app resolveInfo() found. This is the key fix for the
     * "app picker keeps popping up" bug: launching a plain implicit intent
     * (e.g. ACTION_VIEW + image/*) makes Android show a chooser whenever
     * more than one app *could* handle it. An explicit intent skips that
     * chooser entirely and opens the resolved app directly — same app
     * whose icon/label we already showed on the dock button.
     * Returns null if nothing resolves (caller should fall back to the
     * original implicit intent as a last resort).
     */
    public Intent resolveExplicitIntent(Intent... probes) {
        ResolveInfo ri = resolveInfo(probes);
        if (ri == null) return null;
        Intent explicit = new Intent(probes[0]);
        explicit.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
        explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return explicit;
    }
}