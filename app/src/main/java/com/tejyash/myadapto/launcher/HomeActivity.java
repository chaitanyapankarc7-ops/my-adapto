package com.tejyash.myadapto.launcher;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityManager;
import com.tejyash.myadapto.activity.SizeEditingPage;
import com.tejyash.myadapto.activity.VoiceAssitentPage;
import com.tejyash.myadapto.adapter.AppGridAdapter;
import com.tejyash.myadapto.manager.AppManager;
import com.tejyash.myadapto.model.AppInfo;


/**
 * The HOME launcher screen — the adaptive home screen shown after setup.
 *
 * Registered in AndroidManifest.xml with HOME + DEFAULT intent categories.
 * Shows all installed apps in an adaptive grid. Font size, icon size, and
 * grid column count all update in real-time when changed in SizeEditingPage.
 * Column count is now DERIVED from icon/font size (see
 * AccessibilityManager.getGridColumns()) instead of being a fixed 4 —
 * bigger icons/text automatically get fewer, larger columns, and smaller
 * icons/text get more, smaller columns.
 *
 * Bottom dock: Phone | Camera | Gallery | Contacts | SOS | Voice.
 * Phone/Camera/Gallery/Contacts show whichever app the device actually
 * resolves for that action (see setupDock()), so the dock always reflects
 * the user's own installed apps rather than a fixed placeholder icon.
 * SOS and Voice are Adapto's own in-app features, so they keep fixed icons.
 */
public class HomeActivity extends AppCompatActivity
        implements AccessibilityManager.OnAccessibilityChangedListener {

    private AppGridAdapter       adapter;
    private GridLayoutManager    layoutManager;
    private List<AppInfo>        allApps;
    private AccessibilityManager accessibilityManager;
    private AppManager           appManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We set the layout background to transparent so the system wallpaper shows through
        setContentView(R.layout.activity_home);

        accessibilityManager = new AccessibilityManager(this);
        appManager            = new AppManager(this);

        // ── App grid ────────────────────────────────────────────────
        RecyclerView rvApps = findViewById(R.id.rv_apps);
        adapter       = new AppGridAdapter(this);
        layoutManager = new GridLayoutManager(this, accessibilityManager.getGridColumns());

        rvApps.setLayoutManager(layoutManager);
        rvApps.setAdapter(adapter);
        rvApps.setHasFixedSize(false);

        adapter.setOnAppClickListener(this::launchApp);

        allApps = appManager.loadInstalledApps();
        adapter.setApps(allApps);

        // ── Search bar ──────────────────────────────────────────────
        SearchView searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String q) {
                adapter.setApps(appManager.filterApps(allApps, q));
                return true;
            }
        });

        // ── Settings FAB → SizeEditingPage ──────────────────────────
        findViewById(R.id.fab_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SizeEditingPage.class)));

        // ── Bottom dock ──────────────────────────────────────────────
        setupDock();
    }

    // ── Lifecycle: register/unregister pref listener ────────────────
    @Override
    protected void onResume() {
        super.onResume();
        accessibilityManager.setListener(this);
        onAccessibilityChanged(); // apply any changes made while we were in settings
    }

    @Override
    protected void onPause() {
        super.onPause();
        accessibilityManager.clearListener();
    }

    // ── AccessibilityManager.OnAccessibilityChangedListener ─────────
    @Override
    public void onAccessibilityChanged() {
        int cols = accessibilityManager.getGridColumns();
        if (layoutManager.getSpanCount() != cols) {
            layoutManager.setSpanCount(cols);
        }
        adapter.notifyResized(); // refreshes font + icon sizes on all cells
    }

    private void launchApp(AppInfo app) {
        boolean launched = appManager.launchApp(this, app);
        if (!launched) {
            // App was uninstalled since the grid was loaded — refresh
            allApps = appManager.loadInstalledApps();
            adapter.setApps(allApps);
        }
    }

    // ── Bottom dock ────────────────────────────────────────────────
    private void setupDock() {
        // Phone/Camera/Gallery/Contacts: resolve whichever app the device
        // actually uses for that action, so the icon, label, AND the tap
        // itself all point at the user's real installed app — no chooser
        // dialog, no fixed placeholder.
        bindDockSlot(R.id.dock_phone, R.id.dock_phone_label,
                new Intent(Intent.ACTION_DIAL));

        bindDockSlot(R.id.dock_camera, R.id.dock_camera_label,
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE));

        // CATEGORY_APP_GALLERY asks the OS for its designated default
        // gallery app directly — more reliable than probing ACTION_VIEW
        // with a MIME type, which many apps can match and is ambiguous.
        Intent galleryCategory = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY);
        Intent galleryFallback = new Intent(Intent.ACTION_VIEW).setType("image/*");
        bindDockSlot(R.id.dock_gallery, R.id.dock_gallery_label,
                galleryCategory, galleryFallback);

        // Same idea for Contacts — CATEGORY_APP_CONTACTS first.
        Intent contactsCategory = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS);
        Intent contactsFallback = new Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI);
        bindDockSlot(R.id.dock_contacts, R.id.dock_contacts_label,
                contactsCategory, contactsFallback);

        // SOS and Voice are Adapto's own features (not external apps to resolve),
        // so they keep their fixed custom icon and label.
        ImageView imgSOS = findViewById(R.id.dock_sos);
        if (imgSOS != null) imgSOS.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:112"))));

        ImageView imgVoice = findViewById(R.id.dock_voice);
        if (imgVoice != null) imgVoice.setOnClickListener(v ->
                startActivity(new Intent(this, VoiceAssitentPage.class)));
    }

    /**
     * Resolves the real app icon/label for one dock slot and wires the tap
     * to an EXPLICIT intent pointing at that same resolved app — this is
     * what stops Android's app-picker dialog from popping up on tap. If
     * nothing resolves, the XML placeholder drawable/text stays as-is and
     * the tap falls back to the original (first) probe intent.
     */
    private void bindDockSlot(int iconId, int labelId, Intent... probes) {
        ImageView icon = findViewById(iconId);
        if (icon == null) return;

        Drawable realIcon = appManager.resolveIconFor(probes);
        if (realIcon != null) icon.setImageDrawable(realIcon);

        TextView label = findViewById(labelId);
        if (label != null) {
            String realLabel = appManager.resolveLabelFor(probes);
            if (realLabel != null) label.setText(realLabel);
        }

        Intent explicit = appManager.resolveExplicitIntent(probes);
        icon.setOnClickListener(v ->
                startActivity(explicit != null ? explicit : probes[0]));
    }
}