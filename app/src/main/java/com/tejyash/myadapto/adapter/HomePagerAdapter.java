package com.tejyash.myadapto.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.tejyash.myadapto.fregment.AppsFragment;
import com.tejyash.myadapto.fregment.HomeFragment;
import com.tejyash.myadapto.fregment.WidgetsFragment;

public class HomePagerAdapter extends FragmentStateAdapter {

    public HomePagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {

        switch (position) {

            case 0:
                return new HomeFragment();

            case 1:
                return new AppsFragment();

            case 2:
                return new WidgetsFragment();

            default:
                return new HomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}




