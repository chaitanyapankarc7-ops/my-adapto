package com.tejyash.myadapto.fregment;

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityManager;
import com.tejyash.myadapto.adapter.AppGridAdapter;
import com.tejyash.myadapto.adapter.HomeGridAdapter;
import com.tejyash.myadapto.launcher.GridPreferences;
import com.tejyash.myadapto.manager.AppManager;
import com.tejyash.myadapto.model.AppInfo;
import com.tejyash.myadapto.launcher.LauncherItemType;

import java.util.ArrayList;
import java.util.List;

public class AppsFragment extends Fragment
        implements AccessibilityManager.OnAccessibilityChangedListener {

    private AppManager           appManager;
    private AccessibilityManager accessibilityManager;
    private AppGridAdapter       gridAdapter;
    private GridLayoutManager    layoutManager;
    private List<AppInfo>        allApps;
    private RecyclerView         rv;
    private TextView             emptyState;

    public AppsFragment() { }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        appManager           = new AppManager(requireContext());
        accessibilityManager = new AccessibilityManager(requireContext());

        rv = view.findViewById(R.id.rv_apps);
        emptyState = view.findViewById(R.id.tv_empty_state);
        emptyState.setOnClickListener(v -> loadApps());

        // Use GridPreferences for column count
        int columns = GridPreferences.getColumns(requireContext());
        layoutManager = new GridLayoutManager(requireContext(), columns);
        rv.setLayoutManager(layoutManager);

        // NOTE: this list is the full app drawer, not a positioned grid —
        // its own order is never saved. Long-press starts a real drag
        // (see startDragForApp below); actual placement onto Home happens
        // in HomeFragment.handleDrop() via GridPreferences.placeApp().
        gridAdapter = new AppGridAdapter(requireContext(), 0);
        gridAdapter.setOnAppClickListener(app -> appManager.launchApp(requireContext(), app));
        gridAdapter.setOnAppLongClickListener(this::startDragForApp);

        rv.setAdapter(gridAdapter);
        // Drag-to-reorder intentionally removed — this list has no
        // meaningful order to persist, and it used to overwrite the real
        // Home grid's saved layout since both wrote to "page 0".

        loadApps();

        SearchView searchView = view.findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String query) {
                filterApps(query);
                return true;
            }
        });
    }

    /**
     * Loads every installed app, guarded so a failure surfaces as a
     * visible retry state instead of a silently-empty grid — if this
     * ever throws (some OEM PackageManager quirk, permission issue,
     * etc.) you'll see it here rather than just a blank screen.
     */
    private void loadApps() {
        try {
            allApps = appManager.loadInstalledApps();
        } catch (Exception e) {
            Log.e("AppsFragment", "loadInstalledApps failed", e);
            allApps = null;
        }

        if (allApps == null || allApps.isEmpty()) {
            rv.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            emptyState.setText(allApps == null
                    ? "Couldn't load apps — tap to retry"
                    : "No apps found — tap to retry");
            if (allApps == null) {
                Toast.makeText(requireContext(),
                        "Error loading apps — check Logcat for AppsFragment", Toast.LENGTH_LONG).show();
            }
            return;
        }

        rv.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        gridAdapter.setApps(allApps);
    }

    @Override
    public void onResume() {
        super.onResume();
        accessibilityManager.setListener(this);
        onAccessibilityChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        accessibilityManager.clearListener();
    }

    @Override
    public void onAccessibilityChanged() {
        // Update columns when icon size changes
        layoutManager.setSpanCount(GridPreferences.getColumns(requireContext()));
        gridAdapter.notifyResized();

        // Apply high contrast background to the drawer
        if (getView() != null) {
            boolean highContrast = com.tejyash.myadapto.accessibility.AccessibilityPreferences.get(requireContext()).isColorBlindEnabled();
            if (highContrast) {
                getView().setBackgroundColor(android.graphics.Color.BLACK);
            } else {
                getView().setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"));
            }
        }
    }

    /**
     * Long-press on an app in the drawer starts a real OS-level drag —
     * the drawer closes immediately so Home is visible underneath while
     * the system drag shadow keeps following the finger, same as stock
     * Android launchers (all-apps collapses the moment you start dragging).
     * The actual drop is handled per-page in HomeFragment.handleDrop().
     */
    private void startDragForApp(AppInfo app, View itemView) {
        String clipLabel = app.packageName;
        String clipText  = app.packageName;

        // If it's a widget, use the special WIDGET_CLIP_LABEL so HomeFragment
        // knows to treat it as a widget drop instead of an app drop.
        if (app.packageName != null && app.packageName.startsWith("widget:")) {
            clipLabel = HomeGridAdapter.WIDGET_CLIP_LABEL;
            clipText  = app.packageName.substring(7); // "CLOCK", "BATTERY", etc.
        }

        ClipData.Item item = new ClipData.Item(clipText);
        ClipData dragData = new ClipData(clipLabel,
                new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);

        View.DragShadowBuilder shadow = new View.DragShadowBuilder(itemView);
        boolean started = itemView.startDragAndDrop(dragData, shadow, app, 0);
        Log.d("AppsFragment", "startDragAndDrop for " + app.packageName + " -> " + started);

        // CRITICAL: hide the overlay's VISIBILITY immediately (synchronous,
        // not deferred). While it's visible, its elevation keeps it as the
        // topmost view under the finger, which silently swallows every
        // drag-and-drop hit-test — the drop never reaches Home underneath
        // even though the drawer already looks closed. This was the actual
        // cause of drops just vanishing with no log output at all.
        if (getActivity() instanceof com.tejyash.myadapto.launcher.HomeActivity) {
            ((com.tejyash.myadapto.launcher.HomeActivity) getActivity()).hideOverlayImmediately();
        }

        // Deferred via post(): actually CLOSES the drawer (removes the
        // fragment) on the next frame instead of synchronously right here,
        // giving startDragAndDrop() time to fully register with the system
        // before we tear down the fragment that owns the drag's source view.
        // This is separate from hiding the overlay above — that already
        // happened, so the drop routes correctly regardless of exactly when
        // this fragment teardown finishes.
        itemView.post(() -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }

    public void filterApps(String query) {
        if (allApps == null) return;
        gridAdapter.setApps(appManager.filterApps(allApps, query));
    }
}