package com.tejyash.myadapto.receiver;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;

import com.tejyash.myadapto.accessibility.AccessibilityPreferences;

/**
 * Service that handles the actual flashing and vibration during a call.
 * Using a Service ensures the background work isn't killed when the Receiver finishes.
 */
public class CallAlertService extends Service {
    private static final String TAG = "CallAlertService";
    
    private volatile boolean isRunning = false;
    private Thread alertThread = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) return START_STICKY;
        
        isRunning = true;
        startAlerts();
        return START_STICKY;
    }

    private void startAlerts() {
        alertThread = new Thread(() -> {
            AccessibilityPreferences prefs = AccessibilityPreferences.get(this);
            boolean useFlash = prefs.isFlashlightAlertEnabled();
            boolean useVib = prefs.isBigVibrationEnabled();
            
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            String flashId = null;

            if (useFlash) {
                try {
                    flashId = findFlashCameraId(cameraManager);
                } catch (Exception e) {
                    Log.e(TAG, "Could not find flashlight", e);
                }
            }

            while (isRunning) {
                try {
                    // Flash ON
                    if (useFlash && flashId != null) {
                        cameraManager.setTorchMode(flashId, true);
                    }
                    
                    // Vibrate ON (Big vibration)
                    if (useVib && vibrator != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(400);
                        }
                    }

                    Thread.sleep(400);

                    // Flash OFF
                    if (useFlash && flashId != null) {
                        cameraManager.setTorchMode(flashId, false);
                    }
                    
                    Thread.sleep(400);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Alert loop error", e);
                    break;
                }
            }
            
            // Cleanup
            if (useFlash && flashId != null) {
                try {
                    cameraManager.setTorchMode(flashId, false);
                } catch (Exception ignored) {}
            }
            if (useVib && vibrator != null) {
                vibrator.cancel();
            }
        });
        alertThread.start();
    }

    private String findFlashCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Boolean hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (hasFlash != null && hasFlash) return id;
        }
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (alertThread != null) {
            alertThread.interrupt();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
