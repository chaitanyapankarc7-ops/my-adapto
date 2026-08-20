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

    private OnSwipeUpListener listener;
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
        this.listener = l;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                intercepting = false;
                return false; // let children (dock icons) see DOWN so taps still register
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY; // negative = moved up
                if (!intercepting && -dy > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    intercepting = true;
                    return true; // proven vertical drag — take over from the child now
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
                // Covers the case where this DOWN reached us directly —
                // a gesture starting in empty space (no child underneath)
                // skips onInterceptTouchEvent entirely and comes straight
                // here, so downX/downY need to be captured here too.
                downX = ev.getX();
                downY = ev.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.computeCurrentVelocity(1000);
                float velocityY = velocityTracker.getYVelocity();
                float totalDy = ev.getY() - downY;

                // No longer gated on "intercepting" — that flag only ever
                // gets set from onInterceptTouchEvent, which Android skips
                // calling for gestures starting in empty space. If we're
                // receiving these events at all, we own the gesture either
                // way, so just check the movement directly.
                boolean farEnough = totalDy <= -minDragDistancePx;
                boolean fastEnough = -velocityY >= minFlingVelocity;
                Log.d("SwipeUpFrameLayout", "gesture end: totalDy=" + totalDy
                        + " velocityY=" + velocityY + " farEnough=" + farEnough
                        + " fastEnough=" + fastEnough);
                if ((farEnough || fastEnough) && listener != null) {
                    Log.d("SwipeUpFrameLayout", "triggering onSwipeUp");
                    listener.onSwipeUp();
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
