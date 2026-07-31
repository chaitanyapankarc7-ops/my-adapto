package com.tejyash.myadapto.fregment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
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

import android.content.IntentFilter;
import android.os.BatteryManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.activity.VoiceAssitentPage;
import com.tejyash.myadapto.manager.AppManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    // 🔑 PASTE YOUR OPENWEATHERMAP API KEY HERE
    private static final String WEATHER_API_KEY = "c58e01a80fcc649ad953376498228147";

    private AppManager appManager;
    private TextView   clockView;
    private TextView   txtWeather;
    private ImageView  imgWeather;

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
        txtWeather = view.findViewById(R.id.txt_weather);
        imgWeather = view.findViewById(R.id.img_weather);

        setGreeting(view.findViewById(R.id.home_greeting));
        setupDock(view);
        fetchWeather(); // 🌤️ load real weather



// ================= Battery Widget =================

        TextView txtBatteryPercent = view.findViewById(R.id.txtBatteryPercent);
        TextView txtBatteryStatus = view.findViewById(R.id.txtBatteryStatus);
        ProgressBar progressBattery = view.findViewById(R.id.progressBattery);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryIntent = requireContext().registerReceiver(null, intentFilter);

        if (batteryIntent != null) {

            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            int batteryPercent = (int) ((level / (float) scale) * 100);

            progressBattery.setProgress(batteryPercent);
            txtBatteryPercent.setText(batteryPercent + "%");

            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            boolean isCharging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL;

            if (isCharging) {
                txtBatteryStatus.setText("⚡ Charging");
            } else {
                txtBatteryStatus.setText("🔋 On Battery");
            }
        }






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

    // ── Clock ──────────────────────────────────────────────────────
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

    // ── Weather ────────────────────────────────────────────────────
    private void fetchWeather() {
        // Get location
        LocationManager lm = (LocationManager) requireContext()
                .getSystemService(android.content.Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // No permission yet — request it
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 101);
            return;
        }

        Location location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null) location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        // Fallback to Mumbai if location still null
        double lat = location != null ? location.getLatitude()  : 19.0760;
        double lon = location != null ? location.getLongitude() : 72.8777;

        double finalLat = lat;
        double finalLon = lon;

        // Run network call on background thread
        new Thread(() -> {
            try {
                String urlStr = "https://api.openweathermap.org/data/2.5/weather"
                        + "?lat=" + finalLat
                        + "&lon=" + finalLon
                        + "&appid=" + WEATHER_API_KEY
                        + "&units=metric";

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json      = new JSONObject(sb.toString());
                int    temp          = (int) json.getJSONObject("main").getDouble("temp");
                String condition     = json.getJSONArray("weather")
                        .getJSONObject(0).getString("main");
                String weatherText   = temp + "°C • " + condition;
                int    iconRes       = getWeatherIcon(condition);

                // Update UI on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (txtWeather != null) txtWeather.setText(weatherText);
                    if (imgWeather != null) imgWeather.setImageResource(iconRes);
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (txtWeather != null) txtWeather.setText("Weather unavailable");
                });
            }
        }).start();
    }

    // Maps condition string → your drawable icon
    private int getWeatherIcon(String condition) {
        switch (condition.toLowerCase()) {
            case "rain":
            case "drizzle":
            case "thunderstorm":
            case "snow":
            case "clouds":
            default: return R.drawable.ic_weather_sunny;
        }
    }
    // Called after user grants/denies location permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchWeather(); // retry now that we have permission
        }
    }

    // ── Bottom dock ────────────────────────────────────────────────
    private void setupDock(View root) {
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

        ImageView imgSOS = root.findViewById(R.id.dock_sos);
        if (imgSOS != null) imgSOS.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))));

        ImageView imgVoice = root.findViewById(R.id.dock_voice);
        if (imgVoice != null) imgVoice.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), VoiceAssitentPage.class)));
    }

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