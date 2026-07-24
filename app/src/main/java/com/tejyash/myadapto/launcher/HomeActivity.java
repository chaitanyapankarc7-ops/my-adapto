package com.tejyash.myadapto.launcher;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tejyash.myadapto.R;
import com.tejyash.myadapto.activity.SizeEditingPage;
import com.tejyash.myadapto.adapter.HomePagerAdapter;

/**
 * The HOME launcher screen — registered in AndroidManifest.xml with the
 * HOME + DEFAULT intent categories, so this is what Android shows when
 * the user presses the home button.
 *
 * Per the "Activity is the house, Fragments are the rooms" model, this
 * class is only the manager. It does four things and nothing else:
 *
 *   1. Creates the ViewPager2 — the page slider.
 *   2. Attaches HomePagerAdapter — the "waiter" that hands back
 *      HomeFragment / AppsFragment / WidgetsFragment for pages 0/1/2.
 *   3. Wires a TabLayout to that same ViewPager2 via TabLayoutMediator,
 *      so tapping "Home / Apps / Widgets" jumps straight to that page —
 *      useful for anyone who finds swipe gestures hard to perform.
 *   4. Hosts the FAB that opens accessibility settings, since that
 *      action is common to every page, not owned by any single one.
 *
 * Everything else — the dock, the app grid + search, the widget tiles —
 * belongs to whichever fragment owns that page. This activity never
 * touches PackageManager, SharedPreferences, or app data directly.
 */
public class HomeActivity extends AppCompatActivity {

    private static final String[] TAB_TITLES = { "Home", "Apps", "Widgets" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout  tabLayout = findViewById(R.id.tab_layout);

        viewPager.setAdapter(new HomePagerAdapter(this));

        // TabLayoutMediator is the glue between the two: tapping a tab
        // calls viewPager.setCurrentItem(position), and swiping the
        // pager updates which tab is highlighted. Neither view needs to
        // know the other exists — this one call keeps them in sync.
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();

        findViewById(R.id.fab_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SizeEditingPage.class)));
    }
}