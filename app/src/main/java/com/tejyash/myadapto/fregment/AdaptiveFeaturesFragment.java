package com.tejyash.myadapto.fregment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tejyash.myadapto.R;
import com.tejyash.myadapto.utils.Constants;

public class AdaptiveFeaturesFragment extends Fragment {

    private SharedPreferences prefs;

    public AdaptiveFeaturesFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_adaptive_features, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences(Constants.PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);

        setupSwitch(view, R.id.switch_color_blind, Constants.KEY_COLOR_BLIND);
        setupSwitch(view, R.id.switch_flashlight_alerts, Constants.KEY_FLASHLIGHT_ALERTS);
        setupSwitch(view, R.id.switch_vibrate_alerts, Constants.KEY_VIBRATE_ALERTS);
    }

    private void setupSwitch(View root, int switchId, String prefKey) {
        SwitchMaterial sw = root.findViewById(switchId);
        sw.setChecked(prefs.getBoolean(prefKey, false));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(prefKey, isChecked).apply();
            // In a real implementation, this would trigger theme changes or start background services
        });
    }
}
