// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.shapedetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.chromium.base.Log;
import org.chromium.chrome.browser.externalauth.ExternalAuthUtils;
import org.chromium.chrome.browser.externalauth.UserRecoverableErrorHandler;
import org.chromium.gfx.mojom.PointF;
import org.chromium.gfx.mojom.RectF;
import org.chromium.mojo.system.MojoException;
import org.chromium.mojo.system.SharedBufferHandle;
import org.chromium.mojo.system.SharedBufferHandle.MapFlags;
import org.chromium.services.service_manager.InterfaceFactory;
import org.chromium.shape_detection.mojom.BarcodeDetection;
import org.chromium.shape_detection.mojom.BarcodeDetectionResult;

import java.nio.ByteBuffer;

/**
 * Implementation of mojo BarcodeDetection, using Google Play Services vision package.
 */
public class BarcodeDetectionImpl implements BarcodeDetection {
    private static final String TAG = "BarcodeDetectionImpl";

    private final Context mContext;
    private BarcodeDetector mBarcodeDetector;

    public BarcodeDetectionImpl(Context context) {
        mContext = context;
        mBarcodeDetector = new BarcodeDetector.Builder(mContext).build();
    }

    @Override
    public void detect(
            SharedBufferHandle frameData, int width, int height, DetectResponse callback) {
        if (!ExternalAuthUtils.getInstance().canUseGooglePlayServices(
                    mContext, new UserRecoverableErrorHandler.Silent())) {
            Log.e(TAG, "Google Play Services not available");
            callback.call(new BarcodeDetectionResult[0]);
            return;
        }
        // The vision library will be downloaded the first time the API is used
        // on the device; this happens "fast", but it might have not completed,
        // bail in this case. Also, the API was disabled between and v.9.0 and
        // v.9.2, see https://developers.google.com/android/guides/releases.
        if (!mBarcodeDetector.isOperational()) {
            Log.e(TAG, "BarcodeDetector is not operational");
            callback.call(new BarcodeDetectionResult[0]);
            return;
        }

        final long numPixels = (long) width * height;
        // TODO(mcasas): https://crbug.com/670028 homogeneize overflow checking.
        if (!frameData.isValid() || width <= 0 || height <= 0 || numPixels > (Long.MAX_VALUE / 4)) {
            callback.call(new BarcodeDetectionResult[0]);
            return;
        }

        // Mapping |frameData| will fail if the intended mapped size is larger
        // than its actual capacity, which is limited by the appropriate
        // mojo::edk::Configuration entry.
        ByteBuffer imageBuffer = frameData.map(0, numPixels * 4, MapFlags.none());
        if (imageBuffer.capacity() <= 0) {
            callback.call(new BarcodeDetectionResult[0]);
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
            callback.call(new BarcodeDetectionResult[0]);
            return;
        }

        final SparseArray<Barcode> barcodes = mBarcodeDetector.detect(frame);

        BarcodeDetectionResult[] barcodeArray = new BarcodeDetectionResult[barcodes.size()];
        for (int i = 0; i < barcodes.size(); i++) {
            barcodeArray[i] = new BarcodeDetectionResult();
            final Barcode barcode = barcodes.valueAt(i);
            barcodeArray[i].rawValue = barcode.rawValue;
            final Rect rect = barcode.getBoundingBox();
            barcodeArray[i].boundingBox = new RectF();
            barcodeArray[i].boundingBox.x = rect.left;
            barcodeArray[i].boundingBox.y = rect.top;
            barcodeArray[i].boundingBox.width = rect.width();
            barcodeArray[i].boundingBox.height = rect.height();
            final Point[] corners = barcode.cornerPoints;
            barcodeArray[i].cornerPoints = new PointF[corners.length];
            for (int j = 0; j < corners.length; j++) {
                barcodeArray[i].cornerPoints[j] = new PointF();
                barcodeArray[i].cornerPoints[j].x = corners[j].x;
                barcodeArray[i].cornerPoints[j].y = corners[j].y;
            }
        }
        callback.call(barcodeArray);
    }

    @Override
    public void close() {
        mBarcodeDetector.release();
    }

    @Override
    public void onConnectionError(MojoException e) {
        close();
    }

    /**
     * A factory class to register BarcodeDetection interface.
     */
    public static class Factory implements InterfaceFactory<BarcodeDetection> {
        private final Context mContext;

        public Factory(Context context) {
            mContext = context;
        }

        @Override
        public BarcodeDetection createImpl() {
            return new BarcodeDetectionImpl(mContext);
        }
    }
}
