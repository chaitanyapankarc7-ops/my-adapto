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

    // ── Internal helper ─────────────────────────────────────────────
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}