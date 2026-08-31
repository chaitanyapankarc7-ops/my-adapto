package com.tejyash.myadapto.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.tejyash.myadapto.R;
import com.tejyash.myadapto.activity.SizeEditingPage;
import com.tejyash.myadapto.fregment.AppsFragment;
import com.tejyash.myadapto.fregment.HomeFragment;

/**
 * HomeActivity — the "house".
 *
 * No tab bar. Structure is a real launcher pattern:
 *   - home_container permanently shows HomeFragment.
 *   - The app drawer (AppsFragment) is NOT a page you swipe/tab to —
 *     it's an overlay opened on demand into overlay_container, reachable
 *     by swiping up from anywhere on Home (see HomeFragment's own
 *     SwipeUpFrameLayout root — that's where the gesture actually lives
 *     now, not here) or tapping the handle pill, and dismissed with the
 *     system back gesture/button (or automatically when a drag starts —
 *     see AppsFragment.startDragForApp).
 */
public class HomeActivity extends AppCompatActivity {

    private View overlayContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        overlayContainer = findViewById(R.id.overlay_container);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.home_container, new HomeFragment())
                    .commit();
        }

        // Listen for accessibility changes (like color blind theme toggle)
        com.tejyash.myadapto.accessibility.AccessibilityPreferences.get(this).setListener(() -> {
            androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.home_container);
            if (fragment instanceof HomeFragment) {
                ((HomeFragment) fragment).refreshGrid();
            }
        });

        // Re-hide the overlay once its fragment is popped off the back stack
        // (back press/gesture, or automatically when a drag starts — see
        // AppsFragment.startDragForApp), so we're not left with an empty
        // transparent panel sitting on top of Home.
        //
        // IMPORTANT: this used to also call HomeFragment.refreshGrid() here,
        // which forced pagesAdapter.notifyDataSetChanged() — tearing down and
        // rebuilding the Home page's RecyclerView. That's exactly the view a
        // native drag-and-drop uses as its drop target, and since the drawer
        // closes THE MOMENT a drag starts (not after it finishes), that
        // refresh was destroying the drop target mid-drag — which is why
        // dropped icons were vanishing instead of landing. Each successful
        // drop already refreshes itself via HomeGridAdapter.acceptDrop() ->
        // loadFromPrefs(), which only touches that one page's own adapter,
        // not the parent pagesAdapter/RecyclerView identity. No blanket
        // refresh is needed (or safe) here anymore.
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                overlayContainer.setVisibility(View.GONE);
                
                // Refresh home screen to apply any adaptive feature changes
                androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.home_container);
                if (fragment instanceof HomeFragment) {
                    ((HomeFragment) fragment).refreshGrid();
                }
            }
        });

        findViewById(R.id.drawer_handle).setOnClickListener(v -> openAppDrawer());

        findViewById(R.id.fab_settings).setOnClickListener(v ->
                openOverlay(new com.tejyash.myadapto.fregment.AdaptiveFeaturesFragment(), "adaptive_features"));
    }

    public void showSettingsMenu() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        builder.setView(view);

        AlertDialog dialog = builder.create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        view.findViewById(R.id.layoutAccessibility).setOnClickListener(v -> {
            dialog.dismiss();
            openOverlay(new com.tejyash.myadapto.fregment.AdaptiveFeaturesFragment(), "adaptive_features");
        });

        view.findViewById(R.id.layoutWallpaper).setOnClickListener(v -> {
            dialog.dismiss();
            openWallpaperPicker();
        });

        dialog.show();
    }

    public void openWallpaperPicker() {
        try {
            startActivity(Intent.createChooser(
                    new Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"));
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, "No wallpaper picker available on this device",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ── Opening overlays ──────────────────────────────────────────────
    public void openAppDrawer() {
        if (overlayContainer.getVisibility() == View.VISIBLE) return; // already open
        openOverlay(new AppsFragment(), "apps_drawer");
    }

    /**
     * Hides the drawer overlay's VISIBILITY immediately — not the same as
     * actually closing it (that's still popBackStack, done separately and
     * deferred). While overlayContainer is visible, it sits on top of Home
     * with elevation and blocks drag-and-drop hit-testing from ever
     * reaching Home's page underneath, even after the drawer has visually
     * "gone away" from the fragment transaction's perspective. Called the
     * instant a drag starts (see AppsFragment.startDragForApp) so the drop
     * actually routes to Home right away, while the fragment itself (and
     * the drag's source view) survives a little longer until the deferred
     * popBackStack actually runs.
     */
    public void hideOverlayImmediately() {
        overlayContainer.setVisibility(View.GONE);
    }

    public void openOverlay(androidx.fragment.app.Fragment fragment, String backStackName) {
        overlayContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_up, R.anim.stay_still,   // opening
                        R.anim.stay_still, R.anim.slide_out_down // closing (back press)
                )
                .replace(R.id.overlay_container, fragment)
                .addToBackStack(backStackName)
                .commit();
    }

    @Override
    public void onBackPressed() {
        androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.home_container);
        if (fragment instanceof HomeFragment && ((HomeFragment) fragment).isPullDownPanelOpen()) {
            ((HomeFragment) fragment).closePullDownPanel();
            return;
        }
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return;
        }
        // As a custom launcher home screen, do not exit app on back press
    }
}