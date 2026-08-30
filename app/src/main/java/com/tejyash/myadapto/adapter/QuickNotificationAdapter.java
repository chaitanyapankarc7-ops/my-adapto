package com.tejyash.myadapto.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.notifications.AdaptoNotificationListenerService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickNotificationAdapter extends RecyclerView.Adapter<QuickNotificationAdapter.ViewHolder> {

    private final List<AdaptoNotificationListenerService.NotificationItem> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public void setItems(List<AdaptoNotificationListenerService.NotificationItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quick_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdaptoNotificationListenerService.NotificationItem item = items.get(position);
        holder.tvApp.setText(!item.appName.isEmpty() ? item.appName : item.packageName);
        holder.tvTime.setText(timeFormat.format(new Date(item.postTime > 0 ? item.postTime : System.currentTimeMillis())));
        
        if (!item.title.isEmpty()) {
            holder.tvTitle.setVisibility(View.VISIBLE);
            holder.tvTitle.setText(item.title);
        } else {
            holder.tvTitle.setVisibility(View.GONE);
        }

        if (!item.text.isEmpty()) {
            holder.tvText.setVisibility(View.VISIBLE);
            holder.tvText.setText(item.text);
        } else {
            holder.tvText.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvApp, tvTime, tvTitle, tvText;

        ViewHolder(View itemView) {
            super(itemView);
            tvApp = itemView.findViewById(R.id.tv_notif_app);
            tvTime = itemView.findViewById(R.id.tv_notif_time);
            tvTitle = itemView.findViewById(R.id.tv_notif_title);
            tvText = itemView.findViewById(R.id.tv_notif_text);
        }
    }
}
