package com.tejyash.myadapto.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.tejyash.myadapto.accessibility.ScreenContentAccessibilityService;
import com.tejyash.myadapto.overlay.OverlayService;

/**
 * Setup and management screen for the Floating Assistant Bubble and Screen Reader service.
 */
public class OverlaySetupActivity extends AppCompatActivity {

    private TextView tvOverlayStatus;
    private TextView tvAccessStatus;
    private Button btnToggleBubble;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1A1A2E"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(20);
        root.setPadding(pad, dpToPx(32), pad, dpToPx(32));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Assistant Bubble & Screen Reader");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvTitle.setTypeface(null, Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("Enable the floating bubble to trigger your AI assistant from any app, and the screen reader to let it understand your current screen.");
        tvSub.setTextColor(Color.parseColor("#B0B0C0"));
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvSub.setPadding(0, dpToPx(8), 0, dpToPx(20));
        root.addView(tvSub);

        // ── Card 1: Overlay Permission ───────────────────────────────
        CardView cardOverlay = createCard();
        LinearLayout layOverlay = createCardLayout();

        TextView tvOHead = createCardTitle("1. Display Over Other Apps");
        tvOverlayStatus = createCardStatus();

        Button btnGrantOverlay = createActionButton("Grant Overlay Permission", Color.parseColor("#2563EB"));
        btnGrantOverlay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        layOverlay.addView(tvOHead);
        layOverlay.addView(tvOverlayStatus);
        layOverlay.addView(btnGrantOverlay);
        cardOverlay.addView(layOverlay);
        root.addView(cardOverlay);

        // ── Card 2: Accessibility Screen Reader ───────────────────────
        CardView cardAccess = createCard();
        LinearLayout layAccess = createCardLayout();

        TextView tvAHead = createCardTitle("2. Screen Content Reader");
        tvAccessStatus = createCardStatus();

        Button btnGrantAccess = createActionButton("Open Accessibility Settings", Color.parseColor("#2563EB"));
        btnGrantAccess.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        layAccess.addView(tvAHead);
        layAccess.addView(tvAccessStatus);
        layAccess.addView(btnGrantAccess);
        cardAccess.addView(layAccess);
        root.addView(cardAccess);

        // ── Card 3: Floating Bubble Control ─────────────────────────
        CardView cardBubble = createCard();
        LinearLayout layBubble = createCardLayout();

        TextView tvBHead = createCardTitle("3. Floating Bubble");
        TextView tvBDesc = new TextView(this);
        tvBDesc.setText("When active, tap the floating bubble to instantly voice-query the assistant.");
        tvBDesc.setTextColor(Color.parseColor("#B0B0C0"));
        tvBDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvBDesc.setPadding(0, dpToPx(4), 0, dpToPx(12));

        btnToggleBubble = createActionButton("Start Bubble", Color.parseColor("#059669"));
        btnToggleBubble.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant overlay permission first.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (OverlayService.isRunning()) {
                stopService(new Intent(this, OverlayService.class));
                Toast.makeText(this, "Bubble stopped", Toast.LENGTH_SHORT).show();
            } else {
                startService(new Intent(this, OverlayService.class));
                Toast.makeText(this, "Bubble started", Toast.LENGTH_SHORT).show();
            }
            updateUI();
        });

        layBubble.addView(tvBHead);
        layBubble.addView(tvBDesc);
        layBubble.addView(btnToggleBubble);
        cardBubble.addView(layBubble);
        root.addView(cardBubble);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean canOverlay = Settings.canDrawOverlays(this);
        if (canOverlay) {
            tvOverlayStatus.setText("Status: Granted");
            tvOverlayStatus.setTextColor(Color.parseColor("#10B981"));
        } else {
            tvOverlayStatus.setText("Status: Not Granted (Required for bubble)");
            tvOverlayStatus.setTextColor(Color.parseColor("#EF4444"));
        }

        boolean accessEnabled = ScreenContentAccessibilityService.isServiceEnabled(this);
        if (accessEnabled) {
            tvAccessStatus.setText("Status: Enabled (Screen reading active)");
            tvAccessStatus.setTextColor(Color.parseColor("#10B981"));
        } else {
            tvAccessStatus.setText("Status: Disabled (Enable MyAdapto Screen Reader in list)");
            tvAccessStatus.setTextColor(Color.parseColor("#F59E0B"));
        }

        if (OverlayService.isRunning()) {
            btnToggleBubble.setText("Stop Floating Bubble");
            btnToggleBubble.setBackgroundColor(Color.parseColor("#DC2626"));
        } else {
            btnToggleBubble.setText("Start Floating Bubble");
            btnToggleBubble.setBackgroundColor(Color.parseColor("#059669"));
        }
    }

    private CardView createCard() {
        CardView card = new CardView(this);
        card.setCardBackgroundColor(Color.parseColor("#25253B"));
        card.setRadius(dpToPx(14));
        card.setCardElevation(dpToPx(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dpToPx(16));
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout createCardLayout() {
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        int p = dpToPx(16);
        lay.setPadding(p, p, p, p);
        return lay;
    }

    private TextView createCardTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private TextView createCardStatus() {
        TextView tv = new TextView(this);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setPadding(0, dpToPx(4), 0, dpToPx(10));
        return tv;
    }

    private Button createActionButton(String text, int bgColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(bgColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        return btn;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
