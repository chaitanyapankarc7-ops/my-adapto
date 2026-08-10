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
 *   Key   →  "grid_<page>_<row>_<col>"   e.g. "grid_0_1_2"
 *   Value →  packageName                  e.g. "com.google.android.gm"
 *
 * Empty slots are simply not stored — we only save slots that have an app.
 * This keeps storage minimal and makes clearing a slot as simple as removing the key.
 */
public class GridPreferences {

    private static final String PREFS_NAME   = "adapto_grid";
    private static final String KEY_COLUMNS  = "grid_columns";
    private static final String EMPTY_SLOT   = "";   // sentinel for explicitly cleared slots

    /** Fixed rows per Home page. Once a page fills up, placement spills to the next page. */
    public static final int ROWS_PER_PAGE = 5;

    // ── Column count (driven by the icon-size seekbar) ─────────────

    /**
     * Maps the icon-size seekbar step (0-3) to the right column count.
     *   Step 0 (smallest icons) → 5 columns
     *   Step 1                  → 5 columns
     *   Step 2                  → 4 columns
     *   Step 3 (largest icons)  → 3 columns
     *
     * Called from SizeEditingPage whenever the icon seekbar moves.
     */
    public static void saveColumnsFromIconStep(Context ctx, int iconStep) {
        int columns;
        if      (iconStep <= 1) columns = 5;
        else if (iconStep == 2) columns = 4;
        else                    columns = 3;
        saveColumns(ctx, columns);
    }

    public static void saveColumns(Context ctx, int columns) {
        prefs(ctx).edit().putInt(KEY_COLUMNS, columns).apply();
    }

    /** Returns the saved column count (defaults to 4 if never set). */
    public static int getColumns(Context ctx) {
        return prefs(ctx).getInt(KEY_COLUMNS, 4);
    }

    // ── Grid CRUD ───────────────────────────────────────────────────

    /**
     * Save one app into a specific grid slot.
     * Call this after a drag-and-drop or long-press placement.
     */
    public static void saveSlot(Context ctx, GridModel item) {
        prefs(ctx).edit()
                .putString(item.toKey(), item.packageName)
                .apply();
    }

    /**
     * Remove the app from a specific grid slot (makes the slot empty).
     * Call this when the user drags an icon away from a position.
     */
    public static void clearSlot(Context ctx, int page, int row, int col) {
        String key = new GridModel(row, col, page, "").toKey();
        prefs(ctx).edit().remove(key).apply();
    }

    /**
     * Move an app from one slot to another in a single atomic write.
     * Old slot is cleared, new slot is written.
     */
    public static void moveSlot(Context ctx,
                                int fromPage, int fromRow, int fromCol,
                                int toPage,   int toRow,   int toCol,
                                String packageName) {
        SharedPreferences.Editor editor = prefs(ctx).edit();
        // Clear old position
        editor.remove(new GridModel(fromRow, fromCol, fromPage, "").toKey());
        // Write new position
        editor.putString(new GridModel(toRow, toCol, toPage, packageName).toKey(), packageName);
        editor.apply();
    }

    /**
     * Load every saved grid item for a given page.
     * Returns a list — your RecyclerView adapter consumes this directly.
     */
    public static List<GridModel> loadPage(Context ctx, int page) {
        Map<String, ?> all = prefs(ctx).getAll();
        List<GridModel> items = new ArrayList<>();

        String prefix = "grid_" + page + "_";
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix)) continue;

            String pkg = (String) entry.getValue();
            if (pkg == null || pkg.isEmpty()) continue;   // skip empty/cleared slots

            // Key format: "grid_<page>_<row>_<col>"
            String[] parts = key.split("_");
            if (parts.length != 4) continue;

            try {
                int row = Integer.parseInt(parts[2]);
                int col = Integer.parseInt(parts[3]);
                items.add(new GridModel(row, col, page, pkg));
            } catch (NumberFormatException ignored) { }
        }
        return items;
    }

    /**
     * Load ALL grid items across all pages (useful for a flat list or search).
     */
    public static List<GridModel> loadAll(Context ctx) {
        Map<String, ?> all = prefs(ctx).getAll();
        List<GridModel> items = new ArrayList<>();

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("grid_")) continue;
            if (KEY_COLUMNS.equals(key))  continue;   // skip the column-count entry

            String pkg = (String) entry.getValue();
            if (pkg == null || pkg.isEmpty()) continue;

            String[] parts = key.split("_");
            if (parts.length != 4) continue;

            try {
                int page = Integer.parseInt(parts[1]);
                int row  = Integer.parseInt(parts[2]);
                int col  = Integer.parseInt(parts[3]);
                items.add(new GridModel(row, col, page, pkg));
            } catch (NumberFormatException ignored) { }
        }
        return items;
    }

    /**
     * Wipe just one page's slots — used when re-saving a full grid after a
     * drag (e.g. HomeGridAdapter), so other pages aren't touched.
     */
    public static void clearPage(Context ctx, int page) {
        SharedPreferences.Editor editor = prefs(ctx).edit();
        String prefix = "grid_" + page + "_";
        for (String key : prefs(ctx).getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }

    /**
     * Wipe the entire grid — useful for "reset to default" or fresh setup.
     */
    public static void clearAll(Context ctx) {
        SharedPreferences.Editor editor = prefs(ctx).edit();
        Map<String, ?> all = prefs(ctx).getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("grid_")) editor.remove(key);
        }
        editor.apply();
    }

    /**
     * Check if a specific slot is already occupied.
     * Use this before dropping an icon to validate the target cell.
     */
    public static boolean isSlotOccupied(Context ctx, int page, int row, int col) {
        String key = new GridModel(row, col, page, "").toKey();
        String pkg = prefs(ctx).getString(key, null);
        return pkg != null && !pkg.isEmpty();
    }

    // ── Multi-page helpers ──────────────────────────────────────────

    /** Highest page index with at least one app placed, or -1 if Home is empty. */
    public static int getMaxUsedPage(Context ctx) {
        int max = -1;
        for (GridModel g : loadAll(ctx)) {
            if (g.page > max) max = g.page;
        }
        return max;
    }

    /**
     * How many pages the Home pager should show: enough to hold everything
     * placed, plus exactly one extra blank page at the end to grow into
     * (matches "I can increase their number by adding apps to the right").
     */
    public static int getPageCount(Context ctx) {
        return getMaxUsedPage(ctx) + 2; // -1(empty)+2=1 page; page 0 used+2=2 pages; etc.
    }

    /** First empty (row,col) on one specific page, or null if that page is full. */
    private static int[] findFirstEmptySlotOnPage(Context ctx, int page) {
        int columns = getColumns(ctx);
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            for (int col = 0; col < columns; col++) {
                if (!isSlotOccupied(ctx, page, row, col)) return new int[]{row, col};
            }
        }
        return null;
    }

    /** First empty (page,row,col) anywhere, scanning existing pages then spilling to a new one. */
    public static int[] findFirstEmptySlot(Context ctx) {
        for (int page = 0; page <= getMaxUsedPage(ctx) + 1; page++) {
            int[] slot = findFirstEmptySlotOnPage(ctx, page);
            if (slot != null) return new int[]{page, slot[0], slot[1]};
        }
        return new int[]{getMaxUsedPage(ctx) + 1, 0, 0}; // fresh page, first cell
    }

    /** Clears any slot(s) holding this package, on any page — used by "Remove from Home". */
    public static void removeApp(Context ctx, String packageName) {
        for (GridModel g : loadAll(ctx)) {
            if (g.packageName.equals(packageName)) clearSlot(ctx, g.page, g.row, g.col);
        }
    }

    /**
     * Places an app at (page,row,col), treating this as a MOVE: any existing
     * placement of the same package anywhere on Home is cleared first, so
     * dragging an already-placed icon elsewhere doesn't create a duplicate.
     * If the target cell is occupied by a different app, falls back to the
     * nearest empty cell (same page first, then spilling to a new page).
     */
    public static void placeApp(Context ctx, String packageName, int page, int row, int col) {
        for (GridModel g : loadAll(ctx)) {
            if (g.packageName.equals(packageName)) clearSlot(ctx, g.page, g.row, g.col);
        }

        if (isSlotOccupied(ctx, page, row, col)) {
            int[] onPage = findFirstEmptySlotOnPage(ctx, page);
            int[] target = onPage != null ? new int[]{page, onPage[0], onPage[1]} : findFirstEmptySlot(ctx);
            page = target[0];
            row  = target[1];
            col  = target[2];
        }

        saveSlot(ctx, new GridModel(row, col, page, packageName));
    }

    // ── Internal helper ─────────────────────────────────────────────
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}