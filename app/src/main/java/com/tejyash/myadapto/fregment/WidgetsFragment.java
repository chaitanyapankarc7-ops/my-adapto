package com.tejyash.myadapto.fregment;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.activity.VoiceAssitentPage;

/**
 * The Widgets page — a small toolbox of accessibility tools:
 *   Voice Assistant → opens VoiceAssitentPage (same screen the dock uses)
 *   SOS              → dials the emergency number directly
 *   Flashlight       → toggles the device's real torch via CameraManager
 *   Magnifier        → opens the camera app as a quick stand-in loupe
 *
 * Flashlight is the only tile with real state (on/off), so it's the
 * only one that needs cleanup: onPause() turns the torch back off so
 * it never stays lit after the user leaves this page.
 */
public class WidgetsFragment extends Fragment {

    private CameraManager cameraManager;
    private String        flashCameraId; // null if this device has no flash unit
    private boolean       torchOn = false;

    public WidgetsFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_widgets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupVoiceTile(view);
        setupSosTile(view);
        setupFlashlightTile(view);
        setupMagnifierTile(view);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Never leave the torch on after navigating away from this page.
        if (torchOn && flashCameraId != null) {
            try {
                cameraManager.setTorchMode(flashCameraId, false);
            } catch (CameraAccessException ignored) { }
            torchOn = false;
        }
    }

    private void setupVoiceTile(View root) {
        CardView tile = root.findViewById(R.id.tile_voice);
        tile.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), VoiceAssitentPage.class)));
    }

    private void setupSosTile(View root) {
        CardView tile = root.findViewById(R.id.tile_sos);
        tile.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))));
    }

    private void setupMagnifierTile(View root) {
        // No dedicated magnifier lens in this app yet — the camera app,
        // pinch-zoomed, is a reasonable quick stand-in for reading small
        // text or labels up close.
        CardView tile = root.findViewById(R.id.tile_magnifier);
        tile.setOnClickListener(v ->
                startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE)));
    }

    private void setupFlashlightTile(View root) {
        cameraManager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
        flashCameraId = findFlashCameraId();

        CardView tile = root.findViewById(R.id.tile_flashlight);
        tile.setOnClickListener(v -> toggleFlashlight());
    }

    /** Finds the first camera on the device that actually has a flash unit. */
    @Nullable
    private String findFlashCameraId() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) return id;
            }
        } catch (CameraAccessException ignored) { }
        return null; // device has no flash unit — tile becomes a no-op tap
    }

    private void toggleFlashlight() {
        if (flashCameraId == null) return;
        try {
            torchOn = !torchOn;
            cameraManager.setTorchMode(flashCameraId, torchOn);
        } catch (CameraAccessException e) {
            torchOn = false;
        }
    }
}