package com.tejyash.myadapto.launcher;


/**
 * Represents one item placed on the Adapto home-screen grid.
 *
 * An item can be:
 *  - an APP
 *  - a CLOCK widget
 *  - a BATTERY widget
 *  - a WEATHER widget
 *  - etc.
 *
 * row, col  -> position inside the grid
 * page      -> home-screen page
 * packageName -> package name for APP items
 */
public class GridModel {

    public int row;
    public int col;
    public int page;

    // Width and Height in grid cells
    public int spanX = 1;
    public int spanY = 1;

    // Existing app package name.
    // Widgets don't need a package name, so this can be empty.
    public String packageName;

    // New: tells us what kind of item this is.
    public LauncherItemType type;


    /**
     * OLD constructor.
     *
     * We keep this so your existing code such as:
     *
     * new GridModel(row, col, page, packageName)
     *
     * continues working.
     *
     * Anything created this way is treated as an APP.
     */
    public GridModel(
            int row,
            int col,
            int page,
            String packageName
    ) {
        this(
                row,
                col,
                page,
                packageName,
                LauncherItemType.APP
        );
    }


    /**
     * NEW constructor.
     *
     * Use this when creating apps or widgets.
     */
    public GridModel(
            int row,
            int col,
            int page,
            String packageName,
            LauncherItemType type
    ) {
        this.row = row;
        this.col = col;
        this.page = page;
        this.packageName = packageName;
        this.type = type;
    }


    /**
     * Returns the unique SharedPreferences key
     * for this grid position.
     *
     * Example:
     *
     * grid_0_2_1
     *
     * means:
     * page 0
     * row 2
     * column 1
     */
    public String toKey() {
        return "grid_" + page + "_" + row + "_" + col;
    }


    /**
     * Returns a readable representation useful for debugging.
     */
    @Override
    public String toString() {
        return "GridModel{" +
                "page=" + page +
                ", row=" + row +
                ", col=" + col +
                ", type=" + type +
                ", packageName='" + packageName + '\'' +
                '}';
    }
}