package com.tejyash.myadapto.adapter;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityPreferences;
import com.tejyash.myadapto.launcher.GridModel;
import com.tejyash.myadapto.launcher.GridPreferences;
import com.tejyash.myadapto.model.AppInfo;

/**
 * RecyclerView adapter for the home screen app grid.
 * Now supports drag-and-drop reordering with position persistence.
 */
public class AppGridAdapter extends RecyclerView.Adapter<AppGridAdapter.AppViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    public interface OnAppLongClickListener {
        void onAppLongClick(AppInfo app, View itemView);
    }

    private final List<AppInfo>            apps = new ArrayList<>();
    private final AccessibilityPreferences prefs;
    private final Context                  context;
    private OnAppClickListener             clickListener;
    private OnAppLongClickListener         longClickListener;

    // Which page of the home screen this adapter is showing (0 = Apps page)
    private final int page;

    public AppGridAdapter(Context ctx) {
        this(ctx, 0);
    }

    public AppGridAdapter(Context ctx, int page) {
        this.prefs   = AccessibilityPreferences.get(ctx);
        this.context = ctx;
        this.page    = page;
    }

    public void setOnAppClickListener(OnAppClickListener l) {
        this.clickListener = l;
    }

    public void setOnAppLongClickListener(OnAppLongClickListener l) {
        this.longClickListener = l;
    }

    public void setApps(List<AppInfo> newApps) {
        apps.clear();
        apps.addAll(newApps);
        notifyDataSetChanged();
    }

    /** Call after any pref change — re-binds all visible cells with new sizes. */
    public void notifyResized() {
        notifyItemRangeChanged(0, apps.size());
    }

    // ── Drag and drop ───────────────────────────────────────────────

    /**
     * Call this once after setting the adapter on your RecyclerView:
     *
     *   AppGridAdapter adapter = new AppGridAdapter(ctx);
     *   adapter.attachDragToRecyclerView(recyclerView);
     *   recyclerView.setAdapter(adapter);
     */
    public void attachDragToRecyclerView(RecyclerView rv) {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                // Drag directions: up, down, left, right
                ItemTouchHelper.UP | ItemTouchHelper.DOWN |
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                // Swipe directions: none (we don't want swipe-to-delete)
                0
        ) {
            // Called continuously while dragging — swaps items in the list live
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder dragged,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = dragged.getAdapterPosition();
                int to   = target.getAdapterPosition();
                if (from == RecyclerView.NO_ID || to == RecyclerView.NO_ID) return false;

                // Swap in the live list so the UI updates instantly
                Collections.swap(apps, from, to);
                notifyItemMoved(from, to);
                return true;
            }

            // Called when the user lifts their finger — save final positions
            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                saveAllPositions();

                // Remove the visual highlight from the dragged item
                viewHolder.itemView.setAlpha(1f);
                viewHolder.itemView.setScaleX(1f);
                viewHolder.itemView.setScaleY(1f);
            }

            // Visual feedback while dragging — shrink + dim the dragged icon
            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.setAlpha(0.75f);
                    viewHolder.itemView.animate()
                            .scaleX(1.12f)
                            .scaleY(1.12f)
                            .setDuration(150)
                            .start();
                }
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Not used — swipe disabled
            }

            // Only trigger drag on long press (not on tap)
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(rv);
    }

    /**
     * Saves the current list order to GridPreferences.
     * Called automatically after every drag completes.
     *
     * Grid layout:  row = position / columnCount
     *               col = position % columnCount
     */
    private void saveAllPositions() {
        int columns = GridPreferences.getColumns(context);

        // Clear old saved positions for this page first
        GridPreferences.clearAll(context);

        // Save each app at its new row/col
        for (int i = 0; i < apps.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            GridModel model = new GridModel(row, col, page, apps.get(i).packageName);
            GridPreferences.saveSlot(context, model);
        }
    }

    // ── RecyclerView boilerplate ────────────────────────────────────

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_grid, parent, false);
        return new AppViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.bind(app, prefs.getFontSizeSp(), prefs.getIconSizeDp(), prefs.isColorBlindEnabled());
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onAppClick(app);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onAppLongClick(app, v);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return apps.size(); }

    // ── ViewHolder ──────────────────────────────────────────────────
    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView  tvLabel;
        final TextView  tvBadge;

        AppViewHolder(View itemView) {
            super(itemView);
            ivIcon  = itemView.findViewById(R.id.iv_app_icon);
            tvLabel = itemView.findViewById(R.id.tv_app_label);
            tvBadge = itemView.findViewById(R.id.tv_badge);
        }

        void bind(AppInfo app, float fontSp, int iconDp, boolean highContrast) {
            tvLabel.setText(app.label);
            ivIcon.setImageDrawable(app.icon);

            tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);

            if (highContrast) {
                tvLabel.setTextColor(android.graphics.Color.WHITE);
                tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            } else {
                tvLabel.setTextColor(android.graphics.Color.WHITE);
                tvLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
                itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }

            int px = dpToPx(itemView.getContext(), iconDp);
            ViewGroup.LayoutParams lp = ivIcon.getLayoutParams();
            lp.width  = px;
            lp.height = px;
            ivIcon.setLayoutParams(lp);

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