package com.tejyash.myadapto.launcher;

/**
 * Represents one icon slot on the home screen grid.
 *
 * row, col  — position in the grid (0-based)
 * page      — which home screen page (0 = first page, 1 = second, etc.)
 * packageName — the app this slot points to (e.g. "com.google.android.gm")
 *
 * We store packageName instead of the icon/label directly because
 * the icon and label are always fetched fresh from PackageManager —
 * that way they auto-update when the user updates an app.
 */
public class GridModel {

    public int    row;
    public int    col;
    public int    page;
    public String packageName;

    public GridModel(int row, int col, int page, String packageName) {
        this.row         = row;
        this.col         = col;
        this.page        = page;
        this.packageName = packageName;
    }

    // Unique key used to store this slot in SharedPreferences
    // e.g.  "grid_0_2_1"  →  page 0, row 2, col 1
    public String toKey() {
        return "grid_" + page + "_" + row + "_" + col;
    }

    @Override
    public String toString() {
        return "GridModel{page=" + page + " row=" + row + " col=" + col
                + " pkg=" + packageName + "}";
    }
}