package com.tejyash.myadapto.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Reads screen content from external active apps when enabled by the user in Android Accessibility Settings.
 * Provides snapshot context for Voice Assistant and Groq AI queries.
 */
public class ScreenContentAccessibilityService extends AccessibilityService {

    private static volatile String lastScreenText = "";
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_DEPTH = 15;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // Ignore events coming from My Adapto itself
        CharSequence pkg = event.getPackageName();
        if (pkg != null && pkg.toString().equals(getPackageName())) {
            return;
        }

        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                StringBuilder builder = new StringBuilder();
                extractText(rootNode, builder, 0);
                String text = builder.toString().trim();
                if (!text.isEmpty()) {
                    lastScreenText = text;
                }
                rootNode.recycle();
            }
        } catch (Exception ignored) {}
    }

    private void extractText(AccessibilityNodeInfo node, StringBuilder builder, int depth) {
        if (node == null || depth > MAX_DEPTH || builder.length() >= MAX_TEXT_LENGTH) return;

        CharSequence text = node.getText();
        if (!TextUtils.isEmpty(text)) {
            String clean = text.toString().trim();
            if (!clean.isEmpty()) {
                builder.append(clean).append(". ");
            }
        } else {
            CharSequence desc = node.getContentDescription();
            if (!TextUtils.isEmpty(desc)) {
                String clean = desc.toString().trim();
                if (!clean.isEmpty()) {
                    builder.append(clean).append(". ");
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            if (builder.length() >= MAX_TEXT_LENGTH) break;
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractText(child, builder, depth + 1);
                child.recycle();
            }
        }
    }

    public static String getLastScreenText() {
        return lastScreenText != null ? lastScreenText : "";
    }

    public static boolean isServiceEnabled(Context context) {
        if (context == null) return false;
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        String serviceName = context.getPackageName() + "/" + ScreenContentAccessibilityService.class.getName();
        return enabledServices.contains(serviceName);
    }

    @Override
    public void onInterrupt() {}
}
