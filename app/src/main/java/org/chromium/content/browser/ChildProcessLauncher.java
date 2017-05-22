// Copyright 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.Surface;

import org.chromium.base.CommandLine;
import org.chromium.base.CpuFeatures;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.TraceEvent;
import org.chromium.base.UnguessableToken;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.library_loader.Linker;
import org.chromium.content.app.ChromiumLinkerParams;
import org.chromium.content.app.PrivilegedProcessService;
import org.chromium.content.app.SandboxedProcessService;
import org.chromium.content.common.ContentSwitches;
import org.chromium.content.common.FileDescriptorInfo;
import org.chromium.content.common.IChildProcessCallback;
import org.chromium.content.common.SurfaceWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class provides the method to start/stop ChildProcess called by native.
 *
 * Note about threading. The threading here is complicated and not well documented.
 * Code can run on these threads: UI, Launcher, async thread pool, binder, and one-off
 * background threads.
 */
@JNINamespace("content")
public class ChildProcessLauncher {
    private static final String TAG = "ChildProcLauncher";

    static final int CALLBACK_FOR_UNKNOWN_PROCESS = 0;
    static final int CALLBACK_FOR_GPU_PROCESS = 1;
    static final int CALLBACK_FOR_RENDERER_PROCESS = 2;
    static final int CALLBACK_FOR_UTILITY_PROCESS = 3;

    private static class ChildConnectionAllocator {
        // Connections to services. Indices of the array correspond to the service numbers.
        private final ChildProcessConnection[] mChildProcessConnections;

        // The list of free (not bound) service indices.
        // SHOULD BE ACCESSED WITH mConnectionLock.
        private final ArrayList<Integer> mFreeConnectionIndices;
        private final Object mConnectionLock = new Object();

        private final String mChildClassName;
        private final boolean mInSandbox;
        // Each Allocator keeps a queue for the pending spawn data. Once a connection is free, we
        // dequeue the pending spawn data from the same allocator as the connection.
        // SHOULD BE ACCESSED WITH mConnectionLock.
        private final Queue<SpawnData> mPendingSpawnQueue = new LinkedList<>();

        public ChildConnectionAllocator(boolean inSandbox, int numChildServices,
                String serviceClassName) {
            mChildProcessConnections = new ChildProcessConnectionImpl[numChildServices];
            mFreeConnectionIndices = new ArrayList<Integer>(numChildServices);
            for (int i = 0; i < numChildServices; i++) {
                mFreeConnectionIndices.add(i);
            }
            mChildClassName = serviceClassName;
            mInSandbox = inSandbox;
        }

        // Allocate or enqueue. If there are no free slots, return null and enqueue the spawn data.
        public ChildProcessConnection allocate(SpawnData spawnData,
                ChildProcessConnection.DeathCallback deathCallback,
                Bundle childProcessCommonParameters, boolean alwaysInForeground) {
            assert spawnData.inSandbox() == mInSandbox;
            synchronized (mConnectionLock) {
                if (mFreeConnectionIndices.isEmpty()) {
                    Log.d(TAG, "Ran out of services to allocate.");
                    if (!spawnData.forWarmUp()) {
                        mPendingSpawnQueue.add(spawnData);
                    }
                    return null;
                }
                int slot = mFreeConnectionIndices.remove(0);
                assert mChildProcessConnections[slot] == null;
                mChildProcessConnections[slot] =
                        new ChildProcessConnectionImpl(spawnData.context(), slot, mInSandbox,
                                deathCallback, mChildClassName, childProcessCommonParameters,
                                alwaysInForeground, spawnData.getCreationParams());
                Log.d(TAG, "Allocator allocated a connection, sandbox: %b, slot: %d", mInSandbox,
                        slot);
                return mChildProcessConnections[slot];
            }
        }

        // Also return the first SpawnData in the pending queue, if any.
        public SpawnData free(ChildProcessConnection connection) {
            synchronized (mConnectionLock) {
                int slot = connection.getServiceNumber();
                if (mChildProcessConnections[slot] != connection) {
                    int occupier = mChildProcessConnections[slot] == null
                            ? -1 : mChildProcessConnections[slot].getServiceNumber();
                    Log.e(TAG, "Unable to find connection to free in slot: %d "
                            + "already occupied by service: %d", slot, occupier);
                    assert false;
                } else {
                    mChildProcessConnections[slot] = null;
                    assert !mFreeConnectionIndices.contains(slot);
                    mFreeConnectionIndices.add(slot);
                    Log.d(TAG, "Allocator freed a connection, sandbox: %b, slot: %d", mInSandbox,
                            slot);
                }
                return mPendingSpawnQueue.poll();
            }
        }

        public boolean isFreeConnectionAvailable() {
            synchronized (mConnectionLock) {
                return !mFreeConnectionIndices.isEmpty();
            }
        }

        /** @return the count of connections managed by the allocator */
        @VisibleForTesting
        int allocatedConnectionsCountForTesting() {
            return mChildProcessConnections.length - mFreeConnectionIndices.size();
        }

        @VisibleForTesting
        ChildProcessConnection[] connectionArrayForTesting() {
            return mChildProcessConnections;
        }

        @VisibleForTesting
        void enqueuePendingQueueForTesting(SpawnData spawnData) {
            synchronized (mConnectionLock) {
                mPendingSpawnQueue.add(spawnData);
            }
        }

        @VisibleForTesting
        int pendingSpawnsCountForTesting() {
            synchronized (mConnectionLock) {
                return mPendingSpawnQueue.size();
            }
        }
    }

    private static class SpawnData {
        private final boolean mForWarmup; // Do not queue up if failed.
        private final Context mContext;
        private final String[] mCommandLine;
        private final int mChildProcessId;
        private final FileDescriptorInfo[] mFilesToBeMapped;
        private final long mClientContext;
        private final int mCallbackType;
        private final boolean mInSandbox;
        private final ChildProcessCreationParams mCreationParams;

        private SpawnData(boolean forWarmUp, Context context, String[] commandLine,
                int childProcessId, FileDescriptorInfo[] filesToBeMapped, long clientContext,
                int callbackType, boolean inSandbox, ChildProcessCreationParams creationParams) {
            mForWarmup = forWarmUp;
            mContext = context;
            mCommandLine = commandLine;
            mChildProcessId = childProcessId;
            mFilesToBeMapped = filesToBeMapped;
            mClientContext = clientContext;
            mCallbackType = callbackType;
            mInSandbox = inSandbox;
            mCreationParams = creationParams;
        }

        private boolean forWarmUp() {
            return mForWarmup;
        }

        private Context context() {
            return mContext;
        }
        private String[] commandLine() {
            return mCommandLine;
        }
        private int childProcessId() {
            return mChildProcessId;
        }
        private FileDescriptorInfo[] filesToBeMapped() {
            return mFilesToBeMapped;
        }
        private long clientContext() {
            return mClientContext;
        }
        private int callbackType() {
            return mCallbackType;
        }
        private boolean inSandbox() {
            return mInSandbox;
        }
        private ChildProcessCreationParams getCreationParams() {
            return mCreationParams;
        }
    }

    // Service class for child process.
    // Map from package name to ChildConnectionAllocator.
    private static Map<String, ChildConnectionAllocator> sSandboxedChildConnectionAllocatorMap;
    // As the default value it uses PrivilegedProcessService0.
    private static ChildConnectionAllocator sPrivilegedChildConnectionAllocator;

    private static final String NUM_SANDBOXED_SERVICES_KEY =
            "org.chromium.content.browser.NUM_SANDBOXED_SERVICES";
    private static final String NUM_PRIVILEGED_SERVICES_KEY =
            "org.chromium.content.browser.NUM_PRIVILEGED_SERVICES";
    private static final String SANDBOXED_SERVICES_NAME_KEY =
            "org.chromium.content.browser.SANDBOXED_SERVICES_NAME";
    // Overrides the number of available sandboxed services.
    @VisibleForTesting
    public static final String SWITCH_NUM_SANDBOXED_SERVICES_FOR_TESTING = "num-sandboxed-services";
    public static final String SWITCH_SANDBOXED_SERVICES_NAME_FOR_TESTING =
            "sandboxed-services-name";

    private static int getNumberOfServices(Context context, boolean inSandbox, String packageName) {
        int numServices = -1;
        if (inSandbox
                && CommandLine.getInstance().hasSwitch(
                           SWITCH_NUM_SANDBOXED_SERVICES_FOR_TESTING)) {
            String value = CommandLine.getInstance().getSwitchValue(
                    SWITCH_NUM_SANDBOXED_SERVICES_FOR_TESTING);
            if (!TextUtils.isEmpty(value)) {
                try {
                    numServices = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "The value of --num-sandboxed-services is formatted wrongly: "
                                    + value);
                }
            }
        } else {
            try {
                PackageManager packageManager = context.getPackageManager();
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName,
                        PackageManager.GET_META_DATA);
                if (appInfo.metaData != null) {
                    numServices = appInfo.metaData.getInt(inSandbox
                            ? NUM_SANDBOXED_SERVICES_KEY : NUM_PRIVILEGED_SERVICES_KEY, -1);
                }
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException("Could not get application info");
            }
        }
        if (numServices < 0) {
            throw new RuntimeException("Illegal meta data value for number of child services");
        }
        return numServices;
    }

    private static String getClassNameOfService(Context context, boolean inSandbox,
            String packageName) {
        if (!inSandbox) {
            return PrivilegedProcessService.class.getName();
        }
        if (CommandLine.getInstance().hasSwitch(SWITCH_SANDBOXED_SERVICES_NAME_FOR_TESTING)) {
            return CommandLine.getInstance().getSwitchValue(
                    SWITCH_SANDBOXED_SERVICES_NAME_FOR_TESTING);
        }

        String serviceName = null;
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo =
                    packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                serviceName = appInfo.metaData.getString(SANDBOXED_SERVICES_NAME_KEY);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Could not get application info.");
        }

        if (serviceName != null) {
            // Check that the service exists.
            try {
                PackageManager packageManager = context.getPackageManager();
                // PackageManager#getServiceInfo() throws an exception if the service does not
                // exist.
                packageManager.getServiceInfo(new ComponentName(packageName, serviceName + "0"), 0);
                return serviceName;
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(
                        "Illegal meta data value: the child service doesn't exist");
            }
        }
        return SandboxedProcessService.class.getName();
    }

    private static void initConnectionAllocatorsIfNecessary(
            Context context, boolean inSandbox, String packageName) {
        // TODO(mariakhomenko): Uses an Object to lock the access.
        synchronized (ChildProcessLauncher.class) {
            if (inSandbox) {
                if (sSandboxedChildConnectionAllocatorMap == null) {
                    sSandboxedChildConnectionAllocatorMap =
                            new ConcurrentHashMap<String, ChildConnectionAllocator>();
                }
                if (!sSandboxedChildConnectionAllocatorMap.containsKey(packageName)) {
                    Log.w(TAG, "Create a new ChildConnectionAllocator with package name = %s,"
                                    + " inSandbox = true",
                            packageName);
                    sSandboxedChildConnectionAllocatorMap.put(packageName,
                            new ChildConnectionAllocator(true,
                                    getNumberOfServices(context, true, packageName),
                                    getClassNameOfService(context, true, packageName)));
                }
            } else if (sPrivilegedChildConnectionAllocator == null) {
                sPrivilegedChildConnectionAllocator = new ChildConnectionAllocator(false,
                        getNumberOfServices(context, false, packageName),
                        getClassNameOfService(context, false, packageName));
            }
            // TODO(pkotwicz|hanxi): Figure out when old allocators should be removed from
            // {@code sSandboxedChildConnectionAllocatorMap}.
        }
    }

    /**
     * Note: please make sure that the Allocator has been initialized before calling this function.
     * Otherwise, always calls {@link initConnectionAllocatorsIfNecessary} first.
     */
    private static ChildConnectionAllocator getConnectionAllocator(
            String packageName, boolean inSandbox) {
        if (!inSandbox) {
            return sPrivilegedChildConnectionAllocator;
        }
        return sSandboxedChildConnectionAllocatorMap.get(packageName);
    }

    private static ChildConnectionAllocator getAllocatorForTesting(
            Context context, String packageName, boolean inSandbox) {
        initConnectionAllocatorsIfNecessary(context, inSandbox, packageName);
        return getConnectionAllocator(packageName, inSandbox);
    }

    private static ChildProcessConnection allocateConnection(
            SpawnData spawnData, Bundle childProcessCommonParams, boolean alwaysInForeground) {
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
        final Context context = spawnData.context();
        final boolean inSandbox = spawnData.inSandbox();
        String packageName =
                creationParams != null ? creationParams.getPackageName() : context.getPackageName();
        initConnectionAllocatorsIfNecessary(context, inSandbox, packageName);
        return getConnectionAllocator(packageName, inSandbox)
                .allocate(spawnData, deathCallback, childProcessCommonParams, alwaysInForeground);
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

    private static Bundle createCommonParamsBundle(ChildProcessCreationParams params) {
        Bundle commonParams = new Bundle();
        commonParams.putParcelable(
                ChildProcessConstants.EXTRA_LINKER_PARAMS, getLinkerParamsForNewConnection());
        final boolean bindToCallerCheck = params == null ? false : params.getBindToCallerCheck();
        commonParams.putBoolean(ChildProcessConstants.EXTRA_BIND_TO_CALLER, bindToCallerCheck);
        return commonParams;
    }

    private static ChildProcessConnection allocateBoundConnection(SpawnData spawnData,
            boolean alwaysInForeground, ChildProcessConnection.StartCallback startCallback) {
        final Context context = spawnData.context();
        final boolean inSandbox = spawnData.inSandbox();
        final ChildProcessCreationParams creationParams = spawnData.getCreationParams();
        ChildProcessConnection connection = allocateConnection(spawnData,
                createCommonParamsBundle(spawnData.getCreationParams()), alwaysInForeground);
        if (connection != null) {
            connection.start(startCallback);

            String packageName = creationParams != null ? creationParams.getPackageName()
                                                        : context.getPackageName();
            if (inSandbox
                    && !getConnectionAllocator(packageName, inSandbox)
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
                final SpawnData pendingSpawn = freeConnectionAndDequeuePending(conn);
                if (pendingSpawn != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            startInternal(pendingSpawn.context(), pendingSpawn.commandLine(),
                                    pendingSpawn.childProcessId(), pendingSpawn.filesToBeMapped(),
                                    pendingSpawn.clientContext(), pendingSpawn.callbackType(),
                                    pendingSpawn.inSandbox(), pendingSpawn.getCreationParams());
                        }
                    }).start();
                }
            }
        }, FREE_CONNECTION_DELAY_MILLIS);
    }

    private static SpawnData freeConnectionAndDequeuePending(ChildProcessConnection conn) {
        ChildConnectionAllocator allocator = getConnectionAllocator(
                conn.getPackageName(), conn.isInSandbox());
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

    @VisibleForTesting
    public static void setBindingManagerForTesting(BindingManager manager) {
        sBindingManager = manager;
    }

    /** @return true iff the child process is protected from out-of-memory killing */
    @CalledByNative
    private static boolean isOomProtected(int pid) {
        return sBindingManager.isOomProtected(pid);
    }

    /**
     * Sets the visibility of the child process when it changes or when it is determined for the
     * first time.
     */
    @CalledByNative
    public static void setInForeground(int pid, boolean inForeground) {
        sBindingManager.setInForeground(pid, inForeground);
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
    public static void startModerateBindingManagement(
            Context context, boolean moderateBindingTillBackgrounded) {
        sBindingManager.startModerateBindingManagement(context,
                getNumberOfServices(context, true, context.getPackageName()),
                moderateBindingTillBackgrounded);
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
     * in parallel to other startup work. Must not be called on the UI thread. Spare connection is
     * created in sandboxed child process.
     * @param context the application context used for the connection.
     */
    public static void warmUp(Context context) {
        synchronized (sSpareConnectionLock) {
            assert !ThreadUtils.runningOnUiThread();
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
                SpawnData spawnData = new SpawnData(true /* forWarmUp*/, context,
                        null /* commandLine */, -1 /* child process id */,
                        null /* filesToBeMapped */, 0 /* clientContext */,
                        CALLBACK_FOR_RENDERER_PROCESS, true /* inSandbox */, params);
                sSpareSandboxedConnection = allocateBoundConnection(
                        spawnData, false /* alwaysInForeground */, startCallback);
            }
        }
    }

    @CalledByNative
    private static FileDescriptorInfo makeFdInfo(
            int id, int fd, boolean autoClose, long offset, long size) {
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
     * @param clientContext Arbitrary parameter used by the client to distinguish this connection.
     */
    // TODO(boliu): All tests should use this over startForTesting.
    @VisibleForTesting
    @CalledByNative
    static void start(Context context, int paramId, final String[] commandLine, int childProcessId,
            FileDescriptorInfo[] filesToBeMapped, long clientContext) {
        int callbackType = CALLBACK_FOR_UNKNOWN_PROCESS;
        boolean inSandbox = true;
        String processType =
                ContentSwitches.getSwitchValue(commandLine, ContentSwitches.SWITCH_PROCESS_TYPE);
        ChildProcessCreationParams params = ChildProcessCreationParams.get(paramId);
        if (paramId != ChildProcessCreationParams.DEFAULT_ID && params == null) {
            throw new RuntimeException("CreationParams id " + paramId + " not found");
        }
        if (ContentSwitches.SWITCH_RENDERER_PROCESS.equals(processType)) {
            callbackType = CALLBACK_FOR_RENDERER_PROCESS;
        } else {
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
                callbackType = CALLBACK_FOR_GPU_PROCESS;
                inSandbox = false;
            } else if (ContentSwitches.SWITCH_UTILITY_PROCESS.equals(processType)) {
                // We only support sandboxed right now.
                callbackType = CALLBACK_FOR_UTILITY_PROCESS;
            } else {
                assert false;
            }
        }

        startInternal(context, commandLine, childProcessId, filesToBeMapped, clientContext,
                callbackType, inSandbox, params);
    }

    private static ChildProcessConnection startInternal(
            final Context context,
            final String[] commandLine,
            final int childProcessId,
            final FileDescriptorInfo[] filesToBeMapped,
            final long clientContext,
            final int callbackType,
            final boolean inSandbox,
            final ChildProcessCreationParams creationParams) {
        try {
            TraceEvent.begin("ChildProcessLauncher.startInternal");

            ChildProcessConnection allocatedConnection = null;
            String packageName = creationParams != null ? creationParams.getPackageName()
                    : context.getPackageName();
            synchronized (sSpareConnectionLock) {
                if (inSandbox && sSpareSandboxedConnection != null
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
                boolean alwaysInForeground = false;
                if (callbackType == CALLBACK_FOR_GPU_PROCESS) alwaysInForeground = true;
                ChildProcessConnection.StartCallback startCallback =
                        new ChildProcessConnection.StartCallback() {
                            @Override
                            public void onChildStarted() {}

                            @Override
                            public void onChildStartFailed() {
                                Log.e(TAG, "ChildProcessConnection.start failed, trying again");
                                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        // The child process may already be bound to another client
                                        // (this can happen if multi-process WebView is used in more
                                        // than one process), so try starting the process again.
                                        // This connection that failed to start has not been freed,
                                        // so a new bound connection will be allocated.
                                        startInternal(context, commandLine, childProcessId,
                                                filesToBeMapped, clientContext, callbackType,
                                                inSandbox, creationParams);
                                    }
                                });
                            }
                        };

                SpawnData spawnData = new SpawnData(false /* forWarmUp */, context, commandLine,
                        childProcessId, filesToBeMapped, clientContext, callbackType, inSandbox,
                        creationParams);
                allocatedConnection =
                        allocateBoundConnection(spawnData, alwaysInForeground, startCallback);
                if (allocatedConnection == null) {
                    return null;
                }
            }

            Log.d(TAG, "Setting up connection to process: slot=%d",
                    allocatedConnection.getServiceNumber());
            triggerConnectionSetup(allocatedConnection, commandLine, childProcessId,
                    filesToBeMapped, callbackType, clientContext);
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
            String[] commandLine, FileDescriptorInfo[] filesToBeMapped, Bundle sharedRelros) {
        Bundle bundle = new Bundle();
        bundle.putStringArray(ChildProcessConstants.EXTRA_COMMAND_LINE, commandLine);
        bundle.putParcelableArray(ChildProcessConstants.EXTRA_FILES, filesToBeMapped);
        bundle.putInt(ChildProcessConstants.EXTRA_CPU_COUNT, CpuFeatures.getCount());
        bundle.putLong(ChildProcessConstants.EXTRA_CPU_FEATURES, CpuFeatures.getMask());
        bundle.putBundle(Linker.EXTRA_LINKER_SHARED_RELROS, sharedRelros);
        return bundle;
    }

    @VisibleForTesting
    static void triggerConnectionSetup(
            final ChildProcessConnection connection,
            String[] commandLine,
            int childProcessId,
            FileDescriptorInfo[] filesToBeMapped,
            final int callbackType,
            final long clientContext) {
        ChildProcessConnection.ConnectionCallback connectionCallback =
                new ChildProcessConnection.ConnectionCallback() {
                    @Override
                    public void onConnected(int pid) {
                        Log.d(TAG, "on connect callback, pid=%d context=%d callbackType=%d",
                                pid, clientContext, callbackType);
                        if (pid != NULL_PROCESS_HANDLE) {
                            sBindingManager.addNewConnection(pid, connection);
                            sServiceMap.put(pid, connection);
                        }
                        // If the connection fails and pid == 0, the Java-side cleanup was already
                        // handled by DeathCallback. We still have to call back to native for
                        // cleanup there.
                        if (clientContext != 0) {  // Will be 0 in Java instrumentation tests.
                            nativeOnChildProcessStarted(clientContext, pid);
                        }
                    }
                };

        assert callbackType != CALLBACK_FOR_UNKNOWN_PROCESS;
        connection.setupConnection(commandLine,
                                   filesToBeMapped,
                                   createCallback(childProcessId, callbackType),
                                   connectionCallback,
                                   Linker.getInstance().getSharedRelros());
    }

    /**
     * Terminates a child process. This may be called from any thread.
     *
     * @param pid The pid (process handle) of the service connection obtained from {@link #start}.
     */
    @CalledByNative
    static void stop(int pid) {
        Log.d(TAG, "stopping child connection: pid=%d", pid);
        ChildProcessConnection connection = sServiceMap.remove(pid);
        if (connection == null) {
            logPidWarning(pid, "Tried to stop non-existent connection");
            return;
        }
        sBindingManager.clearConnection(pid);
        connection.stop();
        freeConnection(connection);
    }

    /**
     * This implementation is used to receive callbacks from the remote service.
     */
    private static IChildProcessCallback createCallback(
            final int childProcessId, final int callbackType) {
        return new IChildProcessCallback.Stub() {
            @Override
            public void forwardSurfaceForSurfaceRequest(
                    UnguessableToken requestToken, Surface surface) {
                // Do not allow a malicious renderer to connect to a producer. This is only used
                // from stream textures managed by the GPU process.
                if (callbackType != CALLBACK_FOR_GPU_PROCESS) {
                    Log.e(TAG, "Illegal callback for non-GPU process.");
                    return;
                }

                nativeCompleteScopedSurfaceRequest(requestToken, surface);
            }

            @Override
            public SurfaceWrapper getViewSurface(int surfaceId) {
                // Do not allow a malicious renderer to get to our view surface.
                if (callbackType != CALLBACK_FOR_GPU_PROCESS) {
                    Log.e(TAG, "Illegal callback for non-GPU process.");
                    return null;
                }
                Surface surface = ChildProcessLauncher.nativeGetViewSurface(surfaceId);
                if (surface == null) {
                    return null;
                }
                return new SurfaceWrapper(surface);
            }
        };
    }

    static void logPidWarning(int pid, String message) {
        // This class is effectively a no-op in single process mode, so don't log warnings there.
        if (pid > 0 && !nativeIsSingleProcess()) {
            Log.w(TAG, "%s, pid=%d", message, pid);
        }
    }

    @VisibleForTesting
    public static ChildProcessConnection startForTesting(Context context, String[] commandLine,
            FileDescriptorInfo[] filesToMap, ChildProcessCreationParams params) {
        return startInternal(context, commandLine, 0, filesToMap, 0,
                CALLBACK_FOR_RENDERER_PROCESS, true, params);
    }

    @VisibleForTesting
    static ChildProcessConnection allocateBoundConnectionForTesting(Context context,
            ChildProcessCreationParams creationParams) {
        return allocateBoundConnection(
                new SpawnData(false /* forWarmUp */, context, null /* commandLine */,
                        0 /* childProcessId */, null /* filesToBeMapped */, 0 /* clientContext */,
                        CALLBACK_FOR_RENDERER_PROCESS, true /* inSandbox */, creationParams),
                false /* alwaysInForeground */, null);
    }

    @VisibleForTesting
    static ChildProcessConnection allocateConnectionForTesting(
            Context context, ChildProcessCreationParams creationParams) {
        return allocateConnection(
                new SpawnData(false /* forWarmUp */, context, null /* commandLine */,
                        0 /* childProcessId */, null /* filesToBeMapped */, 0 /* clientContext */,
                        CALLBACK_FOR_RENDERER_PROCESS, true /* inSandbox */, creationParams),
                createCommonParamsBundle(creationParams), false);
    }

    /**
     * Queue up a spawn requests for testing.
     */
    @VisibleForTesting
    static void enqueuePendingSpawnForTesting(Context context, String[] commandLine,
            ChildProcessCreationParams creationParams, boolean inSandbox) {
        String packageName = creationParams != null ? creationParams.getPackageName()
                : context.getPackageName();
        ChildConnectionAllocator allocator =
                getAllocatorForTesting(context, packageName, inSandbox);
        allocator.enqueuePendingQueueForTesting(new SpawnData(false /* forWarmUp*/, context,
                commandLine, 1, new FileDescriptorInfo[0], 0, CALLBACK_FOR_RENDERER_PROCESS, true,
                creationParams));
    }

    /**
     * @return the number of sandboxed connections of given {@link packageName} managed by the
     * allocator.
     */
    @VisibleForTesting
    static int allocatedSandboxedConnectionsCountForTesting(Context context, String packageName) {
        initConnectionAllocatorsIfNecessary(context, true, packageName);
        return sSandboxedChildConnectionAllocatorMap.get(packageName)
                .allocatedConnectionsCountForTesting();
    }

    /**
     * @return gets the service connection array for a specific package name.
     */
    @VisibleForTesting
    static ChildProcessConnection[] getSandboxedConnectionArrayForTesting(String packageName) {
        return sSandboxedChildConnectionAllocatorMap.get(packageName).connectionArrayForTesting();
    }

    /** @return the count of services set up and working */
    @VisibleForTesting
    static int connectedServicesCountForTesting() {
        return sServiceMap.size();
    }

    /**
     * @param context The context.
     * @param packageName The package name of the {@link ChildProcessAlocator}.
     * @param inSandbox Whether the connection is sandboxed.
     * @return the count of pending spawns in the queue.
     */
    @VisibleForTesting
    static int pendingSpawnsCountForTesting(
            Context context, String packageName, boolean inSandbox) {
        ChildConnectionAllocator allocator =
                getAllocatorForTesting(context, packageName, inSandbox);
        return allocator.pendingSpawnsCountForTesting();
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

    private static native void nativeOnChildProcessStarted(long clientContext, int pid);
    private static native void nativeCompleteScopedSurfaceRequest(
            UnguessableToken requestToken, Surface surface);
    private static native boolean nativeIsSingleProcess();
    private static native Surface nativeGetViewSurface(int surfaceId);
}
