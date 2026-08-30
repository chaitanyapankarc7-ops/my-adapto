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

    private static volatile boolean isFlashing = false;
    private static volatile boolean isVibrating = false;
    private static Thread flashThread = null;
    private static Thread vibrateThread = null;
    private static String activeFlashCameraId = null;
    private static CameraManager activeCameraManager = null;
    private static Vibrator activeVibrator = null;

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

    private synchronized void startFlashing(Context context) {
        if (isFlashing) return;
        isFlashing = true;

        activeCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        activeFlashCameraId = findFlashCameraId(activeCameraManager);
        if (activeFlashCameraId == null) {
            isFlashing = false;
            return;
        }

        flashThread = new Thread(() -> {
            try {
                while (isFlashing && activeFlashCameraId != null && activeCameraManager != null) {
                    activeCameraManager.setTorchMode(activeFlashCameraId, true);
                    Thread.sleep(300);
                    if (!isFlashing) break;
                    activeCameraManager.setTorchMode(activeFlashCameraId, false);
                    Thread.sleep(300);
                }
            } catch (Exception ignored) {
            } finally {
                if (activeFlashCameraId != null && activeCameraManager != null) {
                    try {
                        activeCameraManager.setTorchMode(activeFlashCameraId, false);
                    } catch (Exception ignored) {}
                }
                isFlashing = false;
            }
        });
        flashThread.start();
    }

    private synchronized void stopFlashing() {
        isFlashing = false;
        if (activeCameraManager != null && activeFlashCameraId != null) {
            try {
                activeCameraManager.setTorchMode(activeFlashCameraId, false);
            } catch (Exception ignored) {}
        }
        if (flashThread != null) {
            flashThread.interrupt();
            flashThread = null;
        }
    }

    private String findFlashCameraId(CameraManager cameraManager) {
        if (cameraManager == null) return null;
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) {
                    return id;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private synchronized void startVibrating(Context context) {
        if (isVibrating) return;
        isVibrating = true;

        activeVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (activeVibrator == null) {
            isVibrating = false;
            return;
        }

        vibrateThread = new Thread(() -> {
            try {
                while (isVibrating && activeVibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activeVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        activeVibrator.vibrate(500);
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ignored) {
            } finally {
                if (activeVibrator != null) {
                    try {
                        activeVibrator.cancel();
                    } catch (Exception ignored) {}
                }
                isVibrating = false;
            }
        });
        vibrateThread.start();
    }

    private synchronized void stopVibrating() {
        isVibrating = false;
        if (activeVibrator != null) {
            try {
                activeVibrator.cancel();
            } catch (Exception ignored) {}
        }
        if (vibrateThread != null) {
            vibrateThread.interrupt();
            vibrateThread = null;
        }
    }
}
