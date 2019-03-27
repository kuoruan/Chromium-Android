// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.compositor.layouts.phone.stack;

import static org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.AnimatableAnimation.addAnimation;

import android.support.annotation.IntDef;
import android.view.animation.Interpolator;

import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.compositor.animation.CompositorAnimator;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation;
import org.chromium.chrome.browser.compositor.layouts.ChromeAnimation.Animatable;
import org.chromium.chrome.browser.compositor.layouts.Layout.Orientation;
import org.chromium.chrome.browser.compositor.layouts.components.LayoutTab;
import org.chromium.ui.interpolators.BakedBezierInterpolator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A factory that builds animations for the tab stack.
 */
public abstract class StackAnimation {
    @IntDef({OverviewAnimationType.ENTER_STACK, OverviewAnimationType.NEW_TAB_OPENED,
            OverviewAnimationType.TAB_FOCUSED, OverviewAnimationType.VIEW_MORE,
            OverviewAnimationType.REACH_TOP, OverviewAnimationType.DISCARD,
            OverviewAnimationType.DISCARD_ALL, OverviewAnimationType.UNDISCARD,
            OverviewAnimationType.START_PINCH, OverviewAnimationType.FULL_ROLL,
            OverviewAnimationType.NONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface OverviewAnimationType {
        int ENTER_STACK = 0;
        int NEW_TAB_OPENED = 1;
        int TAB_FOCUSED = 2;
        int VIEW_MORE = 3;
        int REACH_TOP = 4;
        // Commit/uncommit tab discard animations
        int DISCARD = 5;
        int DISCARD_ALL = 6;
        int UNDISCARD = 7;
        // Start pinch animation un-tilt all the tabs.
        int START_PINCH = 8;
        // Special animation
        int FULL_ROLL = 9;
        // Used for when the current state of the system is not animating
        int NONE = 10;
    }

    protected static final int ENTER_STACK_TOOLBAR_ALPHA_DURATION = 100;
    protected static final int ENTER_STACK_TOOLBAR_ALPHA_DELAY = 100;
    protected static final int ENTER_STACK_ANIMATION_DURATION = 300;
    protected static final int ENTER_STACK_RESIZE_DELAY = 10;
    protected static final int ENTER_STACK_BORDER_ALPHA_DURATION = 200;
    protected static final float ENTER_STACK_SIZE_RATIO = 0.35f;

    protected static final int TAB_FOCUSED_TOOLBAR_ALPHA_DURATION = 250;
    protected static final int TAB_FOCUSED_TOOLBAR_ALPHA_DELAY = 0;
    protected static final int TAB_FOCUSED_ANIMATION_DURATION = 400;
    protected static final int TAB_FOCUSED_Y_STACK_DURATION = 200;
    protected static final int TAB_FOCUSED_BORDER_ALPHA_DURATION = 200;
    protected static final int TAB_FOCUSED_BORDER_ALPHA_DELAY = 0;
    protected static final int TAB_FOCUSED_MAX_DELAY = 100;

    protected static final int VIEW_MORE_ANIMATION_DURATION = 400;
    protected static final float VIEW_MORE_SIZE_RATIO = 0.75f;
    protected static final int VIEW_MORE_MIN_SIZE = 200;

    protected static final int REACH_TOP_ANIMATION_DURATION = 400;

    protected static final int UNDISCARD_ANIMATION_DURATION = 150;

    protected static final int TAB_OPENED_ANIMATION_DURATION = 300;

    protected static final int DISCARD_ANIMATION_DURATION = 150;
    protected static final int TAB_REORDER_DURATION = 500;
    protected static final int TAB_REORDER_START_SPAN = 400;

    protected static final int START_PINCH_ANIMATION_DURATION = 75;

    protected static final int FULL_ROLL_ANIMATION_DURATION = 1000;

    protected final float mWidth;
    protected final float mHeight;
    protected final float mTopBrowserControlsHeight;
    protected final float mBorderTopHeight;
    protected final float mBorderTopOpaqueHeight;
    protected final float mBorderLeftWidth;
    protected final Stack mStack;

    /**
     * Protected constructor.
     *
     * @param stack                       The stack using the animations provided by this class.
     * @param width                       The width of the layout in dp.
     * @param height                      The height of the layout in dp.
     * @param heightMinusBrowserControls  The height of the layout minus the browser controls in dp.
     * @param borderFramePaddingTop       The top padding of the border frame in dp.
     * @param borderFramePaddingTopOpaque The opaque top padding of the border frame in dp.
     * @param borderFramePaddingLeft      The left padding of the border frame in dp.
     */
    protected StackAnimation(Stack stack, float width, float height, float topBrowserControlsHeight,
            float borderFramePaddingTop, float borderFramePaddingTopOpaque,
            float borderFramePaddingLeft) {
        mStack = stack;
        mWidth = width;
        mHeight = height;
        mTopBrowserControlsHeight = topBrowserControlsHeight;

        mBorderTopHeight = borderFramePaddingTop;
        mBorderTopOpaqueHeight = borderFramePaddingTopOpaque;
        mBorderLeftWidth = borderFramePaddingLeft;
    }

    /**
     * The factory method that creates the particular factory method based on the orientation
     * parameter.
     *
     * @param stack                       The stack of tabs being animated.
     * @param width                       The width of the layout in dp.
     * @param height                      The height of the layout in dp.
     * @param heightMinusBrowserControls  The height of the layout minus the browser controls in dp.
     * @param borderFramePaddingTop       The top padding of the border frame in dp.
     * @param borderFramePaddingTopOpaque The opaque top padding of the border frame in dp.
     * @param borderFramePaddingLeft      The left padding of the border frame in dp.
     * @param orientation                 The orientation that will be used to create the
     *                                    appropriate {@link StackAnimation}.
     * @return                            The TabSwitcherAnimationFactory instance.
     */
    public static StackAnimation createAnimationFactory(Stack stack, float width, float height,
            float topBrowserControlsHeight, float borderFramePaddingTop,
            float borderFramePaddingTopOpaque, float borderFramePaddingLeft, int orientation) {
        StackAnimation factory = null;
        switch (orientation) {
            case Orientation.LANDSCAPE:
                factory = new StackAnimationLandscape(stack, width, height,
                        topBrowserControlsHeight, borderFramePaddingTop,
                        borderFramePaddingTopOpaque, borderFramePaddingLeft);
                break;
            case Orientation.PORTRAIT:
            default:
                factory = new StackAnimationPortrait(stack, width, height, topBrowserControlsHeight,
                        borderFramePaddingTop, borderFramePaddingTopOpaque, borderFramePaddingLeft);
                break;
        }

        return factory;
    }

    /**
     * The wrapper method responsible for delegating the animations request to the appropriate
     * helper method.  Not all parameters are used for each request.
     *
     * @param type          The type of animation to be created.  This is what
     *                      determines which helper method is called.
     * @param stack         The current stack.
     * @param tabs          The tabs that make up the current stack that will
     *                      be animated.
     * @param focusIndex    The index of the tab that is the focus of this animation.
     * @param sourceIndex   The index of the tab that triggered this animation.
     * @param spacing       The default spacing between the tabs.
     * @param discardRange  The range of the discard amount value.
     * @return              The resulting TabSwitcherAnimation that will animate the tabs.
     */
    public ChromeAnimation<?> createAnimatorSetForType(@OverviewAnimationType int type, Stack stack,
            StackTab[] tabs, int focusIndex, int sourceIndex, int spacing, float discardRange) {
        if (tabs == null) return null;
        switch (type) {
            case OverviewAnimationType.ENTER_STACK:
                return createEnterStackAnimatorSet(tabs, focusIndex, spacing);
            case OverviewAnimationType.TAB_FOCUSED:
                return createTabFocusedAnimatorSet(tabs, focusIndex, spacing);
            case OverviewAnimationType.VIEW_MORE:
                return createViewMoreAnimatorSet(tabs, sourceIndex);
            case OverviewAnimationType.REACH_TOP:
                return createReachTopAnimatorSet(tabs);
            case OverviewAnimationType.DISCARD:
            // Purposeful fall through
            case OverviewAnimationType.DISCARD_ALL:
            // Purposeful fall through
            case OverviewAnimationType.UNDISCARD:
                return createUpdateDiscardAnimatorSet(stack, tabs, spacing, discardRange);
            case OverviewAnimationType.NEW_TAB_OPENED:
                return createNewTabOpenedAnimatorSet(tabs, focusIndex, discardRange);
            case OverviewAnimationType.START_PINCH:
                return createStartPinchAnimatorSet(tabs);
            case OverviewAnimationType.FULL_ROLL:
                return createFullRollAnimatorSet(tabs);
            default:
                return null;
        }
    }

    protected abstract float getScreenSizeInScrollDirection();

    protected abstract float getScreenPositionInScrollDirection(StackTab tab);

    protected abstract void addTiltScrollAnimation(
            ChromeAnimation<Animatable> set, LayoutTab tab, float end, int duration, int startTime);

    // If this flag is enabled, we're using the non-overlapping tab switcher.
    protected boolean isHorizontalTabSwitcherFlagEnabled() {
        return ChromeFeatureList.isEnabled(ChromeFeatureList.HORIZONTAL_TAB_SWITCHER_ANDROID);
    }

    /**
     * Responsible for generating the animations that shows the stack
     * being entered.
     *
     * @param tabs       The tabs that make up the stack.  These are the
     *                   tabs that will be affected by the TabSwitcherAnimation.
     * @param focusIndex The focused index.  In this case, this is the index of
     *                   the tab that was being viewed before entering the stack.
     * @param spacing    The default spacing between tabs.
     * @return           The TabSwitcherAnimation instance that will tween the
     *                   tabs to create the appropriate animation.
     */
    protected abstract ChromeAnimation<?> createEnterStackAnimatorSet(
            StackTab[] tabs, int focusIndex, int spacing);

    /**
     * Responsible for generating the animations that shows a tab being
     * focused (the stack is being left).
     *
     * @param tabs       The tabs that make up the stack.  These are the
     *                   tabs that will be affected by the TabSwitcherAnimation.
     * @param focusIndex The focused index.  In this case, this is the index of
     *                   the tab clicked and is being brought up to view.
     * @param spacing    The default spacing between tabs.
     * @return           The TabSwitcherAnimation instance that will tween the
     *                   tabs to create the appropriate animation.
     */
    protected abstract ChromeAnimation<?> createTabFocusedAnimatorSet(
            StackTab[] tabs, int focusIndex, int spacing);

    /**
     * Responsible for generating the animations that Shows more of the selected tab.
     *
     * @param tabs          The tabs that make up the stack.  These are the
     *                      tabs that will be affected by the TabSwitcherAnimation.
     * @param selectedIndex The selected index. In this case, this is the index of
     *                      the tab clicked and is being brought up to view.
     * @return              The TabSwitcherAnimation instance that will tween the
     *                      tabs to create the appropriate animation.
     */
    protected abstract ChromeAnimation<?> createViewMoreAnimatorSet(
            StackTab[] tabs, int selectedIndex);

    /**
     * Responsible for generating the TabSwitcherAnimation that moves the tabs up so they
     * reach the to top the screen.
     *
     * @param tabs          The tabs that make up the stack.  These are the
     *                      tabs that will be affected by the TabSwitcherAnimation.
     * @return              The TabSwitcherAnimation instance that will tween the
     *                      tabs to create the appropriate animation.
     */
    protected abstract ChromeAnimation<?> createReachTopAnimatorSet(StackTab[] tabs);

    /**
     * Responsible for generating the animations that moves the tabs back in from
     * discard attempt or commit the current discard (if any). It also re-even the tabs
     * if one of then is removed.
     *
     * @param tabs         The tabs that make up the stack. These are the
     *                     tabs that will be affected by the TabSwitcherAnimation.
     * @param spacing      The default spacing between tabs.
     * @param discardRange The maximum value the discard amount.
     * @return             The TabSwitcherAnimation instance that will tween the
     *                     tabs to create the appropriate animation.
     */
    protected ChromeAnimation<?> createUpdateDiscardAnimatorSet(
            Stack stack, StackTab[] tabs, int spacing, float discardRange) {
        ChromeAnimation<Animatable> set = new ChromeAnimation<Animatable>();

        int dyingTabsCount = 0;
        int firstDyingTabIndex = -1;
        float firstDyingTabOffset = 0;
        for (int i = 0; i < tabs.length; ++i) {
            StackTab tab = tabs[i];

            addTiltScrollAnimation(set, tab.getLayoutTab(), 0.0f, UNDISCARD_ANIMATION_DURATION, 0);

            if (tab.isDying()) {
                dyingTabsCount++;
                if (dyingTabsCount == 1) {
                    firstDyingTabIndex = i;
                    firstDyingTabOffset = getScreenPositionInScrollDirection(tab);
                }
            }
        }

        Interpolator interpolator = BakedBezierInterpolator.FADE_OUT_CURVE;

        int newIndex = 0;
        for (int i = 0; i < tabs.length; ++i) {
            StackTab tab = tabs[i];
            // If the non-overlapping horizontal tab switcher is enabled, we shift all the tabs over
            // simultaneously. Otherwise we stagger the animation start times to create a ripple
            // effect.
            long startTime = isHorizontalTabSwitcherFlagEnabled()
                    ? 0
                    : (long) Math.max(0,
                              TAB_REORDER_START_SPAN / getScreenSizeInScrollDirection()
                                      * (getScreenPositionInScrollDirection(tab)
                                                - firstDyingTabOffset));
            if (tab.isDying()) {
                float discard = tab.getDiscardAmount();
                if (discard == 0.0f) discard = isDefaultDiscardDirectionPositive() ? 0.0f : -0.0f;
                float s = Math.copySign(1.0f, discard);
                long duration = (long) (DISCARD_ANIMATION_DURATION
                        * (1.0f - Math.abs(discard / discardRange)));
                addAnimation(set, tab, StackTab.Property.DISCARD_AMOUNT, discard, discardRange * s,
                        duration, 0, false, interpolator);
            } else {
                if (tab.getDiscardAmount() != 0.f) {
                    addAnimation(set, tab, StackTab.Property.DISCARD_AMOUNT, tab.getDiscardAmount(),
                            0.0f, UNDISCARD_ANIMATION_DURATION, 0);
                }
                addAnimation(set, tab, StackTab.Property.SCALE, tab.getScale(),
                        mStack.getScaleAmount(), DISCARD_ANIMATION_DURATION, 0);

                addAnimation(set, tab.getLayoutTab(), LayoutTab.Property.MAX_CONTENT_HEIGHT,
                        tab.getLayoutTab().getMaxContentHeight(), mStack.getMaxTabHeight(),
                        DISCARD_ANIMATION_DURATION, 0);

                float newScrollOffset = mStack.screenToScroll(spacing * newIndex);

                // If the tab is not dying we want to readjust it's position
                // based on the new spacing requirements.  For a fully discarded tab, just
                // put it in the right place.
                if (tab.getDiscardAmount() >= discardRange) {
                    tab.setScrollOffset(newScrollOffset);
                    tab.setScale(mStack.getScaleAmount());
                } else {
                    float start = tab.getScrollOffset();
                    if (start != newScrollOffset) {
                        addAnimation(set, tab, StackTab.Property.SCROLL_OFFSET, start,
                                newScrollOffset, TAB_REORDER_DURATION, 0);
                    }
                }
                newIndex++;
            }
        }

        // Scroll offset animation for non-overlapping horizontal tab switcher (if enabled)
        if (isHorizontalTabSwitcherFlagEnabled()) {
            NonOverlappingStack nonOverlappingStack = (NonOverlappingStack) stack;
            int centeredTabIndex = nonOverlappingStack.getCenteredTabIndex();

            // For all tab closures (except for the last one), we slide the remaining tabs in to
            // fill the gap.
            //
            // There are two cases where we also need to animate the NonOverlappingStack's overall
            // scroll position over by one tab:
            //
            // - Closing the last tab while centered on it (since we don't have a tab we can slide
            //   over to replace it)
            //
            // - Closing any tab prior to the currently centered one (so we can keep the same tab
            //   centered). Together with animating the individual scroll offsets for each tab, this
            //   has the visual appearance of sliding in the prior tabs from the left (in LTR mode)
            //   to fill the gap.
            boolean closingLastTabWhileCentered =
                    firstDyingTabIndex == tabs.length - 1 && firstDyingTabIndex == centeredTabIndex;
            boolean closingPriorTab =
                    firstDyingTabIndex != -1 && firstDyingTabIndex < centeredTabIndex;

            boolean shouldAnimateStackScrollOffset = closingLastTabWhileCentered || closingPriorTab;

            if (shouldAnimateStackScrollOffset) {
                nonOverlappingStack.suppressScrollClampingForAnimation();
                addAnimation(set, nonOverlappingStack, Stack.Property.SCROLL_OFFSET,
                        stack.getScrollOffset(), -(centeredTabIndex - 1) * stack.getSpacing(),
                        TAB_REORDER_DURATION, 0);
            }
        }

        return set;
    }

    /**
     * This is used to determine the discard direction when user just clicks X to close a tab.
     * On portrait, positive direction (x) is right hand side.
     * On landscape, positive direction (y) is towards bottom.
     * @return True, if default discard direction is positive.
     */
    protected abstract boolean isDefaultDiscardDirectionPositive();

    /**
     * Responsible for generating the animations that shows a new tab being opened.
     *
     * @param tabs          The tabs that make up the stack.  These are the
     *                      tabs that will be affected by the TabSwitcherAnimation.
     * @param focusIndex    The focused index.  In this case, this is the index of
     *                      the tab that was just created.
     * @param discardRange  The maximum value the discard amount.
     * @return              The TabSwitcherAnimation instance that will tween the
     *                      tabs to create the appropriate animation.
     */
    protected ChromeAnimation<?> createNewTabOpenedAnimatorSet(
            StackTab[] tabs, int focusIndex, float discardRange) {
        ChromeAnimation<Animatable> set = new ChromeAnimation<>();

        for (int i = 0; i < tabs.length; i++) {
            addAnimation(set, tabs[i], StackTab.Property.SCROLL_OFFSET, tabs[i].getScrollOffset(),
                    0.0f, TAB_OPENED_ANIMATION_DURATION, 0, false,
                    CompositorAnimator.DECELERATE_INTERPOLATOR);
        }

        return set;
    }

    /**
     * Responsible for generating the animations that flattens tabs when a pinch begins.
     *
     * @param tabs The tabs that make up the stack. These are the tabs that will
     *             be affected by the animations.
     * @return     The TabSwitcherAnimation instance that will tween the tabs to
     *             create the appropriate animation.
     */
    protected ChromeAnimation<?> createStartPinchAnimatorSet(StackTab[] tabs) {
        ChromeAnimation<Animatable> set = new ChromeAnimation<Animatable>();

        for (int i = 0; i < tabs.length; ++i) {
            addTiltScrollAnimation(
                    set, tabs[i].getLayoutTab(), 0, START_PINCH_ANIMATION_DURATION, 0);
        }

        return set;
    }

    /**
     * Responsible for generating the animations that make all the tabs do a full roll.
     *
     * @param tabs The tabs that make up the stack. These are the tabs that will be affected by the
     *             animations.
     * @return     The TabSwitcherAnimation instance that will tween the tabs to create the
     *             appropriate animation.
     */
    protected ChromeAnimation<?> createFullRollAnimatorSet(StackTab[] tabs) {
        ChromeAnimation<Animatable> set = new ChromeAnimation<Animatable>();

        for (int i = 0; i < tabs.length; ++i) {
            LayoutTab layoutTab = tabs[i].getLayoutTab();
            // Set the pivot
            layoutTab.setTiltX(layoutTab.getTiltX(), layoutTab.getScaledContentHeight() / 2.0f);
            layoutTab.setTiltY(layoutTab.getTiltY(), layoutTab.getScaledContentWidth() / 2.0f);
            // Create the angle animation
            addTiltScrollAnimation(set, layoutTab, -360.0f, FULL_ROLL_ANIMATION_DURATION, 0);
        }

        return set;
    }

    /**
     * @return The offset for the toolbar to line the top up with the opaque component of the
     *         border.
     */
    protected float getToolbarOffsetToLineUpWithBorder() {
        return mTopBrowserControlsHeight - mBorderTopOpaqueHeight;
    }

    /**
     * @return The position of the static tab when entering or exiting the tab switcher.
     */
    protected float getStaticTabPosition() {
        return mTopBrowserControlsHeight - mBorderTopHeight;
    }
}
