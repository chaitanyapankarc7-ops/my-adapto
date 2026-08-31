package com.tejyash.myadapto.fregment;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.tejyash.myadapto.launcher.LauncherItemType;

import android.view.DragEvent;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityManager;
import com.tejyash.myadapto.adapter.HomeGridAdapter;
import com.tejyash.myadapto.adapter.HomePagesAdapter;
import com.tejyash.myadapto.launcher.DockPreferences;
import com.tejyash.myadapto.launcher.GridModel;
import com.tejyash.myadapto.launcher.GridPreferences;
import com.tejyash.myadapto.manager.AppManager;
import com.tejyash.myadapto.model.AppInfo;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import com.tejyash.myadapto.adapter.QuickNotificationAdapter;
import com.tejyash.myadapto.notifications.AdaptoNotificationListenerService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment
        implements AccessibilityManager.OnAccessibilityChangedListener {

    private AppManager      appManager;
    private AccessibilityManager accessibilityManager;
    private HomePagesAdapter pagesAdapter;
    private ViewPager2      pager;
    private View            removePill;

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

    private boolean isMenuShowing = false;

    // Quick Controls & Pull-Down Notification Panel
    private View quickControlsPanel;
    private View panelScrim;
    private View notificationPullHandle;
    private QuickNotificationAdapter quickNotifAdapter;
    private boolean isTorchOn = false;
    private CameraManager cameraManager;
    private String flashCameraId = null;
    private AdaptoNotificationListenerService.OnNotificationsChangedListener notifListener;

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
        accessibilityManager = new AccessibilityManager(requireContext());
        appManager.warmCacheAsync(this::refreshGrid);

        setupDock(view);
        setGreeting(view.findViewById(R.id.home_greeting));
        setupHomeGrid(view);
        setupQuickControlsPanel(view);
        
        // Floating Adaptive Features Button removed as per user request

        // Use custom hold logic for the background to match the hold requirement
        setupBackgroundHold(view);
        setupBackgroundHold(view.findViewById(R.id.dock_container));

        // Add default Clock widget if Home is empty
        if (GridPreferences.loadAll(requireContext()).isEmpty()) {
            GridPreferences.placeWidget(requireContext(), LauncherItemType.CLOCK, 0, 0, 0);
            refreshGrid();
        }
        
        ensureDefaultWidgets();
    }

    // ── Quick Controls & Notification Pull-Down Panel ───────────────
    private void setupQuickControlsPanel(View root) {
        quickControlsPanel = root.findViewById(R.id.quick_controls_panel);
        panelScrim = root.findViewById(R.id.panel_scrim);
        notificationPullHandle = root.findViewById(R.id.notification_pull_handle);

        if (notificationPullHandle != null) {
            notificationPullHandle.setOnClickListener(v -> openPullDownPanel());
        }

        if (panelScrim != null) {
            panelScrim.setOnClickListener(v -> closePullDownPanel());
        }

        View btnClose = root.findViewById(R.id.btn_close_panel);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> closePullDownPanel());
        }

        View bottomHandle = root.findViewById(R.id.panel_bottom_handle);
        if (bottomHandle != null) {
            bottomHandle.setOnClickListener(v -> closePullDownPanel());
        }

        // Initialize CameraManager for Flashlight tile
        try {
            cameraManager = (CameraManager) requireContext().getSystemService(android.content.Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                    Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (hasFlash != null && hasFlash) {
                        flashCameraId = id;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Setup Flashlight Tile
        View tileFlashlight = root.findViewById(R.id.tile_flashlight);
        TextView tvFlashlightStatus = root.findViewById(R.id.tv_flashlight_status);
        ImageView ivFlashlightIcon = root.findViewById(R.id.iv_flashlight_icon);
        if (tileFlashlight != null) {
            tileFlashlight.setOnClickListener(v -> toggleTorch(tileFlashlight, tvFlashlightStatus, ivFlashlightIcon));
        }

        // Setup Internet Tile
        View tileInternet = root.findViewById(R.id.tile_internet);
        if (tileInternet != null) {
            tileInternet.setOnClickListener(v -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        startActivity(new Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
                    } else {
                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                    }
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                    } catch (Exception ignored) {}
                }
            });
        }

        // Setup Volume Tile
        View tileVolume = root.findViewById(R.id.tile_volume);
        if (tileVolume != null) {
            tileVolume.setOnClickListener(v -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        startActivity(new Intent(android.provider.Settings.Panel.ACTION_VOLUME));
                    } else {
                        startActivity(new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS));
                    }
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS));
                    } catch (Exception ignored) {}
                }
            });
        }

        // Setup Bluetooth Tile
        View tileBluetooth = root.findViewById(R.id.tile_bluetooth);
        if (tileBluetooth != null) {
            tileBluetooth.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (Exception ignored) {}
            });
        }

        // Setup Notifications RecyclerView
        RecyclerView rvNotifications = root.findViewById(R.id.rv_quick_notifications);
        if (rvNotifications != null) {
            rvNotifications.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
            quickNotifAdapter = new QuickNotificationAdapter();
            rvNotifications.setAdapter(quickNotifAdapter);
        }

        // Setup Grant Notification Access button
        View btnGrantAccess = root.findViewById(R.id.btn_grant_notif_access);
        if (btnGrantAccess != null) {
            btnGrantAccess.setOnClickListener(v -> {
                startActivity(com.tejyash.myadapto.notifications.NotificationAccessHelper.settingsIntent());
            });
        }

        notifListener = this::refreshQuickPanelNotifications;
    }

    private void toggleTorch(View tile, TextView statusText, ImageView icon) {
        if (cameraManager == null || flashCameraId == null) {
            Toast.makeText(requireContext(), "Flashlight not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            isTorchOn = !isTorchOn;
            cameraManager.setTorchMode(flashCameraId, isTorchOn);

            if (statusText != null) {
                statusText.setText(isTorchOn ? "On" : "Off");
                statusText.setTextColor(isTorchOn ? android.graphics.Color.parseColor("#93C5FD") : android.graphics.Color.parseColor("#9CA3AF"));
            }
            if (tile != null) {
                tile.setBackgroundColor(isTorchOn ? android.graphics.Color.parseColor("#1E3A8A") : android.graphics.Color.parseColor("#25253B"));
            }
            if (icon != null) {
                icon.setColorFilter(isTorchOn ? android.graphics.Color.parseColor("#60A5FA") : android.graphics.Color.WHITE);
            }
        } catch (Exception e) {
            isTorchOn = false;
            Toast.makeText(requireContext(), "Could not toggle flashlight", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isPullDownPanelOpen() {
        return quickControlsPanel != null && quickControlsPanel.getVisibility() == View.VISIBLE;
    }

    public void openPullDownPanel() {
        if (quickControlsPanel == null || isPullDownPanelOpen()) return;

        // Update DateTime text
        if (getView() != null) {
            TextView tvDateTime = getView().findViewById(R.id.tv_panel_datetime);
            if (tvDateTime != null) {
                String dateStr = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(new Date());
                String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
                tvDateTime.setText(timeStr + " • " + dateStr);
            }
        }

        refreshQuickPanelNotifications();
        AdaptoNotificationListenerService.registerListener(notifListener);

        quickControlsPanel.setVisibility(View.VISIBLE);
        quickControlsPanel.setTranslationY(-800f);
        quickControlsPanel.animate()
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        if (panelScrim != null) {
            panelScrim.setVisibility(View.VISIBLE);
            panelScrim.setAlpha(0f);
            panelScrim.animate().alpha(1f).setDuration(280).start();
        }
    }

    public void closePullDownPanel() {
        if (quickControlsPanel == null || !isPullDownPanelOpen()) return;

        AdaptoNotificationListenerService.unregisterListener(notifListener);

        quickControlsPanel.animate()
                .translationY(-800f)
                .setDuration(240)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    if (quickControlsPanel != null) {
                        quickControlsPanel.setVisibility(View.GONE);
                    }
                })
                .start();

        if (panelScrim != null) {
            panelScrim.animate()
                    .alpha(0f)
                    .setDuration(240)
                    .withEndAction(() -> {
                        if (panelScrim != null) {
                            panelScrim.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private void refreshQuickPanelNotifications() {
        if (getView() == null || !isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            View root = getView();
            if (root == null) return;

            boolean hasAccess = AdaptoNotificationListenerService.isNotificationAccessGranted(requireContext());
            View layoutPerm = root.findViewById(R.id.layout_notif_permission);
            RecyclerView rvNotifs = root.findViewById(R.id.rv_quick_notifications);
            TextView tvEmpty = root.findViewById(R.id.tv_no_notifications);

            TextView tvHeaderPill = root.findViewById(R.id.tv_header_pill_title);

            if (!hasAccess) {
                if (layoutPerm != null) layoutPerm.setVisibility(View.VISIBLE);
                if (rvNotifs != null) rvNotifs.setVisibility(View.GONE);
                if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                if (tvHeaderPill != null) tvHeaderPill.setText("Quick Panel");
            } else {
                if (layoutPerm != null) layoutPerm.setVisibility(View.GONE);
                List<AdaptoNotificationListenerService.NotificationItem> notifs =
                        AdaptoNotificationListenerService.getRecentNotifications();

                if (notifs.isEmpty()) {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    if (rvNotifs != null) rvNotifs.setVisibility(View.GONE);
                    if (tvHeaderPill != null) tvHeaderPill.setText("Quick Panel");
                } else {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                    if (rvNotifs != null) rvNotifs.setVisibility(View.VISIBLE);
                    if (tvHeaderPill != null) tvHeaderPill.setText("Quick Panel • " + notifs.size() + " new");
                    if (quickNotifAdapter != null) {
                        quickNotifAdapter.setItems(notifs);
                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        accessibilityManager.setListener(this);
        refreshGrid(); // picks up notification badges that changed while another app was open
        promptNotificationAccessIfNeeded();
    }

    /**
     * Fired whenever icon size / font size changes (e.g. from
     * SizeEditingPage). GridPreferences' column count is already updated
     * by the time this fires (see AccessibilityManager), so a plain
     * refreshGrid() rebuilds every page's GridLayoutManager with the new
     * span count — this is what makes the Home grid actually resize
     * instead of just the app drawer.
     */
    @Override
    public void onAccessibilityChanged() {
        refreshGrid();
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
        accessibilityManager.clearListener();
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
        pagesAdapter.setOnWidgetMenuRequestListener(this::showWidgetContextMenu);
        pagesAdapter.setOnEmptySpaceLongClickListener(this::showEditMenu);

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
            swipeZone.setOnSwipeDownListener(this::openPullDownPanel);

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

          // "Remove from Home" drop target
        removePill = root.findViewById(R.id.remove_from_home_pill);
        if (removePill != null) {
            removePill.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        boolean isMove = event.getClipDescription() != null
                                && (HomeGridAdapter.MOVE_CLIP_LABEL.equals(event.getClipDescription().getLabel().toString())
                                || HomeGridAdapter.WIDGET_CLIP_LABEL.equals(event.getClipDescription().getLabel().toString()));
                        if (isMove) removePill.setVisibility(View.VISIBLE);
                        return true;

                    case DragEvent.ACTION_DROP:
                        if (event.getClipData() == null || event.getClipData().getItemCount() == 0) return false;
                        String label = event.getClipDescription() != null ? event.getClipDescription().getLabel().toString() : "";
                        String text = String.valueOf(event.getClipData().getItemAt(0).getText());

                        if (HomeGridAdapter.MOVE_CLIP_LABEL.equals(label)) {
                            GridPreferences.removeApp(requireContext(), text);
                            refreshGrid();
                            return true;
                        } else if (HomeGridAdapter.WIDGET_CLIP_LABEL.equals(label)) {
                            try {
                                LauncherItemType type = LauncherItemType.valueOf(text);
                                GridPreferences.removeWidget(requireContext(), type);
                                refreshGrid();
                                return true;
                            } catch (Exception e) {
                                return false;
                            }
                        }
                        return false;

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
                if (pager != null) pager.setUserInputEnabled(false); // Lock pager during drag
                android.util.Log.d("HomeFragment", "ACTION_DRAG_STARTED on page " + gridAdapter.getPage());
                return true; // must return true here to keep receiving events for this drag

            case DragEvent.ACTION_DRAG_LOCATION: {
                // Hovering near either edge for a moment reveals the
                // next/previous page (creating a new one on the right if
                // needed) — matches dragging an icon off either side of the
                // screen to move it between pages on a normal launcher.
                float rightEdge = recyclerView.getWidth() * 0.75f; // last ~25% of width
                float leftEdge  = recyclerView.getWidth() * 0.25f; // first ~25% of width
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
                String label = event.getClipDescription() != null ? event.getClipDescription().getLabel().toString() : "";
                String content = String.valueOf(event.getClipData().getItemAt(0).getText());

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
                android.util.Log.d("HomeFragment", "placing " + content + " at row=" + row + " col=" + col + " label=" + label);

                if (HomeGridAdapter.MOVE_CLIP_LABEL.equals(label)) {
                    gridAdapter.acceptDrop(content, appManager, row, col);
                } else if (HomeGridAdapter.WIDGET_CLIP_LABEL.equals(label)) {
                    try {
                        LauncherItemType type = LauncherItemType.valueOf(content);
                        gridAdapter.acceptWidgetDrop(type, appManager, row, col);
                    } catch (Exception e) {
                        return false;
                    }
                } else {
                    // Drawer add (or unknown) — assume it's an app package name
                    gridAdapter.acceptDrop(content, appManager, row, col);
                }

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
                if (pager != null) pager.setUserInputEnabled(true); // Unlock pager
                cancelPendingEdgeScroll();
                // Ensure everything is opaque again after any drag
                refreshGrid();
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
        edgeScrollHandler.postDelayed(pendingEdgeScroll, 450);
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


    private void ensureDefaultWidgets() {
        if (!GridPreferences.hasWidget(requireContext(), LauncherItemType.CLOCK)) {
            int[] slot = GridPreferences.findFirstEmptySlot(requireContext());
            GridPreferences.placeWidget(requireContext(), LauncherItemType.CLOCK, slot[0], slot[1], slot[2]);
            refreshGrid();
        }
    }
    public void refreshGrid() {
        if (pagesAdapter != null) {
            pagesAdapter.setPageCount(GridPreferences.getPageCount(requireContext()));
            pagesAdapter.notifyDataSetChanged(); // forces bound pages to reload from prefs
        }
        
        applyThemeColors();
    }

    private void applyThemeColors() {
        if (getView() == null) return;
        boolean highContrast = com.tejyash.myadapto.accessibility.AccessibilityPreferences.get(requireContext()).isColorBlindEnabled();
        
        View root = getView().findViewById(R.id.home_swipe_root);
        View dock = getView().findViewById(R.id.dock_container);
        TextView greeting = getView().findViewById(R.id.home_greeting);
        
        if (highContrast) {
            if (root != null) root.setBackgroundColor(android.graphics.Color.BLACK);
            if (dock != null) dock.setBackgroundColor(android.graphics.Color.BLACK);
            if (greeting != null) {
                greeting.setTextColor(android.graphics.Color.WHITE);
                greeting.setTypeface(null, android.graphics.Typeface.BOLD);
                greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            }
        } else {
            if (root != null) root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            if (dock != null) dock.setBackgroundColor(android.graphics.Color.parseColor("#CC1A1A2E"));
            if (greeting != null) {
                greeting.setTextColor(android.graphics.Color.WHITE);
                greeting.setTypeface(null, android.graphics.Typeface.NORMAL);
                greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            }
        }
    }

    private void showHomeContextMenu(AppInfo app) {
        new AlertDialog.Builder(requireContext())
                .setTitle(app.label)
                .setItems(new String[]{"App Info", "Delete from Home", "Uninstall"},
                        (dialog, which) -> {
                            switch (which) {
                                case 0: appManager.openAppInfo(requireContext(), app); break;
                                case 1:
                                    GridPreferences.removeApp(requireContext(), app.packageName);
                                    refreshGrid();
                                    Toast.makeText(requireContext(), "App removed", Toast.LENGTH_SHORT).show();
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

    private void showWidgetContextMenu(LauncherItemType type) {
        new AlertDialog.Builder(requireContext())
                .setTitle(type.name())
                .setItems(new String[]{"Resize Widget", "Delete Widget", "Cancel"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showResizeDialog(type);
                            } else if (which == 1) {
                                GridPreferences.removeWidget(requireContext(), type);
                                refreshGrid();
                                Toast.makeText(requireContext(), "Widget deleted", Toast.LENGTH_SHORT).show();
                            }
                        })
                .show();
    }

    private void showResizeDialog(LauncherItemType type) {
        int columns = GridPreferences.getColumns(requireContext());
        String[] options = new String[columns];
        for (int i = 0; i < columns; i++) {
            options[i] = (i + 1) + " Column" + (i > 0 ? "s" : "");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Width")
                .setItems(options, (dialog, which) -> {
                    int newSpan = which + 1;
                    updateWidgetSpan(type, newSpan);
                })
                .show();
    }

    private void updateWidgetSpan(LauncherItemType type, int newSpan) {
        for (GridModel item : GridPreferences.loadAll(requireContext())) {
            if (item.type == type) {
                item.spanX = newSpan;
                // Displacement logic if it grows
                GridPreferences.saveSlot(requireContext(), item);
                refreshGrid();
                break;
            }
        }
    }

    private AppInfo findInstalledApp(String packageName) {
        if (appManager == null || packageName == null) return null;
        return appManager.findApp(packageName);
    }

    private void showEditMenu() {
        if (isMenuShowing) return;
        isMenuShowing = true;

        // Big vibration feedback for menu popup
        android.os.Vibrator vibrator = (android.os.Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            boolean bigVib = com.tejyash.myadapto.accessibility.AccessibilityPreferences.get(requireContext()).isBigVibrationEnabled();
            int duration = bigVib ? 300 : 50;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_home, null);
        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.setOnDismissListener(dialog -> isMenuShowing = false);

        view.findViewById(R.id.cardWallpaper).setOnClickListener(v -> {
            if (getActivity() instanceof com.tejyash.myadapto.launcher.HomeActivity) {
                ((com.tejyash.myadapto.launcher.HomeActivity) getActivity()).openWallpaperPicker();
            }
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.cardWidgets).setOnClickListener(v -> {
            showAddWidgetDialog();
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.cardSettings).setOnClickListener(v -> {
            if (getActivity() instanceof com.tejyash.myadapto.launcher.HomeActivity) {
                ((com.tejyash.myadapto.launcher.HomeActivity) getActivity()).openOverlay(
                        new AdaptiveFeaturesFragment(), "adaptive_features");
            }
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void showAddWidgetDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.TransparentDialog)
                .create();
        View view = getLayoutInflater().inflate(R.layout.dialog_add_widget, null);
        dialog.setView(view);

        view.findViewById(R.id.btn_add_clock).setOnClickListener(v -> {
            addWidgetToGrid(LauncherItemType.CLOCK);
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_add_battery).setOnClickListener(v -> {
            addWidgetToGrid(LauncherItemType.BATTERY);
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_add_weather).setOnClickListener(v -> {
            addWidgetToGrid(LauncherItemType.WEATHER);
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_more_tools).setOnClickListener(v -> {
            if (getActivity() instanceof com.tejyash.myadapto.launcher.HomeActivity) {
                ((com.tejyash.myadapto.launcher.HomeActivity) getActivity()).openOverlay(new WidgetsFragment(), "widgets");
            }
            dialog.dismiss();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void addWidgetToGrid(LauncherItemType type) {
        int currentPage = pager != null ? pager.getCurrentItem() : 0;
        int[] slot = GridPreferences.findFirstEmptySlotOnPage(requireContext(), currentPage);
        
        if (slot != null) {
            GridPreferences.placeWidget(requireContext(), type, currentPage, slot[0], slot[1]);
            Toast.makeText(requireContext(), type.name() + " added", Toast.LENGTH_SHORT).show();
        } else {
            int[] globalSlot = GridPreferences.findFirstEmptySlot(requireContext());
            if (globalSlot != null) {
                GridPreferences.placeWidget(requireContext(), type, globalSlot[0], globalSlot[1], globalSlot[2]);
            }
        }
        refreshGrid();
    }

    private void setupBackgroundHold(View v) {
        if (v == null) return;
        final Handler holdHandler = new Handler(Looper.getMainLooper());
        final Runnable holdRunnable = this::showEditMenu;
        final int touchSlop = android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
        final float[] downX = new float[1];
        final float[] downY = new float[1];

        v.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getX();
                    downY[0] = event.getY();
                    holdHandler.postDelayed(holdRunnable, 3000); // 3 seconds as requested
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - downX[0]);
                    float dy = Math.abs(event.getY() - downY[0]);
                    if (dx > touchSlop || dy > touchSlop) {
                        holdHandler.removeCallbacks(holdRunnable);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holdHandler.removeCallbacks(holdRunnable);
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    break;
            }
            return false;
        });
    }
}

