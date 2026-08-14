package com.tejyash.myadapto.launcher;

public class LauncherItem {

    public LauncherItemType type;

    public String packageName;

    public int row;

    public int col;

    public int page;

    public LauncherItem(
            LauncherItemType type,
            String packageName,
            int row,
            int col,
            int page
    ) {
        this.type = type;
        this.packageName = packageName;
        this.row = row;
        this.col = col;
        this.page = page;
    }
}