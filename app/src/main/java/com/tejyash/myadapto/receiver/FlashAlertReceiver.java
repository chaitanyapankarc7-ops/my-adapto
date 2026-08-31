package com.tejyash.myadapto.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Receiver that detects incoming calls and triggers the CallAlertService.
 */
public class FlashAlertReceiver extends BroadcastReceiver {

    private static final String TAG = "FlashAlertReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null || !action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) return;

        com.tejyash.myadapto.accessibility.AccessibilityPreferences prefs =
                com.tejyash.myadapto.accessibility.AccessibilityPreferences.get(context);
        if (!prefs.isFlashlightAlertEnabled() && !prefs.isBigVibrationEnabled()) {
            return;
        }

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        Log.d(TAG, "Phone state changed: " + state);

        Intent serviceIntent = new Intent(context, CallAlertService.class);
        try {
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                context.startService(serviceIntent);
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                context.stopService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error managing CallAlertService", e);
        }
    }
}
