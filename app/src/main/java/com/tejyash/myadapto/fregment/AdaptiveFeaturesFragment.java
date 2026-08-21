package com.tejyash.myadapto.fregment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityPreferences;

public class AdaptiveFeaturesFragment extends Fragment {

    private AccessibilityPreferences accessibilityPrefs;

    public AdaptiveFeaturesFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_adaptive_features, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        accessibilityPrefs = AccessibilityPreferences.get(requireContext());

        setupColorBlindSwitch(view);
        setupFlashlightSwitch(view);
        setupVibrateSwitch(view);
        
        applyHighContrast(view);
    }

    private void applyHighContrast(View root) {
        boolean highContrast = accessibilityPrefs.isColorBlindEnabled();
        
        int bgColor = highContrast ? android.graphics.Color.BLACK : android.graphics.Color.parseColor("#1A1A2E");
        int cardColor = highContrast ? android.graphics.Color.BLACK : android.graphics.Color.parseColor("#25253B");
        int textColor = android.graphics.Color.WHITE;
        
        root.setBackgroundColor(bgColor);
        
        TextView title = root.findViewById(R.id.tv_adaptive_title);
        if (title != null) {
            title.setTextColor(textColor);
            title.setTypeface(null, highContrast ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        updateCard(root, R.id.card_color_blind, cardColor, highContrast);
        updateCard(root, R.id.card_flashlight, cardColor, highContrast);
        updateCard(root, R.id.card_vibrate, cardColor, highContrast);
    }

    private void updateCard(View root, int cardId, int color, boolean highContrast) {
        MaterialCardView card = root.findViewById(cardId);
        if (card != null) {
            card.setCardBackgroundColor(color);
            card.setStrokeWidth(highContrast ? 4 : 0);
            card.setStrokeColor(android.graphics.Color.WHITE);
        }
    }

    private void setupColorBlindSwitch(View root) {
        SwitchMaterial sw = root.findViewById(R.id.switch_color_blind);
        sw.setChecked(accessibilityPrefs.isColorBlindEnabled());
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            accessibilityPrefs.setColorBlindEnabled(isChecked);
            applyHighContrast(root);
        });
    }

    private void setupFlashlightSwitch(View root) {
        SwitchMaterial sw = root.findViewById(R.id.switch_flashlight_alerts);
        sw.setChecked(accessibilityPrefs.isFlashlightAlertEnabled());
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Request permissions if needed
                if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    
                    requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.CAMERA}, 1001);
                    sw.setChecked(false);
                    return;
                }
            }
            accessibilityPrefs.setFlashlightAlertEnabled(isChecked);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1001) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                accessibilityPrefs.setFlashlightAlertEnabled(true);
                SwitchMaterial sw = getView().findViewById(R.id.switch_flashlight_alerts);
                if (sw != null) sw.setChecked(true);
            } else {
                Toast.makeText(requireContext(), "Flashlight alerts require Call and Camera permissions", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1002) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                accessibilityPrefs.setVibrateAlertEnabled(true);
                SwitchMaterial sw = getView().findViewById(R.id.switch_vibrate_alerts);
                if (sw != null) sw.setChecked(true);
            } else {
                Toast.makeText(requireContext(), "Vibrate alerts require Call permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupVibrateSwitch(View root) {
        SwitchMaterial sw = root.findViewById(R.id.switch_vibrate_alerts);
        sw.setChecked(accessibilityPrefs.isVibrateAlertEnabled());
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE}, 1002);
                    sw.setChecked(false);
                    return;
                }
            }
            accessibilityPrefs.setVibrateAlertEnabled(isChecked);
        });
    }
}
