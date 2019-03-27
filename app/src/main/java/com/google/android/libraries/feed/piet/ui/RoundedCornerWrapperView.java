// Copyright 2018 The Feed Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.libraries.feed.piet.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.FrameLayout;
import com.google.android.libraries.feed.common.logging.Logger;
import com.google.android.libraries.feed.common.ui.LayoutUtils;
import com.google.search.now.ui.piet.RoundedCornersProto.RoundedCorners.Corners;

/** Wrapper for {@link View} instances in Piet that require rounded corners. */
public class RoundedCornerWrapperView extends FrameLayout {

  private static final String TAG = "RoundedCornerWrapper";
  private final Paint maskPaint;
  private final PorterDuffXfermode xfermode;
  private final Paint paint;
  private final int roundedCornerRadius;
  private final int bitmask;

  /*@Nullable*/ private RoundRectShape outlineShape = null;

  // This is instantiated in the constructor and modified before use to save time on garbage
  // collection.
  private final RectF maskRect;

  private Bitmap maskBitmap;

  // Doesn't like the call to setOutlineProvider
  @SuppressWarnings("initialization")
  public RoundedCornerWrapperView(Context context, int bitmask, int roundedCornerRadius) {
    super(context);
    this.roundedCornerRadius = roundedCornerRadius;

    maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    xfermode = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
    maskPaint.setXfermode(xfermode);
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    maskRect = new RectF();
    this.bitmask = bitmask;

    if (hasRoundedCorners() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      super.setOutlineProvider(
          new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
              RoundRectShape localOutlineShape = outlineShape;
              if (localOutlineShape == null
                  || localOutlineShape.getHeight() != view.getHeight()
                  || localOutlineShape.getWidth() != view.getWidth()) {
                int radius = makeRadius(view.getWidth(), view.getHeight(), roundedCornerRadius);
                float[] radii = RoundedCornerViewHelper.createRoundedCornerMask(radius, bitmask);
                localOutlineShape = new RoundRectShape(radii, null, null);
                localOutlineShape.resize(view.getWidth(), view.getHeight());
                outlineShape = localOutlineShape;
              }
              localOutlineShape.getOutline(outline);
            }
          });
    }

    setWillNotDraw(false);
  }

  /**
   * Ensures that the wrapper view is invalidated when child views are invalidated. This method only
   * exists in Android O+.
   */
  @Override
  public void onDescendantInvalidated(View child, View target) {
    super.onDescendantInvalidated(child, target);
    if (hasRoundedCorners()) {
      Rect targetRect = new Rect();
      target.getDrawingRect(targetRect);
      invalidate(targetRect);
    }
  }

  /**
   * Using as an indicator that the child view was invalidated. By overriding this method, we ensure
   * that the wrapper view is invalidated when the child view is. This is only used in Android N-
   * and is deprecated, but we must use it because onDescendantInvalidated only exists in O+.
   */
  @Override
  public ViewParent invalidateChildInParent(final int[] location, final Rect dirty) {
    if (hasRoundedCorners()) {
      invalidate(dirty);
    }
    return super.invalidateChildInParent(location, dirty);
  }

  @Override
  public void draw(Canvas canvas) {
    if (!hasRoundedCorners()) {
      super.draw(canvas);
      return;
    }
    if (getWidth() == 0 || getHeight() == 0) {
      // Bitmap creation will fail in these cases
      return;
    }

    Bitmap offscreenBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
    Canvas offscreenCanvas = new Canvas(offscreenBitmap);

    // Draw the view without rounded corners on the offscreen canvas.
    super.draw(offscreenCanvas);

    if (maskBitmap == null
        || maskBitmap.getHeight() != getHeight()
        || maskBitmap.getWidth() != getWidth()) {
      maskBitmap = createMask(getWidth(), getHeight());
    }

    // Mask the parts of the view that should be included
    offscreenCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint);

    paint.setXfermode(null);
    canvas.drawBitmap(offscreenBitmap, 0f, 0f, paint);
  }

  private static int makeRadius(int width, int height, int desiredRadius) {
    if (desiredRadius == 0) {
      return 0;
    }
    int radius = desiredRadius;

    // Radius can't be bigger than half of the width or height of the view.
    // TODO: Allow for radius to be greater than half of the dimensions as long as adjacent
    // corners aren't rounded.
    if (radius > width / 2 || radius > height / 2) {
      radius = Math.min(width / 2, height / 2);
      Logger.w(
          TAG,
          "Can't use radius of %s px, instead using %s. Width: %s, height: %s",
          desiredRadius,
          radius,
          width,
          height);
    }
    return radius;
  }

  private Bitmap createMask(int width, int height) {
    int radius = makeRadius(width, height, roundedCornerRadius);

    Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
    Canvas canvas = new Canvas(mask);

    canvas.drawRect(0, 0, width, height, paint);

    paint.setXfermode(xfermode);

    // First, we want the mask to include everything other than the parts of the corners not visible
    // because of the rounding.
    maskRect.set(0, 0, width, height);
    canvas.drawRoundRect(maskRect, radius, radius, paint);

    // For each corner, we want to include the corner in the mask if we aren't rounding it.
    if (!shouldRoundCorner(Corners.TOP_START)) {
      canvas.drawRect(
          LayoutUtils.isDefaultLocaleRtl() ? topRight(width, radius) : topLeft(radius), paint);
    }
    if (!shouldRoundCorner(Corners.TOP_END)) {
      canvas.drawRect(
          LayoutUtils.isDefaultLocaleRtl() ? topLeft(radius) : topRight(width, radius), paint);
    }
    if (!shouldRoundCorner(Corners.BOTTOM_START)) {
      canvas.drawRect(
          LayoutUtils.isDefaultLocaleRtl()
              ? bottomRight(width, height, radius)
              : bottomLeft(height, radius),
          paint);
    }
    if (!shouldRoundCorner(Corners.BOTTOM_END)) {
      canvas.drawRect(
          LayoutUtils.isDefaultLocaleRtl()
              ? bottomLeft(height, radius)
              : bottomRight(width, height, radius),
          paint);
    }

    return mask;
  }

  private RectF topLeft(int radius) {
    maskRect.set(0, 0, radius, radius);
    return maskRect;
  }

  private RectF topRight(int width, int radius) {
    maskRect.set(width - radius, 0, width, radius);
    return maskRect;
  }

  private RectF bottomLeft(int height, int radius) {
    maskRect.set(0, height - radius, radius, height);
    return maskRect;
  }

  private RectF bottomRight(int width, int height, int radius) {
    maskRect.set(width - radius, height - radius, width, height);
    return maskRect;
  }

  /** This should always be true; we should not be using this view when corners are not round. */
  public boolean hasRoundedCorners() {
    return roundedCornerRadius > 0;
  }

  private boolean shouldRoundCorner(Corners corner) {
    return (bitmask == 0) || (bitmask & corner.getNumber()) != 0;
  }
}
