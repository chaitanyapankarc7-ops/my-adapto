package com.tejyash.myadapto.launcher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.SearchView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityPreferences;
import com.tejyash.myadapto.activity.SizeEditingPage;
import com.tejyash.myadapto.activity.VoiceAssitentPage;
import com.tejyash.myadapto.adapter.AppGridAdapter;
import com.tejyash.myadapto.model.AppInfo;


/**
 * The HOME launcher screen — the adaptive home screen shown after setup.
 *
 * Registered in AndroidManifest.xml with HOME + DEFAULT intent categories.
 * Shows all installed apps in an adaptive grid. Font size, icon size, and column
 * count all update in real-time when changed in SizeEditingPage (settings).
 *
 * Bottom dock provides quick-access icons:
 *   Phone | Camera | Gallery | Contacts | SOS | Voice
 */
public class HomeActivity extends AppCompatActivity
        implements AccessibilityPreferences.OnPrefsChangedListener {

    private AppGridAdapter             adapter;
    private GridLayoutManager          layoutManager;
    private List<AppInfo>              allApps = new ArrayList<>();
    private AccessibilityPreferences   prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We set the layout background to transparent so the system wallpaper shows through
        setContentView(R.layout.activity_home);

        prefs = AccessibilityPreferences.get(this);

        // ── App grid ────────────────────────────────────────────────
        RecyclerView rvApps = findViewById(R.id.rv_apps);
        adapter       = new AppGridAdapter(this);
        layoutManager = new GridLayoutManager(this, prefs.getGridCols());

        rvApps.setLayoutManager(layoutManager);
        rvApps.setAdapter(adapter);
        rvApps.setHasFixedSize(false);

        adapter.setOnAppClickListener(this::launchApp);

        loadInstalledApps();

        // ── Search bar ──────────────────────────────────────────────
        SearchView searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String q) {
                filterApps(q);
                return true;
            }
        });

        // ── Settings FAB → SizeEditingPage ──────────────────────────
        findViewById(R.id.fab_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SizeEditingPage.class)));

        // ── Bottom dock — same intents as original MainActivity8 ────
        setupDock();
    }

    // ── Lifecycle: register/unregister pref listener ────────────────
    @Override
    protected void onResume() {
        super.onResume();
        prefs.setListener(this);
        onPrefsChanged(); // apply any changes made while we were in settings
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.clearListener();
    }

    // ── AccessibilityPreferences.OnPrefsChangedListener ─────────────
    @Override
    public void onPrefsChanged() {
        int cols = prefs.getGridCols();
        if (layoutManager.getSpanCount() != cols) {
            layoutManager.setSpanCount(cols);
        }
        adapter.notifyResized(); // refreshes font + icon sizes on all cells
    }

    // ── HOME button: never go back ───────────────────────────────────


    // ── Load all installed apps via PackageManager ───────────────────
    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        Collections.sort(resolved, new ResolveInfo.DisplayNameComparator(pm));

        allApps.clear();
        for (ResolveInfo ri : resolved) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue; // skip Adapto itself
            allApps.add(new AppInfo(
                    ri.loadLabel(pm).toString(),
                    pkg,
                    ri.activityInfo.name,
                    ri.loadIcon(pm)
            ));
        }
        adapter.setApps(allApps);
    }

    private void filterApps(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.setApps(allApps);
            return;
        }
        String lower = query.toLowerCase().trim();
        List<AppInfo> filtered = new ArrayList<>();
        for (AppInfo a : allApps) {
            if (a.label.toLowerCase().contains(lower)) filtered.add(a);
        }
        adapter.setApps(filtered);
    }

    private void launchApp(AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.packageName, app.activityName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            loadInstalledApps(); // app was uninstalled, refresh
        }
    }

    // ── Bottom dock ────────────────────────────────────────────────
    private void setupDock() {
        // Voice
        ImageView imgVoice = findViewById(R.id.dock_voice);
        if (imgVoice != null) imgVoice.setOnClickListener(v -> {
            startActivity(new Intent(this, VoiceAssitentPage.class));
        });

        // Phone
        ImageView imgPhone = findViewById(R.id.dock_phone);
        if (imgPhone != null) imgPhone.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_DIAL));
        });

        // Camera
        ImageView imgCamera = findViewById(R.id.dock_camera);
        if (imgCamera != null) imgCamera.setOnClickListener(v -> {
            startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
        });

        // Gallery
        ImageView imgGallery = findViewById(R.id.dock_gallery);
        if (imgGallery != null) imgGallery.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setType("image/*");
            startActivity(i);
        });

        // Contacts
        ImageView imgContacts = findViewById(R.id.dock_contacts);
        if (imgContacts != null) imgContacts.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    android.provider.ContactsContract.Contacts.CONTENT_URI));
        });

        // SOS
        ImageView imgSOS = findViewById(R.id.dock_sos);
        if (imgSOS != null) imgSOS.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_DIAL,
                    android.net.Uri.parse("tel:112")));
        });
    }
}
