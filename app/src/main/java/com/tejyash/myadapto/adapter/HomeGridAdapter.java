package com.tejyash.myadapto.adapter;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
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
import com.tejyash.myadapto.launcher.LauncherItemType;
import com.tejyash.myadapto.manager.AppManager;
import com.tejyash.myadapto.model.AppInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * RecyclerView adapter for ONE Home page.
 *
 * Every page contains one physical grid:
 *
 * APP
 * CLOCK
 * BATTERY
 * WEATHER
 * EMPTY
 *
 * Widgets and apps therefore live inside the SAME RecyclerView.
 */
public class HomeGridAdapter
        extends RecyclerView.Adapter<HomeGridAdapter.BaseViewHolder> {


    // ─────────────────────────────────────────────────────────────
    // LISTENERS
    // ─────────────────────────────────────────────────────────────

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }


    public interface OnAppMenuRequestListener {
        void onShowMenu(AppInfo app);
    }

    public interface OnWidgetMenuRequestListener {
        void onShowWidgetMenu(LauncherItemType type);
    }

    public interface OnEmptySpaceLongClickListener {
        void onEmptySpaceLongClick();
    }


    // ─────────────────────────────────────────────────────────────
    // DRAG LABELS
    // ─────────────────────────────────────────────────────────────

    public static final String MOVE_CLIP_LABEL =
            "adapto_move";

    public static final String WIDGET_CLIP_LABEL =
            "adapto_widget_move";


    private static final long MENU_EXTRA_HOLD_MS = 350;


    // ─────────────────────────────────────────────────────────────
    // DATA
    // ─────────────────────────────────────────────────────────────

    private final Context context;

    private final AccessibilityPreferences prefs;

    private final int columns;

    private final int page;


    /**
     * One GridModel per physical grid cell.
     *
     * null = empty
     */
    private final List<GridModel> slots =
            new ArrayList<>();


    /**
     * Installed apps indexed by package name.
     */
    private final Map<String, AppInfo> installedApps =
            new HashMap<>();


    private OnAppClickListener clickListener;

    private OnAppMenuRequestListener menuRequestListener;

    private OnWidgetMenuRequestListener widgetMenuRequestListener;

    private OnEmptySpaceLongClickListener emptySpaceLongClickListener;

    private LauncherItemType selectedWidgetType = null;


    // ─────────────────────────────────────────────────────────────
    // CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────

    public HomeGridAdapter(
            Context ctx,
            int page
    ) {

        this.context =
                ctx.getApplicationContext();

        this.prefs =
                AccessibilityPreferences.get(ctx);

        this.columns =
                GridPreferences.getColumns(ctx);

        this.page =
                page;


        int totalCells =
                GridPreferences.ROWS_PER_PAGE
                        * columns;


        for (int i = 0; i < totalCells; i++) {

            slots.add(null);
        }
    }


    // ─────────────────────────────────────────────────────────────
    // PUBLIC METHODS
    // ─────────────────────────────────────────────────────────────

    public void setOnAppClickListener(
            OnAppClickListener listener
    ) {

        this.clickListener =
                listener;
    }


    public void setOnAppMenuRequestListener(
            OnAppMenuRequestListener listener
    ) {

        this.menuRequestListener =
                listener;
    }

    public void setOnWidgetMenuRequestListener(
            OnWidgetMenuRequestListener listener
    ) {

        this.widgetMenuRequestListener =
                listener;
    }

    public void setOnEmptySpaceLongClickListener(
            OnEmptySpaceLongClickListener listener
    ) {
        this.emptySpaceLongClickListener = listener;
    }


    public int getColumns() {

        return columns;
    }


    public int getPage() {

        return page;
    }

    public int getSpanSize(int position) {
        if (position < 0 || position >= slots.size()) return 1;
        GridModel item = slots.get(position);
        if (item == null) return 1;
        return item.spanX;
    }


    // ─────────────────────────────────────────────────────────────
    // LOAD GRID
    // ─────────────────────────────────────────────────────────────

    /**
     * Loads both apps and widgets from SharedPreferences.
     */
    public void loadFromPrefs(
            AppManager appManager
    ) {

        installedApps.clear();


        // Load installed apps.

        for (
                AppInfo app :
                appManager.loadInstalledApps()
        ) {

            installedApps.put(
                    app.packageName,
                    app
            );
        }


        // Clear current grid.

        for (
                int i = 0;
                i < slots.size();
                i++
        ) {

            slots.set(i, null);
        }


        // Load saved items.

        List<GridModel> placed =
                GridPreferences.loadPage(
                        context,
                        page
                );


        for (
                GridModel item :
                placed
        ) {

            if (item == null) {
                continue;
            }


            int index =
                    item.row * columns
                            + item.col;


            if (
                    index < 0
                            ||
                            index >= slots.size()
            ) {

                continue;
            }


            LauncherItemType type =
                    item.type;


            // Old data fallback.

            if (type == null) {

                type =
                        LauncherItemType.APP;

                item.type =
                        type;
            }


            // ─────────────────────────────────────────────
            // APP
            // ─────────────────────────────────────────────

            if (
                    type ==
                            LauncherItemType.APP
            ) {

                AppInfo app =
                        installedApps.get(
                                item.packageName
                        );


                // App was uninstalled.

                if (app == null) {
                    continue;
                }


                slots.set(
                        index,
                        item
                );

                continue;
            }


            // ─────────────────────────────────────────────
            // WIDGET
            // ─────────────────────────────────────────────

            if (
                    type ==
                            LauncherItemType.CLOCK
                            ||
                            type ==
                                    LauncherItemType.BATTERY
                            ||
                            type ==
                                    LauncherItemType.WEATHER
            ) {

                slots.set(
                        index,
                        item
                );
            }
        }


        notifyDataSetChanged();
    }


    // ─────────────────────────────────────────────────────────────
    // APP DROP
    // ─────────────────────────────────────────────────────────────

    public void acceptDrop(
            String packageName,
            AppManager appManager,
            int row,
            int col
    ) {

        if (
                packageName == null
                        ||
                        packageName.isEmpty()
        ) {

            return;
        }


        GridPreferences.placeApp(
                context,
                packageName,
                page,
                row,
                col
        );


        loadFromPrefs(
                appManager
        );
    }


    public void acceptWidgetDrop(
            LauncherItemType type,
            AppManager appManager,
            int row,
            int col
    ) {

        if (type == null) {
            return;
        }


        GridPreferences.moveWidget(
                context,
                type,
                page,
                row,
                col
        );


        loadFromPrefs(
                appManager
        );
    }


    // ─────────────────────────────────────────────────────────────
    // RECYCLER VIEW
    // ─────────────────────────────────────────────────────────────

    @Override
    public int getItemCount() {

        return slots.size();
    }


    @Override
    public int getItemViewType(
            int position
    ) {

        GridModel item =
                slots.get(position);


        if (
                item == null
                        ||
                        item.type == null
        ) {

            return LauncherItemType.EMPTY.ordinal();
        }


        return item.type.ordinal();
    }


    // ─────────────────────────────────────────────────────────────
    // CREATE VIEW HOLDER
    // ─────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public BaseViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        LauncherItemType type =
                LauncherItemType.values()[viewType];


        switch (type) {


            // ─────────────────────────────────────────
            // APP
            // ─────────────────────────────────────────

            case APP:

                View appView =
                        LayoutInflater.from(
                                parent.getContext()
                        ).inflate(
                                R.layout.item_app_grid,
                                parent,
                                false
                        );


                return new AppViewHolder(
                        appView
                );


            // ─────────────────────────────────────────
            // CLOCK
            // ─────────────────────────────────────────

            case CLOCK:

                View clockView =
                        LayoutInflater.from(
                                parent.getContext()
                        ).inflate(
                                R.layout.item_widget_clock,
                                parent,
                                false
                        );

                return new ClockViewHolder(clockView);


            // ─────────────────────────────────────────
            // BATTERY
            // ─────────────────────────────────────────

            case BATTERY:

                View batteryView =
                        LayoutInflater.from(
                                parent.getContext()
                        ).inflate(
                                R.layout.item_widget_battery,
                                parent,
                                false
                        );

                return new BatteryViewHolder(batteryView);


            // ─────────────────────────────────────────
            // WEATHER
            // ─────────────────────────────────────────

            case WEATHER:

                View weatherView =
                        LayoutInflater.from(
                                parent.getContext()
                        ).inflate(
                                R.layout.item_widget_weather,
                                parent,
                                false
                        );

                return new WeatherViewHolder(weatherView);


            // ─────────────────────────────────────────
            // EMPTY
            // ─────────────────────────────────────────

            case EMPTY:

            default:

                return new EmptyViewHolder(
                        createEmptyView(parent)
                );
        }
    }


    // ─────────────────────────────────────────────────────────────
    // BIND
    // ─────────────────────────────────────────────────────────────

    @Override
    public void onBindViewHolder(
            @NonNull BaseViewHolder holder,
            int position
    ) {

        GridModel item =
                slots.get(position);


        // ─────────────────────────────────────────────
        // APP
        // ─────────────────────────────────────────────

        if (
                holder instanceof AppViewHolder
        ) {

            AppViewHolder appHolder =
                    (AppViewHolder) holder;


            AppInfo app =
                    item != null
                            ? installedApps.get(
                            item.packageName
                    )
                            : null;


            appHolder.bind(
                    app,
                    prefs.getFontSizeSp(),
                    prefs.getIconSizeDp()
            );


            appHolder.setup(
                    app,
                    clickListener,
                    menuRequestListener
            );


            return;
        }


        // ─────────────────────────────────────────────
        // CLOCK
        // ─────────────────────────────────────────────

        if (
                holder instanceof ClockViewHolder
        ) {

            ClockViewHolder clockHolder =
                    (ClockViewHolder) holder;


            clockHolder.bind();

            bindResizeHandles(item, holder.itemView);


            setupWidgetDrag(
                    item,
                    holder.itemView,
                    widgetMenuRequestListener
            );


            return;
        }


        // ─────────────────────────────────────────────
        // BATTERY
        // ─────────────────────────────────────────────

        if (
                holder instanceof BatteryViewHolder
        ) {

            BatteryViewHolder batteryHolder =
                    (BatteryViewHolder) holder;


            batteryHolder.bind();

            bindResizeHandles(item, holder.itemView);


            setupWidgetDrag(
                    item,
                    holder.itemView,
                    widgetMenuRequestListener
            );


            return;
        }


        // ─────────────────────────────────────────────
        // WEATHER
        // ─────────────────────────────────────────────

        if (
                holder instanceof WeatherViewHolder
        ) {

            WeatherViewHolder weatherHolder =
                    (WeatherViewHolder) holder;


            weatherHolder.bind();

            bindResizeHandles(item, holder.itemView);


            setupWidgetDrag(
                    item,
                    holder.itemView,
                    widgetMenuRequestListener
            );


            return;
        }


        // ─────────────────────────────────────────────
        // EMPTY
        // ─────────────────────────────────────────────

        if (
                holder instanceof EmptyViewHolder
        ) {

            EmptyViewHolder emptyHolder = (EmptyViewHolder) holder;
            emptyHolder.setup(() -> {
                if (emptySpaceLongClickListener != null) {
                    emptySpaceLongClickListener.onEmptySpaceLongClick();
                }
            });

            holder.itemView.setContentDescription(
                    "Empty"
            );
        }
    }


    // ─────────────────────────────────────────────────────────────
    // RECYCLE
    // ─────────────────────────────────────────────────────────────

    @Override
    public void onViewRecycled(
            @NonNull BaseViewHolder holder
    ) {

        if (
                holder instanceof ClockViewHolder
        ) {

            ((ClockViewHolder) holder)
                    .stopClock();
        }


        super.onViewRecycled(holder);
    }


    // ─────────────────────────────────────────────────────────────
    // WIDGET VIEW
    // ─────────────────────────────────────────────────────────────

    private View createEmptyView(
            ViewGroup parent
    ) {

        View view = new View(parent.getContext());
        // Standard app cell height roughly
        int height = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 90,
                parent.getContext().getResources().getDisplayMetrics()));

        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
        ));

        return view;
    }

    private void bindResizeHandles(GridModel item, View itemView) {
        // Feature disabled as per user request
    }

    private void updateSpan(GridModel item, int newSpan) {
        item.spanX = newSpan;
        GridPreferences.saveSlot(context, item);
        loadFromPrefs(new AppManager(context));
    }


    // ─────────────────────────────────────────────────────────────
    // BASE HOLDER
    // ─────────────────────────────────────────────────────────────

    static abstract class BaseViewHolder
            extends RecyclerView.ViewHolder {

        BaseViewHolder(
                View itemView
        ) {

            super(itemView);
        }
    }


    // ─────────────────────────────────────────────────────────────
    // APP HOLDER
    // ─────────────────────────────────────────────────────────────

    static class AppViewHolder
            extends BaseViewHolder {


        final ImageView ivIcon;

        final TextView tvLabel;

        final TextView tvBadge;


        private final Handler handler =
                new Handler(
                        Looper.getMainLooper()
                );


        private final int touchSlop;

        private final long longPressMs;


        private float downX;

        private float downY;


        private boolean moved;

        private boolean dragArmed;


        private Runnable armDragRunnable;

        private Runnable showMenuRunnable;


        AppViewHolder(
                View itemView
        ) {

            super(itemView);


            ivIcon =
                    itemView.findViewById(
                            R.id.iv_app_icon
                    );


            tvLabel =
                    itemView.findViewById(
                            R.id.tv_app_label
                    );


            tvBadge =
                    itemView.findViewById(
                            R.id.tv_badge
                    );


            touchSlop =
                    ViewConfiguration
                            .get(
                                    itemView.getContext()
                            )
                            .getScaledTouchSlop();


            longPressMs =
                    ViewConfiguration
                            .getLongPressTimeout();
        }


        void setup(
                @Nullable AppInfo app,
                OnAppClickListener clickListener,
                OnAppMenuRequestListener menuRequestListener
        ) {

            if (app == null) {

                itemView.setOnTouchListener(
                        null
                );

                itemView.setOnClickListener(
                        null
                );

                return;
            }


            itemView.setOnTouchListener(
                    (v, event) -> {

                        switch (
                                event.getActionMasked()
                        ) {


                            case MotionEvent.ACTION_DOWN:

                                downX =
                                        event.getRawX();

                                downY =
                                        event.getRawY();


                                moved = false;

                                dragArmed = false;


                                cancelPending();


                                armDragRunnable =
                                        () -> {
                                            dragArmed = true;
                                            if (v.getParent() != null) {
                                                v.getParent().requestDisallowInterceptTouchEvent(true);
                                            }
                                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                            
                                            // Make original item semi-transparent during drag for a "premium" feel
                                            v.setAlpha(0.3f);
                                            startRepositionDrag(app, v);
                                        };


                                handler.postDelayed(
                                        armDragRunnable,
                                        500 // Start drag after 0.5s hold
                                );


                                showMenuRunnable =
                                        () -> {
                                            if (!moved && !dragArmed && menuRequestListener != null) {
                                                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                                menuRequestListener.onShowMenu(app);
                                            }
                                        };


                                handler.postDelayed(
                                        showMenuRunnable,
                                        2500 // Delete menu appears after 2.5s hold
                                );


                                return false; // MUST RETURN FALSE SO PARENT CAN SEE LONG CLICK


                            case MotionEvent.ACTION_MOVE:

                                if (moved || dragArmed) {
                                    return true;
                                }


                                float dx =
                                        event.getRawX()
                                                - downX;


                                float dy =
                                        event.getRawY()
                                                - downY;


                                if (
                                        Math.abs(dx)
                                                > touchSlop
                                                ||
                                                Math.abs(dy)
                                                        > touchSlop
                                ) {

                                    moved = true;
                                    cancelPending();
                                }


                                return true;


                            case MotionEvent.ACTION_UP:

                                cancelPending();
                                v.setAlpha(1.0f); // Reset transparency if drag didn't happen


                                if (!moved && !dragArmed) {

                                    if (
                                            clickListener
                                                    != null
                                    ) {

                                        clickListener
                                                .onAppClick(
                                                        app
                                                );
                                    }
                                }


                                return true;


                            case MotionEvent.ACTION_CANCEL:

                                cancelPending();

                                return true;


                            default:

                                return false;
                        }
                    }
            );
        }


        private void cancelPending() {

            if (
                    armDragRunnable
                            != null
            ) {

                handler.removeCallbacks(
                        armDragRunnable
                );
            }


            if (
                    showMenuRunnable
                            != null
            ) {

                handler.removeCallbacks(
                        showMenuRunnable
                );
            }
        }


        private void startRepositionDrag(
                AppInfo app,
                View itemView
        ) {

            ClipData.Item item =
                    new ClipData.Item(
                            app.packageName
                    );


            ClipData dragData =
                    new ClipData(
                            MOVE_CLIP_LABEL,
                            new String[]{
                                    ClipDescription
                                            .MIMETYPE_TEXT_PLAIN
                            },
                            item
                    );


            View.DragShadowBuilder shadow =
                    new View.DragShadowBuilder(
                            itemView
                    );


            itemView.startDragAndDrop(
                    dragData,
                    shadow,
                    app,
                    0
            );
        }


        void bind(
                @Nullable AppInfo app,
                float fontSp,
                int iconDp
        ) {

            int px =
                    dpToPx(
                            itemView.getContext(),
                            iconDp
                    );


            ViewGroup.LayoutParams lp =
                    ivIcon.getLayoutParams();


            lp.width = px;

            lp.height = px;


            ivIcon.setLayoutParams(lp);


            if (app == null) {

                ivIcon.setImageDrawable(
                        null
                );

                tvLabel.setText(
                        ""
                );


                if (tvBadge != null) {

                    tvBadge.setVisibility(
                            View.GONE
                    );
                }


                itemView.setClickable(
                        false
                );


                itemView.setContentDescription(
                        "Empty"
                );


                return;
            }


            ivIcon.setImageDrawable(
                    app.icon
            );


            tvLabel.setText(
                    app.label
            );


            tvLabel.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    fontSp
            );


            itemView.setClickable(
                    true
            );


            itemView.setContentDescription(
                    app.label
            );


            if (tvBadge != null) {

                int count =
                        com.tejyash.myadapto.notifications
                                .NotificationBadgeStore
                                .getCount(
                                        itemView.getContext(),
                                        app.packageName
                                );


                if (count > 0) {

                    tvBadge.setVisibility(
                            View.VISIBLE
                    );


                    tvBadge.setText(
                            count > 99
                                    ? "99+"
                                    : String.valueOf(count)
                    );

                } else {

                    tvBadge.setVisibility(
                            View.GONE
                    );
                }
            }
        }


        private static int dpToPx(
                Context ctx,
                int dp
        ) {

            return Math.round(
                    TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            dp,
                            ctx.getResources()
                                    .getDisplayMetrics()
                    )
            );
        }
    }


    // ─────────────────────────────────────────────────────────────
    // CLOCK HOLDER
    // ─────────────────────────────────────────────────────────────

    static class ClockViewHolder
            extends BaseViewHolder {


        final TextView tvTime;
        final TextView tvDate;


        private final Handler handler =
                new Handler(
                        Looper.getMainLooper()
                );


        private final Runnable clockRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        updateTime();


                        handler.postDelayed(
                                this,
                                1000
                        );
                    }
                };


        ClockViewHolder(
                View itemView
        ) {

            super(itemView);


            tvTime =
                    itemView.findViewById(R.id.tv_clock_time);
            tvDate =
                    itemView.findViewById(R.id.tv_clock_date);
        }


        void bind() {

            textViewSetup();


            handler.removeCallbacks(
                    clockRunnable
            );


            updateTime();


            handler.postDelayed(
                    clockRunnable,
                    1000
            );
        }

        private void textViewSetup() {
            itemView.setContentDescription("Clock widget");
        }


        private void updateTime() {

            Date now = new Date();

            String time =
                    new SimpleDateFormat(
                            "h:mm a",
                            Locale.getDefault()
                    ).format(now);

            String date =
                    new SimpleDateFormat(
                            "EEEE, MMM d",
                            Locale.getDefault()
                    ).format(now);


            if (tvTime != null) tvTime.setText(time);
            if (tvDate != null) tvDate.setText(date);
        }


        void stopClock() {

            handler.removeCallbacks(
                    clockRunnable
            );
        }
    }


    // ─────────────────────────────────────────────────────────────
    // BATTERY HOLDER
    // ─────────────────────────────────────────────────────────────

    static class BatteryViewHolder
            extends BaseViewHolder {


        final TextView tvPercent;
        final TextView tvStatus;
        final android.widget.ProgressBar pbBatteryCircle;


        BatteryViewHolder(
                View itemView
        ) {

            super(itemView);


            tvPercent =
                    itemView.findViewById(R.id.tv_battery_percent);
            tvStatus =
                    itemView.findViewById(R.id.tv_battery_status);
            pbBatteryCircle =
                    itemView.findViewById(R.id.pb_battery_circle);
        }


        void bind() {

            updateBattery();


            itemView.setContentDescription(
                    "Battery widget"
            );
        }


        private void updateBattery() {

            IntentFilter filter =
                    new IntentFilter(
                            Intent.ACTION_BATTERY_CHANGED
                    );


            Intent battery =
                    itemView.getContext()
                            .registerReceiver(
                                    null,
                                    filter
                            );


            if (battery == null) {
                if (tvPercent != null) tvPercent.setText("N/A");
                return;
            }


            int level =
                    battery.getIntExtra(
                            BatteryManager.EXTRA_LEVEL,
                            -1
                    );


            int scale =
                    battery.getIntExtra(
                            BatteryManager.EXTRA_SCALE,
                            -1
                    );


            int percent =
                    scale > 0
                            ? (int)
                            (
                                    level /
                                            (float) scale
                                            * 100
                            )
                            : 0;


            int status =
                    battery.getIntExtra(
                            BatteryManager.EXTRA_STATUS,
                            -1
                    );


            boolean charging =
                    status ==
                            BatteryManager
                                    .BATTERY_STATUS_CHARGING
                            ||
                            status ==
                                    BatteryManager
                                            .BATTERY_STATUS_FULL;


            if (tvPercent != null) tvPercent.setText(percent + "%");
            if (pbBatteryCircle != null) {
                pbBatteryCircle.setProgress(percent);
            }
            if (tvStatus != null) {
                tvStatus.setText(charging ? "Charging" : "On Battery");
            }
        }
    }


    // ─────────────────────────────────────────────────────────────
    // WEATHER HOLDER
    // ─────────────────────────────────────────────────────────────

    static class WeatherViewHolder
            extends BaseViewHolder {


        final TextView tvTemp;
        final TextView tvCondition;


        WeatherViewHolder(
                View itemView
        ) {

            super(itemView);


            tvTemp =
                    itemView.findViewById(R.id.tv_weather_temp);
            tvCondition =
                    itemView.findViewById(R.id.tv_weather_condition);
        }


        void bind() {

            if (tvTemp != null) tvTemp.setText("28°C");
            if (tvCondition != null) tvCondition.setText("Sunny");

            itemView.setContentDescription(
                    "Weather widget"
            );
        }
    }


    // ─────────────────────────────────────────────────────────────
    // EMPTY HOLDER
    // ─────────────────────────────────────────────────────────────

    static class EmptyViewHolder
            extends BaseViewHolder {


        EmptyViewHolder(
                View itemView
        ) {

            super(itemView);
        }

        void setup(Runnable onLongClick) {
            itemView.setOnLongClickListener(v -> {
                if (onLongClick != null) {
                    onLongClick.run();
                    return true;
                }
                return false;
            });
        }
    }


    // ─────────────────────────────────────────────────────────────
    // WIDGET DRAG
    // ─────────────────────────────────────────────────────────────

    private void setupWidgetDrag(
            GridModel widget,
            View itemView,
            OnWidgetMenuRequestListener menuListener
    ) {

        if (
                widget == null
                        ||
                        widget.type == null
        ) {

            itemView.setOnTouchListener(
                    null
            );

            return;
        }


        final Handler handler =
                new Handler(
                        Looper.getMainLooper()
                );


        final int touchSlop =
                ViewConfiguration
                        .get(
                                itemView.getContext()
                        )
                        .getScaledTouchSlop();


        final long longPressMs =
                ViewConfiguration
                        .getLongPressTimeout();


        final float[] downX =
                new float[1];


        final float[] downY =
                new float[1];


        final boolean[] moved =
                new boolean[1];

        final boolean[] dragStarted =
                new boolean[1];


        final Runnable[] dragRunnable =
                new Runnable[1];

        final Runnable[] menuRunnable =
                new Runnable[1];


        itemView.setOnTouchListener(
                (v, event) -> {

                    switch (
                            event.getActionMasked()
                    ) {


                        case MotionEvent.ACTION_DOWN:

                            downX[0] =
                                    event.getRawX();


                            downY[0] =
                                    event.getRawY();


                            moved[0] =
                                    false;

                            dragStarted[0] =
                                    false;

                            if (dragRunnable[0] != null) handler.removeCallbacks(dragRunnable[0]);
                            if (menuRunnable[0] != null) handler.removeCallbacks(menuRunnable[0]);


                            dragRunnable[0] =
                                    () -> {
                                        dragStarted[0] = true;
                                        if (v.getParent() != null) {
                                            v.getParent().requestDisallowInterceptTouchEvent(true);
                                        }
                                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                        
                                        // Make original widget semi-transparent during drag
                                        v.setAlpha(0.3f);
                                        startWidgetDrag(widget, v);
                                    };


                            handler.postDelayed(
                                    dragRunnable[0],
                                    500 // Hold 0.5s to start moving
                            );

                            menuRunnable[0] = () -> {
                                if (!moved[0] && !dragStarted[0] && menuListener != null) {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                    menuListener.onShowWidgetMenu(widget.type);
                                }
                            };
                            handler.postDelayed(menuRunnable[0], 2500); // Hold 2.5s for delete menu


                            return false; // MUST RETURN FALSE SO PARENT CAN SEE LONG CLICK


                        case MotionEvent.ACTION_MOVE:

                            if (
                                    moved[0] || dragStarted[0]
                            ) {

                                return true;
                            }


                            float dx =
                                    event.getRawX()
                                            - downX[0];


                            float dy =
                                    event.getRawY()
                                            - downY[0];


                            if (
                                    Math.abs(dx)
                                            > touchSlop
                                            ||
                                            Math.abs(dy)
                                                    > touchSlop
                            ) {

                                moved[0] =
                                        true;


                                if (dragRunnable[0] != null) handler.removeCallbacks(dragRunnable[0]);
                                if (menuRunnable[0] != null) handler.removeCallbacks(menuRunnable[0]);
                            }


                            return true;


                        case MotionEvent.ACTION_UP:

                        case MotionEvent.ACTION_CANCEL:

                            handler.removeCallbacks(dragRunnable[0]);
                            handler.removeCallbacks(menuRunnable[0]);
                            v.setAlpha(1.0f); // Reset transparency

                            if (event.getAction() == MotionEvent.ACTION_UP && !moved[0] && !dragStarted[0]) {
                                // Simple tap - toggle selection for resizing
                                if (selectedWidgetType == widget.type) {
                                    selectedWidgetType = null;
                                } else {
                                    selectedWidgetType = widget.type;
                                }
                                notifyDataSetChanged();
                            }


                            return true;


                        default:

                            return true;
                    }
                }
        );
    }


    private void startWidgetDrag(
            GridModel widget,
            View itemView
    ) {

        if (
                widget == null
                        ||
                        widget.type == null
        ) {

            return;
        }


        String widgetType =
                widget.type.name();


        ClipData.Item item =
                new ClipData.Item(
                        widgetType
                );


        ClipData dragData =
                new ClipData(
                        WIDGET_CLIP_LABEL,
                        new String[]{
                                ClipDescription
                                        .MIMETYPE_TEXT_PLAIN
                        },
                        item
                );


        View.DragShadowBuilder shadow =
                new View.DragShadowBuilder(
                        itemView
                );


        itemView.startDragAndDrop(
                dragData,
                shadow,
                widget,
                0
        );
    }
}