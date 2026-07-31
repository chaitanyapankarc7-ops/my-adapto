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

public class HomeActivity extends AppCompatActivity {

    private static final String[] TAB_TITLES = { "Home", "Apps", "Widgets" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout  tabLayout = findViewById(R.id.tab_layout);

        viewPager.setAdapter(new HomePagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();

        findViewById(R.id.fab_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SizeEditingPage.class)));
    }
}