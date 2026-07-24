package com.tejyash.myadapto.fregment;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.activity.VoiceAssitentPage;
import com.tejyash.myadapto.manager.AppManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The Home page — logo, a time-of-day greeting, a live clock, and the
 * bottom dock (Gallery / Phone / Camera / Contacts / SOS / Voice).
 *
 * The dock-resolving logic (find whichever app the phone actually uses
 * for "camera", "gallery", etc.) used to live directly in HomeActivity.
 * It moved here because the dock is this page's furniture — HomeActivity
 * shouldn't need to know PackageManager exists just to draw one room.
 * All the actual app-lookup work still happens in AppManager; this class
 * only wires the result to the views.
 */
public class HomeFragment extends Fragment {

    private AppManager appManager;
    private TextView    clockView;

    // Ticks the clock every second while this page is on screen, and
    // stops in onPause so it isn't doing pointless work while the user
    // is looking at the Apps or Widgets page instead.
    private final Handler  clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 1000);
        }
    };

    public HomeFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        appManager = new AppManager(requireContext());
        clockView  = view.findViewById(R.id.home_clock);

        setGreeting(view.findViewById(R.id.home_greeting));
        setupDock(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        clockHandler.post(clockTick);
    }

    @Override
    public void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
    }

    private void updateClock() {
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        clockView.setText(time);
    }

    private void setGreeting(TextView greetingView) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12)      greeting = "Good morning";
        else if (hour < 17) greeting = "Good afternoon";
        else                greeting = "Good evening";
        greetingView.setText(greeting);
    }

    // ── Bottom dock ────────────────────────────────────────────────
    private void setupDock(View root) {
        // Phone/Camera/Gallery/Contacts: resolve whichever app the device
        // actually uses for that action, so the icon, label, AND the tap
        // itself all point at the user's real installed app — no chooser
        // dialog, no fixed placeholder.
        bindDockSlot(root, R.id.dock_phone, R.id.dock_phone_label,
                new Intent(Intent.ACTION_DIAL));

        bindDockSlot(root, R.id.dock_camera, R.id.dock_camera_label,
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE));

        Intent galleryCategory = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY);
        Intent galleryFallback = new Intent(Intent.ACTION_VIEW).setType("image/*");
        bindDockSlot(root, R.id.dock_gallery, R.id.dock_gallery_label,
                galleryCategory, galleryFallback);

        Intent contactsCategory = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS);
        Intent contactsFallback = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
        bindDockSlot(root, R.id.dock_contacts, R.id.dock_contacts_label,
                contactsCategory, contactsFallback);

        // SOS and Voice are Adapto's own features, not external apps to
        // resolve, so they keep their fixed icon and go straight to a
        // hardcoded action.
        ImageView imgSOS = root.findViewById(R.id.dock_sos);
        if (imgSOS != null) imgSOS.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))));

        ImageView imgVoice = root.findViewById(R.id.dock_voice);
        if (imgVoice != null) imgVoice.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), VoiceAssitentPage.class)));
    }

    /**
     * Resolves the real app icon/label for one dock slot and wires the tap
     * to an EXPLICIT intent pointing at that same resolved app — this is
     * what stops Android's app-picker dialog from popping up on tap. If
     * nothing resolves, the XML placeholder drawable/text stays as-is and
     * the tap falls back to the original (first) probe intent.
     */
    private void bindDockSlot(View root, int iconId, int labelId, Intent... probes) {
        ImageView icon = root.findViewById(iconId);
        if (icon == null) return;

        Drawable realIcon = appManager.resolveIconFor(probes);
        if (realIcon != null) icon.setImageDrawable(realIcon);

        TextView label = root.findViewById(labelId);
        if (label != null) {
            String realLabel = appManager.resolveLabelFor(probes);
            if (realLabel != null) label.setText(realLabel);
        }

        Intent explicit = appManager.resolveExplicitIntent(probes);
        icon.setOnClickListener(v ->
                startActivity(explicit != null ? explicit : probes[0]));
    }
}