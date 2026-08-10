package com.tejyash.myadapto.adapter;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tejyash.myadapto.manager.AppManager;

/**
 * Backs the Home screen's ViewPager2 — each page is its own RecyclerView
 * with its own HomeGridAdapter (see HomeGridAdapter, one page's worth of
 * placed apps). Pages are created on demand as the user swipes; page count
 * grows via setPageCount() as GridPreferences reports more pages in use.
 *
 * onPageBoundListener hands back each page's (page index, HomeGridAdapter,
 * RecyclerView) as it's bound, so HomeFragment can register a native
 * drag-and-drop listener on that page's RecyclerView — this is how apps
 * dragged from the drawer land on whichever Home page is visible.
 */
public class HomePagesAdapter extends RecyclerView.Adapter<HomePagesAdapter.PageViewHolder> {

    public interface OnPageBoundListener {
        void onPageBound(int page, HomeGridAdapter gridAdapter, RecyclerView recyclerView);
    }

    private final Context context;
    private final AppManager appManager;
    private int pageCount;

    private HomeGridAdapter.OnAppClickListener clickListener;
    private HomeGridAdapter.OnAppMenuRequestListener menuRequestListener;
    private OnPageBoundListener pageBoundListener;

    public HomePagesAdapter(Context ctx, AppManager appManager, int pageCount) {
        this.context = ctx;
        this.appManager = appManager;
        this.pageCount = Math.max(1, pageCount);
    }

    /**
     * Call after Home's layout re-evaluates how many pages are needed.
     *
     * Uses notifyItemRangeInserted for growth instead of a blanket
     * notifyDataSetChanged() — the latter tears down and rebuilds EVERY
     * currently-bound page, including whichever one the user is actively
     * looking at (e.g. the one they just successfully dropped an icon
     * onto), which can make a freshly-placed icon flicker or appear to
     * vanish right after landing. Growing by one page (the common case —
     * placing the very first icon grows page count 1→2) now only touches
     * the new page; the page you're looking at is left completely alone.
     */
    public void setPageCount(int count) {
        count = Math.max(1, count);
        if (count == this.pageCount) return;

        int old = this.pageCount;
        this.pageCount = count;

        if (count > old) {
            notifyItemRangeInserted(old, count - old);
        } else {
            notifyDataSetChanged(); // shrinking removes pages from the middle/end — full refresh is simplest here
        }
    }

    public void setOnAppClickListener(HomeGridAdapter.OnAppClickListener l) {
        this.clickListener = l;
    }

    public void setOnPageBoundListener(OnPageBoundListener l) {
        this.pageBoundListener = l;
    }

    public void setOnAppMenuRequestListener(HomeGridAdapter.OnAppMenuRequestListener l) {
        this.menuRequestListener = l;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView rv = new RecyclerView(parent.getContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setClipToPadding(false);
        return new PageViewHolder(rv);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        HomeGridAdapter gridAdapter = new HomeGridAdapter(context, position);
        holder.recyclerView.setLayoutManager(new GridLayoutManager(context, gridAdapter.getColumns()));
        holder.recyclerView.setAdapter(gridAdapter);
        gridAdapter.setOnAppClickListener(clickListener);
        gridAdapter.setOnAppMenuRequestListener(menuRequestListener);
        gridAdapter.loadFromPrefs(appManager);
        holder.gridAdapter = gridAdapter;

        if (pageBoundListener != null) {
            pageBoundListener.onPageBound(position, gridAdapter, holder.recyclerView);
        }
    }

    @Override
    public int getItemCount() {
        return pageCount;
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        final RecyclerView recyclerView;
        HomeGridAdapter gridAdapter;

        PageViewHolder(RecyclerView itemView) {
            super(itemView);
            this.recyclerView = itemView;
        }
    }
}