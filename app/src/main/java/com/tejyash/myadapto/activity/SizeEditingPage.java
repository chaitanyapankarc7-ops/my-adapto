package com.tejyash.myadapto.activity;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityManager;
import com.tejyash.myadapto.accessibility.AccessibilityPreferences;
import com.tejyash.myadapto.launcher.HomeActivity;
import com.tejyash.myadapto.utils.Constants;

public class SizeEditingPage extends AppCompatActivity {

    private AccessibilityManager accessibilityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.size_editing_page);

        accessibilityManager = new AccessibilityManager(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.button5), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SeekBar   seekTextSize   = findViewById(R.id.seekBar2);
        SeekBar   seekIconSize   = findViewById(R.id.seekBar3);
        Switch    switchContrast = findViewById(R.id.switch1);
        TextView  tvLargeA      = findViewById(R.id.textView20);
        ImageView imgIconBig    = findViewById(R.id.imageView8);
        CardView  card4         = findViewById(R.id.card4);
        TextView  tvContrastLabel = findViewById(R.id.textView16);
        TextView  tvContrastSub   = findViewById(R.id.textView22);

        // ── Restore saved values so seekbars reflect current prefs ──
        seekTextSize.setMax(3);
        seekTextSize.setProgress(accessibilityManager.getFontStep());
        tvLargeA.setTextSize(accessibilityManager.getFontSizeSp() + 10);

        seekIconSize.setMax(3);
        seekIconSize.setProgress(accessibilityManager.getIconStep());
        applyIconPreview(imgIconBig, accessibilityManager.getIconSizeDp());

        // ── Text size — save on every drag, update preview ──────────
        seekTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                float sp = AccessibilityPreferences.TEXT_SIZES[progress];
                tvLargeA.setTextSize(sp + 10);
                if (fromUser) accessibilityManager.setFontStep(progress); // persists + notifies HomeActivity
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s)  {}
        });

        // ── Icon size — save on every drag, update preview ───────────
        seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int dp = AccessibilityPreferences.ICON_SIZES_DP[progress];
                applyIconPreview(imgIconBig, dp);
                if (fromUser) accessibilityManager.setIconStep(progress); // persists + notifies HomeActivity
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s)  {}
        });

        // ── High contrast toggle (visual only — no persistence needed) ──
        switchContrast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                card4.setCardBackgroundColor(0xFF000000);
                tvContrastLabel.setTextColor(0xFFFFFFFF);
                tvContrastSub.setTextColor(0xFFFFFFFF);
                tvContrastLabel.setTypeface(null, Typeface.BOLD);
            } else {
                card4.setCardBackgroundColor(0xFFFFFFFF);
                tvContrastLabel.setTextColor(0xFF0F120B);
                tvContrastSub.setTextColor(0xFF000000);
                tvContrastLabel.setTypeface(null, Typeface.NORMAL);
            }
        });

        // ── Continue button ──────────────────────────────────────────
        Button btn = findViewById(R.id.btn);
        btn.setOnClickListener(v -> {
            // Mark setup complete so next cold start goes straight to HomeActivity
            getSharedPreferences(Constants.PREFS_ONBOARDING, MODE_PRIVATE)
                    .edit()
                    .putBoolean(Constants.KEY_SETUP_COMPLETE, true)
                    .apply();

            Intent intent = new Intent(SizeEditingPage.this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void applyIconPreview(ImageView img, int dp) {
        int px = Math.round(dp * getResources().getDisplayMetrics().density);
        android.view.ViewGroup.LayoutParams lp = img.getLayoutParams();
        lp.width  = px;
        lp.height = px;
        img.setLayoutParams(lp);
    }
}