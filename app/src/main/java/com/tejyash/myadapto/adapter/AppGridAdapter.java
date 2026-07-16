package com.tejyash.myadapto.adapter;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.AccessibilityPreferences;
import com.tejyash.myadapto.model.AppInfo;

/**
 * RecyclerView adapter for the home screen app grid.
 *
 * Reads font size and icon size from AccessibilityPreferences on every bind,
 * so calling notifyResized() after a pref change updates all visible cells instantly.
 */
public class AppGridAdapter extends RecyclerView.Adapter<AppGridAdapter.AppViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    private final List<AppInfo>            apps = new ArrayList<>();
    private final AccessibilityPreferences prefs;
    private OnAppClickListener             clickListener;

    public AppGridAdapter(Context ctx) {
        this.prefs = AccessibilityPreferences.get(ctx);
    }

    public void setOnAppClickListener(OnAppClickListener l) {
        this.clickListener = l;
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
        holder.bind(app, prefs.getFontSizeSp(), prefs.getIconSizeDp());
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onAppClick(app);
        });
    }

    @Override
    public int getItemCount() { return apps.size(); }

    // ── ViewHolder ──────────────────────────────────────────────────
    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView  tvLabel;

        AppViewHolder(View itemView) {
            super(itemView);
            ivIcon  = itemView.findViewById(R.id.iv_app_icon);
            tvLabel = itemView.findViewById(R.id.tv_app_label);
        }

        void bind(AppInfo app, float fontSp, int iconDp) {
            tvLabel.setText(app.label);
            ivIcon.setImageDrawable(app.icon);

            // Apply font size
            tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp);

            // Apply icon size (convert dp → px)
            int px = dpToPx(itemView.getContext(), iconDp);
            ViewGroup.LayoutParams lp = ivIcon.getLayoutParams();
            lp.width  = px;
            lp.height = px;
            ivIcon.setLayoutParams(lp);
        }

        private static int dpToPx(Context ctx, int dp) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp,
                    ctx.getResources().getDisplayMetrics()));
        }
    }
}
