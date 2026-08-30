package com.tejyash.myadapto.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles saving and loading the home screen grid from SharedPreferences.
 *
 * Each grid slot is stored as one key-value pair:
 *
 * Key:
 *   grid_<page>_<row>_<col>
 *
 * Old value format:
 *   com.google.android.gm
 *
 * New value format:
 *   APP|com.google.android.gm
 *   CLOCK|
 *   BATTERY|
 *   WEATHER|
 *
 * Old app-only data is still supported and automatically interpreted as APP.
 */
public class GridPreferences {

    private static final String PREFS_NAME = "adapto_grid";
    private static final String KEY_COLUMNS = "grid_columns";

    /** Separator between LauncherItemType and package name. */
    private static final String VALUE_SEPARATOR = "|";

    /** Fixed rows per Home page. */
    public static final int ROWS_PER_PAGE = 5;


    // ── Column count ────────────────────────────────────────────────

    /**
     * Maps the icon-size seekbar step (0-3) to the column count.
     *
     * Step 0 → 5 columns
     * Step 1 → 5 columns
     * Step 2 → 4 columns
     * Step 3 → 3 columns
     */
    public static void saveColumnsFromIconStep(Context ctx, int iconStep) {

        int columns;

        if (iconStep <= 1) {
            columns = 5;
        } else if (iconStep == 2) {
            columns = 4;
        } else {
            columns = 3;
        }

        saveColumns(ctx, columns);
    }


    public static void saveColumns(Context ctx, int columns) {

        prefs(ctx)
                .edit()
                .putInt(KEY_COLUMNS, columns)
                .apply();
    }


    /** Returns saved column count. */
    public static int getColumns(Context ctx) {

        return prefs(ctx).getInt(KEY_COLUMNS, 4);
    }


    // ── Grid CRUD ───────────────────────────────────────────────────


    /**
     * Saves an app or widget into a grid slot.
     *
     * Examples:
     *
     * APP:
     *   APP|com.google.android.gm
     *
     * CLOCK:
     *   CLOCK|
     *
     * BATTERY:
     *   BATTERY|
     */
    public static void saveSlot(Context ctx, GridModel item) {

        if (item == null || item.type == null) {
            return;
        }

        prefs(ctx)
                .edit()
                .putString(
                        item.toKey(),
                        encodeItem(item)
                )
                .apply();
    }


    /**
     * Removes an item from a grid slot.
     */
    public static void clearSlot(
            Context ctx,
            int page,
            int row,
            int col
    ) {

        String key = new GridModel(
                row,
                col,
                page,
                ""
        ).toKey();

        prefs(ctx)
                .edit()
                .remove(key)
                .apply();
    }


    /**
     * Moves an APP from one slot to another.
     *
     * Kept for backward compatibility with existing adapter code.
     */
    public static void moveSlot(
            Context ctx,
            int fromPage,
            int fromRow,
            int fromCol,
            int toPage,
            int toRow,
            int toCol,
            String packageName
    ) {

        SharedPreferences.Editor editor = prefs(ctx).edit();

        // Remove old position.
        editor.remove(
                new GridModel(
                        fromRow,
                        fromCol,
                        fromPage,
                        ""
                ).toKey()
        );

        // Save new APP position.
        GridModel item = new GridModel(
                toRow,
                toCol,
                toPage,
                packageName,
                LauncherItemType.APP
        );

        editor.putString(
                item.toKey(),
                encodeItem(item)
        );

        editor.apply();
    }


    // ── Page Loading ────────────────────────────────────────────────


    /**
     * Loads every APP/WIDGET from one page.
     */
    public static List<GridModel> loadPage(
            Context ctx,
            int page
    ) {

        Map<String, ?> all = prefs(ctx).getAll();

        List<GridModel> items = new ArrayList<>();

        String prefix = "grid_" + page + "_";

        for (Map.Entry<String, ?> entry : all.entrySet()) {

            String key = entry.getKey();

            if (!key.startsWith(prefix)) {
                continue;
            }

            if (!(entry.getValue() instanceof String)) {
                continue;
            }

            String value = (String) entry.getValue();

            if (value == null || value.isEmpty()) {
                continue;
            }

            String[] parts = key.split("_");

            if (parts.length != 4) {
                continue;
            }

            try {

                int row = Integer.parseInt(parts[2]);
                int col = Integer.parseInt(parts[3]);

                GridModel item = decodeItem(
                        row,
                        col,
                        page,
                        value
                );

                if (item != null) {
                    items.add(item);
                }

            } catch (NumberFormatException ignored) {
                // Invalid grid key.
            }
        }

        return items;
    }


    /**
     * Loads ALL apps and widgets across every page.
     */
    public static List<GridModel> loadAll(Context ctx) {

        Map<String, ?> all = prefs(ctx).getAll();

        List<GridModel> items = new ArrayList<>();

        for (Map.Entry<String, ?> entry : all.entrySet()) {

            String key = entry.getKey();

            if (!key.startsWith("grid_")) {
                continue;
            }

            if (KEY_COLUMNS.equals(key)) {
                continue;
            }

            if (!(entry.getValue() instanceof String)) {
                continue;
            }

            String value = (String) entry.getValue();

            if (value == null || value.isEmpty()) {
                continue;
            }

            String[] parts = key.split("_");

            if (parts.length != 4) {
                continue;
            }

            try {

                int page = Integer.parseInt(parts[1]);
                int row = Integer.parseInt(parts[2]);
                int col = Integer.parseInt(parts[3]);

                GridModel item = decodeItem(
                        row,
                        col,
                        page,
                        value
                );

                if (item != null) {
                    items.add(item);
                }

            } catch (NumberFormatException ignored) {
                // Invalid grid key.
            }
        }

        return items;
    }


    // ── Page Management ─────────────────────────────────────────────


    /**
     * Clears every item from one page.
     */
    public static void clearPage(
            Context ctx,
            int page
    ) {

        SharedPreferences.Editor editor = prefs(ctx).edit();

        String prefix = "grid_" + page + "_";

        for (String key : prefs(ctx).getAll().keySet()) {

            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }

        editor.apply();
    }


    /**
     * Clears the entire home-screen grid.
     */
    public static void clearAll(Context ctx) {

        SharedPreferences.Editor editor = prefs(ctx).edit();

        Map<String, ?> all = prefs(ctx).getAll();

        for (String key : all.keySet()) {

            if (key.startsWith("grid_")) {
                editor.remove(key);
            }
        }

        editor.apply();
    }


    // ── Occupancy ───────────────────────────────────────────────────


    /**
     * Checks whether a grid slot contains ANY item.
     *
     * This now works for:
     *
     * APP
     * CLOCK
     * BATTERY
     * WEATHER
     */
    public static boolean isSlotOccupied(
            Context ctx,
            int page,
            int row,
            int col
    ) {

        String key = new GridModel(
                row,
                col,
                page,
                ""
        ).toKey();

        String value = prefs(ctx).getString(
                key,
                null
        );

        return value != null && !value.isEmpty();
    }


    // ── Multi-page helpers ──────────────────────────────────────────


    /**
     * Returns highest page containing an item.
     *
     * Returns -1 if Home is empty.
     */
    public static int getMaxUsedPage(Context ctx) {

        int max = -1;

        for (GridModel item : loadAll(ctx)) {

            if (item.page > max) {
                max = item.page;
            }
        }

        return max;
    }


    /**
     * Returns number of pages.
     *
     * Always keeps one extra blank page at the end.
     */
    public static int getPageCount(Context ctx) {

        return getMaxUsedPage(ctx) + 2;
    }


    /**
     * Finds first empty slot on a specific page.
     */
    public static int[] findFirstEmptySlotOnPage(
            Context ctx,
            int page
    ) {

        int columns = getColumns(ctx);

        for (int row = 0; row < ROWS_PER_PAGE; row++) {

            for (int col = 0; col < columns; col++) {

                if (!isSlotOccupied(
                        ctx,
                        page,
                        row,
                        col
                )) {

                    return new int[]{
                            row,
                            col
                    };
                }
            }
        }

        return null;
    }


    /**
     * Finds first empty slot anywhere in Home.
     */
    public static int[] findFirstEmptySlot(Context ctx) {

        for (
                int page = 0;
                page <= getMaxUsedPage(ctx) + 1;
                page++
        ) {

            int[] slot =
                    findFirstEmptySlotOnPage(
                            ctx,
                            page
                    );

            if (slot != null) {

                return new int[]{
                        page,
                        slot[0],
                        slot[1]
                };
            }
        }

        return new int[]{
                getMaxUsedPage(ctx) + 1,
                0,
                0
        };
    }


    // ── APP helpers ─────────────────────────────────────────────────


    /**
     * Removes all placements of an APP.
     *
     * Widgets are unaffected.
     */
    public static void removeApp(
            Context ctx,
            String packageName
    ) {

        for (GridModel item : loadAll(ctx)) {

            if (
                    item.type == LauncherItemType.APP
                            &&
                            packageName.equals(item.packageName)
            ) {

                clearSlot(
                        ctx,
                        item.page,
                        item.row,
                        item.col
                );
            }
        }
    }


    public static void placeApp(
            Context ctx,
            String packageName,
            int page,
            int row,
            int col
    ) {

        // Remove existing placement of this APP.
        removeApp(
                ctx,
                packageName
        );

        GridModel item = new GridModel(
                row,
                col,
                page,
                packageName,
                LauncherItemType.APP
        );

        // Displacement logic: bump the occupant(s) if slots are taken
        bumpOccupantsForItem(ctx, item);

        saveSlot(ctx, item);
    }


    // ── WIDGET helpers ──────────────────────────────────────────────


    /**
     * Places a widget at a specific position.
     *
     * If the slot is occupied, the occupant is bumped.
     */
    public static void placeWidget(
            Context ctx,
            LauncherItemType type,
            int page,
            int row,
            int col
    ) {

        if (type == null) {
            return;
        }

        if (type == LauncherItemType.APP) {
            return;
        }

        if (type == LauncherItemType.EMPTY) {
            return;
        }

        GridModel widget = new GridModel(
                row,
                col,
                page,
                "",
                type
        );
        
        // Default widget width is 1 (occupies one block)
        widget.spanX = 1;


        // Displacement logic
        bumpOccupantsForItem(ctx, widget);


        saveSlot(
                ctx,
                widget
        );
    }

    /**
     * Moves an existing widget to a new page/grid position.
     *
     * If the slot is occupied, the occupant is bumped.
     */
    public static void moveWidget(
            Context ctx,
            LauncherItemType type,
            int targetPage,
            int targetRow,
            int targetCol
    ) {

        if (type == null) {
            return;
        }

        if (type == LauncherItemType.APP ||
                type == LauncherItemType.EMPTY) {
            return;
        }

        // Find existing widget to preserve spans
        GridModel existing = null;
        for (GridModel item : loadAll(ctx)) {
            if (item.type == type) {
                existing = item;
                break;
            }
        }

        // Remove the widget's previous position.
        removeWidget(
                ctx,
                type
        );


        GridModel widget =
                new GridModel(
                        targetRow,
                        targetCol,
                        targetPage,
                        "",
                        type
                );
        
        if (existing != null) {
            widget.spanX = existing.spanX;
            widget.spanY = existing.spanY;
        } else {
            widget.spanX = 1;
        }


        // Displacement logic
        bumpOccupantsForItem(ctx, widget);


        saveSlot(
                ctx,
                widget
        );
    }

    private static void bumpOccupantsForItem(Context ctx, GridModel newItem) {
        for (int r = 0; r < newItem.spanY; r++) {
            for (int c = 0; c < newItem.spanX; c++) {
                bumpOccupant(ctx, newItem.page, newItem.row + r, newItem.col + c);
            }
        }
    }

    /**
     * If a slot is occupied, shifts the item currently there (and any
     * subsequent items) to the next available slot on the same page
     * or subsequent pages.
     */
    private static void bumpOccupant(Context ctx, int page, int row, int col) {
        if (!isSlotOccupied(ctx, page, row, col)) return;

        // Find the occupant at this exact spot
        GridModel occupant = null;
        for (GridModel item : loadAll(ctx)) {
            if (item.page == page && item.row == row && item.col == col) {
                occupant = item;
                break;
            }
        }

        if (occupant == null) return;

        // Calculate next slot coordinates
        int columns = getColumns(ctx);
        int nextCol = col + 1;
        int nextRow = row;
        int nextPage = page;

        if (nextCol >= columns) {
            nextCol = 0;
            nextRow++;
        }
        if (nextRow >= ROWS_PER_PAGE) {
            nextRow = 0;
            nextPage++;
        }

        // Recursively push items in the next slot
        bumpOccupant(ctx, nextPage, nextRow, nextCol);

        // Clear previous slot to prevent ghost duplicates
        clearSlot(ctx, occupant.page, occupant.row, occupant.col);

        // Move current occupant to the next slot
        occupant.page = nextPage;
        occupant.row = nextRow;
        occupant.col = nextCol;
        saveSlot(ctx, occupant);
    }

    /**
     * Removes a specific widget type from Home.
     *
     * Example:
     *
     * removeWidget(ctx, LauncherItemType.BATTERY);
     */
    public static void removeWidget(
            Context ctx,
            LauncherItemType type
    ) {

        if (type == null) {
            return;
        }

        for (GridModel item : loadAll(ctx)) {

            if (item.type == type) {

                clearSlot(
                        ctx,
                        item.page,
                        item.row,
                        item.col
                );
            }
        }
    }


    /**
     * Returns whether a particular widget type exists.
     */
    public static boolean hasWidget(
            Context ctx,
            LauncherItemType type
    ) {

        if (type == null) {
            return false;
        }

        for (GridModel item : loadAll(ctx)) {

            if (item.type == type) {
                return true;
            }
        }

        return false;
    }


    // ── Encoding / Decoding ─────────────────────────────────────────


    /**
     * Converts GridModel into SharedPreferences value.
     *
     * APP:
     *   APP|com.android.chrome
     *
     * CLOCK:
     *   CLOCK|
     */
    private static String encodeItem(
            GridModel item
    ) {

        LauncherItemType type = item.type;

        if (type == null) {
            type = LauncherItemType.APP;
        }


        String packageName =
                item.packageName == null
                        ? ""
                        : item.packageName;


        return type.name()
                + VALUE_SEPARATOR
                + packageName
                + VALUE_SEPARATOR
                + item.spanX
                + VALUE_SEPARATOR
                + item.spanY;
    }


    /**
     * Converts a stored SharedPreferences value back into GridModel.
     *
     * Supports BOTH:
     *
     * Old:
     *   com.android.chrome
     *
     * New:
     *   APP|com.android.chrome
     *   CLOCK|
     *   BATTERY|
     */
    private static GridModel decodeItem(
            int row,
            int col,
            int page,
            String value
    ) {

        if (value == null || value.isEmpty()) {
            return null;
        }


        // ── OLD FORMAT ──────────────────────────────────────────────
        //
        // Existing launcher data looked like:
        //
        // com.google.android.gm
        //
        // Therefore anything without "|" is an APP.

        if (!value.contains(VALUE_SEPARATOR)) {

            return new GridModel(
                    row,
                    col,
                    page,
                    value,
                    LauncherItemType.APP
            );
        }


        // ── NEW FORMAT ──────────────────────────────────────────────

        String[] parts =
                value.split(
                        "\\|",
                        -1
                );

        if (parts.length < 2) {
            return null;
        }


        String typeString = parts[0];
        String packageName = parts[1];


        LauncherItemType type;

        try {

            type =
                    LauncherItemType.valueOf(
                            typeString
                    );

        } catch (IllegalArgumentException e) {

            // Unknown item type.
            return null;
        }


        if (type == LauncherItemType.EMPTY) {
            return null;
        }

        GridModel item = new GridModel(
                row,
                col,
                page,
                packageName,
                type
        );

        if (parts.length == 4) {
            try {
                item.spanX = Integer.parseInt(parts[2]);
                item.spanY = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {}
        }


        return item;
    }


    // ── Internal helper ─────────────────────────────────────────────


    private static SharedPreferences prefs(
            Context ctx
    ) {

        return ctx.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );
    }
}