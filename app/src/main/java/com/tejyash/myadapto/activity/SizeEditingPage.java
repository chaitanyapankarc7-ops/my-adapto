package com.tejyash.myadapto.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityManager;
import com.tejyash.myadapto.accessibility.AccessibilityPreferences;
import com.tejyash.myadapto.launcher.HomeActivity;
import com.tejyash.myadapto.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class SizeEditingPage extends AppCompatActivity {

    private static final int REQ_FLASH_PERMS = 2001;
    private static final int REQ_VIB_PERMS = 2002;

    private AccessibilityManager accessibilityManager;
    private AccessibilityPreferences accessibilityPrefs;
    
    private SwitchMaterial switchFlash, switchVib;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.size_editing_page);

        accessibilityManager = new AccessibilityManager(this);
        accessibilityPrefs = AccessibilityPreferences.get(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SeekBar   seekTextSize   = findViewById(R.id.seekBar2);
        SeekBar   seekIconSize   = findViewById(R.id.seekBar3);
        SwitchMaterial switchContrast = findViewById(R.id.switch1);
        TextView  tvLargeA      = findViewById(R.id.textView20);
        ImageView imgIconBig    = findViewById(R.id.imageView8);
        
        switchFlash = findViewById(R.id.switch_flashlight);
        switchVib = findViewById(R.id.switch_vibrate);
        Button btnSetupBubble = findViewById(R.id.btn_setup_bubble);

        // ── Restore saved values ──
        seekTextSize.setMax(3);
        seekTextSize.setProgress(accessibilityManager.getFontStep());
        tvLargeA.setTextSize(TypedValue.COMPLEX_UNIT_SP, accessibilityManager.getFontSizeSp());

        seekIconSize.setMax(3);
        seekIconSize.setProgress(accessibilityManager.getIconStep());
        applyIconPreview(imgIconBig, accessibilityManager.getIconSizeDp());

        // ── Seekbar Listeners ──
        seekTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                float sp = AccessibilityPreferences.TEXT_SIZES[progress];
                tvLargeA.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
                if (fromUser) accessibilityManager.setFontStep(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s)  {}
        });

        seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int dp = AccessibilityPreferences.ICON_SIZES_DP[progress];
                applyIconPreview(imgIconBig, dp);
                if (fromUser) accessibilityManager.setIconStep(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s)  {}
        });

        // ── High Contrast Toggle ──
        applyHighContrastUI(accessibilityPrefs.isColorBlindEnabled());
        switchContrast.setChecked(accessibilityPrefs.isColorBlindEnabled());
        switchContrast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            accessibilityPrefs.setColorBlindEnabled(isChecked);
            applyHighContrastUI(isChecked);
        });

        // ── Flashlight Toggle Logic ──
        switchFlash.setChecked(accessibilityPrefs.isFlashlightAlertEnabled());
        switchFlash.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                if (checkPermission(Manifest.permission.CAMERA) && checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                    accessibilityPrefs.setFlashlightAlertEnabled(true);
                } else {
                    switchFlash.setChecked(false);
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_PHONE_STATE}, 
                        REQ_FLASH_PERMS);
                }
            } else {
                accessibilityPrefs.setFlashlightAlertEnabled(false);
            }
        });

        // ── Vibration Toggle Logic ──
        switchVib.setChecked(accessibilityPrefs.isBigVibrationEnabled());
        switchVib.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                if (checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                    accessibilityPrefs.setBigVibrationEnabled(true);
                    triggerVibrationPreview();
                } else {
                    switchVib.setChecked(false);
                    ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_PHONE_STATE}, 
                        REQ_VIB_PERMS);
                }
            } else {
                accessibilityPrefs.setBigVibrationEnabled(false);
            }
        });

        btnSetupBubble.setOnClickListener(v -> startActivity(new Intent(this, OverlaySetupActivity.class)));

        Button btn = findViewById(R.id.btn);
        btn.setOnClickListener(v -> {
            getSharedPreferences(Constants.PREFS_ONBOARDING, MODE_PRIVATE).edit().putBoolean(Constants.KEY_SETUP_COMPLETE, true).apply();
            Intent intent = new Intent(SizeEditingPage.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private boolean checkPermission(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQ_FLASH_PERMS) {
            if (checkPermission(Manifest.permission.CAMERA) && checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                accessibilityPrefs.setFlashlightAlertEnabled(true);
                switchFlash.setChecked(true);
                Toast.makeText(this, "Flashlight alerts enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions required for flashlight alerts", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_VIB_PERMS) {
            if (checkPermission(Manifest.permission.READ_PHONE_STATE)) {
                accessibilityPrefs.setBigVibrationEnabled(true);
                switchVib.setChecked(true);
                triggerVibrationPreview();
                Toast.makeText(this, "Big vibration enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission required for vibration alerts", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void triggerVibrationPreview() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null) v.vibrate(500);
    }

    private void applyIconPreview(ImageView img, int dp) {
        int px = Math.round(dp * getResources().getDisplayMetrics().density);
        android.view.ViewGroup.LayoutParams lp = img.getLayoutParams();
        lp.width  = px;
        lp.height = px;
        img.setLayoutParams(lp);
    }

    private void applyHighContrastUI(boolean enabled) {
        View mainLayout = findViewById(R.id.main_layout);
        View scrollRoot = findViewById(android.R.id.content);
        if (enabled) {
            if (scrollRoot != null) scrollRoot.setBackgroundColor(android.graphics.Color.BLACK);
            if (mainLayout != null) mainLayout.setBackgroundColor(android.graphics.Color.BLACK);
        } else {
            if (scrollRoot != null) scrollRoot.setBackgroundResource(R.drawable.bg_personalize);
            if (mainLayout != null) mainLayout.setBackgroundResource(R.drawable.bg_personalize);
        }
        int[] cardIds = {R.id.card_text_size, R.id.card_icon_size, R.id.card_high_contrast, R.id.card_flashlight, R.id.card_vibrate, R.id.card_bubble};
        for (int id : cardIds) {
            androidx.cardview.widget.CardView card = findViewById(id);
            if (card != null) card.setCardBackgroundColor(enabled ? android.graphics.Color.BLACK : android.graphics.Color.parseColor("#1AFFFFFF"));
        }
    }
}
