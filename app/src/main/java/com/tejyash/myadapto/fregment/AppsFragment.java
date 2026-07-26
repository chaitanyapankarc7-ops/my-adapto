package com.tejyash.myadapto.fregment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityManager;
import com.tejyash.myadapto.adapter.AppGridAdapter;
import com.tejyash.myadapto.manager.AppManager;
import com.tejyash.myadapto.model.AppInfo;

import java.util.List;

/**
 * The Apps page — search bar + the full installed-app grid.
 *
 * All the actual work (reading PackageManager, filtering by label,
 * launching an app) is delegated to AppManager — this class's only job
 * is wiring that data to the RecyclerView, and reacting when a change
 * in SizeEditingPage should resize the grid live.
 *
 * Flow: PackageManager → AppManager → AppInfo list → AppGridAdapter →
 * RecyclerView → screen (same flow the roadmap describes; it just runs
 * inside a fragment now instead of the activity).
 */
public class AppsFragment extends Fragment
        implements AccessibilityManager.OnAccessibilityChangedListener {

    private AppManager           appManager;
    private AccessibilityManager accessibilityManager;
    private AppGridAdapter       gridAdapter;
    private GridLayoutManager    layoutManager;
    private List<AppInfo>        allApps;

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

        RecyclerView rv = view.findViewById(R.id.rv_apps);
        layoutManager = new GridLayoutManager(requireContext(), accessibilityManager.getGridColumns());
        rv.setLayoutManager(layoutManager);

        gridAdapter = new AppGridAdapter(requireContext());
        gridAdapter.setOnAppClickListener(app -> appManager.launchApp(requireContext(), app));
        rv.setAdapter(gridAdapter);

        allApps = appManager.loadInstalledApps();
        gridAdapter.setApps(allApps);

        SearchView searchView = view.findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String query) {
                filterApps(query);
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        accessibilityManager.setListener(this);
        onAccessibilityChanged(); // pick up any change made while we were away (e.g. in Settings)
    }

    @Override
    public void onPause() {
        super.onPause();
        accessibilityManager.clearListener();
    }

    /** Fires whenever font size / icon size changes anywhere in the app. */
    @Override
    public void onAccessibilityChanged() {
        layoutManager.setSpanCount(accessibilityManager.getGridColumns());
        gridAdapter.notifyResized();
    }

    /** Case-insensitive filter — the rule itself lives in AppManager, this just re-binds the grid. */
    public void filterApps(String query) {
        if (allApps == null) return;
        gridAdapter.setApps(appManager.filterApps(allApps, query));
    }
}