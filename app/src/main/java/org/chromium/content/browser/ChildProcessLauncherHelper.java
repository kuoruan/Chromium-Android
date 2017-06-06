// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.process_launcher.ChildProcessCreationParams;
import org.chromium.base.process_launcher.FileDescriptorInfo;

import java.io.IOException;

/**
 * This is the java counterpart to ChildProcessLauncherHelper. It is owned by native side and
 * has an explicit destroy method.
 * Each public or jni methods should have explicit documentation on what threads they are called.
 */
@JNINamespace("content::internal")
class ChildProcessLauncherHelper {
    private static final String TAG = "ChildProcLH";

    // Note native pointer is only guaranteed live until nativeOnChildProcessStarted.
    private long mNativeChildProcessLauncherHelper;
    private int mPid;

    @CalledByNative
    private static FileDescriptorInfo makeFdInfo(
            int id, int fd, boolean autoClose, long offset, long size) {
        assert LauncherThread.runningOnLauncherThread();
        ParcelFileDescriptor pFd;
        if (autoClose) {
            // Adopt the FD, it will be closed when we close the ParcelFileDescriptor.
            pFd = ParcelFileDescriptor.adoptFd(fd);
        } else {
            try {
                pFd = ParcelFileDescriptor.fromFd(fd);
            } catch (IOException e) {
                Log.e(TAG, "Invalid FD provided for process connection, aborting connection.", e);
                return null;
            }
        }
        return new FileDescriptorInfo(id, pFd, offset, size);
    }

    @CalledByNative
    private static ChildProcessLauncherHelper create(long nativePointer, Context context,
            int paramId, final String[] commandLine, int childProcessId,
            FileDescriptorInfo[] filesToBeMapped) {
        assert LauncherThread.runningOnLauncherThread();
        return new ChildProcessLauncherHelper(
                nativePointer, context, paramId, commandLine, childProcessId, filesToBeMapped);
    }

    private ChildProcessLauncherHelper(long nativePointer, Context context, int paramId,
            final String[] commandLine, int childProcessId, FileDescriptorInfo[] filesToBeMapped) {
        assert LauncherThread.runningOnLauncherThread();
        mNativeChildProcessLauncherHelper = nativePointer;

        ChildProcessLauncher.start(context, paramId, commandLine, childProcessId, filesToBeMapped,
                new ChildProcessLauncher.LaunchCallback() {
                    @Override
                    public void onChildProcessStarted(int pid) {
                        mPid = pid;
                        if (mNativeChildProcessLauncherHelper != 0) {
                            nativeOnChildProcessStarted(mNativeChildProcessLauncherHelper, pid);
                        }
                        mNativeChildProcessLauncherHelper = 0;
                    }
                });
    }

    // Called on client (UI or IO) thread.
    @CalledByNative
    private boolean isOomProtected() {
        return ChildProcessLauncher.getBindingManager().isOomProtected(mPid);
    }

    @CalledByNative
    private void setInForeground(int pid, boolean inForeground) {
        assert LauncherThread.runningOnLauncherThread();
        assert mPid == pid;
        ChildProcessLauncher.getBindingManager().setInForeground(mPid, inForeground);
    }

    // Called on client (UI or IO) thread and launcher thread.
    @CalledByNative
    private static void stop(int pid) {
        ChildProcessLauncher.stop(pid);
    }

    // Called on UI thread.
    @CalledByNative
    private static int getNumberOfRendererSlots() {
        final ChildProcessCreationParams params = ChildProcessCreationParams.getDefault();
        final Context context = ContextUtils.getApplicationContext();
        final boolean inSandbox = true;
        final String packageName =
                params == null ? context.getPackageName() : params.getPackageName();
        try {
            return ChildConnectionAllocator.getNumberOfServices(context, inSandbox, packageName);
        } catch (RuntimeException e) {
            // Unittest packages do not declare services. Some tests require a realistic number
            // to test child process policies, so pick a high-ish number here.
            return 65535;
        }
    }

    // Can be called on a number of threads, including launcher, and binder.
    private static native void nativeOnChildProcessStarted(
            long nativeChildProcessLauncherHelper, int pid);
}
