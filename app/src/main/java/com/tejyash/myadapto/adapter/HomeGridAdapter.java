package com.tejyash.myadapto.adapter;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityPreferences;
import com.tejyash.myadapto.launcher.GridModel;
import com.tejyash.myadapto.launcher.GridPreferences;
import com.tejyash.myadapto.manager.AppManager;
import com.tejyash.myadapto.model.AppInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RecyclerView adapter for ONE Home page's grid — the apps the user has
 * placed on that specific page, at fixed positions, with real empty cells
 * in between. One instance per page (see HomePagesAdapter, which creates
 * one of these per page inside the Home ViewPager2).
 *
 * Separate from AppGridAdapter (the app drawer): the drawer shows every
 * installed app densely, this shows only placed apps sparsely.
 *
 * Long-press on a placed icon is a two-stage gesture, matching a normal
 * launcher:
 *   - Hold, then MOVE your finger → picks the icon up for a real native
 *     drag (reposition on this page, drag to an edge for another page —
 *     see HomeFragment — or drop on the "Remove from Home" pill).
 *   - Hold WITHOUT moving, long enough → shows the context menu (App
 *     Info / Remove from Home / Uninstall) instead.
 *   - Release quickly with no real hold → a normal tap, launches the app.
 * See CellViewHolder's raw touch handling below for how these are told apart.
 */
public class HomeGridAdapter extends RecyclerView.Adapter<HomeGridAdapter.CellViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    /** Fired when a hold-without-moving completes — HomeFragment builds the actual menu. */
    public interface OnAppMenuRequestListener {
        void onShowMenu(AppInfo app);
    }

    /** Custom clip label so drop handlers can tell "moving an existing Home
     *  icon" apart from "adding a new one from the drawer" (see AppsFragment,
     *  which uses plain text/plain for adds). Only reposition drags should
     *  reveal the "Remove from Home" pill. */
    public static final String MOVE_CLIP_LABEL = "adapto_move";

    // How much longer (beyond the system long-press timeout) you have to
    // keep holding, perfectly still, before the menu appears instead of a drag.
    private static final long MENU_EXTRA_HOLD_MS = 350;

    private final Context context;
    private final AccessibilityPreferences prefs;
    private final int columns;
    private final int page;

    // Flattened row-major list of slots for THIS page only; null = empty cell.
    private final List<AppInfo> slots = new ArrayList<>();
    private OnAppClickListener clickListener;
    private OnAppMenuRequestListener menuRequestListener;

    public HomeGridAdapter(Context ctx, int page) {
        this.context = ctx.getApplicationContext();
        this.prefs   = AccessibilityPreferences.get(ctx);
        this.columns = GridPreferences.getColumns(ctx);
        this.page    = page;

        for (int i = 0; i < GridPreferences.ROWS_PER_PAGE * columns; i++) slots.add(null);
    }

    public void setOnAppClickListener(OnAppClickListener l) {
        this.clickListener = l;
    }

    public void setOnAppMenuRequestListener(OnAppMenuRequestListener l) {
        this.menuRequestListener = l;
    }

    public int getColumns() {
        return columns;
    }

    public int getPage() {
        return page;
    }

    /** Rebuilds this page's grid from GridPreferences + currently installed apps. */
    public void loadFromPrefs(AppManager appManager) {
        List<GridModel> placed = GridPreferences.loadPage(context, page);

        Map<String, AppInfo> byPackage = new HashMap<>();
        for (AppInfo a : appManager.loadInstalledApps()) {
            byPackage.put(a.packageName, a);
        }

        for (int i = 0; i < slots.size(); i++) slots.set(i, null);

        for (GridModel g : placed) {
            AppInfo app = byPackage.get(g.packageName);
            if (app == null) continue; // uninstalled since being placed
            int index = g.row * columns + g.col;
            if (index >= 0 && index < slots.size()) slots.set(index, app);
        }

        notifyDataSetChanged();
    }

    /**
     * Accepts a native drag-and-drop drop onto this page at (row,col).
     * Delegates the actual placement logic (move-not-duplicate, fallback
     * to nearest empty cell) to GridPreferences.placeApp, then reloads.
     */
    public void acceptDrop(String packageName, AppManager appManager, int row, int col) {
        GridPreferences.placeApp(context, packageName, page, row, col);
        loadFromPrefs(appManager);
    }

    @NonNull
    @Override
    public CellViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_grid, parent, false);
        return new CellViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CellViewHolder holder, int position) {
        AppInfo app = slots.get(position);
        holder.bind(app, prefs.getFontSizeSp(), prefs.getIconSizeDp());
        holder.setup(app, clickListener, menuRequestListener);
    }

    @Override
    public int getItemCount() {
        return slots.size();
    }

    static class CellViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView  tvLabel;
        final TextView  tvBadge;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final int touchSlop;
        private final long longPressMs;

        private float downX, downY;
        private boolean moved;
        private boolean dragArmed;   // system long-press timeout has fired
        private Runnable armDragRunnable;
        private Runnable showMenuRunnable;

        CellViewHolder(View itemView) {
            super(itemView);
            ivIcon  = itemView.findViewById(R.id.iv_app_icon);
            tvLabel = itemView.findViewById(R.id.tv_app_label);
            tvBadge = itemView.findViewById(R.id.tv_badge);
            touchSlop = ViewConfiguration.get(itemView.getContext()).getScaledTouchSlop();
            longPressMs = ViewConfiguration.getLongPressTimeout();
        }

        /**
         * Raw touch handling replaces plain click/long-click listeners so we
         * can tell "hold then drag" apart from "hold and keep holding" —
         * something a single OnLongClickListener can't distinguish, since it
         * only fires once at the long-press timeout with no way to know
         * what happens next.
         */
        void setup(@Nullable AppInfo app, OnAppClickListener clickListener,
                   OnAppMenuRequestListener menuRequestListener) {
            if (app == null) {
                itemView.setOnTouchListener(null);
                itemView.setOnClickListener(null);
                return;
            }

            itemView.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        moved = false;
                        dragArmed = false;
                        cancelPending();

                        armDragRunnable = () -> dragArmed = true;
                        handler.postDelayed(armDragRunnable, longPressMs);

                        showMenuRunnable = () -> {
                            if (!moved && menuRequestListener != null) {
                                menuRequestListener.onShowMenu(app);
                            }
                        };
                        handler.postDelayed(showMenuRunnable, longPressMs + MENU_EXTRA_HOLD_MS);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (moved) return true;
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            moved = true;
                            cancelPending();
                            if (dragArmed) {
                                startRepositionDrag(app, v);
                            }
                            // Moved before the long-press even armed (a stray
                            // flick) — not a valid gesture, just drop it; the
                            // eventual ACTION_UP won't fire a click since the
                            // view never registered a proper click-worthy tap.
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        cancelPending();
                        if (!moved) {
                            // Released before either timer fired, or fired but
                            // still didn't move — a genuine quick tap.
                            boolean wasQuickTap = !dragArmed;
                            if (wasQuickTap && clickListener != null) {
                                clickListener.onAppClick(app);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        cancelPending();
                        return true;

                    default:
                        return false;
                }
            });
        }

        private void cancelPending() {
            if (armDragRunnable != null)  handler.removeCallbacks(armDragRunnable);
            if (showMenuRunnable != null) handler.removeCallbacks(showMenuRunnable);
        }

        /**
         * Picks the icon up for repositioning — same native drag-and-drop
         * mechanism the drawer uses to add icons (AppsFragment.startDragForApp),
         * just tagged with MOVE_CLIP_LABEL so HomeFragment knows to reveal the
         * "Remove from Home" pill and treat the drop as clearing this slot first.
         */
        private void startRepositionDrag(AppInfo app, View itemView) {
            ClipData.Item item = new ClipData.Item(app.packageName);
            ClipData dragData = new ClipData(
                    HomeGridAdapter.MOVE_CLIP_LABEL,
                    new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN},
                    item);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(itemView);
            itemView.startDragAndDrop(dragData, shadow, app, 0);
        }

        void bind(@Nullable AppInfo app, float fontSp, int iconDp) {
            int px = dpToPx(itemView.getContext(), iconDp);
            ViewGroup.LayoutParams lp = ivIcon.getLayoutParams();
            lp.width  = px;
            lp.height = px;
            ivIcon.setLayoutParams(lp);

            if (app == null) {
                ivIcon.setImageDrawable(null);
                tvLabel.setText("");
                if (tvBadge != null) tvBadge.setVisibility(View.GONE);
                itemView.setClickable(false);
                itemView.setContentDescription("Empty");
                return;
            }

            ivIcon.setImageDrawable(app.icon);
            tvLabel.setText(app.label);
            tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);
            itemView.setClickable(true);
            itemView.setContentDescription(app.label);

            if (tvBadge != null) {
                int count = com.tejyash.myadapto.notifications.NotificationBadgeStore
                        .getCount(itemView.getContext(), app.packageName);
                if (count > 0) {
                    tvBadge.setVisibility(View.VISIBLE);
                    tvBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                } else {
                    tvBadge.setVisibility(View.GONE);
                }
            }
        }

        private static int dpToPx(Context ctx, int dp) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp,
                    ctx.getResources().getDisplayMetrics()));
        }
    }
}
