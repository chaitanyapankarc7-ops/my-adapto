package com.tejyash.myadapto.fregment;

import android.app.AlertDialog;
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

import android.view.DragEvent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.adapter.HomeGridAdapter;
import com.tejyash.myadapto.adapter.HomePagesAdapter;
import com.tejyash.myadapto.launcher.DockPreferences;
import com.tejyash.myadapto.launcher.GridModel;
import com.tejyash.myadapto.launcher.GridPreferences;
import com.tejyash.myadapto.manager.AppManager;
import com.tejyash.myadapto.model.AppInfo;

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

    private AppManager      appManager;
    private HomePagesAdapter pagesAdapter;
    private ViewPager2      pager;
    private View            removePill;
    private TextView   clockView;
    private TextView   txtWeather;
    private ImageView  imgWeather;

    // Drag-to-edge-reveals-adjacent-page support (see handleDrop)
    private final Handler edgeScrollHandler = new Handler(Looper.getMainLooper());
    private Runnable      pendingEdgeScroll;
    private Integer       pendingEdgeScrollDirection;
    // Grace timer: a finger dwelling near an edge naturally jitters in/out of
    // the zone by a pixel or two per frame. Without this, every out-frame
    // cancelled the dwell timer and reset it to zero, so it could dwell there
    // for seconds and still never fire. Real exits (drag exited/dropped/ended)
    // bypass this and cancel immediately.
    private Runnable      pendingLeaveGrace;

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
        setupHomeGrid(view);
        fetchWeather(); // 🌤️ load real weather

        // Warm AppManager's app-list cache off the main thread. First cold
        // bind above may still do the heavy synchronous scan once (cache is
        // empty at that point), but every bind after this completes —
        // including new Home pages created mid-drag by the edge-scroll
        // feature in handleDrop()/movePageForDrag() below — hits the cache
        // instead of re-running queryIntentActivities()+icon composition,
        // which was previously stalling the main thread long enough to
        // starve the edge-scroll dwell timer and kill the drag.
        appManager.warmCacheAsync(this::refreshGrid);



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
        refreshGrid(); // picks up notification badges that changed while another app was open
        promptNotificationAccessIfNeeded();
    }

    /** Asks once, ever, if notification badges aren't enabled yet — never nags repeatedly. */
    private void promptNotificationAccessIfNeeded() {
        if (com.tejyash.myadapto.notifications.NotificationAccessHelper.isEnabled(requireContext())) return;
        if (com.tejyash.myadapto.notifications.NotificationAccessHelper.hasPromptedBefore(requireContext())) return;

        com.tejyash.myadapto.notifications.NotificationAccessHelper.markPrompted(requireContext());
        com.google.android.material.snackbar.Snackbar.make(
                        requireView(), "Enable notification badges on app icons?", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("Enable", v -> startActivity(
                        com.tejyash.myadapto.notifications.NotificationAccessHelper.settingsIntent()))
                .show();
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
        bindDockSlot(root, R.id.dock_phone, R.id.dock_phone_label, "dock_phone",
                new Intent(Intent.ACTION_DIAL));

        bindDockSlot(root, R.id.dock_camera, R.id.dock_camera_label, "dock_camera",
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE));

        Intent galleryCategory = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY);
        Intent galleryFallback = new Intent(Intent.ACTION_VIEW).setType("image/*");
        bindDockSlot(root, R.id.dock_gallery, R.id.dock_gallery_label, "dock_gallery",
                galleryCategory, galleryFallback);

        Intent contactsCategory = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS);
        Intent contactsFallback = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
        bindDockSlot(root, R.id.dock_contacts, R.id.dock_contacts_label, "dock_contacts",
                contactsCategory, contactsFallback);

        // SOS and Voice dock buttons removed for now — will be added back later.
    }

    // ── Home grid — multi-page, real drag-and-drop target ────────────
    private void setupHomeGrid(View root) {
        pager = root.findViewById(R.id.vp_home_pages);
        if (pager == null) return;

        pagesAdapter = new HomePagesAdapter(requireContext(), appManager,
                GridPreferences.getPageCount(requireContext()));

        pagesAdapter.setOnAppClickListener(app -> appManager.launchApp(requireContext(), app));
        // Two-stage gesture lives inside HomeGridAdapter now: hold-then-move
        // starts a drag directly there; hold-without-moving calls back here
        // to show the actual menu (needs appManager, which the adapter doesn't have).
        pagesAdapter.setOnAppMenuRequestListener(this::showHomeContextMenu);

        // Each page gets a native OnDragListener so an app dragged from the
        // drawer (add) or from another Home icon (reposition) can be
        // dropped onto whichever page is currently visible.
        pagesAdapter.setOnPageBoundListener((page, gridAdapter, recyclerView) ->
                recyclerView.setOnDragListener((v, event) ->
                        handleDrop(event, gridAdapter, recyclerView)));

        pager.setAdapter(pagesAdapter);

        // Full-screen swipe-up-to-open-drawer — see SwipeUpFrameLayout, now
        // the root of this whole layout (fragment_home.xml), not just a
        // strip near the dock. Plain taps/clicks on icons still work
        // normally; only a real vertical drag gets stolen from them.
        com.tejyash.myadapto.launcher.SwipeUpFrameLayout swipeZone =
                root instanceof com.tejyash.myadapto.launcher.SwipeUpFrameLayout
                        ? (com.tejyash.myadapto.launcher.SwipeUpFrameLayout) root
                        : root.findViewById(R.id.home_swipe_root);

        if (swipeZone != null) {
            swipeZone.setOnSwipeUpListener(() -> {
                if (getActivity() instanceof com.tejyash.myadapto.launcher.HomeActivity) {
                    ((com.tejyash.myadapto.launcher.HomeActivity) getActivity()).openAppDrawer();
                }
            });

            // Still exclude just the bottom system-gesture strip (not the
            // whole screen now) from Android's own "swipe up = go home"
            // gesture — see the earlier fix; only needed near that edge
            // since the zone covering the whole screen means most swipes
            // never get near it anyway.
            swipeZone.post(() -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    int width = swipeZone.getWidth();
                    int height = swipeZone.getHeight();
                    if (width > 0 && height > 0) {
                        int stripPx = Math.round(32 * getResources().getDisplayMetrics().density);
                        swipeZone.setSystemGestureExclusionRects(
                                java.util.Collections.singletonList(
                                        new android.graphics.Rect(0, height - stripPx, width, height)));
                    }
                }
            });
        }

        // "Remove from Home" drop target — only reveals itself for
        // reposition drags (see HomeGridAdapter.MOVE_CLIP_LABEL), not for
        // fresh adds from the drawer (nothing to remove yet in that case).
        removePill = root.findViewById(R.id.remove_from_home_pill);
        if (removePill != null) {
            removePill.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        boolean isMove = event.getClipDescription() != null
                                && HomeGridAdapter.MOVE_CLIP_LABEL.equals(
                                event.getClipDescription().getLabel());
                        if (isMove) removePill.setVisibility(View.VISIBLE);
                        return true;
                    case DragEvent.ACTION_DROP:
                        if (event.getClipData() == null || event.getClipData().getItemCount() == 0) return false;
                        String pkg = String.valueOf(event.getClipData().getItemAt(0).getText());
                        GridPreferences.removeApp(requireContext(), pkg);
                        refreshGrid();
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        removePill.setVisibility(View.GONE);
                        return true;
                    default:
                        return true;
                }
            });
        }
    }

    /**
     * Handles every stage of a native drag over one Home page. Only ACTION_DROP
     * does real work — the target cell is estimated from where the drop
     * happened, scaled against this page's own width/height, since cells are
     * laid out evenly by GridLayoutManager.
     */
    private boolean handleDrop(DragEvent event, HomeGridAdapter gridAdapter, RecyclerView recyclerView) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                android.util.Log.d("HomeFragment", "ACTION_DRAG_STARTED on page " + gridAdapter.getPage());
                return true; // must return true here to keep receiving events for this drag

            case DragEvent.ACTION_DRAG_LOCATION: {
                // Hovering near either edge for a moment reveals the
                // next/previous page (creating a new one on the right if
                // needed) — matches dragging an icon off either side of the
                // screen to move it between pages on a normal launcher.
                float rightEdge = recyclerView.getWidth() * 0.80f; // last ~20% of width
                float leftEdge  = recyclerView.getWidth() * 0.20f; // first ~20% of width
                if (event.getX() > rightEdge) {
                    android.util.Log.d("HomeFragment", "in RIGHT edge zone, x=" + event.getX() + " width=" + recyclerView.getWidth());
                    cancelLeaveGrace(); // back in the zone — let the dwell timer keep running
                    scheduleEdgeScroll(+1);
                } else if (event.getX() < leftEdge) {
                    android.util.Log.d("HomeFragment", "in LEFT edge zone, x=" + event.getX() + " width=" + recyclerView.getWidth());
                    cancelLeaveGrace();
                    scheduleEdgeScroll(-1);
                } else {
                    // Don't cancel immediately — a jittery finger can dip out
                    // of the zone for a frame or two and come right back.
                    // Only give up on the dwell timer if we stay out for
                    // longer than the grace period.
                    scheduleLeaveGrace();
                }
                return true;
            }

            case DragEvent.ACTION_DRAG_EXITED:
                cancelPendingEdgeScroll();
                return true;

            case DragEvent.ACTION_DROP:
                cancelPendingEdgeScroll();
                android.util.Log.d("HomeFragment", "ACTION_DROP on page " + gridAdapter.getPage()
                        + " at (" + event.getX() + "," + event.getY() + ") rv="
                        + recyclerView.getWidth() + "x" + recyclerView.getHeight());
                if (event.getClipData() == null || event.getClipData().getItemCount() == 0) {
                    android.util.Log.w("HomeFragment", "ACTION_DROP had no clip data");
                    return false;
                }
                String packageName = String.valueOf(event.getClipData().getItemAt(0).getText());

                int columns = gridAdapter.getColumns();
                int row, col;

                // Ask the RecyclerView itself which item view is actually sitting
                // at the drop point — this is exact, unlike estimating from
                // height/rowCount math, which drifts because cells are
                // wrap_content-sized rather than evenly filling the RecyclerView.
                // HomeGridAdapter always renders a (possibly empty) cell view for
                // every slot, so this should find a hit almost everywhere.
                View child = recyclerView.findChildViewUnder(event.getX(), event.getY());
                int position = child != null
                        ? recyclerView.getChildAdapterPosition(child)
                        : RecyclerView.NO_POSITION;

                if (position != RecyclerView.NO_POSITION) {
                    row = position / columns;
                    col = position % columns;
                    android.util.Log.d("HomeFragment", "hit-test found position=" + position);
                } else {
                    // Dropped below/beyond the last rendered row (empty dead
                    // space under a short grid) — estimate using a REAL
                    // rendered cell's height when one exists, since cells are
                    // wrap_content and don't evenly fill the RecyclerView's
                    // full height (that mismatch was the original bug).
                    int rows = GridPreferences.ROWS_PER_PAGE;
                    float cellW = recyclerView.getWidth() / (float) columns;
                    float cellH = recyclerView.getChildCount() > 0
                            ? recyclerView.getChildAt(0).getHeight()
                            : recyclerView.getHeight() / (float) rows;
                    col = clamp((int) (event.getX() / cellW), 0, columns - 1);
                    row = clamp((int) (event.getY() / cellH), 0, rows - 1);
                    android.util.Log.d("HomeFragment", "hit-test missed, falling back to estimate");
                }
                android.util.Log.d("HomeFragment", "placing " + packageName + " at row=" + row + " col=" + col);

                gridAdapter.acceptDrop(packageName, appManager, row, col);

                // DIAGNOSTIC: dump exactly what's now saved for this page, and
                // which page is actually visible right now, so we can tell
                // apart three different possible bugs from Logcat alone:
                //   (a) nothing got saved at all (a placeApp bug)
                //   (b) it saved to a DIFFERENT page than the one being viewed
                //       (a page-routing bug)
                //   (c) it saved correctly to the right page/cell, and this
                //       is a pure rendering/z-order issue, not a data issue
                int visiblePage = pager != null ? pager.getCurrentItem() : -1;
                java.util.List<GridModel> nowOnThisPage = GridPreferences.loadPage(requireContext(), gridAdapter.getPage());
                StringBuilder dump = new StringBuilder();
                for (GridModel g : nowOnThisPage) dump.append(g.toString()).append(" | ");
                android.util.Log.d("HomeFragment", "AFTER DROP — target page=" + gridAdapter.getPage()
                        + " visiblePage=" + visiblePage
                        + " savedOnTargetPage=[" + dump + "]");

                // The drop might have filled the last page — re-check whether
                // a fresh blank page needs to appear at the end.
                if (pagesAdapter != null) {
                    pagesAdapter.setPageCount(GridPreferences.getPageCount(requireContext()));
                }
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                cancelPendingEdgeScroll();
                return true;

            default:
                return true; // accept unhandled actions without special handling
        }
    }

    /**
     * Schedules a page change in the given direction (+1 = next/right,
     * -1 = previous/left) after a short dwell, so a drag has to hover near
     * the edge deliberately rather than flicker-triggering on a pass-through.
     */
    private void scheduleEdgeScroll(int direction) {
        if (pendingEdgeScrollDirection != null && pendingEdgeScrollDirection == direction) {
            return; // already scheduled for this same direction
        }
        cancelPendingEdgeScroll();
        pendingEdgeScrollDirection = direction;
        pendingEdgeScroll = () -> {
            movePageForDrag(direction);
            pendingEdgeScroll = null;
            pendingEdgeScrollDirection = null;
        };
        edgeScrollHandler.postDelayed(pendingEdgeScroll, 550);
    }

    /** direction: +1 advances to the next page (growing one if needed), -1 goes back. */
    private void movePageForDrag(int direction) {
        if (pager == null || pagesAdapter == null) return;
        int target = pager.getCurrentItem() + direction;
        android.util.Log.d("HomeFragment", "movePageForDrag direction=" + direction
                + " current=" + pager.getCurrentItem() + " target=" + target
                + " pageCount=" + pagesAdapter.getItemCount());
        if (target < 0) return; // already on the first page, nothing before it
        if (target >= pagesAdapter.getItemCount()) {
            pagesAdapter.setPageCount(target + 1); // grow to make room for it
        }
        pager.setCurrentItem(target, true);
    }

    private void cancelPendingEdgeScroll() {
        cancelLeaveGrace();
        if (pendingEdgeScroll != null) {
            edgeScrollHandler.removeCallbacks(pendingEdgeScroll);
            pendingEdgeScroll = null;
            pendingEdgeScrollDirection = null;
        }
    }

    /**
     * Starts a short grace window after the finger leaves the edge zone.
     * Only actually cancels the dwell timer once the finger has been outside
     * the zone continuously for the whole grace period — a single jittery
     * frame back in the zone (handled via cancelLeaveGrace()) aborts this and
     * leaves the original dwell timer untouched.
     */
    private void scheduleLeaveGrace() {
        if (pendingEdgeScroll == null) return; // nothing running to protect
        if (pendingLeaveGrace != null) return;  // already counting down
        pendingLeaveGrace = () -> {
            pendingLeaveGrace = null;
            cancelPendingEdgeScroll();
        };
        edgeScrollHandler.postDelayed(pendingLeaveGrace, 150);
    }

    private void cancelLeaveGrace() {
        if (pendingLeaveGrace != null) {
            edgeScrollHandler.removeCallbacks(pendingLeaveGrace);
            pendingLeaveGrace = null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Re-reads placed icons from GridPreferences and re-renders every
     * currently-bound page. Called on resume and whenever the app drawer
     * is dismissed back to this screen (in case a drag/drop just happened).
     */
    public void refreshGrid() {
        if (pagesAdapter != null) {
            pagesAdapter.setPageCount(GridPreferences.getPageCount(requireContext()));
            pagesAdapter.notifyDataSetChanged(); // forces bound pages to reload from prefs
        }
    }

    /** Hold-without-moving on a Home icon calls back here (see HomeGridAdapter). */
    private void showHomeContextMenu(AppInfo app) {
        new AlertDialog.Builder(requireContext())
                .setTitle(app.label)
                .setItems(new String[]{"App Info", "Remove from Home", "Uninstall"},
                        (dialog, which) -> {
                            switch (which) {
                                case 0: appManager.openAppInfo(requireContext(), app); break;
                                case 1:
                                    GridPreferences.removeApp(requireContext(), app.packageName);
                                    refreshGrid();
                                    break;
                                case 2: appManager.requestUninstall(requireContext(), app); break;
                            }
                        })
                .show();
    }

    /**
     * Binds one dock slot. If a previous session left an override saved
     * (via DockPreferences — no longer settable from the UI, see below),
     * that app wins. Otherwise resolves whichever app the device would
     * actually launch for the given system action (dial/camera/gallery/
     * contacts), same as stock Android. Long-press shows the same App
     * Info / Uninstall menu as any other icon — no more "choose app for
     * this slot" picker; that's not how dock icons work on a normal
     * launcher, so it's gone.
     */
    private void bindDockSlot(View root, int iconId, int labelId, String dockKey, Intent... probes) {
        ImageView icon = root.findViewById(iconId);
        if (icon == null) return;
        TextView label = root.findViewById(labelId);

        String overridePkg = DockPreferences.getSlot(requireContext(), dockKey);
        AppInfo overrideApp = overridePkg != null ? findInstalledApp(overridePkg) : null;

        if (overridePkg != null && overrideApp == null) {
            // Previously chosen app was uninstalled since — drop the stale
            // override and fall back to default resolution below.
            DockPreferences.clearSlot(requireContext(), dockKey);
        }

        AppInfo resolvedApp; // whichever app this slot actually points at, for the long-press menu

        if (overrideApp != null) {
            icon.setImageDrawable(overrideApp.icon);
            if (label != null) label.setText(overrideApp.label);
            icon.setOnClickListener(v -> appManager.launchApp(requireContext(), overrideApp));
            resolvedApp = overrideApp;
        } else {
            Drawable realIcon = appManager.resolveIconFor(probes);
            if (realIcon != null) icon.setImageDrawable(realIcon);
            String realLabel = appManager.resolveLabelFor(probes);
            if (realLabel != null && label != null) label.setText(realLabel);
            String realPackage = appManager.resolvePackageFor(probes);

            Intent explicit = appManager.resolveExplicitIntent(probes);
            icon.setOnClickListener(v ->
                    startActivity(explicit != null ? explicit : probes[0]));

            resolvedApp = (realPackage != null && realLabel != null)
                    ? new AppInfo(realLabel, realPackage, null, icon.getDrawable())
                    : null;
        }

        final AppInfo menuApp = resolvedApp;
        icon.setOnLongClickListener(v -> {
            if (menuApp != null) showDockContextMenu(menuApp);
            return true;
        });
    }

    /** Long-press menu for a dock slot — same App Info / Uninstall as Home icons. */
    private void showDockContextMenu(AppInfo app) {
        new AlertDialog.Builder(requireContext())
                .setTitle(app.label)
                .setItems(new String[]{"App Info", "Uninstall"},
                        (dialog, which) -> {
                            switch (which) {
                                case 0: appManager.openAppInfo(requireContext(), app); break;
                                case 1: appManager.requestUninstall(requireContext(), app); break;
                            }
                        })
                .show();
    }

    private AppInfo findInstalledApp(String packageName) {
        for (AppInfo a : appManager.loadInstalledApps()) {
            if (a.packageName.equals(packageName)) return a;
        }
        return null;
    }
}