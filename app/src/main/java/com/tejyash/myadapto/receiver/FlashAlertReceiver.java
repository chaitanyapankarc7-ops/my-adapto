package com.tejyash.myadapto.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.telephony.TelephonyManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.tejyash.myadapto.accessibility.AccessibilityPreferences;

public class FlashAlertReceiver extends BroadcastReceiver {

    private static boolean isFlashing = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            if (AccessibilityPreferences.get(context).isFlashlightAlertEnabled()) {
                startFlashing(context);
            }
            if (AccessibilityPreferences.get(context).isVibrateAlertEnabled()) {
                startVibrating(context);
            }
        } else {
            stopFlashing();
            stopVibrating();
        }
    }

    private void startFlashing(Context context) {
        if (isFlashing) return;
        isFlashing = true;

        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        new Thread(() -> {
            try {
                String cameraId = cameraManager.getCameraIdList()[0];
                while (isFlashing) {
                    cameraManager.setTorchMode(cameraId, true);
                    Thread.sleep(300);
                    cameraManager.setTorchMode(cameraId, false);
                    Thread.sleep(300);
                }
            } catch (CameraAccessException | InterruptedException | ArrayIndexOutOfBoundsException ignored) {
                isFlashing = false;
            }
        }).start();
    }

    private void stopFlashing() {
        isFlashing = false;
    }

    private static boolean isVibrating = false;

    private void startVibrating(Context context) {
        if (isVibrating) return;
        isVibrating = true;

        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        new Thread(() -> {
            while (isVibrating) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(500);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    isVibrating = false;
                }
            }
        }).start();
    }

    private void stopVibrating() {
        isVibrating = false;
    }
}
