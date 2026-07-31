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
import com.tejyash.myadapto.launcher.GridPreferences;
import com.tejyash.myadapto.manager.AppManager;
import com.tejyash.myadapto.model.AppInfo;

import java.util.List;

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

        // Use GridPreferences for column count
        int columns = GridPreferences.getColumns(requireContext());
        layoutManager = new GridLayoutManager(requireContext(), columns);
        rv.setLayoutManager(layoutManager);

        // Page 0 = Apps page
        gridAdapter = new AppGridAdapter(requireContext(), 0);
        gridAdapter.setOnAppClickListener(app -> appManager.launchApp(requireContext(), app));

        rv.setAdapter(gridAdapter);

        // ↓ ONE LINE — this enables long-press drag and drop + auto-save
        gridAdapter.attachDragToRecyclerView(rv);

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
    }

    public void filterApps(String query) {
        if (allApps == null) return;
        gridAdapter.setApps(appManager.filterApps(allApps, query));
    }
}