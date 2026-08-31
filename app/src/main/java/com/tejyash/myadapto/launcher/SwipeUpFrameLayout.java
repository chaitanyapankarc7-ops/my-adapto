package com.tejyash.myadapto.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * A generous, dedicated hit-zone for "swipe up to open the app drawer" —
 * wraps the dock so the whole bottom strip of the screen responds to an
 * upward swipe, not just a thin sliver between components.
 *
 * A plain tap still reaches the dock icon children normally: this only
 * steals the touch sequence away from them once it's proven itself to be
 * a real, mostly-vertical drag past touch-slop — not on every touch-down.
 * That's the standard pattern (same idea RecyclerView/ViewPager2 use
 * internally to decide "is this a scroll or a click").
 */
public class SwipeUpFrameLayout extends FrameLayout {

    public interface OnSwipeUpListener {
        void onSwipeUp();
    }

    public interface OnSwipeDownListener {
        void onSwipeDown();
    }

    private OnSwipeUpListener upListener;
    private OnSwipeDownListener downListener;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int minDragDistancePx;

    private float downX, downY;
    private boolean intercepting;
    private VelocityTracker velocityTracker;

    public SwipeUpFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        minDragDistancePx = Math.round(50 * context.getResources().getDisplayMetrics().density);
    }

    public void setOnSwipeUpListener(OnSwipeUpListener l) {
        this.upListener = l;
    }

    public void setOnSwipeDownListener(OnSwipeDownListener l) {
        this.downListener = l;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                intercepting = false;
                return false; // let children see DOWN so taps still register
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                // Swipe UP: negative dy
                if (!intercepting && -dy > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    intercepting = true;
                    return true;
                }
                // Swipe DOWN: positive dy (captured in upper 75% of screen)
                if (!intercepting && dy > touchSlop && Math.abs(dy) > Math.abs(dx) && downY < (getHeight() > 0 ? getHeight() * 0.75f : Float.MAX_VALUE)) {
                    intercepting = true;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(ev);

        // Allow super class to process events for click/long-click detection
        super.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.computeCurrentVelocity(1000);
                float velocityY = velocityTracker.getYVelocity();
                float totalDy = ev.getY() - downY;

                // Swipe UP (App Drawer)
                boolean upFarEnough = totalDy <= -minDragDistancePx;
                boolean upFastEnough = -velocityY >= minFlingVelocity;
                if ((upFarEnough || upFastEnough) && upListener != null) {
                    upListener.onSwipeUp();
                }

                // Swipe DOWN (Quick Controls & Notifications)
                boolean downFarEnough = totalDy >= minDragDistancePx;
                boolean downFastEnough = velocityY >= minFlingVelocity;
                if ((downFarEnough || downFastEnough) && downListener != null && downY < (getHeight() > 0 ? getHeight() * 0.75f : Float.MAX_VALUE)) {
                    downListener.onSwipeDown();
                }

                velocityTracker.recycle();
                velocityTracker = null;
                intercepting = false;
                return true;
            default:
                return true;
        }
    }
}
