package com.tejyash.myadapto.overlay;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;

import com.tejyash.myadapto.R;
import com.tejyash.myadapto.accessibility.ScreenContentAccessibilityService;
import com.tejyash.myadapto.activity.VoiceAssitentPage;

/**
 * Floating draggable assistant bubble overlay.
 * Tap launches VoiceAssitentPage with current screen text snapshot.
 */
public class OverlayService extends Service {

    private static volatile boolean isRunning = false;
    private WindowManager windowManager;
    private View bubbleView;

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        bubbleView = inflater.inflate(R.layout.layout_assistant_bubble, null);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int sizePx = Math.round(65 * getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                sizePx, sizePx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 300;

        try {
            windowManager.addView(bubbleView, params);
            isRunning = true;
            
            startBreathingAnimation();
        } catch (Exception e) {
            stopSelf();
            return;
        }

        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            isDragging = true;
                        }
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        try {
                            windowManager.updateViewLayout(bubbleView, params);
                        } catch (Exception ignored) {}
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            String snapshot = ScreenContentAccessibilityService.getLastScreenText();
                            Intent intent = new Intent(OverlayService.this, VoiceAssitentPage.class);
                            intent.putExtra("screen_snapshot", snapshot);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void startBreathingAnimation() {
        if (bubbleView == null) return;
        View bg = bubbleView.findViewById(R.id.bubble_bg);
        if (bg == null) bg = bubbleView;

        ScaleAnimation anim = new ScaleAnimation(0.95f, 1.05f, 0.95f, 1.05f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(2000);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        bg.startAnimation(anim);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (bubbleView != null && windowManager != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
            bubbleView = null;
        }
    }
}
