package com.tejyash.myadapto.adapter;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tejyash.myadapto.launcher.LauncherItemType;
import com.tejyash.myadapto.manager.AppManager;

public class HomePagesAdapter
        extends RecyclerView.Adapter<HomePagesAdapter.PageViewHolder> {

    public interface OnPageBoundListener {
        void onPageBound(
                int page,
                HomeGridAdapter gridAdapter,
                RecyclerView recyclerView
        );
    }

    private final Context context;
    private final AppManager appManager;

    private int pageCount;

    private HomeGridAdapter.OnAppClickListener clickListener;
    private HomeGridAdapter.OnAppMenuRequestListener menuRequestListener;
    private HomeGridAdapter.OnWidgetMenuRequestListener widgetMenuRequestListener;
    private HomeGridAdapter.OnEmptySpaceLongClickListener emptySpaceLongClickListener;
    private OnPageBoundListener pageBoundListener;


    public HomePagesAdapter(
            Context ctx,
            AppManager appManager,
            int pageCount
    ) {

        this.context = ctx;
        this.appManager = appManager;

        this.pageCount = Math.max(1, pageCount);
    }


    // ─────────────────────────────────────────────
    // PAGE COUNT
    // ─────────────────────────────────────────────

    public void setPageCount(int count) {

        count = Math.max(1, count);

        if (count == this.pageCount) {
            return;
        }

        int oldCount = this.pageCount;

        this.pageCount = count;

        if (count > oldCount) {

            notifyItemRangeInserted(
                    oldCount,
                    count - oldCount
            );

        } else {

            notifyDataSetChanged();
        }
    }


    // ─────────────────────────────────────────────
    // LISTENERS
    // ─────────────────────────────────────────────

    public void setOnAppClickListener(
            HomeGridAdapter.OnAppClickListener listener
    ) {

        this.clickListener = listener;
    }


    public void setOnAppMenuRequestListener(
            HomeGridAdapter.OnAppMenuRequestListener listener
    ) {

        this.menuRequestListener = listener;
    }


    public void setOnWidgetMenuRequestListener(
            HomeGridAdapter.OnWidgetMenuRequestListener listener
    ) {

        this.widgetMenuRequestListener = listener;
    }

    public void setOnEmptySpaceLongClickListener(
            HomeGridAdapter.OnEmptySpaceLongClickListener listener
    ) {
        this.emptySpaceLongClickListener = listener;
    }


    public void setOnPageBoundListener(
            OnPageBoundListener listener
    ) {

        this.pageBoundListener = listener;
    }


    // ─────────────────────────────────────────────
    // CREATE PAGE
    // ─────────────────────────────────────────────

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        RecyclerView recyclerView =
                new RecyclerView(
                        parent.getContext()
                );

        recyclerView.setLayoutParams(
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        recyclerView.setClipToPadding(false);

        return new PageViewHolder(recyclerView);
    }


    // ─────────────────────────────────────────────
    // BIND PAGE
    // ─────────────────────────────────────────────

    @Override
    public void onBindViewHolder(
            @NonNull PageViewHolder holder,
            int position
    ) {

        /*
         * Every ViewPager page gets its own
         * HomeGridAdapter.
         *
         * IMPORTANT:
         *
         * Apps AND widgets are rendered by
         * HomeGridAdapter.
         *
         * Therefore widgets are physically
         * inside the ViewPager page.
         */

        HomeGridAdapter gridAdapter =
                new HomeGridAdapter(
                        context,
                        position
                );


        // ─────────────────────────────────────
        // GRID
        // ─────────────────────────────────────

        GridLayoutManager layoutManager =
                new GridLayoutManager(
                        context,
                        gridAdapter.getColumns()
                );

        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                boolean highContrast = com.tejyash.myadapto.accessibility.AccessibilityPreferences.get(context).isColorBlindEnabled();
                if (highContrast) {
                    int viewType = gridAdapter.getItemViewType(position);
                    if (viewType == LauncherItemType.CLOCK.ordinal() ||
                        viewType == LauncherItemType.BATTERY.ordinal() ||
                        viewType == LauncherItemType.WEATHER.ordinal()) {
                        return 2; // Span 2 blocks in high contrast for visibility
                    }
                }
                return 1;
            }
        });


        holder.recyclerView.setLayoutManager(
                layoutManager
        );


        // ─────────────────────────────────────
        // ADAPTER
        // ─────────────────────────────────────

        holder.recyclerView.setAdapter(
                gridAdapter
        );


        // ─────────────────────────────────────
        // APP CLICK
        // ─────────────────────────────────────

        gridAdapter.setOnAppClickListener(
                clickListener
        );


        // ─────────────────────────────────────
        // APP LONG PRESS MENU
        // ─────────────────────────────────────

        gridAdapter.setOnAppMenuRequestListener(
                menuRequestListener
        );


        // ─────────────────────────────────────
        // WIDGET LONG PRESS MENU
        // ─────────────────────────────────────

        gridAdapter.setOnWidgetMenuRequestListener(
                widgetMenuRequestListener
        );


        // ─────────────────────────────────────
        // EMPTY SPACE LONG CLICK
        // ─────────────────────────────────────

        gridAdapter.setOnEmptySpaceLongClickListener(
                emptySpaceLongClickListener
        );


        // ─────────────────────────────────────
        // LOAD EVERYTHING
        // ─────────────────────────────────────
        //
        // This loads:
        //
        // APP
        // CLOCK
        // BATTERY
        // WEATHER
        //
        // from GridPreferences.loadPage()
        //
        // So everything belongs to this page.

        gridAdapter.loadFromPrefs(
                appManager
        );


        holder.gridAdapter = gridAdapter;


        // ─────────────────────────────────────
        // DRAG/DROP CALLBACK
        // ─────────────────────────────────────

        if (pageBoundListener != null) {

            pageBoundListener.onPageBound(
                    position,
                    gridAdapter,
                    holder.recyclerView
            );
        }
    }


    // ─────────────────────────────────────────────
    // PAGE COUNT
    // ─────────────────────────────────────────────

    @Override
    public int getItemCount() {
        return pageCount;
    }


    // ─────────────────────────────────────────────
    // VIEW HOLDER
    // ─────────────────────────────────────────────

    static class PageViewHolder
            extends RecyclerView.ViewHolder {

        final RecyclerView recyclerView;

        HomeGridAdapter gridAdapter;


        PageViewHolder(
                RecyclerView itemView
        ) {

            super(itemView);

            this.recyclerView = itemView;
        }
    }
}