// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import org.chromium.base.CpuFeatures;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.library_loader.Linker;
import org.chromium.base.process_launcher.ChildProcessCreationParams;
import org.chromium.base.process_launcher.FileDescriptorInfo;
import org.chromium.content.app.ChromiumLinkerParams;
import org.chromium.content.common.ContentSwitches;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class provides the method to start/stop ChildProcess called by native.
 *
 * Note about threading. The threading here is complicated and not well documented.
 * Code can run on these threads: UI, Launcher, async thread pool, binder, and one-off
 * background threads.
 */
public class ChildProcessLauncher {
    private static final String TAG = "ChildProcLauncher";

    /**
     * Implemented by ChildProcessLauncherHelper.
     */
    public interface LaunchCallback { void onChildProcessStarted(int pid); }

    private static final boolean SPARE_CONNECTION_ALWAYS_IN_FOREGROUND = false;

    @VisibleForTesting
    static ChildProcessConnection allocateConnection(
            ChildSpawnData spawnData, Bundle childProcessCommonParams, boolean forWarmUp) {
        ChildProcessConnection.DeathCallback deathCallback =
                new ChildProcessConnection.DeathCallback() {
                    @Override
                    public void onChildProcessDied(ChildProcessConnection connection) {
                        if (connection.getPid() != 0) {
                            stop(connection.getPid());
                        } else {
                            freeConnection(connection);
                        }
                    }
                };
        final ChildProcessCreationParams creationParams = spawnData.getCreationParams();
        final Context context = spawnData.getContext();
        final boolean inSandbox = spawnData.isInSandbox();
        String packageName =
                creationParams != null ? creationParams.getPackageName() : context.getPackageName();
        return ChildConnectionAllocator.getAllocator(context, packageName, inSandbox)
                .allocate(spawnData, deathCallback, childProcessCommonParams, !forWarmUp);
    }

    private static boolean sLinkerInitialized;
    private static long sLinkerLoadAddress;

    private static ChromiumLinkerParams getLinkerParamsForNewConnection() {
        if (!sLinkerInitialized) {
            if (Linker.isUsed()) {
                sLinkerLoadAddress = Linker.getInstance().getBaseLoadAddress();
                if (sLinkerLoadAddress == 0) {
                    Log.i(TAG, "Shared RELRO support disabled!");
                }
            }
            sLinkerInitialized = true;
        }

        if (sLinkerLoadAddress == 0) return null;

        // Always wait for the shared RELROs in service processes.
        final boolean waitForSharedRelros = true;
        if (Linker.areTestsEnabled()) {
            Linker linker = Linker.getInstance();
            return new ChromiumLinkerParams(sLinkerLoadAddress,
                                            waitForSharedRelros,
                                            linker.getTestRunnerClassNameForTesting(),
                                            linker.getImplementationForTesting());
        } else {
            return new ChromiumLinkerParams(sLinkerLoadAddress,
                                            waitForSharedRelros);
        }
    }

    @VisibleForTesting
    static Bundle createCommonParamsBundle(ChildProcessCreationParams params) {
        Bundle commonParams = new Bundle();
        commonParams.putParcelable(
                ChildProcessConstants.EXTRA_LINKER_PARAMS, getLinkerParamsForNewConnection());
        final boolean bindToCallerCheck = params == null ? false : params.getBindToCallerCheck();
        commonParams.putBoolean(ChildProcessConstants.EXTRA_BIND_TO_CALLER, bindToCallerCheck);
        return commonParams;
    }

    @VisibleForTesting
    static ChildProcessConnection allocateBoundConnection(ChildSpawnData spawnData,
            ChildProcessConnection.StartCallback startCallback, boolean forWarmUp) {
        assert LauncherThread.runningOnLauncherThread();
        final Context context = spawnData.getContext();
        final boolean inSandbox = spawnData.isInSandbox();
        final ChildProcessCreationParams creationParams = spawnData.getCreationParams();

        ChildProcessConnection connection = allocateConnection(
                spawnData, createCommonParamsBundle(spawnData.getCreationParams()), forWarmUp);
        if (connection != null) {
            connection.start(startCallback);

            String packageName = creationParams != null ? creationParams.getPackageName()
                                                        : context.getPackageName();
            if (inSandbox
                    && !ChildConnectionAllocator.getAllocator(context, packageName, inSandbox)
                                .isFreeConnectionAvailable()) {
                // Proactively releases all the moderate bindings once all the sandboxed services
                // are allocated, which will be very likely to have some of them killed by OOM
                // killer.
                sBindingManager.releaseAllModerateBindings();
            }
        }
        return connection;
    }

    private static final long FREE_CONNECTION_DELAY_MILLIS = 1;

    private static void freeConnection(ChildProcessConnection connection) {
        synchronized (sSpareConnectionLock) {
            if (connection.equals(sSpareSandboxedConnection)) sSpareSandboxedConnection = null;
        }

        // Freeing a service should be delayed. This is so that we avoid immediately reusing the
        // freed service (see http://crbug.com/164069): the framework might keep a service process
        // alive when it's been unbound for a short time. If a new connection to the same service
        // is bound at that point, the process is reused and bad things happen (mostly static
        // variables are set when we don't expect them to).
        final ChildProcessConnection conn = connection;
        ThreadUtils.postOnUiThreadDelayed(new Runnable() {
            @Override
            public void run() {
                final ChildSpawnData pendingSpawn = freeConnectionAndDequeuePending(conn);
                if (pendingSpawn != null) {
                    LauncherThread.post(new Runnable() {
                        @Override
                        public void run() {
                            startInternal(pendingSpawn.getContext(), pendingSpawn.getCommandLine(),
                                    pendingSpawn.getChildProcessId(),
                                    pendingSpawn.getFilesToBeMapped(),
                                    pendingSpawn.getLaunchCallback(),
                                    pendingSpawn.getChildProcessCallback(),
                                    pendingSpawn.isInSandbox(), pendingSpawn.isAlwaysInForeground(),
                                    pendingSpawn.getCreationParams());
                        }
                    });
                }
            }
        }, FREE_CONNECTION_DELAY_MILLIS);
    }

    private static ChildSpawnData freeConnectionAndDequeuePending(ChildProcessConnection conn) {
        // TODO(jcivelli): it should be safe to pass a null Context here as it is used to initialize
        // the ChildConnectionAllocator object and if we are freeing a connection, we must have
        // allocated one previously guaranteeing it is already initialized.
        // When we consolidate ChildProcessLauncher and ChildProcessLauncherHelper, we'll have a
        // context around that we can pass in there.
        ChildConnectionAllocator allocator = ChildConnectionAllocator.getAllocator(
                null /* context */, conn.getPackageName(), conn.isInSandbox());
        assert allocator != null;
        return allocator.free(conn);
    }

    // Represents an invalid process handle; same as base/process/process.h kNullProcessHandle.
    private static final int NULL_PROCESS_HANDLE = 0;

    // Map from pid to ChildService connection.
    private static Map<Integer, ChildProcessConnection> sServiceMap =
            new ConcurrentHashMap<Integer, ChildProcessConnection>();

    // Lock and monitor for these members {{{
    private static final Object sSpareConnectionLock = new Object();
    // A pre-allocated and pre-bound connection ready for connection setup, or null.
    private static ChildProcessConnection sSpareSandboxedConnection;
    // If sSpareSandboxedConnection is not null, this indicates whether the service is
    // ready for connection setup. Wait on the monitor lock to be notified when this
    // state changes. sSpareSandboxedConnection may be null after waiting, if starting
    // the service failed.
    private static boolean sSpareConnectionStarting;
    // }}}

    // Manages oom bindings used to bind chind services.
    private static BindingManager sBindingManager = BindingManagerImpl.createBindingManager();

    // Whether the main application is currently brought to the foreground.
    private static boolean sApplicationInForeground = true;

    // TODO(boliu): This should be internal to content.
    public static BindingManager getBindingManager() {
        return sBindingManager;
    }

    @VisibleForTesting
    public static void setBindingManagerForTesting(BindingManager manager) {
        sBindingManager = manager;
    }

    /**
     * Called when the renderer commits a navigation. This signals a time at which it is safe to
     * rely on renderer visibility signalled through setInForeground. See http://crbug.com/421041.
     */
    public static void determinedVisibility(int pid) {
        sBindingManager.determinedVisibility(pid);
    }

    /**
     * Called when the embedding application is sent to background.
     */
    public static void onSentToBackground() {
        sApplicationInForeground = false;
        sBindingManager.onSentToBackground();
    }

    /**
     * Starts moderate binding management.
     * Note: WebAPKs and non WebAPKs share the same moderate binding pool, so the size of the
     * shared moderate binding pool is always set based on the number of sandboxes processes
     * used by Chrome.
     * @param context Android's context.
     * @param moderateBindingTillBackgrounded true if the BindingManager should add a moderate
     * binding to a render process when it is created and remove the moderate binding when Chrome is
     * sent to the background.
     */
    public static void startModerateBindingManagement(Context context) {
        sBindingManager.startModerateBindingManagement(context,
                ChildConnectionAllocator.getNumberOfServices(
                        context, true, context.getPackageName()));
    }

    /**
     * Called when the embedding application is brought to foreground.
     */
    public static void onBroughtToForeground() {
        sApplicationInForeground = true;
        sBindingManager.onBroughtToForeground();
    }

    /**
     * Returns whether the application is currently in the foreground.
     */
    static boolean isApplicationInForeground() {
        return sApplicationInForeground;
    }

    /**
     * Should be called early in startup so the work needed to spawn the child process can be done
     * in parallel to other startup work. Spare connection is created in sandboxed child process.
     * @param context the application context used for the connection.
     */
    public static void warmUp(final Context context) {
        assert ThreadUtils.runningOnUiThread();
        LauncherThread.post(new Runnable() {
            @Override
            public void run() {
                synchronized (sSpareConnectionLock) {
                    if (sSpareSandboxedConnection == null) {
                        ChildProcessCreationParams params = ChildProcessCreationParams.getDefault();
                        sSpareConnectionStarting = true;

                        ChildProcessConnection.StartCallback startCallback =
                                new ChildProcessConnection.StartCallback() {
                                    @Override
                                    public void onChildStarted() {
                                        synchronized (sSpareConnectionLock) {
                                            sSpareConnectionStarting = false;
                                            sSpareConnectionLock.notify();
                                        }
                                    }

                                    @Override
                                    public void onChildStartFailed() {
                                        Log.e(TAG, "Failed to warm up the spare sandbox service");
                                        synchronized (sSpareConnectionLock) {
                                            sSpareSandboxedConnection = null;
                                            sSpareConnectionStarting = false;
                                            sSpareConnectionLock.notify();
                                        }
                                    }
                                };
                        ChildSpawnData spawnData = new ChildSpawnData(context,
                                null /* commandLine */, -1 /* child process id */,
                                null /* filesToBeMapped */, null /* launchCallback */,
                                null /* child process callback */, true /* inSandbox */,
                                SPARE_CONNECTION_ALWAYS_IN_FOREGROUND, params);
                        sSpareSandboxedConnection = allocateBoundConnection(
                                spawnData, startCallback, true /* forWarmUp */);
                    }
                }
            }
        });
    }

    /**
     * Spawns and connects to a child process. May be called on any thread. It will not block, but
     * will instead callback to {@link #nativeOnChildProcessStarted} when the connection is
     * established. Note this callback will not necessarily be from the same thread (currently it
     * always comes from the main thread).
     *
     * @param context Context used to obtain the application context.
     * @param paramId Key used to retrieve ChildProcessCreationParams.
     * @param commandLine The child process command line argv.
     * @param filesToBeMapped File IDs, FDs, offsets, and lengths to pass through.
     */
    static void start(Context context, int paramId, final String[] commandLine, int childProcessId,
            FileDescriptorInfo[] filesToBeMapped, LaunchCallback launchCallback) {
        assert LauncherThread.runningOnLauncherThread();
        IBinder childProcessCallback = null;
        boolean inSandbox = true;
        boolean alwaysInForeground = false;
        String processType =
                ContentSwitches.getSwitchValue(commandLine, ContentSwitches.SWITCH_PROCESS_TYPE);
        ChildProcessCreationParams params = ChildProcessCreationParams.get(paramId);
        if (paramId != ChildProcessCreationParams.DEFAULT_ID && params == null) {
            throw new RuntimeException("CreationParams id " + paramId + " not found");
        }
        if (!ContentSwitches.SWITCH_RENDERER_PROCESS.equals(processType)) {
            if (params != null && !params.getPackageName().equals(context.getPackageName())) {
                // WebViews and WebAPKs have renderer processes running in their applications.
                // When launching these renderer processes, {@link ChildProcessConnectionImpl}
                // requires the package name of the application which holds the renderer process.
                // Therefore, the package name in ChildProcessCreationParams could be the package
                // name of WebViews, WebAPKs, or Chrome, depending on the host application.
                // Except renderer process, all other child processes should use Chrome's package
                // name. In WebAPK, ChildProcessCreationParams are initialized with WebAPK's
                // package name. Make a copy of the WebAPK's params, but replace the package with
                // Chrome's package to use when initializing a non-renderer processes.
                // TODO(boliu): Should fold into |paramId|. Investigate why this is needed.
                params = new ChildProcessCreationParams(context.getPackageName(),
                        params.getIsExternalService(), params.getLibraryProcessType(),
                        params.getBindToCallerCheck());
            }
            if (ContentSwitches.SWITCH_GPU_PROCESS.equals(processType)) {
                childProcessCallback = new GpuProcessCallback();
                inSandbox = false;
                alwaysInForeground = true;
            } else {
                // We only support sandboxed utility processes now.
                assert ContentSwitches.SWITCH_UTILITY_PROCESS.equals(processType);
            }
        }

        startInternal(context, commandLine, childProcessId, filesToBeMapped, launchCallback,
                childProcessCallback, inSandbox, alwaysInForeground, params);
    }

    @VisibleForTesting
    public static ChildProcessConnection startInternal(final Context context,
            final String[] commandLine, final int childProcessId,
            final FileDescriptorInfo[] filesToBeMapped, final LaunchCallback launchCallback,
            final IBinder childProcessCallback, final boolean inSandbox,
            final boolean alwaysInForeground, final ChildProcessCreationParams creationParams) {
        assert LauncherThread.runningOnLauncherThread();
        try {
            TraceEvent.begin("ChildProcessLauncher.startInternal");

            ChildProcessConnection allocatedConnection = null;
            String packageName = creationParams != null ? creationParams.getPackageName()
                    : context.getPackageName();
            synchronized (sSpareConnectionLock) {
                if (inSandbox && sSpareSandboxedConnection != null
                        && SPARE_CONNECTION_ALWAYS_IN_FOREGROUND == alwaysInForeground
                        && sSpareSandboxedConnection.getPackageName().equals(packageName)
                        // Object identity check for getDefault should be enough. The default is
                        // not supposed to change once set.
                        && creationParams == ChildProcessCreationParams.getDefault()) {
                    while (sSpareConnectionStarting) {
                        try {
                            sSpareConnectionLock.wait();
                        } catch (InterruptedException ex) {
                        }
                    }
                    allocatedConnection = sSpareSandboxedConnection;
                    sSpareSandboxedConnection = null;
                }
            }
            if (allocatedConnection == null) {
                ChildProcessConnection.StartCallback startCallback =
                        new ChildProcessConnection.StartCallback() {
                            @Override
                            public void onChildStarted() {}

                            @Override
                            public void onChildStartFailed() {
                                Log.e(TAG, "ChildProcessConnection.start failed, trying again");
                                LauncherThread.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // The child process may already be bound to another client
                                        // (this can happen if multi-process WebView is used in more
                                        // than one process), so try starting the process again.
                                        // This connection that failed to start has not been freed,
                                        // so a new bound connection will be allocated.
                                        startInternal(context, commandLine, childProcessId,
                                                filesToBeMapped, launchCallback,
                                                childProcessCallback, inSandbox, alwaysInForeground,
                                                creationParams);
                                    }
                                });
                            }
                        };

                ChildSpawnData spawnData = new ChildSpawnData(context, commandLine, childProcessId,
                        filesToBeMapped, launchCallback, childProcessCallback, inSandbox,
                        alwaysInForeground, creationParams);
                allocatedConnection =
                        allocateBoundConnection(spawnData, startCallback, false /* forWarmUp */);
                if (allocatedConnection == null) {
                    return null;
                }
            }

            Log.d(TAG, "Setting up connection to process: slot=%d",
                    allocatedConnection.getServiceNumber());
            triggerConnectionSetup(allocatedConnection, commandLine, childProcessId,
                    filesToBeMapped, childProcessCallback, launchCallback);
            return allocatedConnection;
        } finally {
            TraceEvent.end("ChildProcessLauncher.startInternal");
        }
    }

    /**
     * Create the common bundle to be passed to child processes.
     * @param context Application context.
     * @param commandLine Command line params to be passed to the service.
     * @param linkerParams Linker params to start the service.
     */
    protected static Bundle createsServiceBundle(
            String[] commandLine, FileDescriptorInfo[] filesToBeMapped) {
        Bundle bundle = new Bundle();
        bundle.putStringArray(ChildProcessConstants.EXTRA_COMMAND_LINE, commandLine);
        bundle.putParcelableArray(ChildProcessConstants.EXTRA_FILES, filesToBeMapped);
        bundle.putInt(ChildProcessConstants.EXTRA_CPU_COUNT, CpuFeatures.getCount());
        bundle.putLong(ChildProcessConstants.EXTRA_CPU_FEATURES, CpuFeatures.getMask());
        bundle.putBundle(Linker.EXTRA_LINKER_SHARED_RELROS, Linker.getInstance().getSharedRelros());
        return bundle;
    }

    @VisibleForTesting
    static void triggerConnectionSetup(final ChildProcessConnection connection,
            String[] commandLine, int childProcessId, FileDescriptorInfo[] filesToBeMapped,
            final IBinder childProcessCallback, final LaunchCallback launchCallback) {
        assert LauncherThread.runningOnLauncherThread();
        ChildProcessConnection.ConnectionCallback connectionCallback =
                new ChildProcessConnection.ConnectionCallback() {
                    @Override
                    public void onConnected(int pid) {
                        Log.d(TAG, "on connect callback, pid=%d", pid);
                        if (pid != NULL_PROCESS_HANDLE) {
                            sBindingManager.addNewConnection(pid, connection);
                            sServiceMap.put(pid, connection);
                        }
                        // If the connection fails and pid == 0, the Java-side cleanup was already
                        // handled by DeathCallback. We still have to call back to native for
                        // cleanup there.
                        if (launchCallback != null) { // Will be null in Java instrumentation tests.
                            launchCallback.onChildProcessStarted(pid);
                        }
                    }
                };

        connection.setupConnection(
                commandLine, filesToBeMapped, childProcessCallback, connectionCallback);
    }

    /**
     * Terminates a child process. This may be called from any thread.
     *
     * @param pid The pid (process handle) of the service connection obtained from {@link #start}.
     */
    static void stop(int pid) {
        Log.d(TAG, "stopping child connection: pid=%d", pid);
        ChildProcessConnection connection = sServiceMap.remove(pid);
        if (connection == null) {
            // Can happen for single process.
            return;
        }
        sBindingManager.clearConnection(pid);
        connection.stop();
        freeConnection(connection);
    }

    /** @return the count of services set up and working */
    @VisibleForTesting
    static int connectedServicesCountForTesting() {
        return sServiceMap.size();
    }

    /**
     * Kills the child process for testing.
     * @return true iff the process was killed as expected
     */
    @VisibleForTesting
    public static boolean crashProcessForTesting(int pid) {
        if (sServiceMap.get(pid) == null) return false;

        try {
            ((ChildProcessConnectionImpl) sServiceMap.get(pid)).crashServiceForTesting();
        } catch (RemoteException ex) {
            return false;
        }

        return true;
    }
}
