package com.tejyash.myadapto.model;

import android.graphics.drawable.Drawable;

/** One installed app shown in the home screen grid. */
public class AppInfo {
    public final String   label;
    public final String   packageName;
    public final String   activityName;
    public final Drawable icon;

    public AppInfo(String label, String packageName, String activityName, Drawable icon) {
        this.label        = label;
        this.packageName  = packageName;
        this.activityName = activityName;
        this.icon         = icon;
    }
}
