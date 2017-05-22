// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.shapedetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.chromium.base.Log;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;
import org.chromium.gfx.mojom.RectF;
import org.chromium.mojo.system.MojoException;
import org.chromium.mojo.system.SharedBufferHandle;
import org.chromium.mojo.system.SharedBufferHandle.MapFlags;
import org.chromium.services.service_manager.InterfaceFactory;
import org.chromium.shape_detection.mojom.TextDetection;
import org.chromium.shape_detection.mojom.TextDetectionResult;

import java.nio.ByteBuffer;

/**
 * Implementation of mojo TextDetection, using Google Play Services vision package.
 */
public class TextDetectionImpl implements TextDetection {
    private static final String TAG = "TextDetectionImpl";

    private final Context mContext;
    private TextRecognizer mTextRecognizer;

    public TextDetectionImpl(Context context) {
        mContext = context;
        mTextRecognizer = new TextRecognizer.Builder(mContext).build();
    }

    @Override
    public void detect(
            SharedBufferHandle frameData, int width, int height, DetectResponse callback) {
        if (!ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                    mContext, new UserRecoverableErrorHandler.Silent())) {
            Log.e(TAG, "Google Play Services not available");
            callback.call(new TextDetectionResult[0]);
            return;
        }
        // The vision library will be downloaded the first time the API is used
        // on the device; this happens "fast", but it might have not completed,
        // bail in this case. Also, the API was disabled between and v.9.0 and
        // v.9.2, see https://developers.google.com/android/guides/releases.
        if (!mTextRecognizer.isOperational()) {
            Log.e(TAG, "TextDetector is not operational");
            callback.call(new TextDetectionResult[0]);
            return;
        }

        final long numPixels = (long) width * height;
        // TODO(xianglu): https://crbug.com/670028 homogeneize overflow checking.
        if (!frameData.isValid() || width <= 0 || height <= 0 || numPixels > (Long.MAX_VALUE / 4)) {
            callback.call(new TextDetectionResult[0]);
            return;
        }

        // Mapping |frameData| will fail if the intended mapped size is larger
        // than its actual capacity, which is limited by the appropriate
        // mojo::edk::Configuration entry.
        ByteBuffer imageBuffer = frameData.map(0, numPixels * 4, MapFlags.none());
        if (imageBuffer.capacity() <= 0) {
            callback.call(new TextDetectionResult[0]);
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(imageBuffer);

        Frame frame = null;
        try {
            // This constructor implies a pixel format conversion to YUV.
            frame = new Frame.Builder().setBitmap(bitmap).build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Log.e(TAG, "Frame.Builder().setBitmap() or build(): " + ex);
            callback.call(new TextDetectionResult[0]);
            return;
        }

        final SparseArray<TextBlock> textBlocks = mTextRecognizer.detect(frame);

        TextDetectionResult[] detectedTextArray = new TextDetectionResult[textBlocks.size()];
        for (int i = 0; i < textBlocks.size(); i++) {
            detectedTextArray[i] = new TextDetectionResult();
            detectedTextArray[i].rawValue = textBlocks.valueAt(i).getValue();
            final Rect rect = textBlocks.valueAt(i).getBoundingBox();
            detectedTextArray[i].boundingBox = new RectF();
            detectedTextArray[i].boundingBox.x = rect.left;
            detectedTextArray[i].boundingBox.y = rect.top;
            detectedTextArray[i].boundingBox.width = rect.width();
            detectedTextArray[i].boundingBox.height = rect.height();
        }
        callback.call(detectedTextArray);
    }

    @Override
    public void close() {
        mTextRecognizer.release();
    }

    @Override
    public void onConnectionError(MojoException e) {
        close();
    }

    /**
     * A factory class to register TextDetection interface.
     */
    public static class Factory implements InterfaceFactory<TextDetection> {
        private final Context mContext;

        public Factory(Context context) {
            mContext = context;
        }

        @Override
        public TextDetection createImpl() {
            return new TextDetectionImpl(mContext);
        }
    }
}
