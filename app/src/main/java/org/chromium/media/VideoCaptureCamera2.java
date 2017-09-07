// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.JNINamespace;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class implements Video Capture using Camera2 API, introduced in Android
 * API 21 (L Release). Capture takes place in the current Looper, while pixel
 * download takes place in another thread used by ImageReader. A number of
 * static methods are provided to retrieve information on current system cameras
 * and their capabilities, using android.hardware.camera2.CameraManager.
 **/
@JNINamespace("media")
@TargetApi(Build.VERSION_CODES.M)
public class VideoCaptureCamera2 extends VideoCapture {
    // Inner class to extend a CameraDevice state change listener.
    private class CrStateListener extends CameraDevice.StateCallback {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            changeCameraStateAndNotify(CameraState.CONFIGURING);
            if (createPreviewObjectsAndCaptureSession()) return;

            changeCameraStateAndNotify(CameraState.STOPPED);
            nativeOnError(mNativeVideoCaptureDeviceAndroid, "Error configuring camera");
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
            changeCameraStateAndNotify(CameraState.STOPPED);
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
            changeCameraStateAndNotify(CameraState.STOPPED);
            nativeOnError(mNativeVideoCaptureDeviceAndroid,
                    "Camera device error " + Integer.toString(error));
        }
    };

    // Inner class to extend a Capture Session state change listener.
    private class CrPreviewSessionListener extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            Log.d(TAG, "CrPreviewSessionListener.onConfigured");
            mCaptureSession = cameraCaptureSession;
            restartPreview(null);

            // Now wait for trigger on CrPreviewReaderListener.onImageAvailable();
            nativeOnStarted(mNativeVideoCaptureDeviceAndroid);
            changeCameraStateAndNotify(CameraState.STARTED);
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
            // TODO(mcasas): When signalling error, C++ will tear us down. Do we need to clean up?
            changeCameraStateAndNotify(CameraState.STOPPED);
            nativeOnError(mNativeVideoCaptureDeviceAndroid, "Camera session configuration error");
        }
    };

    // Internal class implementing an ImageReader listener for Preview frames. Gets pinged when a
    // new frame is been captured and downloads it to memory-backed buffers.
    private class CrPreviewReaderListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            try (Image image = reader.acquireLatestImage()) {
                if (image == null) return;

                if (image.getFormat() != ImageFormat.YUV_420_888 || image.getPlanes().length != 3) {
                    nativeOnError(mNativeVideoCaptureDeviceAndroid, "Unexpected image format: "
                            + image.getFormat() + " or #planes: " + image.getPlanes().length);
                    throw new IllegalStateException();
                }

                if (reader.getWidth() != image.getWidth()
                        || reader.getHeight() != image.getHeight()) {
                    nativeOnError(mNativeVideoCaptureDeviceAndroid, "ImageReader size ("
                            + reader.getWidth() + "x" + reader.getHeight()
                            + ") did not match Image size (" + image.getWidth() + "x"
                            + image.getHeight() + ")");
                    throw new IllegalStateException();
                }

                nativeOnI420FrameAvailable(mNativeVideoCaptureDeviceAndroid,
                        image.getPlanes()[0].getBuffer(), image.getPlanes()[0].getRowStride(),
                        image.getPlanes()[1].getBuffer(), image.getPlanes()[2].getBuffer(),
                        image.getPlanes()[1].getRowStride(), image.getPlanes()[1].getPixelStride(),
                        image.getWidth(), image.getHeight(), getCameraRotation(),
                        image.getTimestamp());
            } catch (IllegalStateException ex) {
                Log.e(TAG, "acquireLatestImage():", ex);
            }
        }
    };

    // Internal class implementing an ImageReader listener for encoded Photos.
    // Gets pinged when a new Image has been captured.
    private class CrPhotoReaderListener implements ImageReader.OnImageAvailableListener {
        private final long mCallbackId;
        CrPhotoReaderListener(long callbackId) {
            mCallbackId = callbackId;
        }

        private byte[] readCapturedData(Image image) {
            byte[] capturedData = null;
            try {
                capturedData = image.getPlanes()[0].getBuffer().array();
            } catch (UnsupportedOperationException ex) {
                // Try reading the pixels in a different way.
                final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                capturedData = new byte[buffer.remaining()];
                buffer.get(capturedData);
            } finally {
                return capturedData;
            }
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable()");
            try (Image image = reader.acquireLatestImage()) {
                if (image == null) {
                    throw new IllegalStateException();
                }

                if (image.getFormat() != ImageFormat.JPEG) {
                    Log.e(TAG, "Unexpected image format: %d", image.getFormat());
                    throw new IllegalStateException();
                }

                final byte[] capturedData = readCapturedData(image);
                nativeOnPhotoTaken(mNativeVideoCaptureDeviceAndroid, mCallbackId, capturedData);

            } catch (IllegalStateException ex) {
                notifyTakePhotoError(mCallbackId);
                return;
            }

            if (restartPreview(null)) return;

            nativeOnError(mNativeVideoCaptureDeviceAndroid, "Error restarting preview");
        }
    };

    private static final double kNanoSecondsToFps = 1.0E-9;
    private static final String TAG = "VideoCapture";
    private static final long PRECAPTURE_TIMEOUT_MS = 1000;

    // Map of the equivalent color temperature in Kelvin for the White Balance setting. The
    // values are a mixture of educated guesses and data from Android's Camera2 API. The
    // temperatures must be ordered increasingly.
    private static final SparseIntArray COLOR_TEMPERATURES_MAP;
    static {
        COLOR_TEMPERATURES_MAP = new SparseIntArray();
        COLOR_TEMPERATURES_MAP.append(2850, CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT);
        COLOR_TEMPERATURES_MAP.append(2950, CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT);
        COLOR_TEMPERATURES_MAP.append(4250, CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT);
        COLOR_TEMPERATURES_MAP.append(4600, CameraMetadata.CONTROL_AWB_MODE_TWILIGHT);
        COLOR_TEMPERATURES_MAP.append(5000, CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT);
        COLOR_TEMPERATURES_MAP.append(6000, CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT);
        COLOR_TEMPERATURES_MAP.append(7000, CameraMetadata.CONTROL_AWB_MODE_SHADE);
    };

    private static enum CameraState { OPENING, CONFIGURING, STARTED, STOPPED }

    private final Object mCameraStateLock = new Object();

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private Handler mMainHandler;
    private Handler mBackgroundHandler;
    private final Looper mLooper;

    private ImageReader mPreviewReader;
    private ImageReader mPhotoReader;
    private Surface mPrecaptureSurface;

    private Range<Integer> mAeFpsRange;
    private CameraState mCameraState = CameraState.STOPPED;
    private float mMaxZoom = 1.0f;
    private Rect mCropRect = new Rect();
    private int mPhotoWidth;
    private int mPhotoHeight;
    private int mFocusMode = AndroidMeteringMode.CONTINUOUS;
    private int mExposureMode = AndroidMeteringMode.CONTINUOUS;
    private long mLastExposureTimeNs;
    private MeteringRectangle mAreaOfInterest;
    private int mExposureCompensation;
    private int mWhiteBalanceMode = AndroidMeteringMode.CONTINUOUS;
    private int mColorTemperature = -1;
    private int mIso;
    private boolean mRedEyeReduction;
    private int mFillLightMode = AndroidFillLightMode.OFF;
    private boolean mTorch;

    // Service function to grab CameraCharacteristics and handle exceptions.
    private static CameraCharacteristics getCameraCharacteristics(int id) {
        final CameraManager manager =
                (CameraManager) ContextUtils.getApplicationContext().getSystemService(
                        Context.CAMERA_SERVICE);
        try {
            return manager.getCameraCharacteristics(Integer.toString(id));
        } catch (CameraAccessException | IllegalArgumentException ex) {
            Log.e(TAG, "getCameraCharacteristics: ", ex);
        }
        return null;
    }

    // {@link nativeOnPhotoTaken()} needs to be called back if there's any
    // problem after {@link takePhoto()} has returned true.
    private void notifyTakePhotoError(long callbackId) {
        nativeOnPhotoTaken(mNativeVideoCaptureDeviceAndroid, callbackId, new byte[0]);
    }

    // Convenience method to call setRepeatingRequest() and catch its potential Exceptions.
    private boolean restartPreview(Handler handler) {
        Log.d(TAG, "restartPreview()");
        try {
            // (Re)trigger the preview. The registered CaptureCallback will receive the actual
            // capture result parameters. If |handler| is null we'll work on the current Thread
            // Looper.
            mCaptureSession.setRepeatingRequest(
                    mPreviewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session,
                                CaptureRequest request, TotalCaptureResult result) {
                            if (result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null) {
                                mLastExposureTimeNs =
                                        result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                            }
                        }
                    }, handler);

        } catch (CameraAccessException | SecurityException | IllegalStateException
                | IllegalArgumentException ex) {
            Log.e(TAG, "mCaptureSession.setRepeatingRequest: ", ex);
            return false;
        }
        return true;
    }

    private boolean createPreviewObjectsAndCaptureSession() {
        Log.d(TAG, "createPreviewObjectsAndCaptureSession()");
        if (mCameraDevice == null) return false;

        try {
            // TEMPLATE_PREVIEW specifically means "high frame rate is given
            // priority over the highest-quality post-processing".
            mPreviewRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException ex) {
            Log.e(TAG, "createCaptureRequest: ", ex);
            return false;
        }

        if (mPreviewRequestBuilder == null) {
            Log.e(TAG, "mPreviewRequestBuilder error");
            return false;
        }

        // Create an ImageReader with a background thread looper in it to have readback take place
        // on its own thread, and use its Surface for the preview.
        mPreviewReader = ImageReader.newInstance(mCaptureFormat.getWidth(),
                mCaptureFormat.getHeight(), mCaptureFormat.getPixelFormat(), 2 /* maxImages */);
        final CrPreviewReaderListener imageReaderListener = new CrPreviewReaderListener();
        mPreviewReader.setOnImageAvailableListener(imageReaderListener, mBackgroundHandler);
        mPreviewRequestBuilder.addTarget(mPreviewReader.getSurface());

        configureCommonCaptureSettings(mPreviewRequestBuilder);
        mPreviewRequestBuilder.set(
                CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST);
        mPreviewRequestBuilder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_FAST);

        // Depending on the resolution and other parameters, stabilization might not be available,
        // see https://crbug.com/718387.
        // https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.html#CONTROL_VIDEO_STABILIZATION_MODE
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(mId);
        final int[] stabilizationModes = cameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        for (int mode : stabilizationModes) {
            if (mode == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON);
                break;
            }
        }

        // Create another ImageReader on the same background thread to retrieve photos, but do not
        // connect its Surface to the preview request yet.
        final StreamConfigurationMap streamMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        final Size[] supportedSizes = streamMap.getOutputSizes(ImageFormat.JPEG);
        // |supportedSizes[0]| is the largest.
        mPhotoReader = ImageReader.newInstance(supportedSizes[0].getWidth(),
                supportedSizes[0].getHeight(), ImageFormat.JPEG, 1 /* maxImages */);

        // A dummy Surface for precapture operations.  We could also have another ImageReader and
        // make sure to flush it, but it's not worth it.
        SurfaceTexture preview = new SurfaceTexture(/*arbitrary value*/ 1);
        mPrecaptureSurface = new Surface(preview);

        List<Surface> surfaceList = new ArrayList<Surface>(3);
        // Make sure both ImageReader's Surfaces are registered with the session.
        // TODO(mcasas): release these Surfaces when not needed, https://crbug.com/643884.
        surfaceList.add(mPreviewReader.getSurface());
        surfaceList.add(mPhotoReader.getSurface());
        surfaceList.add(mPrecaptureSurface);

        try {
            mCameraDevice.createCaptureSession(surfaceList, new CrPreviewSessionListener(), null);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException ex) {
            Log.e(TAG, "mCameraDevice.createCaptureSession(): ", ex);
            return false;
        }
        // Wait for trigger on CrPreviewSessionListener.onConfigured();
        return true;
    }

    private void configureCommonCaptureSettings(CaptureRequest.Builder requestBuilder) {
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(mId);

        // |mFocusMode| indicates if we're in auto/continuous, single-shot or manual mode.
        // AndroidMeteringMode.SINGLE_SHOT is dealt with independently since it needs to be
        // triggered by a capture.
        if (mFocusMode == AndroidMeteringMode.CONTINUOUS) {
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            requestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        } else if (mFocusMode == AndroidMeteringMode.FIXED) {
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            requestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            // TODO(mcasas): Support manual focus (LENS_FOCUS_DISTANCE), https://crbug.com/732807.
        }

        // |mExposureMode|, |mFillLightMode| and |mTorch| interact to configure the AE and Flash
        // modes. In a nutshell, FLASH_MODE is only effective if the auto-exposure is ON/OFF,
        // otherwise the auto-exposure related flash control overrides it. |mTorch| mode overrides
        // any previous |mFillLightMode| flash control.
        if (mExposureMode == AndroidMeteringMode.FIXED) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);

            // We need to configure by hand the exposure time when AE mode is off.  Set it to the
            // last known exposure interval if known, otherwise set it to the middle of the allowed
            // range. Further tuning will be done via |mIso| and |mExposureCompensation|.
            if (mLastExposureTimeNs != 0) {
                requestBuilder.set(
                        CaptureRequest.SENSOR_EXPOSURE_TIME, mLastExposureTimeNs /* nanoseconds*/);
            } else {
                Range<Long> range = cameraCharacteristics.get(
                        CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                        range.getLower()
                                + (range.getUpper() + range.getLower()) / 2 /* nanoseconds*/);
            }

        } else {
            requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mAeFpsRange);
        }

        if (mTorch) {
            requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
        } else {
            requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);

            // Do not set FLASH_MODE (see https://stackoverflow.com/a/36069908); anyway either of
            // CONTROL_AE_MODE_ON_{ALWAYS_FLASH,AUTO_FLASH,AUTO_FLASH_REDEYE} overrides FLASH_MODE.
            if (mFillLightMode == AndroidFillLightMode.AUTO) {
                Log.d(TAG, "configureCommonCaptureSettings() Flash set to Auto");
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        mRedEyeReduction ? CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE
                                         : CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
            } else if (mFillLightMode == AndroidFillLightMode.FLASH) {
                Log.d(TAG, "configureCommonCaptureSettings() Flash set to Always");
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            } else {
                // The case |mFillLightMode| == AndroidFillLightMode.OFF is already covered by
                // setting CONTROL_AE_MODE to simply either ON/OFF while handling |mExposureMode|.
                final int aeMode = requestBuilder.get(CaptureRequest.CONTROL_AE_MODE);
                assert aeMode == CameraMetadata.CONTROL_AE_MODE_ON
                        || aeMode == CameraMetadata.CONTROL_AE_MODE_OFF;
            }
        }

        requestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, mExposureCompensation);

        // White Balance mode AndroidMeteringMode.SINGLE_SHOT is not supported.
        if (mWhiteBalanceMode == AndroidMeteringMode.CONTINUOUS) {
            requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false);
            requestBuilder.set(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
            // TODO(mcasas): support different luminant color temperatures, e.g. DAYLIGHT, SHADE.
            // https://crbug.com/518807
        } else if (mWhiteBalanceMode == AndroidMeteringMode.NONE) {
            requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false);
            requestBuilder.set(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        } else if (mWhiteBalanceMode == AndroidMeteringMode.FIXED) {
            requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
        }
        if (mColorTemperature > 0) {
            final int colorSetting = getClosestWhiteBalance(mColorTemperature,
                    cameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES));
            Log.d(TAG, " Color temperature (%d ==> %d)", mColorTemperature, colorSetting);
            if (colorSetting != -1) {
                requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, colorSetting);
            }
        }

        if (mAreaOfInterest != null) {
            MeteringRectangle[] array = {mAreaOfInterest};
            Log.d(TAG, "Area of interest %s", mAreaOfInterest.toString());
            requestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, array);
            requestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, array);
            requestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS, array);
        }

        if (!mCropRect.isEmpty()) {
            requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, mCropRect);
        }

        if (mIso > 0) requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);
    }

    private void changeCameraStateAndNotify(CameraState state) {
        synchronized (mCameraStateLock) {
            mCameraState = state;
            mCameraStateLock.notifyAll();
        }
    }

    // Finds the closest Size to (|width|x|height|) in |sizes|, and returns it or null.
    // Ignores |width| or |height| if either is zero (== don't care).
    private static Size findClosestSizeInArray(Size[] sizes, int width, int height) {
        if (sizes == null) return null;
        Size closestSize = null;
        int minDiff = Integer.MAX_VALUE;
        for (Size size : sizes) {
            final int diff = ((width > 0) ? Math.abs(size.getWidth() - width) : 0)
                    + ((height > 0) ? Math.abs(size.getHeight() - height) : 0);
            if (diff < minDiff) {
                minDiff = diff;
                closestSize = size;
            }
        }
        if (minDiff == Integer.MAX_VALUE) {
            Log.e(TAG, "Couldn't find resolution close to (%dx%d)", width, height);
            return null;
        }
        return closestSize;
    }

    private static int findInIntArray(int[] hayStack, int needle) {
        for (int i = 0; i < hayStack.length; ++i) {
            if (needle == hayStack[i]) return i;
        }
        return -1;
    }

    private int getClosestWhiteBalance(int colorTemperature, int[] supportedTemperatures) {
        int minDiff = Integer.MAX_VALUE;
        int matchedTemperature = -1;

        for (int i = 0; i < COLOR_TEMPERATURES_MAP.size(); ++i) {
            if (findInIntArray(supportedTemperatures, COLOR_TEMPERATURES_MAP.valueAt(i)) == -1) {
                continue;
            }
            final int diff = Math.abs(colorTemperature - COLOR_TEMPERATURES_MAP.keyAt(i));
            if (diff >= minDiff) continue;
            minDiff = diff;
            matchedTemperature = COLOR_TEMPERATURES_MAP.valueAt(i);
        }
        return matchedTemperature;
    }

    static boolean isLegacyDevice(int id) {
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(id);
        return cameraCharacteristics != null
                && cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }

    static int getNumberOfCameras() {
        CameraManager manager = null;
        try {
            manager = (CameraManager) ContextUtils.getApplicationContext().getSystemService(
                    Context.CAMERA_SERVICE);
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "getSystemService(Context.CAMERA_SERVICE): ", ex);
            return 0;
        }
        if (manager == null) return 0;
        try {
            return manager.getCameraIdList().length;
        } catch (CameraAccessException | SecurityException | AssertionError ex) {
            // SecurityException is undocumented but seen in the wild: https://crbug/605424.
            Log.e(TAG, "getNumberOfCameras: getCameraIdList(): ", ex);
            return 0;
        }
    }

    static int getCaptureApiType(int id) {
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(id);
        if (cameraCharacteristics == null) {
            return VideoCaptureApi.UNKNOWN;
        }

        final int supportedHWLevel =
                cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        switch (supportedHWLevel) {
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                return VideoCaptureApi.ANDROID_API2_LEGACY;
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                return VideoCaptureApi.ANDROID_API2_FULL;
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                return VideoCaptureApi.ANDROID_API2_LIMITED;
            default:
                return VideoCaptureApi.ANDROID_API2_LEGACY;
        }
    }

    static String getName(int id) {
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(id);
        if (cameraCharacteristics == null) return null;
        final int facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        return "camera2 " + id + ", facing "
                + ((facing == CameraCharacteristics.LENS_FACING_FRONT) ? "front" : "back");
    }

    static VideoCaptureFormat[] getDeviceSupportedFormats(int id) {
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(id);
        if (cameraCharacteristics == null) return null;

        final int[] capabilities =
                cameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        // Per-format frame rate via getOutputMinFrameDuration() is only available if the
        // property REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR is set.
        boolean minFrameDurationAvailable = false;
        for (int cap : capabilities) {
            if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) {
                minFrameDurationAvailable = true;
                break;
            }
        }

        ArrayList<VideoCaptureFormat> formatList = new ArrayList<VideoCaptureFormat>();
        final StreamConfigurationMap streamMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        final int[] formats = streamMap.getOutputFormats();
        for (int format : formats) {
            final Size[] sizes = streamMap.getOutputSizes(format);
            if (sizes == null) continue;
            for (Size size : sizes) {
                double minFrameRate = 0.0f;
                if (minFrameDurationAvailable) {
                    final long minFrameDuration = streamMap.getOutputMinFrameDuration(format, size);
                    minFrameRate = (minFrameDuration == 0)
                            ? 0.0f
                            : (1.0 / kNanoSecondsToFps * minFrameDuration);
                } else {
                    // TODO(mcasas): find out where to get the info from in this case.
                    // Hint: perhaps using SCALER_AVAILABLE_PROCESSED_MIN_DURATIONS.
                    minFrameRate = 0.0;
                }
                formatList.add(new VideoCaptureFormat(
                        size.getWidth(), size.getHeight(), (int) minFrameRate, 0));
            }
        }
        return formatList.toArray(new VideoCaptureFormat[formatList.size()]);
    }

    VideoCaptureCamera2(int id, long nativeVideoCaptureDeviceAndroid) {
        super(id, nativeVideoCaptureDeviceAndroid);
        mLooper = Looper.myLooper();
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(id);
        if (cameraCharacteristics != null) {
            mMaxZoom = cameraCharacteristics.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        }
    }

    @Override
    public boolean allocate(int width, int height, int frameRate) {
        Log.d(TAG, "allocate: requested (%d x %d) @%dfps", width, height, frameRate);
        assert mLooper == Looper.myLooper() : "called on wrong thread";
        synchronized (mCameraStateLock) {
            if (mCameraState == CameraState.OPENING || mCameraState == CameraState.CONFIGURING) {
                Log.e(TAG, "allocate() invoked while Camera is busy opening/configuring.");
                return false;
            }
        }
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(mId);
        final StreamConfigurationMap streamMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        // Find closest supported size.
        final Size[] supportedSizes = streamMap.getOutputSizes(ImageFormat.YUV_420_888);
        final Size closestSupportedSize = findClosestSizeInArray(supportedSizes, width, height);
        if (closestSupportedSize == null) {
            Log.e(TAG, "No supported resolutions.");
            return false;
        }
        final List<Range<Integer>> fpsRanges = Arrays.asList(cameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));
        if (fpsRanges.isEmpty()) {
            Log.e(TAG, "No supported framerate ranges.");
            return false;
        }
        final List<FramerateRange> framerateRanges =
                new ArrayList<FramerateRange>(fpsRanges.size());
        // On some legacy implementations FPS values are multiplied by 1000. Multiply by 1000
        // everywhere for consistency. Set fpsUnitFactor to 1 if fps ranges are already multiplied
        // by 1000.
        final int fpsUnitFactor = fpsRanges.get(0).getUpper() > 1000 ? 1 : 1000;
        for (Range<Integer> range : fpsRanges) {
            framerateRanges.add(new FramerateRange(
                    range.getLower() * fpsUnitFactor, range.getUpper() * fpsUnitFactor));
        }
        final FramerateRange aeFramerateRange =
                getClosestFramerateRange(framerateRanges, frameRate * 1000);
        mAeFpsRange = new Range<Integer>(
                aeFramerateRange.min / fpsUnitFactor, aeFramerateRange.max / fpsUnitFactor);
        Log.d(TAG, "allocate: matched (%d x %d) @[%d - %d]", closestSupportedSize.getWidth(),
                closestSupportedSize.getHeight(), mAeFpsRange.getLower(), mAeFpsRange.getUpper());

        // |mCaptureFormat| is also used to configure the ImageReader.
        mCaptureFormat = new VideoCaptureFormat(closestSupportedSize.getWidth(),
                closestSupportedSize.getHeight(), frameRate, ImageFormat.YUV_420_888);
        mCameraNativeOrientation =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        // TODO(mcasas): The following line is correct for N5 with prerelease Build,
        // but NOT for N7 with a dev Build. Figure out which one to support.
        mInvertDeviceOrientationReadings =
                cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraCharacteristics.LENS_FACING_BACK;
        return true;
    }

    @Override
    public boolean startCapture() {
        assert mLooper == Looper.myLooper() : "called on wrong thread";
        changeCameraStateAndNotify(CameraState.OPENING);
        final CameraManager manager =
                (CameraManager) ContextUtils.getApplicationContext().getSystemService(
                        Context.CAMERA_SERVICE);

        if (!mUseBackgroundThreadForTesting) {
            mMainHandler = new Handler(ContextUtils.getApplicationContext().getMainLooper());
        } else {
            // Usually we deliver frames on application context thread, but unit tests
            // occupy its Looper; deliver frames on a background thread instead.
            HandlerThread thread = new HandlerThread("CameraPicture");
            thread.start();
            mMainHandler = new Handler(thread.getLooper());
        }

        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        mBackgroundHandler = new Handler(thread.getLooper());

        final CrStateListener stateListener = new CrStateListener();
        try {
            manager.openCamera(Integer.toString(mId), stateListener, mMainHandler);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException ex) {
            Log.e(TAG, "allocate: manager.openCamera: ", ex);
            return false;
        }

        return true;
    }

    @Override
    public boolean stopCapture() {
        assert mLooper == Looper.myLooper() : "called on wrong thread";

        // With Camera2 API, the capture is started asynchronously, which will cause problem if
        // stopCapture comes too quickly. Without stopping the previous capture properly, the next
        // startCapture will fail and make Chrome no-responding. So wait camera to be STARTED.
        synchronized (mCameraStateLock) {
            while (mCameraState != CameraState.STARTED && mCameraState != CameraState.STOPPED) {
                try {
                    mCameraStateLock.wait();
                } catch (InterruptedException ex) {
                    Log.e(TAG, "CaptureStartedEvent: ", ex);
                }
            }
            if (mCameraState == CameraState.STOPPED) return true;
        }

        try {
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException | IllegalStateException ex) {
            // Stopping a device whose CameraCaptureSession is closed is not an error: ignore this.
            Log.w(TAG, "abortCaptures: ", ex);
        }
        if (mCameraDevice == null) return false;
        mCameraDevice.close();

        if (mUseBackgroundThreadForTesting) mMainHandler.getLooper().quit();

        changeCameraStateAndNotify(CameraState.STOPPED);
        mCropRect = new Rect();
        return true;
    }

    @Override
    public PhotoCapabilities getPhotoCapabilities() {
        assert mLooper == Looper.myLooper() : "called on wrong thread";
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(mId);
        PhotoCapabilities.Builder builder = new PhotoCapabilities.Builder();

        int minIso = 0;
        int maxIso = 0;
        final Range<Integer> iso_range =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (iso_range != null) {
            minIso = iso_range.getLower();
            maxIso = iso_range.getUpper();
        }
        builder.setMinIso(minIso).setMaxIso(maxIso).setStepIso(1);
        if (mPreviewRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY) != null) {
            builder.setCurrentIso(mPreviewRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY));
        }

        final StreamConfigurationMap streamMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        final Size[] supportedSizes = streamMap.getOutputSizes(ImageFormat.JPEG);
        int minWidth = Integer.MAX_VALUE;
        int minHeight = Integer.MAX_VALUE;
        int maxWidth = 0;
        int maxHeight = 0;
        for (Size size : supportedSizes) {
            if (size.getWidth() < minWidth) minWidth = size.getWidth();
            if (size.getHeight() < minHeight) minHeight = size.getHeight();
            if (size.getWidth() > maxWidth) maxWidth = size.getWidth();
            if (size.getHeight() > maxHeight) maxHeight = size.getHeight();
        }
        builder.setMinHeight(minHeight).setMaxHeight(maxHeight).setStepHeight(1);
        builder.setMinWidth(minWidth).setMaxWidth(maxWidth).setStepWidth(1);
        builder.setCurrentHeight((mPhotoHeight > 0) ? mPhotoHeight : mCaptureFormat.getHeight());
        builder.setCurrentWidth((mPhotoWidth > 0) ? mPhotoWidth : mCaptureFormat.getWidth());

        final float currentZoom =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        .width()
                / (float) mPreviewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION).width();
        // There is no min-zoom per se, so clamp it to always 1.
        builder.setMinZoom(1.0).setMaxZoom(mMaxZoom);
        builder.setCurrentZoom(currentZoom).setStepZoom(0.1);

        // Classify the Focus capabilities. In CONTINUOUS and SINGLE_SHOT, we can call
        // autoFocus(AutoFocusCallback) to configure region(s) to focus onto.
        final int[] jniFocusModes =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        ArrayList<Integer> focusModes = new ArrayList<Integer>(3);
        for (int mode : jniFocusModes) {
            if (mode == CameraMetadata.CONTROL_AF_MODE_OFF) {
                focusModes.add(Integer.valueOf(AndroidMeteringMode.FIXED));
            } else if (mode == CameraMetadata.CONTROL_AF_MODE_AUTO
                    || mode == CameraMetadata.CONTROL_AF_MODE_MACRO) {
                // CONTROL_AF_MODE_{AUTO,MACRO} do not imply continuously focusing.
                if (!focusModes.contains(Integer.valueOf(AndroidMeteringMode.SINGLE_SHOT))) {
                    focusModes.add(Integer.valueOf(AndroidMeteringMode.SINGLE_SHOT));
                }
            } else if (mode == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    || mode == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    || mode == CameraMetadata.CONTROL_AF_MODE_EDOF) {
                if (!focusModes.contains(Integer.valueOf(AndroidMeteringMode.CONTINUOUS))) {
                    focusModes.add(Integer.valueOf(AndroidMeteringMode.CONTINUOUS));
                }
            }
        }
        builder.setFocusModes(integerArrayListToArray(focusModes));

        final int focusMode = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE);
        int jniFocusMode = AndroidMeteringMode.NONE;
        if (focusMode == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                || focusMode == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) {
            jniFocusMode = AndroidMeteringMode.CONTINUOUS;
        } else if (focusMode == CameraMetadata.CONTROL_AF_MODE_AUTO
                || focusMode == CameraMetadata.CONTROL_AF_MODE_MACRO) {
            jniFocusMode = AndroidMeteringMode.SINGLE_SHOT;
        } else if (focusMode == CameraMetadata.CONTROL_AF_MODE_OFF) {
            jniFocusMode = AndroidMeteringMode.FIXED;
        } else {
            assert jniFocusMode == CameraMetadata.CONTROL_AF_MODE_EDOF;
        }
        builder.setFocusMode(jniFocusMode);

        // Auto Exposure is the usual capability and state, unless AE is not available at all, which
        // is signalled by an empty CONTROL_AE_AVAILABLE_MODES list. Exposure Compensation can also
        // support or be locked, this is equivalent to AndroidMeteringMode.FIXED.
        final int[] jniExposureModes =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
        ArrayList<Integer> exposureModes = new ArrayList<Integer>(1);
        for (int mode : jniExposureModes) {
            if (mode == CameraMetadata.CONTROL_AE_MODE_ON
                    || mode == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                    || mode == CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    || mode == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE) {
                exposureModes.add(Integer.valueOf(AndroidMeteringMode.CONTINUOUS));
                break;
            }
        }
        try {
            if (cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)) {
                exposureModes.add(Integer.valueOf(AndroidMeteringMode.FIXED));
            }
        } catch (NoSuchFieldError e) {
            // Ignore this exception, it means CONTROL_AE_LOCK_AVAILABLE is not known.
        }
        builder.setExposureModes(integerArrayListToArray(exposureModes));

        int jniExposureMode = AndroidMeteringMode.CONTINUOUS;
        if (mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE)
                == CameraMetadata.CONTROL_AE_MODE_OFF) {
            jniExposureMode = AndroidMeteringMode.NONE;
        }
        if (mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_LOCK)) {
            jniExposureMode = AndroidMeteringMode.FIXED;
        }
        builder.setExposureMode(jniExposureMode);

        final float step =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                        .floatValue();
        builder.setStepExposureCompensation(step);
        final Range<Integer> exposureCompensationRange =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        builder.setMinExposureCompensation(exposureCompensationRange.getLower() * step);
        builder.setMaxExposureCompensation(exposureCompensationRange.getUpper() * step);
        builder.setCurrentExposureCompensation(
                mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) * step);

        final int[] jniWhiteBalanceMode =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        ArrayList<Integer> whiteBalanceModes = new ArrayList<Integer>(1);
        for (int mode : jniWhiteBalanceMode) {
            if (mode == CameraMetadata.CONTROL_AWB_MODE_AUTO) {
                whiteBalanceModes.add(Integer.valueOf(AndroidMeteringMode.CONTINUOUS));
                break;
            }
        }
        try {
            if (cameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)) {
                whiteBalanceModes.add(Integer.valueOf(AndroidMeteringMode.FIXED));
            }
        } catch (NoSuchFieldError e) {
            // Ignore this exception, it means CONTROL_AWB_LOCK_AVAILABLE is not known.
        }
        builder.setWhiteBalanceModes(integerArrayListToArray(whiteBalanceModes));

        final int whiteBalanceMode = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE);
        if (whiteBalanceMode == CameraMetadata.CONTROL_AWB_MODE_OFF) {
            builder.setWhiteBalanceMode(AndroidMeteringMode.NONE);
        } else if (whiteBalanceMode == CameraMetadata.CONTROL_AWB_MODE_AUTO) {
            builder.setWhiteBalanceMode(AndroidMeteringMode.CONTINUOUS);
        } else {
            builder.setWhiteBalanceMode(AndroidMeteringMode.FIXED);
        }
        builder.setMinColorTemperature(COLOR_TEMPERATURES_MAP.keyAt(0));
        builder.setMaxColorTemperature(
                COLOR_TEMPERATURES_MAP.keyAt(COLOR_TEMPERATURES_MAP.size() - 1));
        final int index = COLOR_TEMPERATURES_MAP.indexOfValue(whiteBalanceMode);
        if (index >= 0) {
            builder.setCurrentColorTemperature(COLOR_TEMPERATURES_MAP.keyAt(index));
        }
        builder.setStepColorTemperature(50);

        if (!cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
            builder.setSupportsTorch(false);
            builder.setRedEyeReduction(false);
        } else {
            // There's no way to query if torch and/or red eye reduction modes are available using
            // Camera2 API but since there's a Flash unit, we assume so.
            builder.setSupportsTorch(true);
            builder.setTorch(mPreviewRequestBuilder.get(CaptureRequest.FLASH_MODE)
                    == CameraMetadata.FLASH_MODE_TORCH);

            builder.setRedEyeReduction(true);

            final int[] flashModes =
                    cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
            ArrayList<Integer> modes = new ArrayList<Integer>(0);
            for (int flashMode : flashModes) {
                if (flashMode == CameraMetadata.FLASH_MODE_OFF) {
                    modes.add(Integer.valueOf(AndroidFillLightMode.OFF));
                } else if (flashMode == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH) {
                    modes.add(Integer.valueOf(AndroidFillLightMode.AUTO));
                } else if (flashMode == CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH) {
                    modes.add(Integer.valueOf(AndroidFillLightMode.FLASH));
                }
            }
            builder.setFillLightModes(integerArrayListToArray(modes));
        }

        return builder.build();
    }

    @Override
    public void setPhotoOptions(double zoom, int focusMode, int exposureMode, double width,
            double height, float[] pointsOfInterest2D, boolean hasExposureCompensation,
            double exposureCompensation, int whiteBalanceMode, double iso,
            boolean hasRedEyeReduction, boolean redEyeReduction, int fillLightMode,
            boolean hasTorch, boolean torch, double colorTemperature) {
        Log.d(TAG, "setPhotoOptions()");
        assert mLooper == Looper.myLooper() : "called on wrong thread";
        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(mId);
        final Rect canvas =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        if (zoom != 0) {
            final float normalizedZoom = Math.max(1.0f, Math.min((float) zoom, mMaxZoom));
            final float cropFactor = (normalizedZoom - 1) / (2 * normalizedZoom);

            mCropRect = new Rect(Math.round(canvas.width() * cropFactor),
                    Math.round(canvas.height() * cropFactor),
                    Math.round(canvas.width() * (1 - cropFactor)),
                    Math.round(canvas.height() * (1 - cropFactor)));
            Log.d(TAG, "zoom level %f, rectangle: %s", normalizedZoom, mCropRect.toString());
        }

        if (focusMode != AndroidMeteringMode.NOT_SET) mFocusMode = focusMode;
        if (exposureMode != AndroidMeteringMode.NOT_SET) mExposureMode = exposureMode;
        if (whiteBalanceMode != AndroidMeteringMode.NOT_SET) mWhiteBalanceMode = whiteBalanceMode;

        if (width > 0) mPhotoWidth = (int) Math.round(width);
        if (height > 0) mPhotoHeight = (int) Math.round(height);

        // Upon new |zoom| configuration, clear up the previous |mAreaOfInterest| if any.
        if (mAreaOfInterest != null && !mAreaOfInterest.getRect().isEmpty() && zoom > 0) {
            mAreaOfInterest = null;
        }
        // Also clear |mAreaOfInterest| if the user sets it as NONE.
        if (mFocusMode == AndroidMeteringMode.NONE || mExposureMode == AndroidMeteringMode.NONE) {
            mAreaOfInterest = null;
        }
        // Update |mAreaOfInterest| if the camera supports and there are |pointsOfInterest2D|.
        final boolean pointsOfInterestSupported =
                cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) > 0
                || cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) > 0
                || cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) > 0;
        if (pointsOfInterestSupported && pointsOfInterest2D.length > 0) {
            assert pointsOfInterest2D.length == 2 : "Only 1 point of interest supported";
            assert pointsOfInterest2D[0] <= 1.0 && pointsOfInterest2D[0] >= 0.0;
            assert pointsOfInterest2D[1] <= 1.0 && pointsOfInterest2D[1] >= 0.0;
            // Calculate a Rect of 1/8 the |visibleRect| dimensions, and center it w.r.t. |canvas|.
            final Rect visibleRect = (mCropRect.isEmpty()) ? canvas : mCropRect;
            int centerX = Math.round(pointsOfInterest2D[0] * visibleRect.width());
            int centerY = Math.round(pointsOfInterest2D[1] * visibleRect.height());
            if (visibleRect.equals(mCropRect)) {
                centerX += (canvas.width() - visibleRect.width()) / 2;
                centerY += (canvas.height() - visibleRect.height()) / 2;
            }
            final int regionWidth = visibleRect.width() / 8;
            final int regionHeight = visibleRect.height() / 8;

            mAreaOfInterest = new MeteringRectangle(Math.max(0, centerX - regionWidth / 2),
                    Math.max(0, centerY - regionHeight / 2), regionWidth, regionHeight,
                    MeteringRectangle.METERING_WEIGHT_MAX);

            Log.d(TAG, "Calculating (%.2fx%.2f) wrt to %s (canvas being %s)", pointsOfInterest2D[0],
                    pointsOfInterest2D[1], visibleRect.toString(), canvas.toString());
            Log.d(TAG, "Area of interest %s", mAreaOfInterest.toString());
        }

        if (hasExposureCompensation) {
            mExposureCompensation = (int) Math.round(exposureCompensation
                    / cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                              .floatValue());
        }
        if (iso > 0) mIso = (int) Math.round(iso);
        if (colorTemperature > 0) mColorTemperature = (int) Math.round(colorTemperature);

        if (hasRedEyeReduction) mRedEyeReduction = redEyeReduction;
        if (fillLightMode != AndroidFillLightMode.NOT_SET) mFillLightMode = fillLightMode;
        if (hasTorch) mTorch = torch;

        // Reuse most of |mPreviewRequestBuilder| since it has expensive items inside that have
        // to do with preview, e.g. the ImageReader and its associated Surface.
        configureCommonCaptureSettings(mPreviewRequestBuilder);

        // Trigger a focus adjustment when |focusMode| is being configured to SINGLE_SHOT or if we
        // are already in CONTROL_AF_MODE_AUTO and we get a set of |pointsOfInterest2D|.
        if (focusMode == AndroidMeteringMode.SINGLE_SHOT
                || (pointsOfInterest2D.length > 0
                           && mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE)
                                   == CameraMetadata.CONTROL_AF_MODE_AUTO)) {
            // CONTROL_AF_MODE_AUTO only updates the focus upon trigger and not continuously.
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            try {
                // No need to listen to the Auto Focus state updates, so leave |listener| empty.
                mCaptureSession.capture(
                        mPreviewRequestBuilder.build(), null /* listener */, mBackgroundHandler);
            } catch (CameraAccessException | SecurityException | IllegalStateException
                    | IllegalArgumentException ex) {
                Log.e(TAG, "Single-shot focus mCaptureSession.capture: ", ex);
                return;
            }
        }

        restartPreview(mBackgroundHandler);
    }

    @Override
    public boolean takePhoto(final long callbackId) {
        Log.d(TAG, "takePhoto()");
        assert mLooper == Looper.myLooper() : "called on wrong thread";

        final CrPhotoReaderListener photoReaderListener = new CrPhotoReaderListener(callbackId);
        mPhotoReader.setOnImageAvailableListener(photoReaderListener, mBackgroundHandler);

        CaptureRequest.Builder photoRequestBuilder = null;
        try {
            photoRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException ex) {
            Log.e(TAG, "createCaptureRequest() error ", ex);
            return false;
        }
        if (photoRequestBuilder == null) {
            Log.e(TAG, "photoRequestBuilder error");
            return false;
        }
        photoRequestBuilder.addTarget(mPhotoReader.getSurface());
        photoRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getCameraRotation());

        configureCommonCaptureSettings(photoRequestBuilder);

        // If there is no flash configured, we can just capture using |photoRequestBuilder| and let
        // the results be collected in |photoReaderListener|.
        if (mFillLightMode == AndroidFillLightMode.OFF || mTorch) {
            try {
                mCaptureSession.capture(photoRequestBuilder.build(), null, mBackgroundHandler);
            } catch (CameraAccessException | IllegalArgumentException | SecurityException ex) {
                Log.e(TAG, "mCaptureSession.capture() " + ex);
                return false;
            }
            return true;
        }

        // To use the flash we need to trigger a precapture sequence on a still capture session
        // described by |precaptureRequestBuilder|.
        CaptureRequest.Builder precaptureRequestBuilder = null;
        try {
            precaptureRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException ex) {
            Log.e(TAG, "createCaptureRequest() error ", ex);
            return false;
        }
        if (precaptureRequestBuilder == null) {
            Log.e(TAG, "precaptureRequestBuilder is null");
            return false;
        }

        precaptureRequestBuilder.addTarget(mPrecaptureSurface);

        precaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                photoRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE));
        precaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

        final long precaptureTimestamp = SystemClock.elapsedRealtime();
        final CaptureRequest photoRequest = photoRequestBuilder.build();
        final CameraCaptureSession.CaptureCallback precaptureStateObserver =
                new CameraCaptureSession.CaptureCallback() {
                    private boolean mPhotoCaptureTriggered = false;

                    @Override
                    public void onCaptureCompleted(CameraCaptureSession session,
                            CaptureRequest request, TotalCaptureResult result) {
                        process(result);
                    }

                    @Override
                    public void onCaptureProgressed(CameraCaptureSession session,
                            CaptureRequest request, CaptureResult partialResult) {
                        process(partialResult);
                    }

                    void process(CaptureResult result) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null) return;

                        // After the first convergence, discard eventual subsequent fragments.
                        if (mPhotoCaptureTriggered) return;

                        boolean timedOut = (SystemClock.elapsedRealtime() - precaptureTimestamp)
                                > PRECAPTURE_TIMEOUT_MS;

                        // Wait until the AE routine converges or we timeout.
                        // https://developer.android.com/reference/android/hardware/camera2/CaptureResult.html#CONTROL_AE_STATE
                        if (aeState == CaptureRequest.CONTROL_AE_STATE_CONVERGED
                                || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                                || timedOut) {
                            mPhotoCaptureTriggered = true;

                            try {
                                mCaptureSession.capture(photoRequest, null, null);
                            } catch (CameraAccessException | IllegalArgumentException
                                    | SecurityException ex) {
                                Log.e(TAG, "mCaptureSession.capture(): " + ex);
                            }
                        }
                    }
                };

        try {
            mCaptureSession.capture(
                    precaptureRequestBuilder.build(), precaptureStateObserver, mBackgroundHandler);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException ex) {
            Log.e(TAG, "takePhoto() - mCaptureSession.setRepeatingRequest()" + ex);
            return false;
        }
        return true;
    }

    @Override
    public void deallocate() {
        Log.d(TAG, "deallocate");
    }
}
