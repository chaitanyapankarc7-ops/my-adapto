package com.tejyash.myadapto.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.tejyash.myadapto.model.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns all app-loading, filtering, launching, and default-app-resolution
 * logic that used to live directly inside HomeActivity. Pulling it out
 * means HomeActivity's job goes back to just wiring up views, matching the
 * roadmap rule: "Activities only manage UI, business logic belongs in
 * Manager classes."
 */
public class AppManager {

    private static final String TAG = "AppManager";

    private final Context appContext;

    // ── In-memory app-list cache ────────────────────────────────────
    // loadInstalledApps() used to run queryIntentActivities() + per-icon
    // loadIcon() (which triggers MIUI's IconCustomizer composite) fresh on
    // EVERY call. HomeGridAdapter.loadFromPrefs() calls it on every single
    // page bind, so scrolling/creating Home pages — especially the drag-to-
    // edge new-page flow — was re-running this full ~100+ app scan/composite
    // synchronously on the main thread each time, stalling the Looper for
    // seconds (see Davey!/Skipped-frames logs) and starving postDelayed()
    // timers like the edge-scroll dwell timer. Caching means only the FIRST
    // call per process does the heavy work; everything after is instant.
    private static volatile List<AppInfo> cachedApps = null;
    private static final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static boolean receiverRegistered = false;

    public AppManager(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        registerPackageChangeReceiverOnce();
    }

    /** Invalidates the cache whenever an app is installed/removed/updated,
     *  so Home/drawer eventually reflect real device state again. Registered
     *  once per process (guarded), lives for the app's lifetime — fine since
     *  it's tied to the application context, not an Activity/Fragment. */
    private void registerPackageChangeReceiverOnce() {
        if (receiverRegistered) return;
        receiverRegistered = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                invalidateCache();
            }
        };
        // API 33+ requires an explicit export flag for context-registered
        // receivers or it throws SecurityException at runtime (target_sdk 36
        // here). This is a system-only protected broadcast, so NOT_EXPORTED
        // is correct — no other app can send it to us anyway.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
    }

    /** Drops the cache so the next loadInstalledApps() call re-scans for real. */
    public static void invalidateCache() {
        cachedApps = null;
    }

    /**
     * Loads the app list off the main thread and warms the cache, then
     * calls back on the main thread. Call this once, early (e.g.
     * HomeFragment.onViewCreated), BEFORE any RecyclerView binding happens,
     * so every subsequent synchronous loadInstalledApps() call — including
     * ones triggered mid-drag by new Home pages — hits the cache instead of
     * re-scanning. Safe to call even if already cached/loading; onReady
     * still fires once with the current/finished list.
     */
    public void warmCacheAsync(Runnable onReady) {
        if (cachedApps != null) {
            if (onReady != null) onReady.run();
            return;
        }
        bgExecutor.execute(() -> {
            List<AppInfo> apps = loadInstalledApps(); // heavy work, off main thread now
            mainHandler.post(() -> {
                if (onReady != null) onReady.run();
            });
        });
    }

    /**
     * Every launchable app on the device, alphabetical, excluding Adapto itself.
     *
     * Tries queryIntentActivities() first (the standard approach). Some OEM
     * Android skins (seen on real devices, not emulators) are stricter about
     * package visibility than stock AOSP even with <queries> declared
     * correctly in the manifest, and can return a suspiciously small list.
     * If that happens, falls back to getInstalledApplications() + checking
     * each package for a launch intent individually — a different
     * PackageManager code path that isn't always subject to the same
     * OEM-level filtering. Logs counts from each path either way so a
     * real-device failure is diagnosable from Logcat instead of silent.
     */
    public List<AppInfo> getCachedApps() {
        return cachedApps;
    }

    /**
     * Finds a single app quickly. If cache is ready, does a fast in-memory lookup.
     * If cache is not yet ready, resolves ONLY THIS single package from PackageManager
     * without querying all installed packages on the device, preventing multi-second
     * main-thread UI stalls.
     */
    public AppInfo findApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;

        List<AppInfo> cached = cachedApps;
        if (cached != null) {
            for (AppInfo a : cached) {
                if (a.packageName.equals(packageName)) return a;
            }
            return null;
        }

        try {
            PackageManager pm = appContext.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            Intent launch = pm.getLaunchIntentForPackage(packageName);
            return new AppInfo(
                    ai.loadLabel(pm).toString(),
                    packageName,
                    launch != null && launch.getComponent() != null ? launch.getComponent().getClassName() : null,
                    ai.loadIcon(pm)
            );
        } catch (Exception e) {
            return null;
        }
    }

    public List<AppInfo> loadInstalledApps() {
        List<AppInfo> cached = cachedApps; // local copy, cache may be replaced concurrently
        if (cached != null) return cached;

        synchronized (AppManager.class) {
            if (cachedApps != null) return cachedApps;

            PackageManager pm = appContext.getPackageManager();
            List<AppInfo> apps = loadViaQueryIntentActivities(pm);
            Log.d(TAG, "queryIntentActivities found " + apps.size() + " apps");

            if (apps.size() < 5) { // suspiciously few for a real phone — try the fallback path
                List<AppInfo> fallback = loadViaGetInstalledApplications(pm);
                Log.d(TAG, "fallback getInstalledApplications found " + fallback.size() + " apps");
                if (fallback.size() > apps.size()) apps = fallback;
            }

            cachedApps = apps; // warm the cache so every future call (any thread) is instant
            return apps;
        }
    }

    private List<AppInfo> loadViaQueryIntentActivities(PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(intent, 0);
        } catch (Exception e) {
            Log.e(TAG, "queryIntentActivities threw", e);
            return new ArrayList<>();
        }
        Collections.sort(resolved, new ResolveInfo.DisplayNameComparator(pm));

        List<AppInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : resolved) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(appContext.getPackageName())) continue; // skip Adapto itself
            if (!seen.add(pkg)) continue;
            apps.add(new AppInfo(
                    ri.loadLabel(pm).toString(),
                    pkg,
                    ri.activityInfo.name,
                    ri.loadIcon(pm)
            ));
        }
        return apps;
    }

    private List<AppInfo> loadViaGetInstalledApplications(PackageManager pm) {
        List<AppInfo> apps = new ArrayList<>();
        try {
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : installed) {
                if (ai.packageName.equals(appContext.getPackageName())) continue;
                Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                if (launch == null) continue; // not launchable (library/service-only package)
                apps.add(new AppInfo(
                        ai.loadLabel(pm).toString(),
                        ai.packageName,
                        launch.getComponent() != null ? launch.getComponent().getClassName() : null,
                        ai.loadIcon(pm)
                ));
            }
            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        } catch (Exception e) {
            Log.e(TAG, "getInstalledApplications threw", e);
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
        if (app == null || app.packageName == null) return false;
        Intent intent;
        if (app.activityName != null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(app.packageName, app.activityName);
        } else {
            intent = ctx.getPackageManager().getLaunchIntentForPackage(app.packageName);
        }
        if (intent == null) return false;
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

    /** Package name of whichever app resolveInfo() found for these probes, or null. */
    public String resolvePackageFor(Intent... probes) {
        ResolveInfo ri = resolveInfo(probes);
        return ri != null ? ri.activityInfo.packageName : null;
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

    /** Opens the system "App Info" screen for the given app. */
    public void openAppInfo(Context ctx, AppInfo app) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + app.packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    /** Triggers the system uninstall confirmation dialog for the given app. */
    public void requestUninstall(Context ctx, AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + app.packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}