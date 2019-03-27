// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.autofill_assistant.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.fullscreen.ChromeFullscreenManager;
import org.chromium.chrome.browser.util.AccessibilityUtil;
import org.chromium.content_public.browser.GestureListenerManager;
import org.chromium.content_public.browser.GestureStateListener;
import org.chromium.content_public.browser.WebContents;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A view that filters out touch events, letting through only the touch events events that are
 * within a specified touchable area.
 *
 * <p>This view decides whether to forward gestures to the views below or the compositor view.
 *
 * <p>When accessibility (touch exploration) is enabled, this view:
 * <ul>
 * <li>avoids covering the top and bottom controls, even when the full overlay is on
 * <li>is fully visible and accessible as long as a touchable area is available.
 * TODO(crbug.com/806868):restrict access when using touch exploration as well
 * </ul>
 *
 * <p>TODO(crbug.com/806868): To better integrate with the layout, the event filtering and
 * forwarding implemented in this view should likely be a {@link
 * org.chromium.chrome.browser.compositor.layouts.eventfilter.EventFilter}, and part of a scene.
 */
public class TouchEventFilterView
        extends View implements ChromeFullscreenManager.FullscreenListener, GestureStateListener {

    /**
     * Complain after there's been {@link TAP_TRACKING_COUNT} taps within
     * {@link @TAP_TRACKING_DURATION_MS} in the unallowed area.
     */
    private static final int TAP_TRACKING_COUNT = 3;
    private static final long TAP_TRACKING_DURATION_MS = 15_000;

    /** A mode that describes what's happening to the current gesture. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NO_GESTURE_MODE, TRACKING_GESTURE_MODE, FORWARDING_GESTURE_MODE})
    private @interface GestureMode {}

    /** There's no current gesture. */
    private static final int NO_GESTURE_MODE = 0;

    /**
     * The current gesture is being tracked and buffered. The gesture might later on transition to
     * forwarding mode or it might be abandoned.
     */
    private static final int TRACKING_GESTURE_MODE = 1;

    /** The current gesture is being forwarded to the content view. */
    private static final int FORWARDING_GESTURE_MODE = 2;

    private AssistantOverlayDelegate mDelegate;
    private ChromeFullscreenManager mFullscreenManager;
    private GestureListenerManager mGestureListenerManager;
    private View mCompositorView;
    private final Paint mGrayOut;
    private final Paint mClear;

    @AssistantOverlayState
    private int mCurrentState = AssistantOverlayState.HIDDEN;

    private List<RectF> mTouchableArea = Collections.emptyList();

    /** Padding added between the element area and the grayed-out area. */
    private final float mPaddingPx;

    /** Size of the corner of the cleared-out areas. */
    private final float mCornerPx;

    /** A single RectF instance used for drawing, to avoid creating many instances when drawing. */
    private final RectF mDrawRect = new RectF();

    /**
     * Detects taps: {@link GestureDetector#onTouchEvent} returns {@code true} after a tap event.
     */
    private final GestureDetector mTapDetector;

    /**
     * Detects scrolls and flings: {@link GestureDetector#onTouchEvent} returns {@code true} a
     * scroll or fling event.
     */
    private final GestureDetector mScrollDetector;

    /** The current state of the gesture filter. */
    @GestureMode
    private int mCurrentGestureMode;

    /**
     * A capture of the motion event that are part of the current gesture, kept around in case they
     * need to be forwarded while {@code mCurrentGestureMode == TRACKING_GESTURE_MODE}.
     *
     * <p>Elements of this list must be recycled. Call {@link #cleanupCurrentGestureBuffer}.
     */
    private List<MotionEvent> mCurrentGestureBuffer = new ArrayList<>();

    /** Times, in millisecond, of unexpected taps detected outside of the allowed area. */
    private final List<Long> mUnexpectedTapTimes = new ArrayList<>();

    /** True while the browser is scrolling. */
    private boolean mBrowserScrolling;

    /**
     * Scrolling offset to use while scrolling right after scrolling.
     *
     * <p>This value shifts the touchable area by that many pixels while scrolling.
     */
    private int mBrowserScrollOffsetY;

    /**
     * Offset reported at the beginning of a scroll.
     *
     * <p>This is used to interpret the offsets reported by subsequent calls to {@link
     * #onScrollOffsetOrExtentChanged} or {@link #onScrollEnded}.
     */
    private int mInitialBrowserScrollOffsetY;

    /**
     * Current offset that applies on mTouchableArea.
     *
     * <p>This value shifts the touchable area by that many pixels after the end of a scroll and
     * before the next update, which resets this value.
     */
    private int mOffsetY;

    /**
     * Current top margin of this view.
     *
     * <p>Margins are set when the top or bottom controller are fully shown. When they're shown
     * partially, during a scroll, margins are always 0. The drawing takes care of adapting.
     *
     * <p>Always 0 unless accessibility is turned on.
     *
     * <p>TODO(crbug.com/806868): Better integrate this filter with the view layout to make it
     * automatic.
     */
    private int mMarginTop;

    /** Current bottom margin of this view. */
    private int mMarginBottom;

    public TouchEventFilterView(Context context) {
        this(context, null, 0);
    }

    public TouchEventFilterView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public TouchEventFilterView(Context context, AttributeSet attributeSet, int defStyle) {
        super(context, attributeSet, defStyle);
        mGrayOut = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGrayOut.setColor(
                ApiCompatibilityUtils.getColor(context.getResources(), R.color.black_alpha_65));
        mGrayOut.setStyle(Paint.Style.FILL);

        mPaddingPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
        mCornerPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics());
        // TODO(crbug.com/806868): Add support for XML attributes configuration.

        mClear = new Paint();
        mClear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        mTapDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });
        mScrollDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public boolean onScroll(
                    MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return true;
            }

            @Override
            public boolean onFling(
                    MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return true;
            }
        });
    }

    /** Initializes dependencies. */
    public void init(ChromeFullscreenManager fullscreenManager, WebContents webContents,
            View compositorView) {
        mFullscreenManager = fullscreenManager;
        mFullscreenManager.addListener(this);
        mGestureListenerManager = GestureListenerManager.fromWebContents(webContents);
        mGestureListenerManager.addListener(this);
        maybeUpdateVerticalMargins();
        mCompositorView = compositorView;
    }

    public void deInit() {
        mDelegate = null;
        mCompositorView = null;
        if (mFullscreenManager != null) {
            mFullscreenManager.removeListener(this);
            mFullscreenManager = null;
        }
        if (mGestureListenerManager != null) {
            mGestureListenerManager.removeListener(this);
            mGestureListenerManager = null;
        }
        cleanupCurrentGestureBuffer();
    }

    /**
     * Set this view delegate.
     */
    public void setDelegate(AssistantOverlayDelegate delegate) {
        mDelegate = delegate;
    }

    /**
     * Set the current state of the overlay.
     */
    public void setState(@AssistantOverlayState int newState) {
        mCurrentState = newState;

        // Reset tap counter each time we hide the overlay.
        if (mCurrentState == AssistantOverlayState.HIDDEN) {
            mUnexpectedTapTimes.clear();
        }

        updateVisibility();
        invalidate();
    }

    /**
     * Set the touchable area. This only applies if current state is AssistantOverlayState.PARTIAL.
     */
    public void setTouchableArea(List<RectF> touchableArea) {
        mTouchableArea = touchableArea;

        clearOffsets();
        if (mCurrentState == AssistantOverlayState.PARTIAL) {
            invalidate();
        }
    }

    private void updateVisibility() {
        if (AccessibilityUtil.isAccessibilityEnabled()) {
            // Touch exploration is fully disabled if there's an overlay in front. In this case, the
            // overlay must be fully gone and filtering elements for touch exploration must happen
            // at another level.
            //
            // TODO(crbug.com/806868): filter elements available to touch exploration, when it
            // is enabled.
            setVisibility(mCurrentState != AssistantOverlayState.FULL ? View.GONE : View.VISIBLE);
        }

        setAlpha(mCurrentState == AssistantOverlayState.HIDDEN ? 0.0f : 1.0f);
    }

    private void clearOffsets() {
        mOffsetY = 0;
        mInitialBrowserScrollOffsetY += mBrowserScrollOffsetY;
        mBrowserScrollOffsetY = 0;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getY() < getVisualViewportTop() || event.getY() > getVisualViewportBottom()) {
            // The event is meant for the top or bottom bar. Let it through.
            return false;
        }

        // Note that partial overlays have precedence over full overlays
        switch (mCurrentState) {
            case AssistantOverlayState.PARTIAL:
                return dispatchTouchEventWithPartialOverlay(event);
            case AssistantOverlayState.FULL:
                return dispatchTouchEventWithFullOverlay(event);
            default:
                return dispatchTouchEventWithNoOverlay();
        }
    }

    private boolean dispatchTouchEventWithNoOverlay() {
        if (mDelegate != null) {
            mDelegate.onUserInteractionInsideTouchableArea();
        }
        return false;
    }

    private boolean dispatchTouchEventWithFullOverlay(MotionEvent event) {
        if (mTapDetector.onTouchEvent(event)) {
            onUnexpectedTap(event);
        }
        return true;
    }

    /**
     * Let through or intercept gestures.
     *
     * <p>If the event starts a gesture, with ACTION_DOWN, within a touchable area, let the event
     * through.
     *
     * <p>If the event starts a gesture outside a touchable area, forward it to the compositor once
     * it's clear that it's a scroll, fling or multi-touch event - and not a tap event.
     *
     * @return true if the event was handled by this view, as defined for {@link
     *         View#dispatchTouchEvent}
     */
    private boolean dispatchTouchEventWithPartialOverlay(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: // Starts a new gesture.

                // Reset is needed, as ACTION_DOWN can interrupt a running gesture
                resetCurrentGesture();

                if (shouldLetEventThrough(event)) {
                    if (mDelegate != null) {
                        mDelegate.onUserInteractionInsideTouchableArea();
                    }
                    // This is the last we'll hear of this gesture unless it turns multi-touch. No
                    // need to track or forward it.
                    return false;
                }
                if (event.getPointerCount() > 0 && event.getPointerId(0) != 0) {
                    // We're being offered a previously let-through gesture, which turned
                    // multi-touch. This isn't a real gesture start.
                    return false;
                }

                // Track the gesture in case this is a tap, which we should handle, or a
                // scroll/fling/pinch, which we should forward.
                mCurrentGestureMode = TRACKING_GESTURE_MODE;
                mCurrentGestureBuffer.add(MotionEvent.obtain(event));
                mScrollDetector.onTouchEvent(event);
                mTapDetector.onTouchEvent(event);
                return true;

            case MotionEvent.ACTION_MOVE: // Continues a gesture.
                switch (mCurrentGestureMode) {
                    case TRACKING_GESTURE_MODE:
                        if (mScrollDetector.onTouchEvent(event)) {
                            // The current gesture is a scroll or a fling. Forward it.
                            startForwardingGesture(event);
                            return true;
                        }

                        // Continue accumulating events.
                        mTapDetector.onTouchEvent(event);
                        mCurrentGestureBuffer.add(MotionEvent.obtain(event));
                        return true;

                    case FORWARDING_GESTURE_MODE:
                        mCompositorView.dispatchTouchEvent(event);
                        return true;

                    default:
                        return true;
                }

            case MotionEvent.ACTION_POINTER_DOWN: // Continues a multi-touch gesture
            case MotionEvent.ACTION_POINTER_UP:
                switch (mCurrentGestureMode) {
                    case TRACKING_GESTURE_MODE:
                        // The current gesture has just become a multi-touch gesture. Forward it.
                        startForwardingGesture(event);
                        return true;

                    case FORWARDING_GESTURE_MODE:
                        mCompositorView.dispatchTouchEvent(event);
                        return true;

                    default:
                        return true;
                }

            case MotionEvent.ACTION_UP: // Ends a gesture
            case MotionEvent.ACTION_CANCEL:
                switch (mCurrentGestureMode) {
                    case TRACKING_GESTURE_MODE:
                        if (mTapDetector.onTouchEvent(event)) {
                            onUnexpectedTap(event);
                        }
                        resetCurrentGesture();
                        return true;

                    case FORWARDING_GESTURE_MODE:
                        mCompositorView.dispatchTouchEvent(event);
                        resetCurrentGesture();
                        return true;

                    default:
                        return true;
                }

            default:
                return true;
        }
    }

    /** Clears all information about the current gesture. */
    private void resetCurrentGesture() {
        mCurrentGestureMode = NO_GESTURE_MODE;
        cleanupCurrentGestureBuffer();
    }

    /** Clears {@link #mCurrentGestureStart}, recycling it if necessary. */
    private void cleanupCurrentGestureBuffer() {
        for (MotionEvent event : mCurrentGestureBuffer) {
            event.recycle();
        }
        mCurrentGestureBuffer.clear();
    }

    /** Enables forwarding of the current gesture, starting with {@link currentEvent}. */
    private void startForwardingGesture(MotionEvent currentEvent) {
        mCurrentGestureMode = FORWARDING_GESTURE_MODE;
        for (MotionEvent event : mCurrentGestureBuffer) {
            mCompositorView.dispatchTouchEvent(event);
        }
        cleanupCurrentGestureBuffer();
        mCompositorView.dispatchTouchEvent(currentEvent);
    }

    /**
     * Returns {@code true} if {@code event} is for a position in the touchable area
     * or the top/bottom bar.
     */
    private boolean shouldLetEventThrough(MotionEvent event) {
        int yTop = getVisualViewportTop();
        int yBottom = getVisualViewportBottom();
        if (event.getY() < yTop || event.getY() > yBottom) {
            // Let it through. The event is meant for the top or bottom bar UI controls, not the
            // webpage.
            return true;
        }
        int height = yBottom - yTop;
        return isInTouchableArea(((float) event.getX()) / getWidth(),
                (((float) event.getY() - yTop + mBrowserScrollOffsetY + mOffsetY) / height));
    }

    /** Returns the origin of the visual viewport in this view. */
    @Override
    @SuppressLint("CanvasSize")
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mCurrentState == AssistantOverlayState.HIDDEN) {
            return;
        }
        canvas.drawPaint(mGrayOut);

        int width = canvas.getWidth();
        int yTop = getVisualViewportTop();
        if (yTop > 0) {
            canvas.drawRect(0, 0, width, yTop, mClear);
        }
        int yBottom = getVisualViewportBottom();
        if (yBottom > 0) {
            canvas.drawRect(0, yBottom, width, canvas.getHeight(), mClear);
        }

        if (mCurrentState != AssistantOverlayState.PARTIAL) {
            return;
        }

        int height = yBottom - yTop;
        for (RectF rect : mTouchableArea) {
            mDrawRect.left = rect.left * width - mPaddingPx;
            mDrawRect.top =
                    yTop + rect.top * height - mPaddingPx - mBrowserScrollOffsetY - mOffsetY;
            mDrawRect.right = rect.right * width + mPaddingPx;
            mDrawRect.bottom =
                    yTop + rect.bottom * height + mPaddingPx - mBrowserScrollOffsetY - mOffsetY;
            if (mDrawRect.left <= 0 && mDrawRect.right >= width) {
                // Rounded corners look strange in the case where the rectangle takes exactly the
                // width of the screen.
                canvas.drawRect(mDrawRect, mClear);
            } else {
                canvas.drawRoundRect(mDrawRect, mCornerPx, mCornerPx, mClear);
            }
        }
    }

    @Override
    public void onContentOffsetChanged(int offset) {
        invalidate();
    }

    @Override
    public void onControlsOffsetChanged(int topOffset, int bottomOffset, boolean needsAnimate) {
        maybeUpdateVerticalMargins();
        invalidate();
    }

    @Override
    public void onToggleOverlayVideoMode(boolean enabled) {}

    @Override
    public void onBottomControlsHeightChanged(int bottomControlsHeight) {
        invalidate();
    }

    @Override
    public void onUpdateViewportSize() {
        invalidate();
    }

    /** Called at the beginning of a scroll gesture triggered by the browser. */
    @Override
    public void onScrollStarted(int scrollOffsetY, int scrollExtentY) {
        mBrowserScrolling = true;
        mInitialBrowserScrollOffsetY = scrollOffsetY;
        mBrowserScrollOffsetY = 0;
        invalidate();
    }

    /** Called during a scroll gesture triggered by the browser. */
    @Override
    public void onScrollOffsetOrExtentChanged(int scrollOffsetY, int scrollExtentY) {
        if (!mBrowserScrolling) {
            // onScrollOffsetOrExtentChanged will be called alone, without onScrollStarted during a
            // Javascript-initiated scroll.
            askForTouchableAreaUpdate();
            return;
        }
        mBrowserScrollOffsetY = scrollOffsetY - mInitialBrowserScrollOffsetY;
        invalidate();
        askForTouchableAreaUpdate();
    }

    /** Called at the end of a scroll gesture triggered by the browser. */
    @Override
    public void onScrollEnded(int scrollOffsetY, int scrollExtentY) {
        if (!mBrowserScrolling) {
            return;
        }
        mOffsetY += (scrollOffsetY - mInitialBrowserScrollOffsetY);
        mBrowserScrollOffsetY = 0;
        mBrowserScrolling = false;
        invalidate();
        askForTouchableAreaUpdate();
    }

    /** Considers whether to let the client know about unexpected taps. */
    private void onUnexpectedTap(MotionEvent e) {
        long eventTimeMs = e.getEventTime();
        for (Iterator<Long> iter = mUnexpectedTapTimes.iterator(); iter.hasNext();) {
            Long timeMs = iter.next();
            if ((eventTimeMs - timeMs) >= TAP_TRACKING_DURATION_MS) {
                iter.remove();
            }
        }
        mUnexpectedTapTimes.add(eventTimeMs);
        if (mUnexpectedTapTimes.size() == TAP_TRACKING_COUNT && mDelegate != null) {
            mDelegate.onUnexpectedTaps();
            mUnexpectedTapTimes.clear();
        }
    }

    private void askForTouchableAreaUpdate() {
        if (mDelegate != null) {
            mDelegate.updateTouchableArea();
        }
    }

    private boolean isInTouchableArea(float x, float y) {
        for (RectF rect : mTouchableArea) {
            if (rect.contains(x, y, x, y)) {
                return true;
            }
        }
        return false;
    }

    /** Gets the top position, within this view, of the visual viewport. */
    private int getVisualViewportTop() {
        return getTopBarHeight() - mMarginTop;
    }

    /** Gets the bottom position, within this view, of the visual viewport. */
    private int getVisualViewportBottom() {
        return getHeight() - (getBottomBarHeight() - mMarginBottom);
    }

    /** Gets the height of the visual viewport. */
    private int getVisualViewportHeight() {
        return getVisualViewportBottom() - getVisualViewportTop();
    }

    /** Gets the current height of the bottom bar. */
    private int getBottomBarHeight() {
        if (mFullscreenManager == null) return 0;
        return (int) (mFullscreenManager.getBottomControlsHeight()
                - mFullscreenManager.getBottomControlOffset());
    }

    /** Gets the current height of the top bar. */
    private int getTopBarHeight() {
        if (mFullscreenManager == null) return 0;
        return (int) mFullscreenManager.getContentOffset();
    }

    /**
     * Updates the vertical margins of the view when accessibility is enabled.
     *
     * <p>When the controls are fully visible, the view covers has just the right margins to cover
     * only the web page.
     *
     * <p>When the controls are fully invisible, the view covers everything, which matches the web
     * page.
     *
     * <p>When the controls are partially visible, when animating, the view covers everything,
     * including parts of the controls. Drawing takes care of making this look good.
     */
    private void maybeUpdateVerticalMargins() {
        if (mFullscreenManager == null) return;

        if (mFullscreenManager.areBrowserControlsFullyVisible()
                && AccessibilityUtil.isAccessibilityEnabled()) {
            setVerticalMargins(getTopBarHeight(), getBottomBarHeight());
        } else {
            setVerticalMargins(0, 0);
        }
    }

    /** Sets top and bottom margin of the view, if necessary */
    private void setVerticalMargins(int top, int bottom) {
        if (top == mMarginTop && bottom == mMarginBottom) return;

        mMarginTop = top;
        mMarginBottom = bottom;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) getLayoutParams();
        params.setMargins(/* left= */ 0, /* top= */ top, /* right= */ 0, /* bottom= */ bottom);
        setLayoutParams(params);
    }
}
